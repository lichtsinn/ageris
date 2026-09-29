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
package com.medallia.merci.core.utils;

/**
 * Interface for finding Java classes based on their names.
 *
 * @param <T> Base type of class.
 */
public interface ClassFinder<T> {
    /**
     * Return Java class based on provided name.
     *
     * @param className name for class lookup
     * @return class class for given name
     * @throws ClassNotFoundException if no class could be found with provided name
     */
    Class<T> findClass(String className) throws ClassNotFoundException;
}
