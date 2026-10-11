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
 * The managers an application asked for and their loader: built from the settings, started, watched for the first
 * attempt to load, and stopped.
 */
final class Managers {

    static final String FEATURE_FLAGS = "featureFlags";
    static final String CONFIGS = "configs";
    static final String JSON_CONFIGS = "jsonConfigs";

    private final FeatureFlagManager featureFlags;
    private final ConfigManager configs;
    private final JsonConfigManager jsonConfigs;
    private final ConfigurationLoader loader;

    Managers(Ageris ageris, Settings settings) {
        featureFlags = featureFlags(ageris, settings.application(), settings.featureFlagFiles());
        configs = configs(ageris, settings.application(), settings.configFiles());
        jsonConfigs = jsonConfigs(ageris, settings.application(), settings.jsonConfigFiles());
        loader = ageris.createLoader(settings.refreshInterval());
    }

    /**
     * @return the feature flag manager, null if no files are set for it
     */
    FeatureFlagManager featureFlags() {
        return featureFlags;
    }

    /**
     * @return the config manager, null if no files are set for it
     */
    ConfigManager configs() {
        return configs;
    }

    /**
     * @return the JSON config manager, null if no files are set for it
     */
    JsonConfigManager jsonConfigs() {
        return jsonConfigs;
    }

    void stop() {
        loader.shutdown();
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

    /**
     * Starts the loader and waits until every manager has loaded or has failed its first attempt. A first attempt that
     * fails is an answer, so the start of an application does not sit out the whole wait because of a mistake in a file.
     *
     * @return false if the wait ran out before every manager had its first attempt
     */
    boolean startAndAwaitFirstAttempt(Duration timeout) {
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

    boolean isLoaded() {
        return statuses().values().stream().allMatch(status -> status.getState() == ConfigurationStatus.State.LOADED);
    }

    String whyNotLoaded(boolean attempted, Duration timeout) {
        final StringBuilder why = new StringBuilder();
        for (final ConfigurationStatus status : statuses().values()) {
            if (status.getFailure() != null) {
                why.append(why.length() == 0 ? "" : "; ").append(describe(status.getFailure()));
            }
        }
        if (!attempted) {
            why.append(why.length() == 0 ? "" : "; ").append("the configuration files were not loaded within ").append(timeout);
        }
        return "Ageris could not load its configuration files: " + why
                + ". Set ageris.fail-on-first-load-failure to false to start anyway, with defaults until a load succeeds";
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
}
