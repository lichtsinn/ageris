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
 * libraries are present the health check and the OpenFeature provider.
 *
 * Each of them is offered only when there is something to offer, so that an application without settings for Ageris, or
 * without one of the libraries, can look for any of these types and simply finds nothing. All of them have a low weight,
 * so that a service of the same type that the application offers wins.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Only a place for the services, which Helidon creates.
final class AgerisServices {

    private static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private AgerisServices() {
        // Only holds the services.
    }

    /**
     * Ageris itself. It has the run level of the start, so that a service registry that an application starts creates it
     * then, and a mistake in a configuration file stops the start. It uses the Ageris an application created itself, if
     * there is one; else it sets one up from the settings; and without any files set it sets nothing up.
     */
    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP)
    static final class Runtime {

        private final AgerisHelidon ageris;
        private final boolean own;

        @Service.Inject
        Runtime(Config config, List<AgerisCustomizer> customizers, Optional<ConfigurationFetcher> fetcher, Optional<Ageris> core) {
            final Optional<AgerisHelidon> created = AgerisHelidon.createdByApplication();
            final Config settings = config.get("ageris");
            own = created.isEmpty() && Settings.hasFiles(settings);
            if (own) {
                final AgerisHelidon.Builder builder = AgerisHelidon.builder().forRegistry().config(settings)
                        .fetcher(fetcher.orElse(null)).ageris(core.orElse(null));
                customizers.forEach(builder::customize);
                ageris = builder.build();
            } else {
                ageris = created.orElse(null);
            }
        }

        /** Stops what this service started. An Ageris the application created is the application's to close. */
        @Service.PreDestroy
        void stop() {
            if (own) {
                ageris.close();
            }
        }

        Optional<AgerisHelidon> ageris() {
            return Optional.ofNullable(ageris);
        }

        Optional<Managers> managers() {
            return ageris().map(AgerisHelidon::managers);
        }
    }

    /** Offers the feature flag manager, if files are set for it. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class FeatureFlags implements Supplier<Optional<FeatureFlagManager>> {

        private final Runtime runtime;

        @Service.Inject
        FeatureFlags(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<FeatureFlagManager> get() {
            return runtime.managers().map(Managers::featureFlags);
        }
    }

    /** Offers the config manager, if files are set for it. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class Configs implements Supplier<Optional<ConfigManager>> {

        private final Runtime runtime;

        @Service.Inject
        Configs(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<ConfigManager> get() {
            return runtime.managers().map(Managers::configs);
        }
    }

    /** Offers the JSON config manager, if files are set for it. */
    @Service.Singleton
    @Weight(WEIGHT)
    static class JsonConfigs implements Supplier<Optional<JsonConfigManager>> {

        private final Runtime runtime;

        @Service.Inject
        JsonConfigs(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<JsonConfigManager> get() {
            return runtime.managers().map(Managers::jsonConfigs);
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

    /**
     * Offers the OpenFeature provider, to an application that has the provider module and the OpenFeature SDK and has
     * files set for both the feature flag manager and the JSON config manager.
     */
    @Service.Singleton
    @Weight(WEIGHT)
    static class OpenFeature implements Supplier<Optional<AgerisProvider>> {

        private final Runtime runtime;

        @Service.Inject
        OpenFeature(Runtime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<AgerisProvider> get() {
            final boolean both = runtime.managers().filter(managers -> managers.featureFlags() != null && managers.jsonConfigs() != null).isPresent();
            try {
                return both ? runtime.ageris().map(AgerisHelidon::openFeatureProvider) : Optional.empty();
            } catch (LinkageError missing) {
                /* The application has the OpenFeature SDK, which is why it was asked for, but not the provider of Ageris. */
                return Optional.empty();
            }
        }
    }
}
