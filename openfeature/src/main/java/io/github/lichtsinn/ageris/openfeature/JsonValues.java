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

import com.fasterxml.jackson.databind.JsonNode;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the JSON of a config into the value an OpenFeature caller receives for an object lookup.
 */
final class JsonValues {

    private JsonValues() {
        // Static methods only.
    }

    /**
     * @param json JSON of a config, or a part of it
     * @return an object as a structure, a list as a list, a single value as that value, and JSON null as a null value
     */
    static Value toValue(JsonNode json) {
        Value value;
        if (json.isObject()) {
            value = new Value(structure(json));
        } else if (json.isArray()) {
            value = new Value(list(json));
        } else if (json.isTextual()) {
            value = new Value(json.textValue());
        } else if (json.isBoolean()) {
            value = new Value(json.booleanValue());
        } else if (json.isNumber()) {
            value = number(json);
        } else {
            value = new Value();
        }
        return value;
    }

    private static ImmutableStructure structure(JsonNode json) {
        final Map<String, Value> members = new LinkedHashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            members.put(field.getKey(), toValue(field.getValue()));
        }
        return new ImmutableStructure(members);
    }

    private static List<Value> list(JsonNode json) {
        final List<Value> elements = new ArrayList<>(json.size());
        for (final JsonNode element : json) {
            elements.add(toValue(element));
        }
        return elements;
    }

    /**
     * A whole number that fits an int stays whole. Every other number is a double, which is the only other kind of number
     * that every supported version of the OpenFeature SDK has.
     */
    private static Value number(JsonNode json) {
        return json.isIntegralNumber() && json.canConvertToInt() ? new Value(json.intValue()) : new Value(json.doubleValue());
    }
}
