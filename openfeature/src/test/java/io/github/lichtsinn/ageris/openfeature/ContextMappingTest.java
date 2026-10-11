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
package io.github.lichtsinn.ageris.openfeature;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ContextMapping}.
 */
public class ContextMappingTest {

    @Test
    public void testNoContextIsAnEmptyContext() {
        Assert.assertEquals(new ConfigurationContext(), ContextMapping.toAgeris(null, "user"));
        Assert.assertEquals(new ConfigurationContext(), ContextMapping.toAgeris(new ImmutableContext(), "user"));
    }

    @Test
    public void testTargetingKeyGoesUnderTheGivenType() {
        Assert.assertEquals("joe", ContextMapping.toAgeris(new ImmutableContext("joe"), "user").get("user"));
        Assert.assertEquals("joe", ContextMapping.toAgeris(new ImmutableContext("joe"), "customer").get("customer"));
        Assert.assertNull(ContextMapping.toAgeris(new ImmutableContext("joe"), "customer").get("user"));
        Assert.assertNull(ContextMapping.toAgeris(new ImmutableContext("joe"), "user").get("targetingKey"));
    }

    @Test
    public void testEmptyTargetingKeyIsLeftOut() {
        Assert.assertEquals(new ConfigurationContext(), ContextMapping.toAgeris(new ImmutableContext(""), "user"));
    }

    @Test
    public void testTextsNumbersAndBooleansBecomeTexts() {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("environment", new Value("qa"));
        attributes.put("tier", new Value(3));
        attributes.put("ratio", new Value(2.5));
        attributes.put("whole", new Value(3.0));
        attributes.put("account", new Value(12345678901.0));
        attributes.put("huge", new Value(1e300));
        attributes.put("beta", new Value(true));

        ConfigurationContext context = ContextMapping.toAgeris(new ImmutableContext(attributes), "user");

        Assert.assertEquals("qa", context.get("environment"));
        Assert.assertEquals("3", context.get("tier"));
        Assert.assertEquals("2.5", context.get("ratio"));
        Assert.assertEquals("3", context.get("whole"));
        Assert.assertEquals("12345678901", context.get("account"));
        Assert.assertEquals("1.0E300", context.get("huge"));
        Assert.assertEquals("true", context.get("beta"));
    }

    @Test
    public void testDatesListsStructuresAndNullsAreLeftOut() {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("since", new Value(Instant.parse("2026-01-01T00:00:00Z")));
        attributes.put("groups", new Value(Arrays.asList(new Value("a"), new Value("b"))));
        attributes.put("address", new Value(new ImmutableStructure(Collections.singletonMap("city", new Value("Berlin")))));
        attributes.put("nothing", new Value());
        attributes.put("environment", new Value("qa"));

        ConfigurationContext expected = new ConfigurationContext();
        expected.put("environment", "qa");
        Assert.assertEquals(expected, ContextMapping.toAgeris(new ImmutableContext(attributes), "user"));
    }

    @Test
    public void testAttributeWithTheNameOfTheTypeWinsOverTheTargetingKey() {
        ConfigurationContext context = ContextMapping.toAgeris(
                new ImmutableContext("key-1", Collections.singletonMap("user", new Value("joe"))), "user");

        Assert.assertEquals("joe", context.get("user"));
    }
}
