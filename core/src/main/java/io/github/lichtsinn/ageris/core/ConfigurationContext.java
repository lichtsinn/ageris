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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generic configuration context, representing context type to value mappings, i.e. "environment" -> "qa".
 */
public class ConfigurationContext {

    /* Created with the first mapping, so that a subclass that takes its values from elsewhere does not pay for it. */
    private Map<String, String> contextMap = Collections.emptyMap();

    /**
     * Creates empty configuration context.
     */
    public ConfigurationContext() {
        // Nothing to set up: the context is empty until something is put into it.
    }

    /**
     * Inserts or updates a context type to value mapping, i.e. "environment" -> "qa".
     *
     * @param type type of mapping
     * @param value value of mapping
     */
    public void put(String type, String value) {
        if (contextMap.isEmpty()) {
            contextMap = new LinkedHashMap<>();
        }
        contextMap.put(type, value);
    }

    /**
     * Returns context value associated with provided context type, or null if none found.
     *
     * This is the only method Ageris calls when it looks something up, so a subclass may override it to take the values
     * from elsewhere, instead of copying them into a configuration context for every lookup.
     *
     * @param type name of context type
     * @return context-value, null if none found
     */
    public String get(String type) {
        return contextMap.get(type);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return Objects.equals(contextMap, ((ConfigurationContext) other).contextMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextMap);
    }
}
