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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A configuration modifiers is an override hierarchy in the definition of a configuration.
 *
 * I.e., in the following JSON representation of a feature flag configuration, the modifiers hierarchy is defined to override
 * the default value (object) 'false' with 'true' for (runtime) context values 'qa' and 'prod' of type 'environment'.
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
 * @param <T> type of context
 */
@JsonIgnoreProperties({"comment"})
public final class Modifiers<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Modifiers.class);

    /** Type of context values to override default value from parent level in configuration hierarchy. */
    @JsonProperty("type")
    private final String type;

    /** Map of context value to configuration context. */
    @JsonProperty("contexts")
    private final Map<String, Context<T>> contexts;

    /** Rules for context values that have no entry in the map, tried in order. */
    @JsonProperty("rules")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<Rule<T>> rules;

    /**
     * Creates new modifiers container for a given context type and a mapping of context values new contexts.
     *
     * @param type context type
     * @param contexts map of context value to context object, must not be null
     * @throws IllegalArgumentException if the context type or the contexts are null
     */
    public Modifiers(String type, Map<String, Context<T>> contexts) {
        this(type, contexts, null);
    }

    /**
     * Creates new modifiers container for a given context type, a mapping of context values to new contexts, and rules for
     * the context values that have no entry in the mapping.
     *
     * @param type context type
     * @param contexts map of context value to context object; may be null if there are rules
     * @param rules rules, tried in order after the contexts; nullable
     * @throws IllegalArgumentException if the context type is null, if there are neither contexts nor rules, or if a rule is
     *     null or has neither a list nor a percentage
     */
    @JsonCreator
    public Modifiers(@JsonProperty(value = "type", required = true) String type,
                     @JsonProperty("contexts") Map<String, Context<T>> contexts,
                     @JsonProperty("rules") List<Rule<T>> rules) {
        if (type == null) {
            throw new IllegalArgumentException("Configuration modifiers require a non-null context type");
        }
        this.type = type;
        this.rules = checked(type, rules);
        if (contexts == null && this.rules.isEmpty()) {
            /* Neither contexts nor rules, which is also what an explicit "contexts": null amounts to. */
            throw new IllegalArgumentException("Configuration modifiers of type " + type + " need \"contexts\" or at least one rule in \"rules\"");
        }
        this.contexts = contexts == null ? Collections.emptyMap() : contexts;
    }

    /**
     * Returns config value (object) for a given (runtime) context, null if none found.
     *
     * @param runtimeContext configuration context at runtime
     * @return configuration value of type T for given configuration context, null if none found.
     */
    public T getValue(ConfigurationContext runtimeContext) {
        return getValue(runtimeContext, Rule.seed(null));
    }

    /**
     * Returns config value (object) for a given (runtime) context, null if none found.
     *
     * @param runtimeContext configuration context at runtime
     * @param seed seed of the configuration, for the percentages of rules
     * @return configuration value of type T for given configuration context, null if none found.
     */
    T getValue(ConfigurationContext runtimeContext, int seed) {
        final String runtimeContextValue = runtimeContext.get(type);
        if (runtimeContextValue == null) {
            return null;
        }
        final Context<T> context = nested(runtimeContextValue, seed);
        if (context == null) {
            return null;
        }
        return context.getValue(runtimeContext, seed);
    }

    /**
     * @return type of context values these modifiers look at
     */
    String type() {
        return type;
    }

    /**
     * @return the rules as a list that cannot be changed, after checking that each of them has a match
     */
    private static <T> List<Rule<T>> checked(String type, List<Rule<T>> rules) {
        if (rules == null) {
            return Collections.emptyList();
        }
        final List<Rule<T>> copy = Collections.unmodifiableList(new ArrayList<>(rules));
        boolean everyone = false;
        for (int index = 0; index < copy.size(); index++) {
            final Rule<T> rule = copy.get(index);
            if (rule == null) {
                throw new IllegalArgumentException("Rule " + (index + 1) + " for " + type + " is null");
            }
            if (!rule.hasMatch()) {
                throw new IllegalArgumentException("Rule " + (index + 1) + " for " + type + " has no match: it needs \"in\" or \"percent\"");
            }
            if (everyone) {
                LOG.warn("Rule {} for {} can never be reached: an earlier rule matches every value", index + 1, type);
            }
            everyone = everyone || rule.matchesEveryone();
        }
        return copy;
    }

    /**
     * @param runtimeContextValue the caller's value for the type of these modifiers, not null
     * @param seed seed of the configuration, for the percentages of rules
     * @return the context of the named entry for the value, else of the first rule that matches it, else null
     */
    Context<T> nested(String runtimeContextValue, int seed) {
        Context<T> context = contexts.get(runtimeContextValue);
        /* By index, so that no iterator is created on the path of a lookup. */
        for (int index = 0; context == null && index < rules.size(); index++) {
            if (rules.get(index).matches(runtimeContextValue, seed)) {
                context = rules.get(index).node();
            }
        }
        return context;
    }
}
