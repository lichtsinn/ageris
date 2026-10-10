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
package io.github.lichtsinn.ageris.core.structure;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A rule in the modifiers of a configuration: a list of values, a percentage or both, to match the caller's value for the
 * context type of the modifiers, and the value, with optional modifiers of its own, that applies when the rule matches.
 *
 * I.e., in the following JSON representation the feature flag is true for the users anna and ben, and for 10 percent of users.
 *
 *  "modifiers": {
 *      "type": "user",
 *      "rules": [
 *          { "in": ["anna", "ben"], "value": true },
 *          { "percent": 10, "value": true }
 *      ]
 *  }
 *
 * A percentage puts the caller's value into one of 10,000 buckets by hashing it together with the name of the configuration,
 * so the same caller always gets the same answer. Everything a rule needs for matching is prepared when it is created, and it
 * is never changed afterwards.
 *
 * @param <T> type of value object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"comment"})
@SuppressWarnings("PMD.ShortClassName") // Named after the "rules" of a configuration file.
public final class Rule<T> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_DECIMALS = 2;
    private static final int NO_PERCENT = -1;
    private static final String OUT_OF_RANGE = "\"percent\" must be between 0 and 100 in a rule, but was ";

    /** Values, one of which the caller's value must equal; empty if the rule has no list. */
    @JsonProperty("in")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> oneOf;

    /** Share of callers, from 0 to 100; null if the rule has no percentage. */
    @JsonProperty("percent")
    private final BigDecimal percent;

    /** Mandatory value object of the rule. */
    @JsonProperty("value")
    @SuppressWarnings("PMD.SingularField") // Read by Jackson when the rule is written.
    private final T value;

    /** Optional modifiers to override the value, nullable. */
    @JsonProperty("modifiers")
    @SuppressWarnings("PMD.SingularField") // Read by Jackson when the rule is written.
    private final Modifiers<T> modifiers;

    /* Prepared from the fields above. Transient, so that no JSON mapper takes them for a part of the JSON representation. */
    private final transient int threshold;
    private final transient Context<T> node;

    /**
     * Creates a rule. The list and the percentage are taken as they are written in a configuration file, and checked here,
     * so that a number in the list or a text for the percentage is refused and not converted.
     *
     * @param oneOf texts, one of which the caller's value must equal, "in" in a file; null for no list
     * @param percent share of callers, a number from 0 to 100 with at most two decimals; null for no percentage
     * @param value value object of the rule, must not be null
     * @param modifiers optional modifiers to override the value, nullable
     * @throws IllegalArgumentException if the list is empty or holds anything but texts, if the percentage is not a number, is
     *     out of range or has more than two decimals, or if the value is null
     */
    @JsonCreator
    public Rule(@JsonProperty("in") List<?> oneOf,
                @JsonProperty("percent") Object percent,
                @JsonProperty("value") T value,
                @JsonProperty("modifiers") Modifiers<T> modifiers) {
        if (value == null) {
            throw new IllegalArgumentException("A rule needs a \"value\"");
        }
        this.oneOf = texts(oneOf);
        this.percent = decimal(percent);
        this.threshold = threshold(this.percent);
        this.value = value;
        this.modifiers = modifiers;
        this.node = new Context<>(this.value, this.modifiers);
    }

    /**
     * @param name name of a configuration; null counts as empty
     * @return seed for the percentages in the rules of that configuration, which gives every configuration its own callers
     */
    public static int seed(String name) {
        return Murmur3.hash(name == null ? "" : name, 0);
    }

    /**
     * Refuses a key the file format does not have, whatever the settings of the JSON mapper. A later version may add a kind
     * of match, and a rule that lost one would apply to more callers than its author meant.
     *
     * @param key the unknown key
     * @param ignored its value
     */
    @JsonAnySetter
    void unknown(String key, Object ignored) {
        throw new IllegalArgumentException("Unknown key \"" + key + "\" in a rule; a rule may hold in, percent, value, modifiers and comment");
    }

    /**
     * @return true if this rule has a list or a percentage
     */
    boolean hasMatch() {
        return !oneOf.isEmpty() || percent != null;
    }

    /**
     * @return true if this rule matches every value, which makes the rules after it unreachable
     */
    boolean matchesEveryone() {
        return oneOf.isEmpty() && threshold == Murmur3.BUCKETS;
    }

    /**
     * @param callerValue the caller's value for the context type of the modifiers, not null
     * @param seed seed of the configuration this rule belongs to, see {@link #seed(String)}
     * @return true if the value is listed, where there is a list, and inside the percentage, where there is one
     */
    boolean matches(String callerValue, int seed) {
        return (oneOf.isEmpty() || oneOf.contains(callerValue))
                && (threshold == NO_PERCENT || Murmur3.bucket(callerValue, seed) < threshold);
    }

    /**
     * @return the value and modifiers of this rule
     */
    Context<T> node() {
        return node;
    }

    private static Set<String> texts(List<?> values) {
        if (values == null) {
            return Collections.emptySet();
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("\"in\" needs at least one value in a rule");
        }
        final Set<String> texts = new LinkedHashSet<>();
        for (final Object element : values) {
            if (!(element instanceof String)) {
                throw new IllegalArgumentException("\"in\" may only hold texts in a rule, but holds " + element);
            }
            texts.add((String) element);
        }
        return Collections.unmodifiableSet(texts);
    }

    private static BigDecimal decimal(Object percent) {
        if (percent == null || percent instanceof BigDecimal) {
            return (BigDecimal) percent;
        }
        if (!(percent instanceof Number)) {
            throw new IllegalArgumentException("\"percent\" must be a number in a rule, but was "
                    + (percent instanceof String ? "the text \"" + percent + "\"" : percent));
        }
        if (!Double.isFinite(((Number) percent).doubleValue())) {
            throw new IllegalArgumentException(OUT_OF_RANGE + percent);
        }
        return new BigDecimal(percent.toString());
    }

    private static int threshold(BigDecimal percent) {
        if (percent == null) {
            return NO_PERCENT;
        }
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(OUT_OF_RANGE + percent);
        }
        if (percent.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("\"percent\" may have at most two decimals in a rule, but was " + percent);
        }
        return percent.movePointRight(MAX_DECIMALS).intValueExact();
    }
}
