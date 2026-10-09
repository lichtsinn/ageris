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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for listeners on the three configuration managers.
 */
public class ManagerListenerTest {

    private final List<String> calls = new ArrayList<>();
    private final ConfigurationListener listener = update -> calls.add("called");

    @Test
    public void testFeatureFlagManagerCallsAndRemovesListeners() {
        FeatureFlagManager manager = new FeatureFlagManager();
        manager.addListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);

        manager.removeListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);
    }

    @Test
    public void testConfigManagerCallsAndRemovesListeners() {
        ConfigManager manager = new ConfigManager();
        manager.addListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);

        manager.removeListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);
    }

    @Test
    public void testJsonConfigManagerCallsAndRemovesListeners() {
        JsonConfigManager manager = new JsonConfigManager();
        manager.addListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);

        manager.removeListener(listener);
        manager.updateConfigurations(Collections.emptyMap());
        Assert.assertEquals(Collections.singletonList("called"), calls);
    }
}
