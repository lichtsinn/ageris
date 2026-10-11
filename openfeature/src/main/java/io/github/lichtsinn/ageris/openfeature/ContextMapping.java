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

import java.math.BigDecimal;

/**
 * Turns the evaluation context of an OpenFeature caller into the configuration context Ageris evaluates with.
 */
final class ContextMapping {

    private ContextMapping() {
        // Static methods only.
    }

    /**
     * @param context evaluation context of the caller, nullable
     * @param keyType context type the targeting key is put under
     * @return configuration context with the targeting key and every attribute that has a text; it reads them from the
     *     caller's context when Ageris asks for them, so that nothing is copied for a lookup
     */
    static ConfigurationContext toAgeris(EvaluationContext context, String keyType) {
        return context == null ? new ConfigurationContext() : new CallerContext(context, keyType);
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

    /**
     * A number is written the way a file would hold it: digits, without an exponent, and without a decimal point where it is
     * whole, also when the caller passed it as a double. What is not a number at all, such as infinity, has no text.
     */
    private static String number(Object number) {
        final boolean decimal = number instanceof Double || number instanceof Float || number instanceof BigDecimal;
        if (decimal && !Double.isFinite(((Number) number).doubleValue())) {
            return null;
        }
        final String written = String.valueOf(number);
        return decimal ? new BigDecimal(written).stripTrailingZeros().toPlainString() : written;
    }

    /**
     * The evaluation context of an OpenFeature caller, seen as a configuration context.
     */
    private static final class CallerContext extends ConfigurationContext {

        private final EvaluationContext caller;
        private final String keyType;

        private CallerContext(EvaluationContext caller, String keyType) {
            super();
            this.caller = caller;
            this.keyType = keyType;
        }

        @Override
        public String get(String type) {
            /* The SDK also lists the targeting key as an attribute of its own name; it is only reachable as the key type. */
            if (EvaluationContext.TARGETING_KEY.equals(type)) {
                return null;
            }
            final String text = text(caller.getValue(type));
            if (text != null || !keyType.equals(type)) {
                return text;
            }
            final String targetingKey = caller.getTargetingKey();
            return targetingKey == null || targetingKey.isEmpty() ? null : targetingKey;
        }

        @Override
        public void put(String type, String value) {
            throw new UnsupportedOperationException("The context of an OpenFeature caller is not changed through Ageris");
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
