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
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.ConfigurationStatus;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the managers an application asked for, starts the loader and waits for the first load. The managers are handed
 * out by this object, so that no other bean gets one before it is loaded.
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
        final String application = properties.application();
        if (application == null || application.isBlank()) {
            throw new IllegalStateException("Set ageris.application to the name of the application, as the fetcher of the configuration files uses it");
        }
        customizers.forEach(customizer -> customizer.customize(ageris));
        featureFlags = featureFlags(ageris, application, properties.featureFlags().files());
        configs = configs(ageris, application, properties.configs().files());
        jsonConfigs = jsonConfigs(ageris, application, properties.jsonConfigs().files());
        loader = ageris.createLoader(properties.refreshInterval());
        loader.start();
        if (!loader.awaitFirstLoad(properties.firstLoadTimeout()) && properties.failOnFirstLoadFailure()) {
            loader.shutdown();
            throw new IllegalStateException(whyNotLoaded(properties));
        }
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

    private String whyNotLoaded(AgerisProperties properties) {
        final StringBuilder why = new StringBuilder();
        for (final ConfigurationStatus status : statuses().values()) {
            if (status.getFailure() != null) {
                why.append(why.length() == 0 ? "" : "; ").append(status.getFailure().getMessage());
            }
        }
        final String reason = why.length() == 0 ? "the configuration files were not loaded within " + properties.firstLoadTimeout() : why.toString();
        return "Ageris could not load its configuration files: " + reason
                + ". Set ageris.fail-on-first-load-failure=false to start anyway, with defaults until a load succeeds";
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
