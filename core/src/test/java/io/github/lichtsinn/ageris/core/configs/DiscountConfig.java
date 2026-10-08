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

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Config with a value from the file and a binding by type, and behavior that uses both.
 */
public class DiscountConfig {

    private final int percent;
    private final PriceSource prices;

    @JsonCreator
    public DiscountConfig(@JsonProperty(value = "percent", required = true) int percent,
                          @JacksonInject PriceSource prices) {
        this.percent = percent;
        this.prices = prices;
    }

    @JsonProperty("percent")
    public int getPercent() {
        return percent;
    }

    /** Deliberately not a JavaBean getter, so the bound object is never serialized. */
    public PriceSource prices() {
        return prices;
    }

    public int discountedPrice() {
        return prices.basePrice() - prices.basePrice() * percent / 100;
    }
}
