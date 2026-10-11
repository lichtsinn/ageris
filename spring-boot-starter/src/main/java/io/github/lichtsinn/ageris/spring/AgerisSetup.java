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
package io.github.lichtsinn.ageris.spring;

import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.ConfigurationListener;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.ConfigurationStatus;
import io.github.lichtsinn.ageris.core.ConfigurationUpdate;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;

import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Builds the managers an application asked for, starts the loader and waits for the first attempt to load them. The
 * managers are handed out by this object, so that no other bean gets one before that.
 */
final class AgerisSetup {

    static final String FEATURE_FLAGS = "featureFlags";
    static final String CONFIGS = "configs";
    static final String JSON_CONFIGS = "jsonConfigs";

    private final FeatureFlagManager featureFlags;
    private final ConfigManager configs;
    private final JsonConfigManager jsonConfigs;
    private final ConfigurationLoader loader;

    AgerisSetup(Ageris ageris, AgerisProperties properties, List<AgerisCustomizer> customizers) {
        final String application = checked(properties);
        customizers.forEach(customizer -> customizer.customize(ageris));
        featureFlags = featureFlags(ageris, application, properties.featureFlags().files());
        configs = configs(ageris, application, properties.configs().files());
        jsonConfigs = jsonConfigs(ageris, application, properties.jsonConfigs().files());
        loader = ageris.createLoader(properties.refreshInterval());
        final boolean attempted = startAndAwaitFirstAttempt(properties.firstLoadTimeout());
        if (!isLoaded() && properties.failOnFirstLoadFailure()) {
            loader.shutdown();
            throw new IllegalStateException(whyNotLoaded(attempted, properties));
        }
    }

    /**
     * @return the name of the application, after checking what a file of properties can get wrong
     */
    private static String checked(AgerisProperties properties) {
        final String application = properties.application();
        if (application == null || application.isBlank()) {
            throw new IllegalStateException("Set ageris.application to the name of the application, as the fetcher of the configuration files uses it");
        }
        if (properties.refreshInterval().isZero() || properties.refreshInterval().isNegative()) {
            throw new IllegalStateException("ageris.refresh-interval must be longer than zero, but was " + properties.refreshInterval());
        }
        refuseBlank("ageris.feature-flags.files", properties.featureFlags().files());
        refuseBlank("ageris.configs.files", properties.configs().files());
        refuseBlank("ageris.json-configs.files", properties.jsonConfigs().files());
        return application;
    }

    private static void refuseBlank(String property, List<String> files) {
        if (files.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(property + " holds a blank file name: " + files);
        }
    }

    /**
     * Starts the loader and waits until every manager has loaded or has failed its first attempt. A first attempt that
     * fails is an answer, so the start of an application does not sit out the whole wait because of a mistake in a file.
     *
     * @return false if the wait ran out before every manager had its first attempt
     */
    private boolean startAndAwaitFirstAttempt(Duration timeout) {
        final CountDownLatch attempts = new CountDownLatch(statuses().size());
        final List<Runnable> removals = new ArrayList<>();
        if (featureFlags != null) {
            removals.add(watch(attempts, featureFlags::addListener, featureFlags::removeListener));
        }
        if (configs != null) {
            removals.add(watch(attempts, configs::addListener, configs::removeListener));
        }
        if (jsonConfigs != null) {
            removals.add(watch(attempts, jsonConfigs::addListener, jsonConfigs::removeListener));
        }
        loader.start();
        try {
            return attempts.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            removals.forEach(Runnable::run);
        }
    }

    /**
     * @return what takes the listener off the manager again
     */
    private static Runnable watch(CountDownLatch attempts, Consumer<ConfigurationListener> add, Consumer<ConfigurationListener> remove) {
        final ConfigurationListener listener = new FirstAttempt(attempts);
        add.accept(listener);
        return () -> remove.accept(listener);
    }

    private boolean isLoaded() {
        return statuses().values().stream().allMatch(status -> status.getState() == ConfigurationStatus.State.LOADED);
    }

    /**
     * @param failure why a manager could not load, not null
     * @return the failure in words a person can use: some exceptions only carry a path or nothing at all
     */
    static String describe(Exception failure) {
        if (failure instanceof NoSuchFileException) {
            return "No such file: " + failure.getMessage();
        }
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }

    FeatureFlagManager featureFlagManager() {
        return featureFlags;
    }

    ConfigManager configManager() {
        return configs;
    }

    JsonConfigManager jsonConfigManager() {
        return jsonConfigs;
    }

    ConfigurationLoader loader() {
        return loader;
    }

    /**
     * @return the status of each manager that exists, by its name
     */
    Map<String, ConfigurationStatus> statuses() {
        final Map<String, ConfigurationStatus> statuses = new LinkedHashMap<>();
        if (featureFlags != null) {
            statuses.put(FEATURE_FLAGS, featureFlags.getStatus());
        }
        if (configs != null) {
            statuses.put(CONFIGS, configs.getStatus());
        }
        if (jsonConfigs != null) {
            statuses.put(JSON_CONFIGS, jsonConfigs.getStatus());
        }
        return statuses;
    }

    /** Called by Spring when the application context closes. */
    void close() {
        loader.shutdown();
    }

    private String whyNotLoaded(boolean attempted, AgerisProperties properties) {
        final StringBuilder why = new StringBuilder();
        for (final ConfigurationStatus status : statuses().values()) {
            if (status.getFailure() != null) {
                why.append(why.length() == 0 ? "" : "; ").append(describe(status.getFailure()));
            }
        }
        if (!attempted) {
            why.append(why.length() == 0 ? "" : "; ").append("the configuration files were not loaded within ").append(properties.firstLoadTimeout());
        }
        return "Ageris could not load its configuration files: " + why
                + ". Set ageris.fail-on-first-load-failure=false to start anyway, with defaults until a load succeeds";
    }

    /**
     * Counts the first thing that happens to a manager, which is that it loads or that its first attempt fails.
     */
    private static final class FirstAttempt implements ConfigurationListener {

        private final CountDownLatch attempts;
        private final AtomicBoolean counted = new AtomicBoolean();

        private FirstAttempt(CountDownLatch attempts) {
            this.attempts = attempts;
        }

        @Override
        public void onUpdate(ConfigurationUpdate update) {
            // The change of status that comes with the first update is what counts.
        }

        @Override
        public void onStatusChange(ConfigurationStatus status) {
            if (counted.compareAndSet(false, true)) {
                attempts.countDown();
            }
        }
    }

    /** Each of the three returns null where no files are set: the application did not ask for that manager. */
    private static FeatureFlagManager featureFlags(Ageris ageris, String application, List<String> files) {
        if (files.isEmpty()) {
            return null;
        }
        final Ageris.FeatureFlagManagerBuilder builder = ageris.addFeatureFlagManager(application);
        files.forEach(builder::registerFile);
        return builder.build();
    }

    private static ConfigManager configs(Ageris ageris, String application, List<String> files) {
        if (files.isEmpty()) {
            return null;
        }
        final Ageris.ConfigManagerBuilder builder = ageris.addConfigManager(application);
        files.forEach(builder::registerFile);
        return builder.build();
    }

    private static JsonConfigManager jsonConfigs(Ageris ageris, String application, List<String> files) {
        if (files.isEmpty()) {
            return null;
        }
        final Ageris.JsonConfigManagerBuilder builder = ageris.addJsonConfigManager(application);
        files.forEach(builder::registerFile);
        return builder.build();
    }
}
