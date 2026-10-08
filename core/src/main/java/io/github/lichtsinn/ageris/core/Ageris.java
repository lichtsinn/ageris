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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.ConfigurationLoaderMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;

import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import io.github.lichtsinn.ageris.core.utils.ClassFinder;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;

import java.security.MessageDigest;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main Ageris initializer, that creates {@link FeatureFlagManager}, {@link ConfigManager}, {@link JsonConfigManager}
 * and {@link ConfigurationLoader} instances.
 */
public final class Ageris {

    private final ConfigurationFetcher fetcher;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final MessageDigest digest;
    private final List<ConfigurationReader> readers;
    private final Bindings bindings;
    private ConfigurationLoaderMetrics metrics;
    private boolean skipNonInstantiable;
    private int maximumSkips;

    /**
     * Creates main Ageris initializer with provided configuration fetcher.
     *
     * @param fetcher configuration fetcher
     */
    public Ageris(ConfigurationFetcher fetcher) {
        this(fetcher, Executors.newScheduledThreadPool(3), new ObjectMapper(), sha256());
    }

    /**
     * Creates main Ageris initializer with provided configuration fetcher, executor service, object mapper and message digest.
     *
     * @param fetcher configuration fetcher
     * @param executorService executor service for scheduling asynchronous configuration tasks
     * @param objectMapper Jackson JSON deserializer
     * @param digest message digest, used only as a prototype: every configuration reader is given its own
     *               instance of the same algorithm, because {@link MessageDigest} is not thread-safe and
     *               readers run concurrently on the shared executor service
     */
    public Ageris(ConfigurationFetcher fetcher, ScheduledExecutorService executorService, ObjectMapper objectMapper, MessageDigest digest) {
        this.fetcher = fetcher;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        this.digest = digest;
        readers = new ArrayList<>();
        bindings = new Bindings();
        skipNonInstantiable = false;
        maximumSkips = Integer.MAX_VALUE;
    }

