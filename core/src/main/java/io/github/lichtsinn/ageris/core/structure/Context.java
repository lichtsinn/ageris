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
package io.github.lichtsinn.ageris.core.structure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.Evaluation;

import java.util.Arrays;
import java.util.Collections;

/**
 * A configuration context is a sub-tree in the definition of a configuration, that defines a (default) value object of type T
 * at the current level, and an optional override hierarchy, called modifiers.
 *
 * I.e., in the following JSON representation of a feature flag configuration, the top default value (object)
 * of type Boolean is 'false' and the modifiers hierarchy is defined to override the default value depending
 * on an environment value.
 *
 *  "enable-feature-one": {
 *      "value": false,
 *      "modifiers": {
 *          "type": "environment",
 *          "contexts": {
 *              "qa": {
 *                  "value": true
 *              },
 *              "prod": {
 *                  "value": true
 *              }
 *          }
 *      }
 *  }
 *
 * @param <T> type of value object
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"comment"})
public final class Context<T> {

    /** Room for the matches of two levels, which is as deep as most configurations go. */
    private static final int FIRST_PAIRS = 4;

    /** Mandatory, default value object. */
    @JsonProperty("value")
    private final T value;

    /** Optional modifiers to override default value object, nullable. */
    @JsonProperty("modifiers")
    private final Modifiers<T> modifiers;

    /**
     * Creates context based on mandatory (default) value object and optional modifiers.
     *
     * @param value mandatory default value object, must not be null
     * @param modifiers optional modifiers to override default, nullable
     * @throws IllegalArgumentException if the value object is null
     */
    @JsonCreator
    public Context(@JsonProperty(value = "value", required = true) T value,
                   @JsonProperty(value = "modifiers") Modifiers<T> modifiers) {
        if (value == null) {
            /* required = true rejects an absent "value" field, but only asks whether the field was present:
             * an explicit "value": null satisfies it and would otherwise deserialize cleanly into a context
             * with no default value, reaching the configuration store and failing every evaluation. */
            throw new IllegalArgumentException("Configuration context requires a non-null value");
        }
        this.value = value;
        this.modifiers = modifiers;
    }

    /**
     * Returns value object based on provided runtime context. A percentage in a rule is decided here as for a configuration
     * without a name, so the callers it selects differ from those of the same context inside a named configuration.
     *
     * @param runtimeContext context, provided at runtime
     * @return value object based on runtime context
     */
    public T getValue(ConfigurationContext runtimeContext) {
        return getValue(runtimeContext, Rule.seed(null));
    }

    /**
     * Returns value object based on provided runtime context. Ageris calls this with the seed of the configuration the context
     * belongs to, so that a percentage in a rule gives every configuration its own callers.
     *
     * @param runtimeContext context, provided at runtime
     * @param seed seed of the configuration, see {@link Rule#seed(String)}
     * @return value object based on runtime context
     */
    public T getValue(ConfigurationContext runtimeContext, int seed) {
        if (modifiers != null) {
            T modifiersValue = modifiers.getValue(runtimeContext, seed);
            if (modifiersValue != null) {
                return modifiersValue;
            }
        }
        return value;
    }

    /**
     * Returns value object based on provided runtime context, together with the modifiers that matched. The value is
     * the one {@link #getValue(ConfigurationContext)} returns.
     *
     * @param runtimeContext context, provided at runtime
     * @return evaluation with the value object based on runtime context
     */
    public Evaluation<T> evaluate(ConfigurationContext runtimeContext) {
        return evaluate(runtimeContext, Rule.seed(null));
    }

    /**
     * Returns value object based on provided runtime context, together with the modifiers that matched. The value is
     * the one {@link #getValue(ConfigurationContext, int)} returns.
     *
     * @param runtimeContext context, provided at runtime
     * @param seed seed of the configuration, see {@link Rule#seed(String)}
     * @return evaluation with the value object based on runtime context
     */
    public Evaluation<T> evaluate(ConfigurationContext runtimeContext, int seed) {
        if (modifiers == null) {
            return new Evaluation<>(value, Evaluation.Reason.STATIC, Collections.emptyMap());
        }
        /* Context type and context value of each match, in turn: an array costs far less here than a map. */
        String[] pairs = new String[FIRST_PAIRS];
        int count = 0;
        Context<T> deepest = this;
        while (deepest.modifiers != null) {
            final String contextValue = runtimeContext.get(deepest.modifiers.type());
            final Context<T> nested = contextValue == null ? null : deepest.modifiers.nested(contextValue, seed);
            if (nested == null) {
                break;
            }
            if (count == pairs.length) {
                pairs = Arrays.copyOf(pairs, count * 2);
            }
            pairs[count++] = deepest.modifiers.type();
            pairs[count++] = contextValue;
            deepest = nested;
        }
        if (count == 0) {
            return new Evaluation<>(deepest.value, Evaluation.Reason.DEFAULT, Collections.emptyMap());
        }
        return Evaluation.of(deepest.value, Evaluation.Reason.MATCH, count == pairs.length ? pairs : Arrays.copyOf(pairs, count));
    }
}
