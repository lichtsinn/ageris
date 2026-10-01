/*
 * Copyright 2018 Medallia, Inc.
 * Modifications copyright 2026 Mario Lichtsinn; this file differs from the version
 * released by Medallia, Inc. See NOTICE.
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

import java.util.HashMap;

/**
 * Unit tests for {@link ConfigurationContext}.
 */
public class ConfigurationContextTest {

    @Test
    public void testGetReturnsValueAfterPut()  {
        ConfigurationContext runtimeContext = new ConfigurationContext();
        runtimeContext.put("environment", "qa");
        Assert.assertEquals("qa", runtimeContext.get("environment"));
    }

    @Test
    public void testEquals() {
        ConfigurationContext runtimeContext1 = new ConfigurationContext();
        runtimeContext1.put("environment", "qa");
        Assert.assertTrue(runtimeContext1.equals(runtimeContext1));
        Assert.assertFalse(runtimeContext1.equals(null));
        Assert.assertFalse(runtimeContext1.equals(new HashMap()));
        ConfigurationContext runtimeContext2 = new ConfigurationContext();
        runtimeContext2.put("environment", "qa");
        Assert.assertTrue(runtimeContext1.equals(runtimeContext2));
        ConfigurationContext runtimeContext3 = new ConfigurationContext();
        runtimeContext3.put("environment", "prod");
        Assert.assertFalse(runtimeContext1.equals(runtimeContext3));

    }

    @Test
    public void testHashCode() {
        ConfigurationContext runtimeContext1 = new ConfigurationContext();
        runtimeContext1.put("environment", "qa");
        ConfigurationContext runtimeContext2 = new ConfigurationContext();
        runtimeContext2.put("environment", "qa");
        Assert.assertEquals(runtimeContext1.hashCode(), runtimeContext2.hashCode());
    }
}