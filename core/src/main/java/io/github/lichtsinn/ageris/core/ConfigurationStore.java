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

import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory store for configurations, held by each of the configuration managers.
 *
 * @param <T> type of configuration to be stored
 */
final class ConfigurationStore<T> {

    private final AtomicReference<Map<String, Configuration<T>>> configurationStore;

    /* One immutable object replaced in a single step, so a caller never sees the state of one refresh with the
     * failure of another. */
    private final AtomicReference<ConfigurationStatus> status;

    /** Creates an empty store. */
    ConfigurationStore() {
        configurationStore = new AtomicReference<>(new HashMap<>());
        status = new AtomicReference<>(new ConfigurationStatus(State.NOT_LOADED, null));
    }

    /**
     * Replaces the stored configurations.
     *
     * @param configurations map of configurations to be stored
     */
    void updateConfigurations(Map<String, Configuration<T>> configurations) {
        /* Request threads read this map while the loader thread replaces it, so the store must never hold a
         * map the caller can still mutate. Copying also keeps the order the configurations arrived in. */
        configurationStore.set(Collections.unmodifiableMap(new LinkedHashMap<>(configurations)));
        status.set(new ConfigurationStatus(State.LOADED, null));
    }

    /**
     * Records a refresh that found the content unchanged, so that the stored configurations are current.
     */
    void unchanged() {
        status.set(new ConfigurationStatus(State.LOADED, null));
    }

    /**
     * Records a failed refresh. The stored configurations are kept.
     *
     * @param failure exception that ended the refresh
     */
    void failed(Exception failure) {
        status.updateAndGet(current -> new ConfigurationStatus(
                current.getState() == State.NOT_LOADED ? State.NOT_LOADED : State.STALE, failure));
    }

    /**
     * @return status of this store
     */
    ConfigurationStatus getStatus() {
        return status.get();
    }

    /**
     * @return list of configuration names from configuration store.
     */
    List<String> getConfigurationNames() {
        return new ArrayList<>(configurationStore.get().keySet());
    }

    /**
     * Returns configuration store as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @return JSON of serialized configuration store
     * @throws IOException in case of JSON processing issues
     */
    String asString(ConfigurationWriter<T> writer) throws IOException {
        return writer.writeValueAsString(configurationStore.get());
    }

    /**
     * Returns configuration as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @param configName name of configuration to be serialized by configuration writer
     * @return JSON of serialized configuration
     * @throws IOException in case of JSON processing issues
     */
    String asString(ConfigurationWriter<T> writer, String configName) throws IOException {
        Configuration<T> configuration = configurationStore.get().get(configName);
        if (configuration == null) {
            return writer.writeValueAsString(Collections.emptyMap());
        }
        Map<String, Configuration<T>> map = new HashMap<>();
        map.put(configuration.getName(), configuration);
        return writer.writeValueAsString(map);
    }

    /**
     * Return configuration value object of type T with provided name from configuration store
     * for given runtime context, default value if no configuration with provided name found.
     *
     * @param name name of configuration to be evaluated
     * @param runtimeContext context at runtime
     * @param defaultValue value to be returned, if no configuration with provided name found
     * @return configuration value object
     */
    T getValue(String name, ConfigurationContext runtimeContext, T defaultValue) {
        Configuration<T> configuration = configurationStore.get().get(name);
        if (configuration != null) {
            return configuration.getValue(runtimeContext);
        }
        return defaultValue;
    }
}
