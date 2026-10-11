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
package io.github.lichtsinn.ageris.spring;

import io.github.lichtsinn.ageris.core.Ageris;

/**
 * Lets an application set up what properties cannot: the bindings of its policies, and the other options of
 * {@link Ageris}. Every bean of this type is called once, before the managers are built.
 *
 *  &#64;Bean
 *  AgerisCustomizer bindings(PricingService pricing) {
 *      return ageris -&gt; ageris.bind(PricingService.class, pricing);
 *  }
 */
@FunctionalInterface
public interface AgerisCustomizer {

    /**
     * @param ageris the Ageris of the application, before its managers are built
     */
    void customize(Ageris ageris);
}
