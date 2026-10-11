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
import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.filesystem.ConfigurationFetcherMetrics;
import io.github.lichtsinn.ageris.core.filesystem.FilesystemConfigurationFetcher;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import io.helidon.config.Config;
import io.helidon.health.HealthCheck;

import java.nio.file.FileSystems;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ageris for a Helidon SE application, set up from the "ageris" part of Helidon's configuration: the managers whose files
 * are set, and a loader that is started here and stopped with {@link #close()}.
 *
 *  AgerisHelidon ageris = AgerisHelidon.create(config.get("ageris"));
 *  FeatureFlagManager featureFlagManager = ageris.featureFlagManager();
 *
 * An application that uses Helidon's service registry does not need this class: there the managers can be injected.
 */
public final class AgerisHelidon implements AutoCloseable {

    /** One thread for each of the three managers an application can have. */
    private static final int LOADER_THREADS = 3;

    /* The Ageris an application created itself, for the services of the service registry to find. */
    private static final AtomicReference<AgerisHelidon> BY_APPLICATION = new AtomicReference<>();

    private final Settings settings;
    private final Managers managers;
    private final AtomicBoolean closed = new AtomicBoolean();

    private AgerisHelidon(Builder builder) {
        final Config config = Objects.requireNonNull(builder.config, "config");
        if (!Settings.hasFiles(config)) {
            throw new IllegalStateException("Nothing for Ageris to load: set at least one of " + Settings.FEATURE_FLAG_FILES + ", "
                    + Settings.CONFIG_FILES + " and " + Settings.JSON_CONFIG_FILES
                    + (config.get("ageris").exists() ? ". Hand over the part of the configuration that holds them: config.get(\"ageris\")" : ""));
        }
        settings = Settings.from(config);
        settings.check();
        final Ageris ageris = builder.ageris == null ? withDaemonThreads(fetcher(builder.fetcher, settings)) : builder.ageris;
        builder.customizers.forEach(customizer -> customizer.customize(ageris));
        managers = new Managers(ageris, settings);
        final boolean attempted = managers.startAndAwaitFirstAttempt(settings.firstLoadTimeout());
        if (!managers.isLoaded() && settings.failOnFirstLoad()) {
            managers.stop();
            throw new IllegalStateException(managers.whyNotLoaded(attempted, settings.firstLoadTimeout()));
        }
    }

    /**
     * Sets Ageris up, starts its loader and waits for the first attempt to load the configuration files.
     *
     * @param config the "ageris" part of Helidon's configuration
     * @return Ageris, with its managers loaded
     * @throws IllegalStateException if a setting is missing or cannot be right, or if the files cannot be loaded and the
     *     application did not ask to start anyway
     */
    public static AgerisHelidon create(Config config) {
        return builder().config(config).build();
    }

    /**
     * @return builder for an application that has bindings for its policies, a fetcher of its own, or other options
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the feature flag manager
     * @throws IllegalStateException if no files are set for it
     */
    public FeatureFlagManager featureFlagManager() {
        return configured(managers.featureFlags(), Settings.FEATURE_FLAG_FILES);
    }

    /**
     * @return the config manager, for configs of the application's own classes and for policies
     * @throws IllegalStateException if no files are set for it
     */
    public ConfigManager configManager() {
        return configured(managers.configs(), Settings.CONFIG_FILES);
    }

    /**
     * @return the JSON config manager
     * @throws IllegalStateException if no files are set for it
     */
    public JsonConfigManager jsonConfigManager() {
        return configured(managers.jsonConfigs(), Settings.JSON_CONFIG_FILES);
    }

    /**
     * Needs the health library of Helidon in the application.
     *
     * @return a readiness check that is down while a manager has never loaded
     */
    public HealthCheck healthCheck() {
        /* Created by its own class: this one must load in an application that does not have the health library. */
        return AgerisHealthCheck.create(managers);
    }

    /**
     * Needs the OpenFeature provider of Ageris and the OpenFeature SDK in the application. Registering the provider with
     * OpenFeature is left to the application.
     *
     * @return a provider that answers from the feature flag manager and the JSON config manager
     * @throws IllegalStateException if files are not set for both of them
     */
    public AgerisProvider openFeatureProvider() {
        if (managers.featureFlags() == null || managers.jsonConfigs() == null) {
            throw new IllegalStateException("The OpenFeature provider answers from feature flags and JSON configs: set both "
                    + Settings.FEATURE_FLAG_FILES + " and " + Settings.JSON_CONFIG_FILES);
        }
        return AgerisProvider.builder(managers.featureFlags(), managers.jsonConfigs()).targetingKeyType(settings.targetingKeyType()).build();
    }

    /**
     * Stops the loader. Does nothing when called again.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            BY_APPLICATION.compareAndSet(this, null);
            managers.stop();
        }
    }

    /**
     * @return the feature flag manager, the config manager and the JSON config manager, each null if no files are set for it
     */
    Managers managers() {
        return managers;
    }

    /**
     * @return the Ageris an application created itself and has not closed, which the services for Helidon's service
     *     registry then use, so that an application never ends up with two
     */
    static Optional<AgerisHelidon> createdByApplication() {
        return Optional.ofNullable(BY_APPLICATION.get());
    }

    /**
     * An Ageris whose loader runs on daemon threads. Helidon does not stop what was started when a later step of the
     * start fails, and other threads would then keep an application alive that should end and be started again.
     */
    @SuppressWarnings("PMD.CloseResource") // The loader of Ageris shuts the executor down when it is stopped.
    private static Ageris withDaemonThreads(ConfigurationFetcher fetcher) {
        final AtomicInteger count = new AtomicInteger();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(LOADER_THREADS, task -> {
            final Thread thread = new Thread(task, "ageris-loader-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            return new Ageris(fetcher, executor, new ObjectMapper(), MessageDigest.getInstance("SHA-256"));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <M> M configured(M manager, String setting) {
        if (manager == null) {
            throw new IllegalStateException("No files are set for this manager: set " + setting);
        }
        return manager;
    }

    private static ConfigurationFetcher fetcher(ConfigurationFetcher own, Settings settings) {
        if (own != null) {
            return own;
        }
        if (settings.path().isBlank()) {
            throw new IllegalStateException("Set ageris.path to the directory of the configuration files, or supply a ConfigurationFetcher");
        }
        return new FilesystemConfigurationFetcher(FileSystems.getDefault(), settings.path(), false, new ConfigurationFetcherMetrics());
    }

    /**
     * Builder for {@link AgerisHelidon}.
     */
    public static final class Builder {

        private final List<AgerisCustomizer> customizers = new ArrayList<>();
        private Config config;
        private ConfigurationFetcher fetcher;
        private Ageris ageris;
        private boolean forRegistry;

        private Builder() {
        }

        /**
         * @param config the "ageris" part of Helidon's configuration
         * @return this builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * @param customizer called with the Ageris of the application before its managers are built, for the bindings of
         *     policies and for other options
         * @return this builder
         */
        public Builder customize(AgerisCustomizer customizer) {
            customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return this;
        }

        /**
         * @param fetcher fetcher of the configuration files, used instead of the one for the directory in ageris.path
         * @return this builder
         */
        public Builder fetcher(ConfigurationFetcher fetcher) {
            this.fetcher = fetcher;
            return this;
        }

        /**
         * @param ageris an Ageris of the application's own, for what neither the configuration nor a customizer can set,
         *     such as files in YAML; the managers and the loader are then built on it
         * @return this builder
         */
        public Builder ageris(Ageris ageris) {
            this.ageris = ageris;
            return this;
        }

        /**
         * Sets Ageris up, starts its loader and waits for the first attempt to load the configuration files.
         *
         * @return Ageris, with its managers loaded
         * @throws IllegalStateException if a setting is missing or cannot be right, or if the files cannot be loaded and
         *     the application did not ask to start anyway
         */
        public AgerisHelidon build() {
            final AgerisHelidon created = new AgerisHelidon(this);
            if (!forRegistry) {
                BY_APPLICATION.set(created);
            }
            return created;
        }

        /** Marks what the services of the service registry build themselves, which is not what an application created. */
        Builder forRegistry() {
            forRegistry = true;
            return this;
        }
    }
}
