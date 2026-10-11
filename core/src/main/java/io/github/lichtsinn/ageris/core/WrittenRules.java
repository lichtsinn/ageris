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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds what is wrong with a rule as it is written in a file, where a JSON mapper no longer shows it.
 *
 * A mapper keeps the last of two equal keys without a word, so a rule with "percent": 0 and "percent": 100 would apply to
 * everyone. Outside of rules the last one keeps counting, as it always has. And a mapper usually reads a number as a double,
 * which drops decimals beyond the sixteenth or so, so a percentage is checked for its decimals as it was written.
 */
final class WrittenRules {

    private static final String MODIFIERS = "modifiers";
    private static final String CONTEXTS = "contexts";
    private static final String RULES = "rules";
    private static final String PERCENT = "percent";
    private static final int MAX_DECIMALS = 2;

    private WrittenRules() {
        // Static methods only.
    }

    /**
     * @param parser parser at the start of the content of a configuration file
     * @param root name of the root field holding the configurations
     * @return name of a configuration to the problem found in one of its rules; empty if nothing is wrong
     * @throws IOException if the content cannot be parsed
     */
    static Map<String, String> find(JsonParser parser, String root) throws IOException {
        final Map<String, String> found = new HashMap<>();
        if (parser.nextToken() == JsonToken.START_OBJECT) {
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                final boolean isRoot = root.equals(parser.currentName());
                if (parser.nextToken() == JsonToken.START_OBJECT && isRoot) {
                    configurations(parser, found);
                } else {
                    parser.skipChildren();
                }
            }
        }
        return found;
    }

    private static void configurations(JsonParser parser, Map<String, String> found) throws IOException {
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            final String name = parser.currentName();
            parser.nextToken();
            final String problem = context(parser);
            if (problem != null) {
                found.put(name, problem);
            }
        }
    }

    /** Reads a value with optional modifiers, which is what a configuration, a named entry and a rule hold. */
    private static String context(JsonParser parser) throws IOException {
        String problem = null;
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                problem = first(problem, member(parser, null));
            }
        } else {
            parser.skipChildren();
        }
        return problem;
    }

    private static String rule(JsonParser parser) throws IOException {
        String problem = null;
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            final Set<String> keys = new HashSet<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                problem = first(problem, member(parser, keys));
            }
        } else {
            parser.skipChildren();
        }
        return problem;
    }

    /** Reads one key and its value; only modifiers are looked into, since everything else belongs to the application. */
    private static String member(JsonParser parser, Set<String> keys) throws IOException {
        final String key = parser.currentName();
        String problem = null;
        if (keys != null && !keys.add(key)) {
            problem = "Key \"" + key + "\" appears twice in a rule";
        }
        final JsonToken token = parser.nextToken();
        if (keys != null && PERCENT.equals(key) && token.isNumeric() && hasTooManyDecimals(parser.getText())) {
            problem = first(problem, "\"percent\" may have at most two decimals in a rule, but was " + parser.getText());
        }
        if (MODIFIERS.equals(key)) {
            problem = first(problem, modifiers(parser));
        } else {
            parser.skipChildren();
        }
        return problem;
    }

    private static boolean hasTooManyDecimals(String written) {
        try {
            return new BigDecimal(written).stripTrailingZeros().scale() > MAX_DECIMALS;
        } catch (NumberFormatException exception) {
            /* A way of writing a number that only some formats know. What it comes to is checked when the rule is created. */
            return false;
        }
    }

    private static String modifiers(JsonParser parser) throws IOException {
        String problem = null;
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                final String key = parser.currentName();
                final JsonToken token = parser.nextToken();
                if (CONTEXTS.equals(key) && token == JsonToken.START_OBJECT) {
                    problem = first(problem, namedEntries(parser));
                } else if (RULES.equals(key) && token == JsonToken.START_ARRAY) {
                    problem = first(problem, rules(parser));
                } else {
                    parser.skipChildren();
                }
            }
        } else {
            parser.skipChildren();
        }
        return problem;
    }

    private static String namedEntries(JsonParser parser) throws IOException {
        String problem = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            parser.nextToken();
            problem = first(problem, context(parser));
        }
        return problem;
    }

    private static String rules(JsonParser parser) throws IOException {
        String problem = null;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            problem = first(problem, rule(parser));
        }
        return problem;
    }

    private static String first(String earlier, String later) {
        return earlier == null ? later : earlier;
    }
}
