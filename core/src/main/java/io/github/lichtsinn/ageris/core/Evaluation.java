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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of evaluating a feature flag or config for a configuration context: the value, and why it is that value.
 *
 * @param <T> type of the value
 */
public final class Evaluation<T> {

    /**
     * Why an evaluation returned its value.
     */
    public enum Reason {
        /** Nothing is defined under the name. The value is the fallback of the manager or of the caller. */
        NOT_FOUND,
        /** The definition has no modifiers, so the value is the same for every configuration context. */
        STATIC,
        /** The definition has modifiers, and none matched the configuration context. The value is the base value. */
        DEFAULT,
        /** At least one modifier matched the configuration context. */
        MATCH
    }

    private final T value;
    private final Reason reason;
    private final Map<String, String> matches;

    /**
     * Creates an evaluation.
     *
     * @param value the value, null if there is none
     * @param reason why the value is the value
     * @param matches context type to context value of the modifiers that matched, outermost first
     * @throws IllegalArgumentException if the reason or the matches are null
     */
    public Evaluation(T value, Reason reason, Map<String, String> matches) {
        if (reason == null) {
            throw new IllegalArgumentException("Evaluation requires a non-null reason");
        }
        if (matches == null) {
            throw new IllegalArgumentException("Evaluation requires non-null matches");
        }
        this.value = value;
        this.reason = reason;
        this.matches = matches.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(matches));
    }

    /**
     * @return the value
     */
    public T getValue() {
        return value;
    }

    /**
     * @return why the value is the value
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * @return context type to context value of the modifiers that matched, outermost first; empty unless the reason
     *     is {@link Reason#MATCH}; unmodifiable
     */
    public Map<String, String> getMatches() {
        return matches;
    }

    @Override
    public String toString() {
        return matches.isEmpty() ? value + " (" + reason + ")" : value + " (" + reason + " " + matches + ")";
    }
}
