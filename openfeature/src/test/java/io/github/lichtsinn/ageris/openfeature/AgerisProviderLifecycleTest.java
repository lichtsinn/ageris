/*
 * Copyright 2026 Mario Lichtsinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lichtsinn.ageris.openfeature;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests how {@link AgerisProvider} starts and stops the loader, and which events it sends. The loader refreshes when
 * the test tells it to, so nothing here depends on time. Events arrive on a thread of OpenFeature; the test takes them
 * from a queue in the order they were sent, which also shows that nothing was sent in between.
 */
public class AgerisProviderLifecycleTest {

    private static final String FLAGS = "{ \"feature-flags\": { \"one\": { \"value\": true } } }";
    private static final String MORE_FLAGS = "{ \"feature-flags\": { \"one\": { \"value\": false }, \"two\": { \"value\": true } } }";
    private static final String BAD_FLAGS = "{ \"feature-flags\": { \"one\": { \"value\": \"neither true nor false\" } } }";
    private static final String CONFIGS = "{ \"configs\": { \"color\": { \"value\": \"blue\" } } }";
    private static final String OTHER_CONFIGS = "{ \"configs\": { \"color\": { \"value\": \"green\" } } }";
    private static final String BAD_CONFIGS = "this is not JSON";
    private static final Duration SHORT = Duration.ofMillis(50);

    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    private final AtomicReference<String> flagContent = new AtomicReference<>(FLAGS);
    private final AtomicReference<String> configContent = new AtomicReference<>(CONFIGS);
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private final List<Runnable> refreshes = new ArrayList<>();
    private final List<AgerisProvider> providers = new ArrayList<>();
    private final ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    private FeatureFlagManager flags;
    private JsonConfigManager configs;
    private ConfigurationLoader loader;

    /** Only the providers of this test are shut down: shutting the whole API down would also end its delivery of events. */
    @After
    public void tearDown() {
        providers.forEach(AgerisProvider::shutdown);
    }

    @Test
    public void testInitializationStartsTheLoaderAndWaitsForTheFirstLoad() {
        setUpAgeris(true);
        Assert.assertEquals(State.NOT_LOADED, flags.getStatus().getState());

        api.setProviderAndWait("start", provider());

        Client client = api.getClient("start");
        Assert.assertEquals(ProviderState.READY, client.getProviderState());
        Assert.assertTrue(client.getBooleanValue("one", false));
        Assert.assertEquals("blue", client.getStringValue("color", "default"));
    }

    @Test
    public void testFailedFirstLoadFailsInitializationWithTheReason() {
        flagContent.set(BAD_FLAGS);
        setUpAgeris(true);
        try {
            api.setProviderAndWait("bad", impatientProvider());
            Assert.fail("Expected initialization to fail");
        } catch (OpenFeatureError expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Cannot read one: "));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("/flags.json"));
        }
        Assert.assertEquals(ProviderState.ERROR, api.getClient("bad").getProviderState());
        Assert.assertTrue("A provider in error answers with the default", api.getClient("bad").getBooleanValue("one", true));
    }

    @Test
    public void testProviderRecoversAfterAFailedFirstLoad() throws InterruptedException {
        flagContent.set(BAD_FLAGS);
        setUpAgeris(true);
        try {
            api.setProviderAndWait("heal", impatientProvider());
            Assert.fail("Expected initialization to fail");
        } catch (OpenFeatureError expected) {
            Assert.assertEquals(ProviderState.ERROR, api.getClient("heal").getProviderState());
        }
        Client client = api.getClient("heal");
        client.onProviderReady(details -> events.add("ready"));
        client.onProviderConfigurationChanged(details -> events.add("changed " + sorted(details)));
        client.onProviderStale(details -> events.add("stale " + details.getMessage()));
        Mockito.verify(executor, Mockito.never()).shutdown();

        /* The loader keeps trying. A refresh that fails again changes nothing; the one after the fix does. */
        refresh();
        flagContent.set(FLAGS);
        refresh();

        Assert.assertEquals("ready", nextEvent());
        Assert.assertEquals("changed [one]", nextEvent());
        Assert.assertEquals(ProviderState.READY, client.getProviderState());
        Assert.assertTrue(client.getBooleanValue("one", false));
        flagContent.set(BAD_FLAGS);
        refresh();
        Assert.assertTrue("After recovering, a stale period is reported as usual", nextEvent().startsWith("stale "));
    }

