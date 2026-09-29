/*
 * Copyright 2018 Medallia, Inc.
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
package com.medallia.merci.core;

import com.medallia.merci.core.fetcher.ConfigurationFetcher;
import com.medallia.merci.core.metrics.UpdateConfigurationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader for configurations.
 *
 *  @param <T> type of configuration
 */
public class ConfigurationReader<T> {

    private final Logger log = LoggerFactory.getLogger(ConfigurationReader.class);

    private final String application;
    private final List<String> fileNames;
    private final ConfigurationFetcher fetcher;
    private final ConfigurationMapper<T> parser;
    private final ConfigurationManager<T> manager;

    /** Message digest. */
    private final MessageDigest digest;

    /** Metrics for configurations. */
    private final UpdateConfigurationMetrics metrics;

    /** Maximum number of time the injected (decorated) configuration adapter will not be updated in case of same content. */
    private final int maximumSkips;

    /** Number of same-content skips left before updating the injected (decorated) configuration adapter. */
    private final AtomicInteger skipsLeft;

    /** Hash of configuration content from response of previous config request. */
    private byte[] previousHash;

    /**
     * Creates configuration reader.
     *
     * @param application application
     * @param fileNames names of textual configuration files
     * @param fetcher fetcher for remote or local configuration files
     * @param parser deserializer for textual configuration files
     * @param manager configuration manager
     * @param digest message digest
     * @param metrics metrics
     * @param maximumSkips maximum number of skips
     */
    public ConfigurationReader(String application, List<String> fileNames,
                               ConfigurationFetcher fetcher,
                               ConfigurationMapper<T> parser,
                               ConfigurationManager<T> manager,
                               MessageDigest digest,
                               UpdateConfigurationMetrics metrics,
                               final int maximumSkips) {
        this.application = application;
        this.fetcher = fetcher;
        this.parser = parser;
        this.manager = manager;
        this.fileNames = fileNames;
        this.digest = digest;
        this.metrics = metrics;
        previousHash = new byte[0];
        this.maximumSkips = maximumSkips;
        skipsLeft = new AtomicInteger(maximumSkips);
    }

    /**
     * Execute fetch, parse and store of configurations.
     *
     * @throws IOException in case of a failure
     */
    public void execute() throws IOException {
        Map<String, String> contents = fetcher.fetch(fileNames, application);
        if (contents.isEmpty() && !fileNames.isEmpty()) {
            /* Storing nothing would drop every configuration and silently fall back to the defaults that
             * callers pass to isActive() and getConfig(). Fetching no content at all means the configuration
             * source is unavailable, not that all configurations were deleted, so keep the ones already
             * loaded and try again on the next cycle. */
            throw new IOException("Fetched no configuration content for any of " + fileNames + " of application "
                    + application + ", keeping previously loaded configurations");
        }
        List<String> missingFileNames = missingFileNames(contents);
        if (!missingFileNames.isEmpty()) {
            /* A partial fetch still updates, because a fetcher may be configured to treat absent files as
             * optional. Configurations defined only in those files do disappear, so say so. */
            log.warn("Fetched {} of {} configuration files for application {}, missing {}. Configurations defined"
                            + " only in the missing files fall back to the defaults provided by callers.",
                    contents.size(), fileNames.size(), application, missingFileNames);
        }
        contents.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(entry -> digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        byte[] hash = digest.digest();
        if (skipsLeft.getAndDecrement() > 0 && Arrays.equals(previousHash, hash)) {
            metrics.incrementSameContentsSkips();
        } else {
            metrics.incrementNewContentsUpdates();
            updateConfigurationManager(contents);
            previousHash = hash;
            skipsLeft.set(maximumSkips);
        }
    }

    /**
     * Update configuration manager with configurations from provided map.
     *
     * @throws IOException in case of a problem parsing configuration content
     */
    private void updateConfigurationManager(Map<String, String> contents) throws IOException {
        int numConfigurations = 0;
        int numContentFailures = 0;
        Map<String, Configuration<T>> configurationCache = new LinkedHashMap<> ();
        for (String content : contents.values()) {
            try {
                Map<String, Configuration<T>> configurations = parser.readValue(content);
                numConfigurations += configurations.size();
                configurationCache.putAll(configurations);
            } catch (IOException exception) {
                numContentFailures++;
            }
        }
        metrics.incrementContentFailures(numContentFailures);
        if (numContentFailures > 0) {
            throw new IOException("Bad configuration content.");
        }
        metrics.incrementNameDuplicates(numConfigurations - configurationCache.size());
        metrics.incrementUpdates(configurationCache.size());
        manager.updateConfigurations(configurationCache);
    }

    /**
     * Returns the registered file names for which the fetcher returned no content.
     *
     * @param contents fetched configuration content, keyed by file name
     * @return registered file names absent from the fetched content, empty if all were fetched
     */
    private List<String> missingFileNames(Map<String, String> contents) {
        List<String> missing = new ArrayList<>(fileNames);
        missing.removeAll(contents.keySet());
        return missing;
    }

    /**
     * Sets internal counter of possible number of skips to zero (reset).
     */
    public void reset() {
        skipsLeft.lazySet(0);
    }
}
