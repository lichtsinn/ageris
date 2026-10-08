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
 * Config that exposes its binding through a JavaBean getter, which gives the binding a property name.
 */
public class ExposedBindingConfig {

    private final int percent;
    private final PriceSource prices;

    @JsonCreator
    public ExposedBindingConfig(@JsonProperty("percent") int percent,
                                @JacksonInject PriceSource prices) {
        this.percent = percent;
        this.prices = prices;
    }

    public int getPercent() {
        return percent;
    }

    public PriceSource getPrices() {
        return prices;
    }
}
