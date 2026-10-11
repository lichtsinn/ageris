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
import dev.openfeature.sdk.MutableContext;
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
        Assert.assertNull(ContextMapping.toAgeris(null, "user").get("user"));
        Assert.assertNull(ContextMapping.toAgeris(new ImmutableContext(), "user").get("user"));
        Assert.assertNull(ContextMapping.toAgeris(new ImmutableContext(), "user").get("environment"));
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
        Assert.assertNull(ContextMapping.toAgeris(new ImmutableContext(""), "user").get("user"));
    }

    @Test
    public void testTextsNumbersAndBooleansBecomeTexts() {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("environment", new Value("qa"));
        attributes.put("tier", new Value(3));
        attributes.put("ratio", new Value(2.5));
        attributes.put("whole", new Value(3.0));
        attributes.put("account", new Value(12345678901.0));
        attributes.put("huge", new Value(1e20));
        attributes.put("precise", new Value(12345678.9));
        attributes.put("long", new Value(1234567890123456.0));
        attributes.put("small", new Value(0.00001));
        attributes.put("zero", new Value(0.0));
        attributes.put("negative", new Value(-3.0));
        attributes.put("beta", new Value(true));

        ConfigurationContext context = ContextMapping.toAgeris(new ImmutableContext(attributes), "user");

        Assert.assertEquals("qa", context.get("environment"));
        Assert.assertEquals("3", context.get("tier"));
        Assert.assertEquals("2.5", context.get("ratio"));
        Assert.assertEquals("3", context.get("whole"));
        Assert.assertEquals("12345678901", context.get("account"));
        Assert.assertEquals("100000000000000000000", context.get("huge"));
        Assert.assertEquals("12345678.9", context.get("precise"));
        Assert.assertEquals("1234567890123456", context.get("long"));
        Assert.assertEquals("0.00001", context.get("small"));
        Assert.assertEquals("0", context.get("zero"));
        Assert.assertEquals("-3", context.get("negative"));
        Assert.assertEquals("true", context.get("beta"));
    }

    @Test
    public void testNumbersThatAreNotNumbersAreLeftOut() {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("nan", new Value(Double.NaN));
        attributes.put("infinite", new Value(Double.POSITIVE_INFINITY));

        ConfigurationContext context = ContextMapping.toAgeris(new ImmutableContext(attributes), "user");

        Assert.assertNull(context.get("nan"));
        Assert.assertNull(context.get("infinite"));
    }

    @Test
    public void testValuesAreReadWhenTheyAreAskedFor() {
        MutableContext caller = new MutableContext("joe");
        ConfigurationContext context = ContextMapping.toAgeris(caller, "user");

        caller.add("environment", "qa");

        Assert.assertEquals("Nothing was copied when the context was handed over", "qa", context.get("environment"));
        Assert.assertEquals("joe", context.get("user"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContextOfACallerCannotBeChangedThroughAgeris() {
        ContextMapping.toAgeris(new ImmutableContext("joe"), "user").put("user", "anna");
    }

    @Test
    public void testContextsOfTwoCallersAreNotEqual() {
        ConfigurationContext joe = ContextMapping.toAgeris(new ImmutableContext("joe"), "user");
        ConfigurationContext anna = ContextMapping.toAgeris(new ImmutableContext("anna"), "user");

        Assert.assertNotEquals(joe, anna);
        Assert.assertEquals(joe, joe);
    }

    @Test
    public void testDatesListsStructuresAndNullsAreLeftOut() {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("since", new Value(Instant.parse("2026-01-01T00:00:00Z")));
        attributes.put("groups", new Value(Arrays.asList(new Value("a"), new Value("b"))));
        attributes.put("address", new Value(new ImmutableStructure(Collections.singletonMap("city", new Value("Berlin")))));
        attributes.put("nothing", new Value());
        attributes.put("environment", new Value("qa"));

        ConfigurationContext context = ContextMapping.toAgeris(new ImmutableContext(attributes), "user");

        Assert.assertNull(context.get("since"));
        Assert.assertNull(context.get("groups"));
        Assert.assertNull(context.get("address"));
        Assert.assertNull(context.get("nothing"));
        Assert.assertEquals("qa", context.get("environment"));
    }

    @Test
    public void testAttributeWithTheNameOfTheTypeWinsOverTheTargetingKey() {
        ConfigurationContext context = ContextMapping.toAgeris(
                new ImmutableContext("key-1", Collections.singletonMap("user", new Value("joe"))), "user");

        Assert.assertEquals("joe", context.get("user"));
    }
}
