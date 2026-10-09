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
package io.github.lichtsinn.ageris.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An update that a configuration manager has applied, as passed to a {@link ConfigurationListener}.
 */
public final class ConfigurationUpdate {

    private final List<String> configurationNames;

    /**
     * Creates an update.
     *
     * @param configurationNames names of the configurations now loaded
     */
    ConfigurationUpdate(List<String> configurationNames) {
        this.configurationNames = Collections.unmodifiableList(new ArrayList<>(configurationNames));
    }

    /**
     * @return names of the configurations now loaded, unmodifiable
     */
    public List<String> getConfigurationNames() {
        return configurationNames;
    }
}
