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

import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.metrics.UpdateConfigurationMetrics;
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
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    /** Store of the manager, told the outcome of every refresh. Null when the manager is not one of Ageris's own. */
    private final ConfigurationStore<T> store;

    /** Message digest. */
    private final MessageDigest digest;

    /** Metrics for configurations. */
    private final UpdateConfigurationMetrics metrics;

    /** Maximum number of time the injected (decorated) configuration adapter will not be updated in case of same content. */
    private final int maximumSkips;

    /** Number of same-content skips left before updating the injected (decorated) configuration adapter. */
    private final AtomicInteger skipsLeft;

    /** Hash of configuration content from response of previous config request. */
    /* Volatile covers the reference only, which is all that is needed: the array is replaced, never modified. */
    private volatile byte[] previousHash;

    /** Released by the first refresh that succeeds. */
    private final CountDownLatch firstLoad = new CountDownLatch(1);

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
        this(application, fileNames, fetcher, parser, manager, digest, metrics, maximumSkips, null);
    }

    /**
     * Creates configuration reader that reports the outcome of every refresh to the store of its manager.
     *
     * @param application application
     * @param fileNames names of textual configuration files
     * @param fetcher fetcher for remote or local configuration files
     * @param parser deserializer for textual configuration files
     * @param manager configuration manager
     * @param digest message digest
     * @param metrics metrics
     * @param maximumSkips maximum number of skips
     * @param store store of the manager, or null if the manager is not one of Ageris's own
     */
    ConfigurationReader(String application, List<String> fileNames,
                        ConfigurationFetcher fetcher,
                        ConfigurationMapper<T> parser,
                        ConfigurationManager<T> manager,
                        MessageDigest digest,
                        UpdateConfigurationMetrics metrics,
                        final int maximumSkips,
                        ConfigurationStore<T> store) {
        this.application = application;
        this.fetcher = fetcher;
        this.parser = parser;
        this.manager = manager;
        this.store = store;
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
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void execute() throws IOException {
        try {
            refresh();
            firstLoad.countDown();
        } catch (IOException | RuntimeException exception) {
            if (store != null) {
                store.failed(exception);
            }
            throw exception;
        }
    }

    /**
     * Waits until the first refresh has succeeded.
     *
     * @param timeout maximum time to wait
     * @param unit unit of the timeout
     * @return true if a refresh has succeeded, false if the timeout passed first
     * @throws InterruptedException if the waiting thread is interrupted
     */
    boolean awaitFirstLoad(long timeout, TimeUnit unit) throws InterruptedException {
        return firstLoad.await(timeout, unit);
    }

    /**
     * Fetches, parses and stores the configurations.
     *
     * @throws IOException in case of a failure
     */
    private void refresh() throws IOException {
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
            if (store != null) {
                store.unchanged();
            }
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
        /* Sorted by file name, so the message and the reported cause do not depend on the order of the fetch. */
        Map<String, IOException> failures = new TreeMap<>();
        Map<String, Configuration<T>> configurationCache = new LinkedHashMap<> ();
        for (Map.Entry<String, String> content : contents.entrySet()) {
            try {
                Map<String, Configuration<T>> configurations = parser.readValue(content.getValue());
                numConfigurations += configurations.size();
                configurationCache.putAll(configurations);
            } catch (IOException exception) {
                failures.put(content.getKey(), exception);
            }
        }
        metrics.incrementContentFailures(failures.size());
        if (!failures.isEmpty()) {
            IOException first = failures.values().iterator().next();
            throw new IOException("Cannot load " + failures.keySet() + " of application " + application + ": " + first.getMessage(), first);
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
