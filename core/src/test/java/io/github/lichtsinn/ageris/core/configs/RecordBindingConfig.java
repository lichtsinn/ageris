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

/**
 * Record that asks for a binding. Every component of a record is a property of the file, so it must never load.
 *
 * @param percent discount in percent, from the configuration file
 * @param prices binding from the application
 */
public record RecordBindingConfig(int percent, @JacksonInject PriceSource prices) { }
