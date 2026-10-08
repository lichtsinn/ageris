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

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Refuses any class that would let a configuration file supply or replace a binding.
 *
 * A member that is only injected has no name in the file, so the file cannot reach it. Two things make a
 * binding reachable, and Jackson then lets a value in the file replace the bound object, swapping a live
 * object for data:
 *
 * A binding that is also a named property. Such a class is refused.
 *
 * A property that is written through a field, a setter, or a getter returning a collection or map, which
 * Jackson fills in place. The field that holds a binding becomes writable as soon as the class has a getter
 * for it, and nothing marks that field as the binding's. A class that receives bindings may therefore take
 * values from the file through its constructor parameters only.
 *
 * Both are refused whether or not the file tries to use the property.
 */
final class BindingGuard extends BeanDeserializerModifier {

    private final Logger log = LoggerFactory.getLogger(BindingGuard.class);

    /**
     * Installs the guard on a mapper. The mapper is changed, so this must be a private copy, never the
     * mapper an application handed in.
     *
     * @param mapper private mapper used to read config classes
     */
    static void install(ObjectMapper mapper) {
        mapper.registerModule(new SimpleModule("ageris-binding-guard").setDeserializerModifier(new BindingGuard()));
        mapper.setAnnotationIntrospector(new UnnamedBindings(mapper.getDeserializationConfig().getAnnotationIntrospector()));
    }

    @Override
    public List<BeanPropertyDefinition> updateProperties(DeserializationConfig config,
                                                         BeanDescription beanDescription,
                                                         List<BeanPropertyDefinition> propertyDefinitions) {
        AnnotationIntrospector introspector = config.getAnnotationIntrospector();
        String className = beanDescription.getBeanClass().getName();
        boolean receivesBindings = receivesBindings(introspector, beanDescription);
        for (BeanPropertyDefinition property : propertyDefinitions) {
            if (isBinding(introspector, property.getConstructorParameter())
                    || isBinding(introspector, property.getField())
                    || isBinding(introspector, property.getSetter())) {
                throw refusal("Property '" + property.getName() + "' of " + className
                        + " is a binding and must not also be a property of the configuration file; remove its @JsonProperty");
            }
            if (receivesBindings && property.getConstructorParameter() == null
                    && (property.getField() != null || property.getSetter() != null || isFilledThroughGetter(property))) {
                throw refusal("Property '" + property.getName() + "' of " + className + " can be written from the configuration file"
                        + " through a field, a setter, or a getter that returns a collection or map. A class that receives bindings"
                        + " takes values from the file through its constructor only; make the property a constructor parameter,"
                        + " or put @JsonIgnore on it if it holds a binding");
            }
        }
        return propertyDefinitions;
    }

    /**
     * @return true if a constructor or factory method parameter, a field or a setter of the class is injected.
     */
    private static boolean receivesBindings(AnnotationIntrospector introspector, BeanDescription beanDescription) {
        if (beanDescription.findInjectables() != null && !beanDescription.findInjectables().isEmpty()) {
            return true;
        }
        List<AnnotatedWithParams> creators = new ArrayList<>();
        creators.addAll(beanDescription.getConstructors());
        creators.addAll(beanDescription.getFactoryMethods());
        for (AnnotatedWithParams creator : creators) {
            for (int index = 0; index < creator.getParameterCount(); index++) {
                if (isBinding(introspector, creator.getParameter(index))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBinding(AnnotationIntrospector introspector, AnnotatedMember member) {
        return member != null && introspector.findInjectableValue(member) != null;
    }

    /**
     * @return true if the property has a getter whose collection or map Jackson would add the file's values to.
     */
    private static boolean isFilledThroughGetter(BeanPropertyDefinition property) {
        if (property.getGetter() == null) {
            return false;
        }
        Class<?> type = property.getGetter().getRawType();
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    /* A refused class is a mistake in the class, not in the file, and in skip mode the caller only counts
     * it, so it is reported here where it cannot be overlooked. */
    private IllegalArgumentException refusal(String message) {
        log.error(message);
        return new IllegalArgumentException(message);
    }

    /**
     * Keeps an injected constructor parameter nameless when the mapper would otherwise name it.
     *
     * A mapper that knows constructor parameter names, for instance through Jackson's parameter names
     * module, gives every parameter a property name. An injected parameter the author never named would
     * then become reachable from the file after all.
     */
    private static final class UnnamedBindings extends AnnotationIntrospectorPair {

        private static final long serialVersionUID = 1L;

        UnnamedBindings(AnnotationIntrospector introspector) {
            super(NopAnnotationIntrospector.instance, introspector);
        }

        @Override
        public String findImplicitPropertyName(AnnotatedMember member) {
            if (findInjectableValue(member) != null && findNameForDeserialization(member) == null) {
                return null;
            }
            return super.findImplicitPropertyName(member);
        }
    }
}
