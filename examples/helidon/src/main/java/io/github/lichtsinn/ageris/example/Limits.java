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
package io.github.lichtsinn.ageris.example;

import java.util.List;

/**
 * A config: what a caller may do. Ageris creates it from the configuration file and never changes it afterwards, so a
 * record is all it takes. When the file changes, callers get a new one.
 *
 * @param maxItems how many items a caller may have
 * @param regions where a caller may keep them
 */
public record Limits(int maxItems, List<String> regions) {

    /**
     * Creates the limits.
     *
     * @param maxItems how many items a caller may have
     * @param regions where a caller may keep them
     */
    public Limits {
        regions = List.copyOf(regions);
    }
}