    @Test
    public void testInitializationAfterShutdownIsRefused() {
        setUpAgeris(true);
        AgerisProvider provider = provider();
        api.setProviderAndWait("again", provider);
        provider.shutdown();

        try {
            provider.initialize(null);
            Assert.fail("Expected initialization to be refused");
        } catch (OpenFeatureError expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("was shut down"));
        }
    }

    @Test
    public void testProviderShutDownBeforeItWasEverInitializedCannotBeUsed() {
        setUpAgeris(true);
        AgerisProvider provider = provider();
        provider.shutdown();

        try {
            api.setProviderAndWait("late", provider);
            Assert.fail("Expected initialization to be refused");
        } catch (OpenFeatureError expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("was shut down"));
        }
        Assert.assertTrue("The loader was not started", refreshes.isEmpty());
    }

    @Test
    public void testFirstLoadThatDoesNotHappenInTimeFailsInitialization() {
        setUpAgeris(false);

        try {
            api.setProviderAndWait("slow", impatientProvider());
            Assert.fail("Expected initialization to fail");
        } catch (OpenFeatureError expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("did not all load within PT0.05S"));
        }
    }

    @Test
    public void testSecondInitializationDoesNotStartTheLoaderAgain() {
        setUpAgeris(true);
        AgerisProvider provider = provider();

        provider.initialize(null);
        provider.initialize(null);

        Assert.assertEquals("One scheduled refresh for each of the two managers", 2, refreshes.size());
        Assert.assertTrue(provider.getBooleanEvaluation("one", false, null).getValue());
    }

    @Test
    public void testShutdownStopsTheLoader() {
        setUpAgeris(true);
        AgerisProvider provider = provider();
        api.setProviderAndWait("stop", provider);
        Mockito.verify(executor, Mockito.never()).shutdown();

        provider.shutdown();

        Mockito.verify(executor).shutdown();
    }

    @Test
    public void testShutdownBeforeInitializationStopsNothing() {
        setUpAgeris(true);

        provider().shutdown();

        Mockito.verify(executor, Mockito.never()).shutdown();
    }

    @Test
    public void testProviderWithoutALoaderLeavesItToTheApplication() {
        setUpAgeris(true);
        loader.start();
        AgerisProvider provider = new AgerisProvider(flags, configs);
        api.setProviderAndWait("own", provider);
        Assert.assertTrue(api.getClient("own").getBooleanValue("one", false));

        provider.shutdown();

        Mockito.verify(executor, Mockito.never()).shutdown();
    }

    @Test
    public void testNewContentIsReportedWithTheNamesOfTheManager() throws InterruptedException {
        Client client = startWithEvents("changed");

        flagContent.set(MORE_FLAGS);
        refresh();

        Assert.assertEquals("changed [one, two]", nextEvent());
        Assert.assertFalse(client.getBooleanValue("one", true));
        configContent.set(OTHER_CONFIGS);
        refresh();
        Assert.assertEquals("changed [color]", nextEvent());
        Assert.assertEquals("green", client.getStringValue("color", "default"));
    }

    @Test
    public void testUnchangedContentIsNotReported() throws InterruptedException {
        startWithEvents("unchanged");

        refresh();
        refresh();
        configContent.set(OTHER_CONFIGS);
        refresh();

        Assert.assertEquals("The first event is the one for the change", "changed [color]", nextEvent());
    }

    @Test
    public void testStaleIsReportedOnceAndReadyWhenEveryManagerHasRecovered() throws InterruptedException {
        Client client = startWithEvents("stale");

        flagContent.set(BAD_FLAGS);
        refresh();
        String stale = nextEvent();
        Assert.assertTrue(stale, stale.startsWith("stale ") && stale.contains("Cannot read one: "));
        Assert.assertEquals(ProviderState.STALE, client.getProviderState());
        Assert.assertTrue("A stale provider keeps answering", client.getBooleanValue("one", false));

        /* The second manager becomes stale, a refresh fails again, and the first one recovers: no event for any of it. */
        configContent.set(BAD_CONFIGS);
        refresh();
        Assert.assertEquals(State.STALE, configs.getStatus().getState());
        refresh();
        flagContent.set(FLAGS);
        refresh();
        Assert.assertEquals(State.LOADED, flags.getStatus().getState());
        Assert.assertEquals(State.STALE, configs.getStatus().getState());

        configContent.set(CONFIGS);
        refresh();
        Assert.assertEquals("Nothing was sent between stale and ready", "ready", nextEvent());
        Assert.assertEquals(ProviderState.READY, client.getProviderState());

        flagContent.set(BAD_FLAGS);
        refresh();
        Assert.assertTrue("A second stale period is reported again", nextEvent().startsWith("stale "));
    }

    @Test
    public void testNothingIsSentAfterShutdown() throws InterruptedException {
        setUpAgeris(true);
        AgerisProvider provider = provider();
        api.setProviderAndWait("after", provider);
        api.getClient("after").onProviderConfigurationChanged(details -> events.add("changed " + sorted(details)));

        provider.shutdown();
        flagContent.set(MORE_FLAGS);
        refresh();

        Assert.assertFalse("The manager still updated", flags.isActive("one", new io.github.lichtsinn.ageris.core.ConfigurationContext()));
        Assert.assertNull("But the provider no longer listens", events.poll(SHORT.toMillis(), TimeUnit.MILLISECONDS));
    }

    private Client startWithEvents(String domain) throws InterruptedException {
        setUpAgeris(true);
        api.setProviderAndWait(domain, provider());
        Client client = api.getClient(domain);
        client.onProviderConfigurationChanged(details -> events.add("changed " + sorted(details)));
        client.onProviderStale(details -> events.add("stale " + details.getMessage()));
        client.onProviderReady(details -> events.add("ready"));
        /* A handler for "ready" is called at once when the provider already is ready. */
        Assert.assertEquals("ready", nextEvent());
        return client;
    }

    /**
     * @param runsFirstRefresh true for an executor that runs a refresh when the loader schedules it, as a real one
     *     would shortly after; false for one that never runs anything
     */
    private void setUpAgeris(boolean runsFirstRefresh) {
        Mockito.when(executor.scheduleWithFixedDelay(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class))).thenAnswer(invocation -> {
                    Runnable refresh = invocation.getArgument(0);
                    refreshes.add(refresh);
                    if (runsFirstRefresh) {
                        refresh.run();
                    }
                    return null;
                });
        Ageris ageris = new Ageris((fileNames, application) -> {
            Map<String, String> contents = new HashMap<>();
            for (String fileName : fileNames) {
                contents.put(fileName, "/flags.json".equals(fileName) ? flagContent.get() : configContent.get());
            }
            return contents;
        }, executor, new ObjectMapper(), sha256());
        flags = ageris.addFeatureFlagManager("myapp").registerFile("/flags.json").build();
        configs = ageris.addJsonConfigManager("myapp").registerFile("/configs.json").build();
        loader = ageris.createLoader(Duration.ofSeconds(10));
    }

    private AgerisProvider provider() {
        AgerisProvider provider = new AgerisProvider(flags, configs, loader);
        providers.add(provider);
        return provider;
    }

    private AgerisProvider impatientProvider() {
        AgerisProvider provider = AgerisProvider.builder(flags, configs).loader(loader).firstLoadTimeout(SHORT).build();
        providers.add(provider);
        return provider;
    }

    private void refresh() {
        refreshes.forEach(Runnable::run);
    }

    private String nextEvent() throws InterruptedException {
        String event = events.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull("No event within ten seconds", event);
        return event;
    }

    private static String sorted(EventDetails details) {
        List<String> names = new ArrayList<>(details.getFlagsChanged());
        Collections.sort(names);
        return names.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
