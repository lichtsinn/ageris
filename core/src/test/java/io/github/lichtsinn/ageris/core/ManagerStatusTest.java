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

import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Unit tests for the status of the three configuration managers.
 */
public class ManagerStatusTest {

    @Test
    public void testFeatureFlagManagerIsLoadedAfterADirectUpdate() {
        FeatureFlagManager manager = new FeatureFlagManager();
        Assert.assertEquals(State.NOT_LOADED, manager.getStatus().getState());

        manager.updateConfigurations(Collections.emptyMap());

        Assert.assertEquals(State.LOADED, manager.getStatus().getState());
    }

    @Test
    public void testConfigManagerIsLoadedAfterADirectUpdate() {
        ConfigManager manager = new ConfigManager();
        Assert.assertEquals(State.NOT_LOADED, manager.getStatus().getState());

        manager.updateConfigurations(Collections.emptyMap());

        Assert.assertEquals(State.LOADED, manager.getStatus().getState());
    }

    @Test
    public void testJsonConfigManagerIsLoadedAfterADirectUpdate() {
        JsonConfigManager manager = new JsonConfigManager();
        Assert.assertEquals(State.NOT_LOADED, manager.getStatus().getState());

        manager.updateConfigurations(Collections.emptyMap());

        Assert.assertEquals(State.LOADED, manager.getStatus().getState());
    }
}
