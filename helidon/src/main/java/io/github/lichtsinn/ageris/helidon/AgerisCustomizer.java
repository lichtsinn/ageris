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
package io.github.lichtsinn.ageris.helidon;

import io.github.lichtsinn.ageris.core.Ageris;

/**
 * Lets an application set up what configuration cannot: the bindings of its policies, and the other options of
 * {@link Ageris}. A customizer is called once, before the managers are built. In the declarative style it is a service
 * of the application; in the imperative style it is handed to the builder of {@link AgerisHelidon}.
 */
@FunctionalInterface
public interface AgerisCustomizer {

    /**
     * @param ageris the Ageris of the application, before its managers are built
     */
    void customize(Ageris ageris);
}
