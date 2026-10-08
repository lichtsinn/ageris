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
package io.github.lichtsinn.ageris.core.configs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stands in for a live object an application binds, such as a pricing service. It can also be read from
 * JSON, so that the tests can show a configuration file is never allowed to supply one.
 */
public class PriceSource {

    private final int basePrice;

    @JsonCreator
    public PriceSource(@JsonProperty("basePrice") int basePrice) {
        this.basePrice = basePrice;
    }

    public int basePrice() {
        return basePrice;
    }
}
