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

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.function.IntSupplier;

/**
 * A policy for the tests: a value from the file, and a binding that the application supplies.
 */
public class Discount {

    private final int percent;
    private final IntSupplier basePrice;

    @JsonCreator
    public Discount(@JsonProperty(value = "percent", required = true) int percent, @JacksonInject IntSupplier basePrice) {
        this.percent = percent;
        this.basePrice = basePrice;
    }

    public int price() {
        return basePrice.getAsInt() - basePrice.getAsInt() * percent / 100;
    }
}
