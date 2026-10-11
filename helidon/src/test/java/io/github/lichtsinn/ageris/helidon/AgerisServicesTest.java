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
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.InMemoryAgeris;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Tests the declarative style: Ageris offered through a real service registry of Helidon, with the configuration and
 * the services of an application handed to the registry.
 */
public class AgerisServicesTest {

    private static final ConfigurationContext NO_CONTEXT = new ConfigurationContext();
    private static final String[] FLAGS_AND_JSON = {"ageris.application", "myapp", "ageris.feature-flags.files.0", "/featureflags.json",
        "ageris.json-configs.files.0", "/json-configs.json"};

    private final Files files = new Files();
    private ServiceRegistryManager manager;

    @After
    public void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    public void testManagersCanBeInjected() {
        ServiceRegistry registry = start(application(FLAGS_AND_JSON));

        Assert.assertTrue(registry.get(FeatureFlagManager.class).isActive("enable-checkout", NO_CONTEXT));
        Assert.assertEquals("hello", registry.get(JsonConfigManager.class).getConfig("greeting", NO_CONTEXT).asText());
    }

    @Test
    public void testManagerWithoutFilesIsNamed() {
        ServiceRegistry registry = start(application(FLAGS_AND_JSON));

        AgerisHelidonTest.assertFails(() -> registry.get(ConfigManager.class), "set ageris.configs.files");
    }

    @Test
    public void testNothingIsSetUpWithoutSettingsAndTheStartDoesNotFail() {
        ServiceRegistry registry = start(ServiceRegistryConfig.builder().putContractInstance(Config.class, AgerisHelidonTest.config()));

        AgerisHelidonTest.assertFails(() -> registry.get(FeatureFlagManager.class), "Ageris is not set up: set ageris.application and the files");
        Assert.assertTrue("No health check either", registry.all(HealthCheck.class).isEmpty());
    }

    @Test
    public void testMistakeInAFileStopsTheStartOfTheRegistry() {
        files.put("/featureflags.json", Files.BAD_FLAGS);

        AgerisHelidonTest.assertFails(() -> start(application(FLAGS_AND_JSON)), "Cannot read enable-checkout: ");
    }

    @Test
    public void testCustomizerOfTheApplicationBindsWhatAPolicyNeeds() {
        AgerisCustomizer bindings = ageris -> ageris.bind(IntSupplier.class, () -> 200);

        ServiceRegistry registry = start(application("ageris.application", "myapp", "ageris.configs.files.0", "/configs.json")
                .putContractInstance(AgerisCustomizer.class, bindings));

        Assert.assertEquals(150, registry.get(ConfigManager.class).getConfig(Discount.class, NO_CONTEXT).price());
    }

    @Test
    public void testManagerOfTheApplicationWins() {
        FeatureFlagManager own = new InMemoryAgeris().setFlag("only-here", true).featureFlagManager();

        ServiceRegistry registry = start(application(FLAGS_AND_JSON).putContractInstance(FeatureFlagManager.class, own));

        Assert.assertSame(own, registry.get(FeatureFlagManager.class));
    }

    @Test
    public void testServiceOfTheApplicationWinsByItsWeight() {
        FeatureFlagManager own = new InMemoryAgeris().setFlag("only-here", true).featureFlagManager();
        ApplicationFlags.offer(own);
        try {
            ServiceRegistry registry = start(application(FLAGS_AND_JSON));

            Assert.assertSame(own, registry.get(FeatureFlagManager.class));
        } finally {
            ApplicationFlags.offer(null);
        }
    }

    @Test
    public void testShutdownOfTheRegistryStopsTheLoader() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executor.scheduleWithFixedDelay(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class))).thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return null;
                });
        start(application(FLAGS_AND_JSON).putContractInstance(Ageris.class, ageris(executor)));
        Mockito.verify(executor, Mockito.never()).shutdown();

        manager.shutdown();
        manager = null;

        Mockito.verify(executor, Mockito.times(1)).shutdown();
    }

    @Test
    public void testHealthCheckIsFoundByType() {
        ServiceRegistry registry = start(application(FLAGS_AND_JSON));

        List<HealthCheck> checks = registry.all(HealthCheck.class);

        Assert.assertEquals(1, checks.size());
        Assert.assertEquals("ageris", checks.get(0).name());
        Assert.assertEquals(HealthCheckResponse.Status.UP, checks.get(0).call().status());
    }

    @Test
    public void testOpenFeatureProviderCanBeInjected() {
        ServiceRegistry registry = start(application(FLAGS_AND_JSON));

        AgerisProvider provider = registry.get(AgerisProvider.class);

        Assert.assertTrue(provider.getBooleanEvaluation("enable-checkout", false, new ImmutableContext()).getValue());
    }

    @Test
    public void testOpenFeatureProviderNeedsBothManagers() {
        ServiceRegistry registry = start(application("ageris.application", "myapp", "ageris.feature-flags.files.0", "/featureflags.json"));

        AgerisHelidonTest.assertFails(() -> registry.get(AgerisProvider.class), "set both ageris.feature-flags.files and ageris.json-configs.files");
    }

    /** The registry of an application with these settings, and with a fetcher of its own that serves the files of the test. */
    private ServiceRegistryConfig.Builder application(String... settings) {
        return ServiceRegistryConfig.builder().putContractInstance(Config.class, AgerisHelidonTest.config(settings))
                .putContractInstance(ConfigurationFetcher.class, files.fetcher());
    }

    private ServiceRegistry start(ServiceRegistryConfig.Builder application) {
        manager = ServiceRegistryManager.start(application.build());
        return manager.registry();
    }

    private Ageris ageris(ScheduledExecutorService executor) {
        try {
            return new Ageris(files.fetcher(), executor, new ObjectMapper(), MessageDigest.getInstance("SHA-256"));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
