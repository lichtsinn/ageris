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
import io.github.lichtsinn.ageris.core.metrics.ConfigurationLoaderMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for {@link ConfigurationLoader#awaitFirstLoad(Duration)}.
 */
public class ConfigurationLoaderAwaitTest {

    private static final String FLAGS = "{ \"feature-flags\": { \"enable-all\": { \"value\": true } } }";
    private static final String CONFIGS = "{ \"configs\": { } }";
    private static final String BOTH = "{ \"feature-flags\": { \"enable-all\": { \"value\": true } }, \"configs\": { } }";
    private static final Duration SHORT = Duration.ofMillis(50);

    private final ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
    private final AtomicReference<String> content = new AtomicReference<>(BOTH);
    private final Ageris ageris = new Ageris((fileNames, application) -> ImmutableMap.of("/all.json", content.get()),
            executorService, new ObjectMapper(), sha256());

    @Test
    public void testReturnsTrueOnceEveryManagerHasLoaded() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ageris.addConfigManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        capturedRefreshes(2).forEach(Runnable::run);

        Assert.assertTrue(loader.awaitFirstLoad(SHORT));
    }

    @Test
    public void testReturnsFalseWhenOneManagerHasNotLoadedInTime() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ageris.addConfigManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        capturedRefreshes(2).get(0).run();

        Assert.assertFalse(loader.awaitFirstLoad(SHORT));
    }

    @Test
    public void testReturnsTrueAtOnceWithoutManagers() {
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        Assert.assertTrue(loader.awaitFirstLoad(Duration.ZERO));
    }

    @Test
    public void testTimesOutWhenTheLoaderWasNeverStarted() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createLoader(Duration.ofSeconds(10));

        Assert.assertFalse(loader.awaitFirstLoad(SHORT));
    }

    @Test
    public void testInterruptedWaitReturnsFalseAndKeepsTheInterruptFlag() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        Thread.currentThread().interrupt();
        boolean loaded = loader.awaitFirstLoad(Duration.ofSeconds(30));

        /* Thread.interrupted() also clears the flag, so that it does not leak into other tests. */
        Assert.assertTrue(Thread.interrupted());
        Assert.assertFalse(loaded);
    }

    @Test
    public void testFailedFirstRefreshDoesNotEndTheWait() {
        content.set("not json");
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        Runnable refresh = capturedRefreshes(1).get(0);

        refresh.run();
        Assert.assertFalse(loader.awaitFirstLoad(Duration.ZERO));

        content.set(FLAGS);
        refresh.run();
        Assert.assertTrue(loader.awaitFirstLoad(SHORT));
    }

    @Test
    public void testFailedFirstRefreshEndsTheWaitForTheFirstAttempt() {
        content.set("not json");
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        capturedRefreshes(1).get(0).run();

        Assert.assertTrue(loader.awaitFirstAttempt(SHORT));
        Assert.assertFalse(loader.awaitFirstLoad(Duration.ZERO));
    }

    @Test
    public void testFirstAttemptEndsOnceEveryManagerHasLoadedOrFailed() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ageris.addConfigManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        List<Runnable> refreshes = capturedRefreshes(2);

        refreshes.get(0).run();
        Assert.assertFalse(loader.awaitFirstAttempt(SHORT));

        refreshes.get(1).run();
        Assert.assertTrue(loader.awaitFirstAttempt(SHORT));
    }

    @Test
    public void testFirstAttemptTimesOutWhenTheLoaderWasNeverStarted() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createLoader(Duration.ofSeconds(10));

        Assert.assertFalse(loader.awaitFirstAttempt(SHORT));
    }

    @Test
    public void testInterruptedWaitForTheFirstAttemptReturnsFalseAndKeepsTheInterruptFlag() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        Thread.currentThread().interrupt();
        boolean attempted = loader.awaitFirstAttempt(Duration.ofSeconds(30));

        /* Thread.interrupted() also clears the flag, so that it does not leak into other tests. */
        Assert.assertTrue(Thread.interrupted());
        Assert.assertFalse(attempted);
    }

    @Test
    public void testManagerWithoutFilesDoesNotBlockTheWait() {
        Ageris empty = new Ageris((fileNames, application) -> Collections.emptyMap(), executorService, new ObjectMapper(), sha256());
        FeatureFlagManager featureFlagManager = empty.addFeatureFlagManager("myapp").build();
        ConfigurationLoader loader = empty.createAndStartLoader(Duration.ofSeconds(10));

        capturedRefreshes(1).get(0).run();

        Assert.assertTrue(loader.awaitFirstLoad(SHORT));
        Assert.assertEquals(State.LOADED, featureFlagManager.getStatus().getState());
    }

    @Test
    public void testVeryLargeTimeoutDoesNotOverflow() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        capturedRefreshes(1).get(0).run();

        Assert.assertTrue(loader.awaitFirstLoad(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test(timeout = 30000)
    public void testVeryLargeTimeoutWaitsUntilTheLoad() throws InterruptedException {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        Runnable refresh = capturedRefreshes(1).get(0);
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread waiting = new Thread(() -> result.set(loader.awaitFirstLoad(Duration.ofSeconds(Long.MAX_VALUE))));
        waiting.start();

        waiting.join(200);
        Assert.assertTrue("The wait ended before anything had loaded", waiting.isAlive());
        refresh.run();
        waiting.join(10000);

        Assert.assertEquals(Boolean.TRUE, result.get());
    }

    @Test
    public void testWaitWorksForAReaderBuiltWithoutAgeris() throws IOException {
        FeatureFlagManager manager = new FeatureFlagManager();
        ConfigurationReader<Boolean> reader = new ConfigurationReader<>("myapp", Collections.singletonList("/all.json"),
                (fileNames, application) -> ImmutableMap.of("/all.json", FLAGS),
                new FeatureFlagMapper("feature-flags", false, new ObjectMapper(), new FeatureFlagMetrics()), manager, sha256(),
                new FeatureFlagMetrics(), 0);
        ConfigurationLoader loader = new ConfigurationLoader(new ConfigurationLoaderMetrics(), Collections.singletonList(reader),
                executorService, Duration.ofSeconds(10));

        Assert.assertFalse(loader.awaitFirstLoad(Duration.ZERO));
        reader.execute();

        Assert.assertTrue(loader.awaitFirstLoad(Duration.ZERO));
        Assert.assertEquals(State.LOADED, manager.getStatus().getState());
    }

    @Test(timeout = 5000)
    public void testVeryLargeNegativeTimeoutReportsWithoutWaiting() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));

        Assert.assertFalse(loader.awaitFirstLoad(Duration.ofSeconds(Long.MIN_VALUE)));
    }

    @Test
    public void testZeroTimeoutReportsWithoutWaiting() {
        ageris.addFeatureFlagManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        Runnable refresh = capturedRefreshes(1).get(0);

        Assert.assertFalse(loader.awaitFirstLoad(Duration.ZERO));
        refresh.run();
        Assert.assertTrue(loader.awaitFirstLoad(Duration.ZERO));
    }

    @Test
    public void testWaitsForARealLoaderThread() {
        ScheduledExecutorService realExecutor = Executors.newSingleThreadScheduledExecutor();
        Ageris real = new Ageris((fileNames, application) -> ImmutableMap.of("/all.json", CONFIGS),
                realExecutor, new ObjectMapper(), sha256());
        ConfigManager configManager = real.addConfigManager("myapp").registerFile("/all.json").build();
        ConfigurationLoader loader = real.createAndStartLoader(Duration.ofSeconds(60));
        try {
            Assert.assertTrue(loader.awaitFirstLoad(Duration.ofSeconds(10)));
            Assert.assertEquals(State.LOADED, configManager.getStatus().getState());
        } finally {
            loader.shutdown();
        }
    }

    private List<Runnable> capturedRefreshes(int expected) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService, Mockito.times(expected)).scheduleWithFixedDelay(captor.capture(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
        return captor.getAllValues();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
