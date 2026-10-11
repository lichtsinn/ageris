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

    private static final String[] NO_MATCHES = {};

    private final T value;
    private final Reason reason;

    /* Context type and context value of each match, in turn. Never changed, and never handed out. */
    private final String[] pairs;

    /* The matches as a map. Built from the pairs when it is first asked for, since many callers only want value and reason. */
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile Map<String, String> matches;

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
        this.pairs = NO_MATCHES;
        this.matches = matches.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(matches));
    }

    private Evaluation(T value, Reason reason, String... pairs) {
        this.value = value;
        this.reason = reason;
        this.pairs = pairs;
    }

    /**
     * Creates an evaluation from the matches written out in turn, which costs less than a map:
     * {@code Evaluation.of(true, Reason.MATCH, "environment", "qa", "user", "joe")}.
     *
     * @param value the value, null if there is none
     * @param reason why the value is the value
     * @param typesAndValues context type and context value of each modifier that matched, outermost first
     * @param <T> type of the value
     * @return the evaluation
     * @throws IllegalArgumentException if the reason is null, or if a context type comes without a value
     */
    @SuppressWarnings("PMD.ShortMethodName") // The usual name for a factory like this one, as in List.of.
    public static <T> Evaluation<T> of(T value, Reason reason, String... typesAndValues) {
        if (reason == null) {
            throw new IllegalArgumentException("Evaluation requires a non-null reason");
        }
        if (typesAndValues == null || typesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Evaluation requires a context value for every context type");
        }
        return new Evaluation<>(value, reason, typesAndValues.clone());
    }

    /**
     * @param other another value
     * @param <U> type of the other value
     * @return an evaluation with the other value and the reason and matches of this one
     */
    <U> Evaluation<U> withValue(U other) {
        return pairs.length == 0 ? new Evaluation<>(other, reason, getMatches()) : new Evaluation<>(other, reason, pairs);
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
        Map<String, String> result = matches;
        if (result == null) {
            /* Two threads may both build the map. Each gets a complete one, and one of the two is kept. */
            result = asMap(pairs);
            matches = result;
        }
        return result;
    }

    private static Map<String, String> asMap(String... pairs) {
        final Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        return getMatches().isEmpty() ? value + " (" + reason + ")" : value + " (" + reason + " " + getMatches() + ")";
    }
}
