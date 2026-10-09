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
import io.github.lichtsinn.ageris.core.Evaluation.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory store for configurations, held by each of the configuration managers.
 *
 * @param <T> type of configuration to be stored
 */
final class ConfigurationStore<T> {

    private final Logger log = LoggerFactory.getLogger(ConfigurationStore.class);

    /* Copy-on-write, so that a listener may add or remove listeners while it is being called. Such a change
     * takes effect from the next update. */
    private final List<ConfigurationListener> listeners = new CopyOnWriteArrayList<>();

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
     * Replaces the stored configurations and calls the listeners.
     *
     * @param configurations map of configurations to be stored
     */
    void updateConfigurations(Map<String, Configuration<T>> configurations) {
        updateConfigurations(configurations, true);
    }

    /**
     * Replaces the stored configurations.
     *
     * @param configurations map of configurations to be stored
     * @param notify true to call the listeners, false if the content is the same as the content loaded last
     */
    void updateConfigurations(Map<String, Configuration<T>> configurations, boolean notify) {
        /* Request threads read this map while the loader thread replaces it, so the store must never hold a
         * map the caller can still mutate. Copying also keeps the order the configurations arrived in. */
        configurationStore.set(Collections.unmodifiableMap(new LinkedHashMap<>(configurations)));
        status.set(new ConfigurationStatus(State.LOADED, null));
        if (notify) {
            notifyListeners();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private void notifyListeners() {
        ConfigurationUpdate update = new ConfigurationUpdate(getConfigurationNames());
        for (ConfigurationListener listener : listeners) {
            try {
                listener.onUpdate(update);
            } catch (Throwable throwable) {
                /* A listener must not undo an update that was applied, or keep the other listeners from hearing of it.
                 * Errors are caught too: on the loader's thread an error would end the scheduled refresh of this
                 * manager silently and for good. */
                log.error("Configuration listener {} failed", listener.getClass().getName(), throwable);
            }
        }
    }

    /**
     * Adds a listener that is called after the stored configurations were replaced.
     *
     * @param listener listener to be added
     */
    void addListener(ConfigurationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        listeners.add(listener);
    }

    /**
     * Removes a listener. Does nothing if it was not added.
     *
     * @param listener listener to be removed
     */
    void removeListener(ConfigurationListener listener) {
        listeners.remove(listener);
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

    /**
     * Evaluates the configuration with provided name for given runtime context. The value is the one
     * {@link #getValue(String, ConfigurationContext, Object)} returns.
     *
     * @param name name of configuration to be evaluated
     * @param runtimeContext context at runtime
     * @param fallback value of the evaluation, if no configuration with provided name found
     * @return evaluation of the configuration
     */
    Evaluation<T> evaluate(String name, ConfigurationContext runtimeContext, T fallback) {
        Configuration<T> configuration = configurationStore.get().get(name);
        if (configuration != null) {
            return configuration.evaluate(runtimeContext);
        }
        return new Evaluation<>(fallback, Reason.NOT_FOUND, Collections.emptyMap());
    }
}
