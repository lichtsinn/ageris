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
package io.github.lichtsinn.ageris.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Default implementation for {@link ClassFinder}.
 */
public class DefaultClassFinder implements ClassFinder<Object> {

    private final TypeFactory typeFactory;

    /**
     * Creates default class finder, using type factory from Jackson's object mapper.
     */
    public DefaultClassFinder() {
        this(new ObjectMapper().getTypeFactory());
    }

    /**
     * Creates default class finder with provided type factory.
     *
     * @param typeFactory type factory to be used for class lookup.
     */
    public DefaultClassFinder(TypeFactory typeFactory) {
        this.typeFactory = typeFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Object> findClass(String className) throws ClassNotFoundException {
        return (Class<Object>) typeFactory.findClass(className);
    }
}
