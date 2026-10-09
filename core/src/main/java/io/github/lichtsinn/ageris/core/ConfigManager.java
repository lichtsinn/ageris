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

import io.github.lichtsinn.ageris.core.exception.ConfigInstantiationException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * In-memory store for mixed-type configurations.
 */
public class ConfigManager implements ConfigurationManager<Object> {

    private final ConfigurationStore<Object> store = new ConfigurationStore<>();

    /**
     * Creates new config manager.
     */
    public ConfigManager() {
        // Nothing to initialize beyond the store.
    }

    @Override
    public void updateConfigurations(Map<String, Configuration<Object>> configurations) {
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
    ConfigurationStore<Object> store() {
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
    public String asString(ConfigurationWriter<Object> writer) throws IOException {
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
    public String asString(ConfigurationWriter<Object> writer, String configName) throws IOException {
        return store.asString(writer, configName);
    }

    /**
     * Return config value object of class T for given request (runtime) config context.
     *
     * @param clazz Java class of config to be evaluated
     * @param runtimeContext context from request to be used for evaluation of config
     * @param <T> class of config
     * @return config value object from config store, or a new instance from the class's no-argument constructor
     * @throws ConfigInstantiationException in case of instantiation problems
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public <T> T getConfig(Class<T> clazz, ConfigurationContext runtimeContext) throws ConfigInstantiationException {
        Object value = store.getValue(clazz.getName(), runtimeContext, null);
        if (value != null) {
            try {
                return clazz.cast(value);
            } catch (ClassCastException exception) {
                // try to return new config object based on default constructor
            }
        }
        try {
            /* Class.newInstance() is deprecated and propagates any exception the constructor throws
             * undeclared, including checked ones. Going through the constructor wraps those in
             * InvocationTargetException instead, so every failure here is reported as declared. */
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException exception) {
            throw new ConfigInstantiationException(exception);
        }
    }
}
