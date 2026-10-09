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
package io.github.lichtsinn.ageris.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lichtsinn.ageris.core.structure.Context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hands out configuration managers for tests, with feature flags and configs set from Java values.
 *
 * The managers are the ones an application uses, so the code under test needs no change. A value that is set
 * applies to every configuration context and takes effect before the call returns. Listeners of the manager are
 * called on every call that sets a value, also when the value is the one already set, whereas a refresh calls
 * them only when the content has changed. A policy is set like any config: construct it with the objects it would
 * be bound to, or with stand-ins for them.
 *
 * This class is for tests. Nothing refreshes its managers.
 *
 * Listeners are called while this object is locked. A listener may set another value itself, but it must not
 * wait for another thread that sets a value on this or another {@code InMemoryAgeris}.
 */
public final class InMemoryAgeris {

    private final FeatureFlagManager featureFlagManager = new FeatureFlagManager();
    private final ConfigManager configManager = new ConfigManager();
    private final JsonConfigManager jsonConfigManager = new JsonConfigManager();

    /* Insertion-ordered, so the managers list their entries in the order a test first set them. */
    private final Map<String, Boolean> flags = new LinkedHashMap<>();
    private final Map<String, Object> configs = new LinkedHashMap<>();
    private final Map<String, JsonNode> jsonConfigs = new LinkedHashMap<>();

    /**
     * Creates managers that count as loaded and hold no feature flags or configs.
     */
    public InMemoryAgeris() {
        featureFlagManager.updateConfigurations(Collections.emptyMap());
        configManager.updateConfigurations(Collections.emptyMap());
        jsonConfigManager.updateConfigurations(Collections.emptyMap());
    }

    /**
     * @return the feature flag manager, the same one on every call
     */
    public FeatureFlagManager featureFlagManager() {
        return featureFlagManager;
    }

    /**
     * @return the config manager, the same one on every call
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * @return the JSON config manager, the same one on every call
     */
    public JsonConfigManager jsonConfigManager() {
        return jsonConfigManager;
    }

    /**
     * Sets a feature flag for every configuration context.
     *
     * @param name name of the feature flag
     * @param active true if the feature flag is on
     * @return this
     */
    public synchronized InMemoryAgeris setFlag(String name, boolean active) {
        requireNonNull(name, "name");
        flags.put(name, active);
        featureFlagManager.updateConfigurations(configurations(flags));
        return this;
    }

    /**
     * Removes a feature flag, so that callers get their default for it. Does nothing if it is not set.
     *
     * @param name name of the feature flag
     * @return this
     */
    public synchronized InMemoryAgeris removeFlag(String name) {
        requireNonNull(name, "name");
        if (flags.remove(name) != null) {
            featureFlagManager.updateConfigurations(configurations(flags));
        }
        return this;
    }

    /**
     * Sets a config or a policy for every configuration context, under its own class. An object of an anonymous or
     * generated class, such as a lambda or a mock, is rejected: use {@link #setConfig(Class, Object)} for it.
     *
     * @param config the config
     * @return this
     */
    public InMemoryAgeris setConfig(Object config) {
        requireNonNull(config, "config");
        if (config instanceof Class) {
            throw new IllegalArgumentException("Pass a config, not its class " + ((Class<?>) config).getName());
        }
        Class<?> type = config.getClass();
        if (isGenerated(type)) {
            /* Stored under such a class the config would never be found, and the test would see a default
             * without any hint why. */
            throw new IllegalArgumentException("Nothing looks a config up by the generated class " + type.getName()
                    + ". Name the class your code asks for: setConfig(type, config)");
        }
        return putConfig(type, config);
    }

    /**
     * Sets a config or a policy for every configuration context, under the given class. Use this when the class
     * of the object is not the class the code under test asks for, as with a subclass or a mock.
     *
     * @param type class the config is looked up with
     * @param config the config
     * @param <T> type of the config
     * @return this
     */
    public <T> InMemoryAgeris setConfig(Class<T> type, T config) {
        requireNonNull(type, "type");
        requireNonNull(config, "config");
        if (!type.isInstance(config)) {
            throw new IllegalArgumentException(config.getClass().getName() + " is not a " + type.getName());
        }
        return putConfig(type, config);
    }

    /**
     * Removes a config, so that callers get one from its no-argument constructor. Does nothing if it is not set.
     *
     * @param type class the config is looked up with
     * @return this
     */
    public synchronized InMemoryAgeris removeConfig(Class<?> type) {
        requireNonNull(type, "type");
        if (configs.remove(type.getName()) != null) {
            configManager.updateConfigurations(configurations(configs));
        }
        return this;
    }

    /**
     * Sets a JSON config for every configuration context.
     *
     * @param name name of the JSON config
     * @param config the JSON config
     * @return this
     */
    public synchronized InMemoryAgeris setJsonConfig(String name, JsonNode config) {
        requireNonNull(name, "name");
        requireNonNull(config, "config");
        jsonConfigs.put(name, config);
        jsonConfigManager.updateConfigurations(configurations(jsonConfigs));
        return this;
    }

    /**
     * Removes a JSON config, so that callers get an empty one. Does nothing if it is not set.
     *
     * @param name name of the JSON config
     * @return this
     */
    public synchronized InMemoryAgeris removeJsonConfig(String name) {
        requireNonNull(name, "name");
        if (jsonConfigs.remove(name) != null) {
            jsonConfigManager.updateConfigurations(configurations(jsonConfigs));
        }
        return this;
    }

    private synchronized InMemoryAgeris putConfig(Class<?> type, Object config) {
        configs.put(type.getName(), config);
        configManager.updateConfigurations(configurations(configs));
        return this;
    }

    /* A context without modifiers gives the same value for every configuration context. */
    private static <T> Map<String, Configuration<T>> configurations(Map<String, T> values) {
        Map<String, Configuration<T>> configurations = new LinkedHashMap<>();
        values.forEach((name, value) -> configurations.put(name, new Configuration<>(name, new Context<>(value, null))));
        return configurations;
    }

    /* Anonymous classes, lambdas, and the subclasses and proxies that mocking and proxy libraries generate. */
    private static boolean isGenerated(Class<?> type) {
        return type.isAnonymousClass() || type.isSynthetic() || type.isHidden()
                || type.getName().contains("$MockitoMock$") || type.getName().contains("$$");
    }

    private static void requireNonNull(Object argument, String name) {
        if (argument == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