    /**
     * Sets metrics for loader.
     *
     * @param metrics metrics for loader
     */
    public void setMetrics(ConfigurationLoaderMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Sets maximum number of skips, until a refresh is forced. A refresh is skipped when the fetched content is the same
     * as the content loaded last. The default is {@link Integer#MAX_VALUE}, so unchanged content is in practice never
     * loaded again. With 0, the content is loaded again on every refresh.
     *
     * @param maximumSkips maximum number of skips
     */
    public void setMaximumSkips(int maximumSkips) {
        this.maximumSkips = maximumSkips;
    }

    /**
     * Continues loading configurations, just skip each non-instantiable configuration. A skipped configuration is no
     * longer served, even if an earlier version of it had loaded, and is not tried again until the content changes.
     */
    public void skipNonInstantiableConfiguration() {
        skipNonInstantiable = true;
    }

    /**
     * Stops loading of all configurations in case of a non-instantiable configuration, keeping the ones already loaded.
     * This is the default.
     */
    public void failNonInstantiableConfiguration() {
        skipNonInstantiable = false;
    }

    /**
     * Binds an object that config classes can receive through their constructor, by declaring a parameter
     * of this type annotated with {@code @JacksonInject}.
     *
     * The object is shared by every config that asks for it and by every thread that uses those configs, so
     * it must be safe to use concurrently. Bind everything before the loader is started. A configuration
     * file can never supply or replace a binding.
     *
     * @param type type a config class declares to receive the binding
     * @param binding object to hand to config classes
     * @param <B> type of the binding
     * @throws IllegalArgumentException if type or binding is null, or the type is already bound
     */
    public <B> void bind(Class<B> type, B binding) {
        bindings.bind(type, binding);
    }

    /**
     * Binds an object under a name, for when two objects of the same type are needed. A config class asks
     * for it with {@code @JacksonInject("name")} on a constructor parameter.
     *
     * The same rules apply as for {@link #bind(Class, Object)}.
     *
     * @param name name a config class uses to ask for the binding
     * @param binding object to hand to config classes
     * @throws IllegalArgumentException if name or binding is null, or the name is already bound
     */
    public void bind(String name, Object binding) {
        bindings.bind(name, binding);
    }

    /**
     * Creates builder with new {@link FeatureFlagManager} for provided application.
     *
     * @param application name of application
     * @return new builder for feature flag manager
     */
    public FeatureFlagManagerBuilder addFeatureFlagManager(String application) {
        return new FeatureFlagManagerBuilder(application);
    }

    /**
     * Creates builder with new {@link ConfigManager} for provided application.
     *
     * @param application name of application
     * @return new builder for config manager
     */
    public ConfigManagerBuilder addConfigManager(String application) {
        return new ConfigManagerBuilder(application);
    }

    /**
     * Creates builder with new {@link JsonConfigManager} for provided application.
     *
     * @param application name of application
     * @return new builder for Json config manager
     */
    public JsonConfigManagerBuilder addJsonConfigManager(String application) {
        return new JsonConfigManagerBuilder(application);
    }

    /**
     * Creates new {@link ConfigurationLoader} with provided refresh interval and immediately start it.
     *
     * @param refreshInterval interval between cycles of loading configuration
     * @return new, started configuration loader
     */
    public ConfigurationLoader createAndStartLoader(Duration refreshInterval) {
        ConfigurationLoader loader = createLoader(refreshInterval);
        loader.start();
        return loader;
    }

    /**
     * Creates new {@link ConfigurationLoader} with provided refresh interval.
     *
     * @param refreshInterval interval between cycles of loading configuration
     * @return new configuration loader
     */
    public ConfigurationLoader createLoader(Duration refreshInterval) {
        if (metrics == null) {
            metrics = new ConfigurationLoaderMetrics();
        }
        ConfigurationLoader loader = new ConfigurationLoader(metrics, Collections.unmodifiableList(new ArrayList<>(readers)),
                executorService, refreshInterval);
        readers.clear();
        return loader;
    }

    /**
     * Builder for {@link FeatureFlagManager}.
     */
    public class FeatureFlagManagerBuilder {

        private final String application;
        private final List<String> fileNames;
        private final String rootNode;
        private FeatureFlagMetrics metrics;

        /**
         * Creates builder for {@link FeatureFlagManager}.
         */
        public FeatureFlagManagerBuilder(String application) {
            this.application = application;
            this.fileNames = new ArrayList<>();
            rootNode = "feature-flags";
        }

        /**
         * Register name of file with feature flags.
         */
        public FeatureFlagManagerBuilder registerFile(String fileName) {
            this.fileNames.add(fileName);
            return this;
        }

        /**
         * Set metrics.
         */
        public FeatureFlagManagerBuilder setMetrics(FeatureFlagMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * @return {@link FeatureFlagManager}.
         */
        public FeatureFlagManager build() {
            FeatureFlagManager featureFlagManager = new FeatureFlagManager();
            if (metrics == null) {
                metrics = new FeatureFlagMetrics();
            }
            FeatureFlagMapper featureFlagMapper = new FeatureFlagMapper(rootNode, skipNonInstantiable, objectMapper, metrics, bindings);
            ConfigurationReader<Boolean> featureFlagReader = new ConfigurationReader<>(application, fileNames, fetcher, featureFlagMapper, featureFlagManager, newDigest(), metrics, maximumSkips);
            readers.add(featureFlagReader);
            return featureFlagManager;
        }
    }

    /**
     * Builder for {@link ConfigManager}.
     */
    public class ConfigManagerBuilder {

        private final String application;
        private final List<String> fileNames;
        private final String rootNode;
        private final ClassFinder<Object> classFinder;
        private ConfigMetrics metrics;

        /**
         * Creates builder for {@link ConfigManager}.
         */
        public ConfigManagerBuilder(String application) {
            this.application = application;
            this.fileNames = new ArrayList<>();
            rootNode = "configs";
            classFinder = new DefaultClassFinder();
        }

        /**
         * Register name of file with configs.
         */
        public ConfigManagerBuilder registerFile(String fileName) {
            this.fileNames.add(fileName);
            return this;
        }

        /**
         * Set metrics.
         */
        public ConfigManagerBuilder setMetrics(ConfigMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * @return {@link ConfigManager}.
         */
        public ConfigManager build() {
            ConfigManager configManager = new ConfigManager();
            if (metrics == null) {
                metrics = new ConfigMetrics();
            }
            ConfigurationMapper<Object> configMapper = new ConfigurationMapper<>(rootNode, skipNonInstantiable, objectMapper, metrics, classFinder,
                    bindings);
            ConfigurationReader<Object> configReader = new ConfigurationReader<>(application, fileNames, fetcher, configMapper, configManager, newDigest(), metrics, maximumSkips);
            readers.add(configReader);
            return configManager;
        }
    }

    /**
     * Builder for {@link JsonConfigManager}.
     */
    public class JsonConfigManagerBuilder {

        private final String application;
        private final List<String> fileNames;
        private final String rootNode;
        private JsonConfigMetrics metrics;

        /**
         * Creates builder for {@link JsonConfigManager}.
         */
        public JsonConfigManagerBuilder(String application) {
            this.application = application;
            this.fileNames = new ArrayList<>();
            rootNode = "configs";
        }

        /**
         * Register name of file with Json configs.
         */
        public JsonConfigManagerBuilder registerFile(String fileName) {
            this.fileNames.add(fileName);
            return this;
        }

        /**
         * Set metrics.
         */
        public JsonConfigManagerBuilder setMetrics(JsonConfigMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * @return {@link JsonConfigManager}.
         */
        public JsonConfigManager build() {
            JsonConfigManager configManager = new JsonConfigManager();
            if (metrics == null) {
                metrics = new JsonConfigMetrics();
            }
            JsonConfigMapper configMapper = new JsonConfigMapper(rootNode, skipNonInstantiable, objectMapper, metrics, bindings);
            ConfigurationReader<JsonNode> configReader = new ConfigurationReader<>(application, fileNames, fetcher, configMapper, configManager, newDigest(), metrics, maximumSkips);
            readers.add(configReader);
            return configManager;
        }
    }

    /**
     * Returns a new message digest instance using the same algorithm as the prototype digest.
     *
     * Each {@link ConfigurationReader} must own its digest: {@link MessageDigest} is not thread-safe, and
     * {@link ConfigurationLoader} schedules every reader on one shared executor service, so readers would
     * otherwise interleave their update() and digest() calls. Because digest() also resets the instance, a
     * reader could then hash content it never fetched, or hash nothing at all, and wrongly conclude that
     * configurations were unchanged - delaying real updates by up to maximumSkips refresh cycles.
     *
     * @return new message digest instance, never shared with another reader
     * @throws IllegalStateException in case lookup throws NoSuchAlgorithmException
     */
    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(digest.getAlgorithm());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * @return SHA-256 message digest instance.
     * @throws IllegalStateException in case lookup throws NoSuchAlgorithmException
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
