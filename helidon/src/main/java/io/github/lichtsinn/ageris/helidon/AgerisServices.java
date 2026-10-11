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

import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.service.registry.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What this module offers to an application that uses Helidon's service registry: the managers, and where their
 * libraries are present the health check and the OpenFeature provider. All of them have a low weight, so that a service
 * of the same type that the application offers wins.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Only a place for the services, which Helidon creates.
final class AgerisServices {

    private static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;
    private static final String NOT_SET_UP = "Ageris is not set up: set ageris.application and the files of at least one manager, such as ";

    private AgerisServices() {
        // Only holds the services.
    }

    /**
     * Ageris itself. It is created when the service registry starts, not at the first lookup, so that a mistake in a
     * configuration file stops the start of the application. Without any files set it sets nothing up.
     */
    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP)
    static final class Runtime {

        private final AgerisHelidon ageris;

        @Service.Inject
        Runtime(Config config, List<AgerisCustomizer> customizers, Optional<ConfigurationFetcher> fetcher, Optional<Ageris> own) {
            final Config settings = config.get("ageris");
            if (Settings.from(settings).hasFiles()) {
                final AgerisHelidon.Builder builder = AgerisHelidon.builder().config(settings).fetcher(fetcher.orElse(null)).ageris(own.orElse(null));
                customizers.forEach(builder::customize);
                ageris = builder.build();
            } else {
                ageris = null;
            }
        }

        @Service.PreDestroy
        void stop() {
            if (ageris != null) {
                ageris.close();
            }
        }

        Optional<AgerisHelidon> ageris() {
            return Optional.ofNullable(ageris);
        }

        AgerisHelidon setUp(String setting) {
            if (ageris == null) {
                throw new IllegalStateException(NOT_SET_UP + setting);
            }
            return ageris;
        }
    }

    /** Offers the feature flag manager. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class FeatureFlags implements Supplier<FeatureFlagManager> {

        private final Runtime runtime;

        @Service.Inject
        FeatureFlags(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public FeatureFlagManager get() {
            return runtime.setUp(Settings.FEATURE_FLAG_FILES).featureFlagManager();
        }
    }

    /** Offers the config manager. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class Configs implements Supplier<ConfigManager> {

        private final Runtime runtime;

        @Service.Inject
        Configs(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public ConfigManager get() {
            return runtime.setUp(Settings.CONFIG_FILES).configManager();
        }
    }

    /** Offers the JSON config manager. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class JsonConfigs implements Supplier<JsonConfigManager> {

        private final Runtime runtime;

        @Service.Inject
        JsonConfigs(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public JsonConfigManager get() {
            return runtime.setUp(Settings.JSON_CONFIG_FILES).jsonConfigManager();
        }
    }

    /** Offers the health check, to an application that has the health library of Helidon and has Ageris set up. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class Health implements Supplier<Optional<HealthCheck>> {

        private final Runtime runtime;

        @Service.Inject
        Health(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<HealthCheck> get() {
            return runtime.ageris().map(AgerisHelidon::healthCheck);
        }
    }

    /** Offers the OpenFeature provider, to an application that has the provider module and the OpenFeature SDK. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class OpenFeature implements Supplier<AgerisProvider> {

        private final Runtime runtime;

        @Service.Inject
        OpenFeature(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public AgerisProvider get() {
            return runtime.setUp(Settings.FEATURE_FLAG_FILES).openFeatureProvider();
        }
    }
}
