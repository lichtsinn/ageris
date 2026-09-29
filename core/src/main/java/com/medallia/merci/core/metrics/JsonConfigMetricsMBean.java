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
 * Metrics container for JsonNode configs.
 */
public interface JsonConfigMetricsMBean {

    /** @return number of skipped update cycles of JsonNode configs due to same textual content. */
    long getJsonConfigSameContentsSkips();

    /** @return number of successful update cycles of JsonNode configs due to new textual content. */
    long getJsonConfigNewContentsUpdates();

    /** @return number of successful JsonNode configs updates. */
    long getJsonConfigUpdates();

    /** @return number of failures reading and deserializing JsonNode configs due to deserialization problems with textual content. */
    long getJsonConfigContentFailures();

    /** @return total number of skipped JsonNode configs due to problems instantiating Java configuration objects. */
    long getJsonConfigNonInstantiableSkips();

    /** @return number of duplicate JsonNode config name detections. */
    long getJsonConfigNameDuplicates();
}
