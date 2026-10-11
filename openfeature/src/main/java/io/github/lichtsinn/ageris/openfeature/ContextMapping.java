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
package io.github.lichtsinn.ageris.openfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;
import io.github.lichtsinn.ageris.core.ConfigurationContext;

import java.util.Map;

/**
 * Turns the evaluation context of an OpenFeature caller into the configuration context Ageris evaluates with.
 */
final class ContextMapping {

    /** Above this a double no longer holds every whole number, so it is written as a double. */
    private static final double WHOLE_LIMIT = 1e15;

    private ContextMapping() {
        // Static methods only.
    }

    /**
     * @param context evaluation context of the caller, nullable
     * @param keyType context type the targeting key is put under
     * @return configuration context with the targeting key and every attribute that has a text
     */
    static ConfigurationContext toAgeris(EvaluationContext context, String keyType) {
        final ConfigurationContext result = new ConfigurationContext();
        if (context == null) {
            return result;
        }
        final String targetingKey = context.getTargetingKey();
        if (targetingKey != null && !targetingKey.isEmpty()) {
            result.put(keyType, targetingKey);
        }
        for (final Map.Entry<String, Value> attribute : context.asUnmodifiableMap().entrySet()) {
            final String text = text(attribute.getValue());
            /* The SDK also lists the targeting key as an attribute of its own name; it is already taken care of. */
            if (text != null && !EvaluationContext.TARGETING_KEY.equals(attribute.getKey())) {
                result.put(attribute.getKey(), text);
            }
        }
        return result;
    }

    /**
     * @return the text of a text, a number or a boolean; null for everything else, which has no obvious text
     */
    private static String text(Value value) {
        String text = null;
        if (value == null) {
            return text;
        }
        if (value.isString()) {
            text = value.asString();
        } else if (value.isBoolean()) {
            text = String.valueOf(value.asBoolean());
        } else if (value.isNumber()) {
            text = number(value.asObject());
        }
        return text;
    }

    /** A whole number is written without a decimal point, also when the caller passed it as a double. */
    private static String number(Object number) {
        if (number instanceof Double) {
            final double decimal = (Double) number;
            if (decimal % 1 == 0 && Math.abs(decimal) < WHOLE_LIMIT) {
                return Long.toString((long) decimal);
            }
        }
        return String.valueOf(number);
    }
}
