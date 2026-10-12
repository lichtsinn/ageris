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
package io.github.lichtsinn.ageris.helidon;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.ImmutableContext;
import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Tests the imperative style: Ageris created by the application from Helidon's configuration.
 */
public class AgerisHelidonTest {

    private static final ConfigurationContext NO_CONTEXT = new ConfigurationContext();

    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    private final Files files = new Files();
    private final List<Runnable> refreshes = new ArrayList<>();

    @Test
    public void testFilesInADirectory() throws IOException {
        write("featureflags.json", Files.FLAGS);
        write("json-configs.json", Files.JSON_CONFIGS);

        try (AgerisHelidon ageris = AgerisHelidon.create(config("application", "myapp", "path", directory.getRoot().toString(),
                "feature-flags.files.0", "/featureflags.json", "json-configs.files.0", "/json-configs.json"))) {
            Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", NO_CONTEXT));
            Assert.assertEquals("hello", ageris.jsonConfigManager().getConfig("greeting", NO_CONTEXT).asText());
            Assert.assertEquals(State.LOADED, ageris.featureFlagManager().getStatus().getState());
        }
    }

    @Test
    public void testFilesWrittenAsOneTextWithCommas() {
        files.put("/more.json", "{ \"feature-flags\": { \"second\": { \"value\": true } } }");

        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files", "/featureflags.json, /more.json")) {
            Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", NO_CONTEXT));
            Assert.assertTrue(ageris.featureFlagManager().isActive("second", NO_CONTEXT));
        }
    }

    @Test
    public void testManagerWithoutFilesIsNamed() {
        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json")) {
            assertFails(ageris::configManager, "set ageris.configs.files");
            assertFails(ageris::jsonConfigManager, "set ageris.json-configs.files");
        }
    }

    @Test
    public void testNothingToLoadIsNamed() {
        assertFails(() -> AgerisHelidon.create(config("application", "myapp")), "set at least one of ageris.feature-flags.files");
    }

    @Test
    public void testSettingsThatCannotBeRightAreNamed() {
        assertFails(() -> withFetcher("feature-flags.files.0", "/featureflags.json"), "Set ageris.application");
        assertFails(() -> AgerisHelidon.create(config("application", "myapp", "feature-flags.files.0", "/featureflags.json")), "Set ageris.path");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files", "/featureflags.json,"),
                "ageris.feature-flags.files holds a blank file name");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "refresh-interval", "PT0S"),
                "ageris.refresh-interval must be longer than zero");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "first-load-timeout", "-PT1S"),
                "ageris.first-load-timeout must not be negative");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "first-load-timeout", "PT0S"),
                "ageris.first-load-timeout must be longer than zero while ageris.fail-on-first-load-failure is true");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "refresh-interval", "10s"),
                "ageris.refresh-interval must be a duration such as PT10S");
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "first-load-timeout", "soon"),
                "ageris.first-load-timeout must be a duration such as PT10S");
    }

    @Test
    public void testSettingThatIsNeitherTrueNorFalseIsNamed() {
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json", "fail-on-first-load-failure", "maybe"),
                "ageris.fail-on-first-load-failure must be true or false");
    }

    @Test
    public void testFilesSetToNothingCountAsNotSet() {
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files", ""), "set at least one of ageris.feature-flags.files");
    }

    @Test
    public void testWholeConfigurationInPlaceOfItsAgerisPartIsPointedOut() {
        assertFails(() -> AgerisHelidon.builder().config(config("ageris.application", "myapp", "ageris.feature-flags.files.0", "/featureflags.json"))
                .fetcher(files.fetcher()).build(), "config.get(\"ageris\")");
    }

    @Test
    public void testLoaderThreadsDoNotKeepTheApplicationAlive() {
        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json")) {
            Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", NO_CONTEXT));
            List<Thread> loaders = Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith("ageris-loader-")).toList();

            Assert.assertFalse("The loader has threads of its own", loaders.isEmpty());
            Assert.assertTrue("A failed start must be able to end the application: " + loaders, loaders.stream().allMatch(Thread::isDaemon));
        }
    }

    @Test
    public void testMissingFileSaysSo() {
        assertFails(() -> AgerisHelidon.create(config("application", "myapp", "path", directory.getRoot().toString(),
                "feature-flags.files.0", "/featureflags.json")), "No such file: ");
    }

    @Test
    public void testCustomizerBindsWhatAPolicyNeeds() {
        try (AgerisHelidon ageris = AgerisHelidon.builder().config(config("application", "myapp", "configs.files.0", "/configs.json"))
                .fetcher(files.fetcher()).customize(core -> core.bind(IntSupplier.class, () -> 200)).build()) {
            Assert.assertEquals(150, ageris.configManager().getConfig(Discount.class, NO_CONTEXT).price());
        }
    }

    @Test
    public void testPolicyWithoutItsBindingStopsTheStart() {
        assertFails(() -> withFetcher("application", "myapp", "configs.files.0", "/configs.json"), "No binding for");
    }

    @Test(timeout = 20_000)
    public void testFailedFirstLoadDoesNotWaitForTheTimeout() {
        files.put("/featureflags.json", Files.BAD_FLAGS);

        /* With the default of 30 seconds: the failure of the first attempt is the answer, not the end of the wait. */
        assertFails(() -> withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json"), "Cannot read enable-checkout: ");
    }

    @Test
    public void testFirstAttemptThatDoesNotHappenInTimeFails() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

        assertFails(() -> AgerisHelidon.builder().config(config("application", "myapp", "feature-flags.files.0", "/featureflags.json",
                "first-load-timeout", "PT0.05S")).ageris(ageris(executor)).build(), "were not loaded within PT0.05S");
        Mockito.verify(executor, Mockito.times(1)).shutdown();
    }

    @Test
    public void testApplicationCanStartWithoutAFirstLoadAndRecovers() throws InterruptedException {
        files.put("/featureflags.json", Files.BAD_FLAGS);

        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json",
                "fail-on-first-load-failure", "false", "refresh-interval", "PT0.02S")) {
            FeatureFlagManager flags = ageris.featureFlagManager();
            Assert.assertEquals(State.NOT_LOADED, flags.getStatus().getState());
            Assert.assertFalse("A default until a load succeeds", flags.isActive("enable-checkout", NO_CONTEXT));
            CountDownLatch loaded = new CountDownLatch(1);
            flags.addListener(update -> loaded.countDown());

            files.put("/featureflags.json", Files.FLAGS);

            Assert.assertTrue("The loader kept trying", loaded.await(10, TimeUnit.SECONDS));
            Assert.assertTrue(flags.isActive("enable-checkout", NO_CONTEXT));
        }
    }

    @Test
    public void testCloseStopsTheLoaderOnce() {
        ScheduledExecutorService executor = executorThatRunsTheFirstRefresh();
        AgerisHelidon ageris = AgerisHelidon.builder().config(config("application", "myapp", "feature-flags.files.0", "/featureflags.json"))
                .ageris(ageris(executor)).build();
        Mockito.verify(executor, Mockito.never()).shutdown();

        ageris.close();
        ageris.close();

        Mockito.verify(executor, Mockito.times(1)).shutdown();
    }

    @Test
    public void testHealthIsUpWhenLoadedAndWhenStale() {
        try (AgerisHelidon ageris = AgerisHelidon.builder().config(config("application", "myapp", "feature-flags.files.0", "/featureflags.json",
                "json-configs.files.0", "/json-configs.json")).ageris(ageris(executorThatRunsTheFirstRefresh())).build()) {
            HealthCheck check = ageris.healthCheck();
            Assert.assertEquals("ageris", check.name());
            Assert.assertEquals(HealthCheckType.READINESS, check.type());
            Assert.assertEquals(HealthCheckResponse.Status.UP, check.call().status());
            Assert.assertEquals("LOADED", check.call().details().get("featureFlags"));
            Assert.assertEquals("LOADED", check.call().details().get("jsonConfigs"));
            Assert.assertFalse("Only the managers that exist", check.call().details().containsKey("configs"));

            files.put("/featureflags.json", Files.BAD_FLAGS);
            refreshes.forEach(Runnable::run);

            Assert.assertEquals(HealthCheckResponse.Status.UP, check.call().status());
            String flags = String.valueOf(check.call().details().get("featureFlags"));
            Assert.assertTrue(flags, flags.startsWith("STALE: ") && flags.contains("Cannot read enable-checkout: "));
        }
    }

    @Test
    public void testHealthIsDownWhenAManagerNeverLoaded() {
        files.put("/featureflags.json", Files.BAD_FLAGS);

        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json",
                "fail-on-first-load-failure", "false")) {
            HealthCheckResponse response = ageris.healthCheck().call();

            Assert.assertEquals(HealthCheckResponse.Status.DOWN, response.status());
            String flags = String.valueOf(response.details().get("featureFlags"));
            Assert.assertTrue(flags, flags.startsWith("NOT_LOADED: ") && flags.contains("Cannot read enable-checkout: "));
        }
    }

    @Test
    public void testOpenFeatureProviderAnswersFromTheManagers() {
        files.put("/featureflags.json", "{ \"feature-flags\": { \"for-customers\": { \"value\": false, \"modifiers\": {"
                + " \"type\": \"customer\", \"contexts\": { \"acme\": { \"value\": true } } } } } }");

        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json",
                "json-configs.files.0", "/json-configs.json", "openfeature.targeting-key-type", "customer")) {
            AgerisProvider provider = ageris.openFeatureProvider();

            Assert.assertTrue(provider.getBooleanEvaluation("for-customers", false, new ImmutableContext("acme")).getValue());
            Assert.assertEquals("hello", provider.getStringEvaluation("greeting", "default", new ImmutableContext()).getValue());
        }
    }

    @Test
    public void testOpenFeatureProviderNeedsBothManagers() {
        try (AgerisHelidon ageris = withFetcher("application", "myapp", "feature-flags.files.0", "/featureflags.json")) {
            assertFails(ageris::openFeatureProvider, "set both ageris.feature-flags.files and ageris.json-configs.files");
        }
    }

    private AgerisHelidon withFetcher(String... settings) {
        return AgerisHelidon.builder().config(config(settings)).fetcher(files.fetcher()).build();
    }

    static Config config(String... keysAndValues) {
        Map<String, String> settings = new HashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            settings.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Config.just(ConfigSources.create(settings));
    }

    private void write(String fileName, String content) throws IOException {
        Path application = directory.getRoot().toPath().resolve("myapp");
        java.nio.file.Files.createDirectories(application);
        java.nio.file.Files.write(application.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    static void assertFails(Runnable code, String expectedInMessage) {
        try {
            code.run();
            Assert.fail("Expected a failure that says: " + expectedInMessage);
        } catch (RuntimeException failure) {
            StringBuilder messages = new StringBuilder();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                messages.append(cause.getMessage()).append('\n');
            }
            Assert.assertTrue(messages.toString(), messages.toString().contains(expectedInMessage));
        }
    }

    private Ageris ageris(ScheduledExecutorService executor) {
        try {
            return new Ageris(files.fetcher(), executor, new ObjectMapper(), MessageDigest.getInstance("SHA-256"));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** An executor that runs a refresh when the loader schedules it, and keeps it for the test to run again. */
    private ScheduledExecutorService executorThatRunsTheFirstRefresh() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executor.scheduleWithFixedDelay(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class))).thenAnswer(invocation -> {
                    Runnable refresh = invocation.getArgument(0);
                    refreshes.add(refresh);
                    refresh.run();
                    return null;
                });
        return executor;
    }
}
