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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Objects the application has bound on {@link Ageris}. A config class asks for one with
 * {@code @JacksonInject} on a constructor parameter, either by the parameter's declared type or by name.
 */
final class Bindings extends InjectableValues {

    private final Logger log = LoggerFactory.getLogger(Bindings.class);

    /** Bound objects by name; a type is stored under its class name, the id Jackson uses for an unnamed request. */
    private final Map<String, Object> bindings = new ConcurrentHashMap<>();

    /**
     * Binds an object under its type.
     *
     * @param type type a config class declares to receive the binding
     * @param binding object to hand to config classes
     * @param <B> type of the binding
     * @throws IllegalArgumentException if type or binding is null, or the type is already bound
     */
    <B> void bind(Class<B> type, B binding) {
        if (type == null) {
            throw new IllegalArgumentException("The type of a binding must not be null");
        }
        put(type.getName(), binding);
    }

    /**
     * Binds an object under a name.
     *
     * @param name name a config class uses to ask for the binding
     * @param binding object to hand to config classes
     * @throws IllegalArgumentException if name or binding is null, or the name is already bound
     */
    void bind(String name, Object binding) {
        if (name == null) {
            throw new IllegalArgumentException("The name of a binding must not be null");
        }
        put(name, binding);
    }

    private void put(String key, Object binding) {
        if (binding == null) {
            throw new IllegalArgumentException("The binding for " + key + " must not be null");
        }
        if (bindings.putIfAbsent(key, binding) != null) {
            throw new IllegalArgumentException("There is already a binding for " + key);
        }
    }

    @Override
    public Object findInjectableValue(Object valueId, DeserializationContext context, BeanProperty forProperty, Object beanInstance)
            throws JsonMappingException {
        Object binding = valueId instanceof String ? bindings.get(valueId) : null;
        if (binding == null) {
            String owner = forProperty == null || forProperty.getMember() == null
                    ? "a config class" : forProperty.getMember().getDeclaringClass().getName();
            String message = "No binding for " + valueId + ", required by " + owner + ". Bind it on Ageris before the loader starts.";
            /* A missing binding is a setup mistake rather than a bad file, and in skip mode the caller only
             * counts it, so it is reported here where it cannot be overlooked. */
            log.error(message);
            throw new JsonMappingException((Closeable) null, message);
        }
        return binding;
    }
}
