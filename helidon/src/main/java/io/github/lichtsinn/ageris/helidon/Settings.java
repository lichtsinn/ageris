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

import io.helidon.config.Config;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings of Ageris, as read from the "ageris" part of Helidon's configuration.
 *
 * @param application name of the application, as the fetcher of the configuration files uses it
 * @param path directory that holds the configuration files
 * @param refreshInterval time between two refreshes
 * @param firstLoadTimeout how long the start waits for the first attempt to load
 * @param failOnFirstLoad whether the start fails when the first attempt fails or takes too long
 * @param featureFlagFiles files of the feature flag manager
 * @param configFiles files of the config manager
 * @param jsonConfigFiles files of the JSON config manager
 * @param targetingKeyType context type for the targeting key of OpenFeature callers
 */
record Settings(String application,
                String path,
                Duration refreshInterval,
                Duration firstLoadTimeout,
                boolean failOnFirstLoad,
                List<String> featureFlagFiles,
                List<String> configFiles,
                List<String> jsonConfigFiles,
                String targetingKeyType) {

    static final String FEATURE_FLAG_FILES = "ageris.feature-flags.files";
    static final String CONFIG_FILES = "ageris.configs.files";
    static final String JSON_CONFIG_FILES = "ageris.json-configs.files";

    private static final Duration REFRESH = Duration.ofSeconds(10);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * @param config the "ageris" part of Helidon's configuration
     * @return the settings, with the defaults for what is not set
     */
    static Settings from(Config config) {
        return new Settings(config.get("application").asString().orElse(""),
                config.get("path").asString().orElse(""),
                duration(config, "refresh-interval", REFRESH),
                duration(config, "first-load-timeout", TIMEOUT),
                trueOrFalse(config, "fail-on-first-load-failure", true),
                files(config.get("feature-flags.files")),
                files(config.get("configs.files")),
                files(config.get("json-configs.files")),
                config.get("openfeature.targeting-key-type").asString().orElse("user"));
    }

    /**
     * Looks at the files only, so that a setting that cannot be right does not matter to an application that set no files.
     *
     * @param config the "ageris" part of Helidon's configuration
     * @return true if files are set for at least one manager, which is what asks for Ageris to be set up
     */
    static boolean hasFiles(Config config) {
        return !files(config.get("feature-flags.files")).isEmpty() || !files(config.get("configs.files")).isEmpty()
                || !files(config.get("json-configs.files")).isEmpty();
    }

    /**
     * @return true if files are set for at least one manager, which is what asks for Ageris to be set up
     */
    boolean hasFiles() {
        return !featureFlagFiles.isEmpty() || !configFiles.isEmpty() || !jsonConfigFiles.isEmpty();
    }

    /**
     * Refuses what a configuration can get wrong, with a message that names the setting.
     *
     * @throws IllegalStateException if a setting is missing or cannot be right
     */
    void check() {
        if (application.isBlank()) {
            throw new IllegalStateException("Set ageris.application to the name of the application, as the fetcher of the configuration files uses it");
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalStateException("ageris.refresh-interval must be longer than zero, but was " + refreshInterval);
        }
        refuseTimeoutThatCannotBeRight();
        refuseBlank(FEATURE_FLAG_FILES, featureFlagFiles);
        refuseBlank(CONFIG_FILES, configFiles);
        refuseBlank(JSON_CONFIG_FILES, jsonConfigFiles);
    }

    /** A duration the way Helidon's configuration writes it, such as PT10S; anything else is refused with the name of the setting. */
    private static Duration duration(Config config, String key, Duration unlessSet) {
        final String written = config.get(key).asString().orElse("");
        if (written.isBlank()) {
            return unlessSet;
        }
        try {
            return Duration.parse(written.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("ageris." + key + " must be a duration such as PT10S for ten seconds, but was " + written, exception);
        }
    }

    /** Helidon reads anything it does not know as false, which would switch a safeguard off by a slip of the pen. */
    private static boolean trueOrFalse(Config config, String key, boolean unlessSet) {
        final String written = config.get(key).asString().orElse("").trim();
        if (written.isEmpty()) {
            return unlessSet;
        }
        if (!"true".equalsIgnoreCase(written) && !"false".equalsIgnoreCase(written)) {
            throw new IllegalStateException("ageris." + key + " must be true or false, but was " + written);
        }
        return Boolean.parseBoolean(written);
    }

    private void refuseTimeoutThatCannotBeRight() {
        if (firstLoadTimeout.isNegative()) {
            throw new IllegalStateException("ageris.first-load-timeout must not be negative, but was " + firstLoadTimeout);
        }
        if (firstLoadTimeout.isZero() && failOnFirstLoad) {
            /* Loading takes some time, so a start that does not wait and must not go on without a load can only fail. */
            throw new IllegalStateException("ageris.first-load-timeout must be longer than zero while ageris.fail-on-first-load-failure"
                    + " is true; zero means not to wait, which only fits a start that goes on without a load");
        }
    }

    private static void refuseBlank(String setting, List<String> files) {
        if (files.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(setting + " holds a blank file name: \"" + String.join("\", \"", files) + "\"");
        }
    }

    /** A list as a list, or one text with the names separated by commas, as a properties file has it. */
    private static List<String> files(Config config) {
        final List<String> files = new ArrayList<>();
        for (final String entry : config.asList(String.class).orElse(List.of())) {
            for (final String name : entry.split(",", -1)) {
                files.add(name.trim());
            }
        }
        /* A key with nothing behind it, as a placeholder that came to nothing leaves it, is a list that is not set. */
        return files.size() == 1 && files.get(0).isEmpty() ? List.of() : List.copyOf(files);
    }
}
