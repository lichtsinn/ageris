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
package io.github.lichtsinn.ageris.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the status a manager reports while Ageris refreshes it.
 */
public class AgerisStatusTest {

    private static final String NUMBER_CONFIG = "io.github.lichtsinn.ageris.core.configs.NumberConfig";
    private static final String GOOD = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"number\" : 7 } } } }";
    private static final String CHANGED = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"number\" : 8 } } } }";
    private static final String BAD = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"badproperty\" : 1 } } } }";
    private static final String NEEDS_BINDING = "{ \"configs\": { \"io.github.lichtsinn.ageris.core.configs.DiscountConfig\": {"
            + " \"value\": { \"percent\": 25 } } } }";

    private final ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
    private final AtomicReference<String> content = new AtomicReference<>(GOOD);
    private final Ageris ageris = new Ageris((fileNames, application) -> ImmutableMap.of("/configs.json", content.get()),
            executorService, new ObjectMapper(), sha256());

    @Test
    public void testManagerIsNotLoadedBeforeTheFirstRefresh() {
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        startLoader();

        Assert.assertEquals(State.NOT_LOADED, configManager.getStatus().getState());
        Assert.assertNull(configManager.getStatus().getFailure());
    }

    @Test
    public void testFailedFirstLoadIsNotLoadedAndNamesTheFileAndTheCause() {
        content.set(BAD);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        startLoader().run();

        ConfigurationStatus status = configManager.getStatus();
        Assert.assertEquals(State.NOT_LOADED, status.getState());
        Assert.assertTrue(status.getFailure().getMessage(), status.getFailure().getMessage().contains("/configs.json"));
        Assert.assertTrue(status.getFailure().getMessage(), status.getFailure().getMessage().contains("myapp"));
        Assert.assertTrue(status.getFailure().getMessage(), status.getFailure().getMessage().contains("badproperty"));
        Assert.assertNotNull(status.getFailure().getCause());
    }

    @Test
    public void testBadContentAfterALoadMakesTheManagerStaleAndKeepsServing() {
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        Runnable refresh = startLoader();
        refresh.run();
        Assert.assertEquals(State.LOADED, configManager.getStatus().getState());

        content.set(BAD);
        refresh.run();

        Assert.assertEquals(State.STALE, configManager.getStatus().getState());
        Assert.assertTrue(configManager.getStatus().getFailure().getMessage().contains("badproperty"));
        Assert.assertEquals(7, configManager.getConfig(NumberConfig.class, new ConfigurationContext()).getNumber());
    }

    @Test
    public void testRevertingToTheLoadedContentMakesTheManagerLoadedAgain() {
        ConfigMetrics configMetrics = new ConfigMetrics();
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").setMetrics(configMetrics).build();
        Runnable refresh = startLoader();
        refresh.run();
        content.set(BAD);
        refresh.run();

        content.set(GOOD);
        refresh.run();

        Assert.assertEquals(State.LOADED, configManager.getStatus().getState());
        Assert.assertNull(configManager.getStatus().getFailure());
        /* Proves the revert went through the unchanged-content path, not through a second update. */
        Assert.assertEquals(1, configMetrics.getConfigSameContentsSkips());
    }

    @Test
    public void testFixedContentMakesTheManagerLoadedAgain() {
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        Runnable refresh = startLoader();
        refresh.run();
        content.set(BAD);
        refresh.run();

        content.set(CHANGED);
        refresh.run();

        Assert.assertEquals(State.LOADED, configManager.getStatus().getState());
        Assert.assertNull(configManager.getStatus().getFailure());
        Assert.assertEquals(8, configManager.getConfig(NumberConfig.class, new ConfigurationContext()).getNumber());
    }

    @Test
    public void testMissingBindingIsNamedInTheFailure() {
        content.set(NEEDS_BINDING);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        startLoader().run();

        String message = configManager.getStatus().getFailure().getMessage();
        Assert.assertTrue(message, message.contains("No binding for"));
        Assert.assertTrue(message, message.contains("/configs.json"));
    }

    @Test
    public void testFetcherRuntimeExceptionIsTheRecordedFailure() {
        IllegalStateException problem = new IllegalStateException("fetcher broke");
        ConfigurationFetcher fetcher = (fileNames, application) -> {
            throw problem;
        };
        Ageris failing = new Ageris(fetcher, executorService, new ObjectMapper(), sha256());
        ConfigManager configManager = failing.addConfigManager("myapp").registerFile("/configs.json").build();
        failing.createAndStartLoader(Duration.ofSeconds(10));

        capturedRefresh().run();

        Assert.assertEquals(State.NOT_LOADED, configManager.getStatus().getState());
        Assert.assertSame(problem, configManager.getStatus().getFailure());
    }

    @Test
    public void testSkippedEntryLeavesTheManagerLoaded() {
        content.set(BAD);
        ageris.skipNonInstantiableConfiguration();
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        startLoader().run();

        Assert.assertEquals(State.LOADED, configManager.getStatus().getState());
        Assert.assertNull(configManager.getStatus().getFailure());
    }

    @Test
    public void testMissingRootFieldIsAFailureThatNamesTheField() {
        content.set("{ \"feature-flags\": { } }");
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        startLoader().run();

        Assert.assertEquals(State.NOT_LOADED, configManager.getStatus().getState());
        Assert.assertTrue(configManager.getStatus().getFailure().getMessage(), configManager.getStatus().getFailure().getMessage().contains("configs"));
    }

    @Test
    public void testFetchThatReturnsNothingMakesALoadedManagerStale() {
        AtomicReference<Boolean> available = new AtomicReference<>(Boolean.TRUE);
        ConfigurationFetcher fetcher = (fileNames, application) -> available.get()
                ? ImmutableMap.of("/configs.json", GOOD) : ImmutableMap.of();
        Ageris fading = new Ageris(fetcher, executorService, new ObjectMapper(), sha256());
        ConfigManager configManager = fading.addConfigManager("myapp").registerFile("/configs.json").build();
        fading.createAndStartLoader(Duration.ofSeconds(10));
        Runnable refresh = capturedRefresh();
        refresh.run();

        available.set(Boolean.FALSE);
        refresh.run();

        Assert.assertEquals(State.STALE, configManager.getStatus().getState());
        Assert.assertEquals(7, configManager.getConfig(NumberConfig.class, new ConfigurationContext()).getNumber());
    }

    @Test
    public void testFeatureFlagAndJsonConfigManagersReportFailuresToo() {
        content.set("not json");
        FeatureFlagManager featureFlagManager = ageris.addFeatureFlagManager("myapp").registerFile("/configs.json").build();
        JsonConfigManager jsonConfigManager = ageris.addJsonConfigManager("myapp").registerFile("/configs.json").build();
        ageris.createAndStartLoader(Duration.ofSeconds(10));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService, Mockito.times(2)).scheduleWithFixedDelay(captor.capture(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));

        captor.getAllValues().forEach(Runnable::run);

        Assert.assertNotNull(featureFlagManager.getStatus().getFailure());
        Assert.assertNotNull(jsonConfigManager.getStatus().getFailure());
    }

    private Runnable startLoader() {
        ageris.createAndStartLoader(Duration.ofSeconds(10));
        return capturedRefresh();
    }

    private Runnable capturedRefresh() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(captor.capture(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
        return captor.getValue();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
