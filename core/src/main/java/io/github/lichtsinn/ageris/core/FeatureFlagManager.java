/*
 * Copyright 2018 Medallia, Inc.
 * Modifications copyright 2026 Mario Lichtsinn; this file differs from the version
 * released by Medallia, Inc. See NOTICE.
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * In-memory store for runtime feature flags.
 */
public class FeatureFlagManager implements ConfigurationManager<Boolean> {

    private final ConfigurationStore<Boolean> store = new ConfigurationStore<>();

    /**
     * Creates new feature flag manager.
     */
    public FeatureFlagManager() {
        // Nothing to initialize beyond the store.
    }

    @Override
    public void updateConfigurations(Map<String, Configuration<Boolean>> configurations) {
        store.updateConfigurations(configurations);
    }

    /**
     * @return status of this manager: whether it has loaded, and why its last refresh failed if it did.
     */
    public ConfigurationStatus getStatus() {
        return store.getStatus();
    }

    /**
     * Adds a listener that is called after this manager has loaded new content. It is not called for content that is
     * the same as the content loaded last, or for a refresh that failed.
     *
     * @param listener listener to be added
     */
    public void addListener(ConfigurationListener listener) {
        store.addListener(listener);
    }

    /**
     * Removes a listener. Does nothing if it was not added.
     *
     * @param listener listener to be removed
     */
    public void removeListener(ConfigurationListener listener) {
        store.removeListener(listener);
    }

    /**
     * @return the store of this manager, for the reader that refreshes it
     */
    ConfigurationStore<Boolean> store() {
        return store;
    }

    /**
     * @return list of configuration names from configuration store.
     */
    public List<String> getConfigurationNames() {
        return store.getConfigurationNames();
    }

    /**
     * Returns configuration store as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @return JSON of serialized configuration store
     * @throws IOException in case of JSON processing issues
     */
    public String asString(ConfigurationWriter<Boolean> writer) throws IOException {
        return store.asString(writer);
    }

    /**
     * Returns configuration as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @param configName name of configuration to be serialized by configuration writer
     * @return JSON of serialized configuration
     * @throws IOException in case of JSON processing issues
     */
    public String asString(ConfigurationWriter<Boolean> writer, String configName) throws IOException {
        return store.asString(writer, configName);
    }

    /**
     * Returns true if feature flag with given name (case-sensitive) was found and is active for provided runtime configuration context,
     * false otherwise.
     *
     * @param name name of feature flag to be evaluated, case-sensitive
     * @param runtimeContext configuration context from request to be used for evaluation of feature flag
     * @return true if feature flag was found and is active
     */
    public boolean isActive(String name, ConfigurationContext runtimeContext) {
        return isActive(name, runtimeContext, false);
    }

    /**
     * Return true if feature flag with given name (case-sensitive) was found and is active for provided runtime configuration context,
     * default value if feature flag could not be found.
     *
     * @param name name of feature flag to be evaluate, case-sensitive
     * @param runtimeContext configuration context from request to be used for evaluation of feature flag
     * @param defaultValue default to be return in feature flag manager does not contain feature flag
     * @return true or false if feature flag was found and is active or inactive, default value if feature flag could not be found.
     */
    public boolean isActive(String name, ConfigurationContext runtimeContext, boolean defaultValue) {
        return store.getValue(name, runtimeContext, Boolean.valueOf(defaultValue)).booleanValue();
    }
}
