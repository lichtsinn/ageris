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
package com.medallia.merci.core.metrics;

/**
 * Bean for metrics of feature flags.
 */
public interface FeatureFlagMetricsMBean {

    /** @return total number of skipped update cycles of feature flags due to same configuration content. */
    long getFeatureFlagSameContentsSkips();

    /** @return total number of successful update cycles of feature flags due to new configuration content. */
    long getFeatureFlagNewContentsUpdates();

    /** @return total number of successful feature flag updates. */
    long getFeatureFlagUpdates();

    /** @return total number of failures reading feature flags due due to problems parsing configuration content. */
    long getFeatureFlagContentFailures();

    /** @return total number of skipped feature flags due to problems instantiating Java configuration objects. */
    long getFeatureFlagNonInstantiableSkips();

    /** @return total number of duplicate feature flag name detections. */
    long getFeatureFlagNameDuplicates();
}
