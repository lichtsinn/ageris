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
package io.github.lichtsinn.ageris.core.filesystem;

import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics container for the {@link io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher}.
 * Implements mbean defined in {@link ConfigurationFetcherMetricsMBean}.
 */
public class ConfigurationFetcherMetrics implements ConfigurationFetcherMetricsMBean {

    /** Counter for requests, failed and successful. */
    private final LongAdder requests;

    /** Counter for failed requests . */
    private final LongAdder failures;

    /** Counter for missing files. */
    private final LongAdder numMissingFiles;

    /**
     * Creates metrics container.
     */
    public ConfigurationFetcherMetrics() {
        requests = new LongAdder();
        failures = new LongAdder();
        numMissingFiles = new LongAdder();
    }

    /**
     * Increment counter for requests, failed and successful.
     */
    public void incrementRequests() {
        requests.increment();
    }

    /**
     * Increment counter for failed requests.
     */
    public void incrementFailures() {
        failures.increment();
    }

    /**
     * Increment counter for missing files.
     */
    public void incrementMissingFiles() {
        numMissingFiles.increment();
    }

    @Override
    public long getRequests() {
        return requests.sum();
    }

    @Override
    public long getFailures() {
        return failures.sum();
    }

    @Override
    public long getMissingFiles() {
        return numMissingFiles.sum();
    }
}
