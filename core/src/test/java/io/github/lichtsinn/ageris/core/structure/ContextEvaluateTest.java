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
package io.github.lichtsinn.ageris.core.structure;

import com.google.common.collect.ImmutableMap;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.Evaluation;
import io.github.lichtsinn.ageris.core.Evaluation.Reason;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link Context#evaluate(ConfigurationContext)}.
 */
public class ContextEvaluateTest {

    /** "base" for everyone, "qa" in environment qa, and "qa-eu1" in environment qa on cluster eu1. */
    private final Context<String> ruled = new Context<>("base",
            new Modifiers<>("environment", ImmutableMap.of(
                    "qa", new Context<>("qa",
                            new Modifiers<>("cluster", ImmutableMap.of(
                                    "eu1", new Context<>("qa-eu1", null)))))));

    private final Context<String> plain = new Context<>("base", null);

    @Test
    public void testContextWithoutModifiersIsStatic() {
        Evaluation<String> evaluation = plain.evaluate(context("environment", "qa"));

        Assert.assertEquals("base", evaluation.getValue());
        Assert.assertEquals(Reason.STATIC, evaluation.getReason());
        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
    }

    @Test
    public void testNoMatchingModifierIsDefault() {
        Evaluation<String> evaluation = ruled.evaluate(context("environment", "prod"));

        Assert.assertEquals("base", evaluation.getValue());
        Assert.assertEquals(Reason.DEFAULT, evaluation.getReason());
        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
    }

    @Test
    public void testEmptyContextIsDefault() {
        Assert.assertEquals(Reason.DEFAULT, ruled.evaluate(new ConfigurationContext()).getReason());
    }

    @Test
    public void testContextWithAnUnrelatedTypeIsDefault() {
        Evaluation<String> evaluation = ruled.evaluate(context("cluster", "eu1"));

        Assert.assertEquals("base", evaluation.getValue());
        Assert.assertEquals(Reason.DEFAULT, evaluation.getReason());
    }

    @Test
    public void testSingleMatch() {
        Evaluation<String> evaluation = ruled.evaluate(context("environment", "qa"));

        Assert.assertEquals("qa", evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Collections.singletonMap("environment", "qa"), evaluation.getMatches());
    }

    @Test
    public void testNestedMatchListsTheStepsOutermostFirst() {
        Evaluation<String> evaluation = ruled.evaluate(context("cluster", "eu1", "environment", "qa"));

        Assert.assertEquals("qa-eu1", evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Arrays.asList("environment", "cluster"), new ArrayList<>(evaluation.getMatches().keySet()));
        Assert.assertEquals(ImmutableMap.of("environment", "qa", "cluster", "eu1"), evaluation.getMatches());
    }

    @Test
    public void testPartialMatchStopsAtTheDeepestStepThatMatched() {
        Evaluation<String> evaluation = ruled.evaluate(context("environment", "qa", "cluster", "us2"));

        Assert.assertEquals("qa", evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Collections.singletonMap("environment", "qa"), evaluation.getMatches());
    }

    @Test
    public void testNullNestedContextIsNoMatch() {
        Map<String, Context<String>> contexts = new HashMap<>();
        contexts.put("qa", null);
        Context<String> withNull = new Context<>("base", new Modifiers<>("environment", contexts));

        Evaluation<String> evaluation = withNull.evaluate(context("environment", "qa"));

        Assert.assertEquals("base", evaluation.getValue());
        Assert.assertEquals(Reason.DEFAULT, evaluation.getReason());
        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
    }

    @Test
    public void testSameTypeNestedInsideItselfIsReportedOnce() {
        Context<String> nested = new Context<>("base",
                new Modifiers<>("environment", ImmutableMap.of(
                        "qa", new Context<>("outer",
                                new Modifiers<>("environment", ImmutableMap.of(
                                        "qa", new Context<>("inner", null)))))));

        Evaluation<String> evaluation = nested.evaluate(context("environment", "qa"));

        Assert.assertEquals("inner", evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Collections.singletonMap("environment", "qa"), evaluation.getMatches());
    }

    @Test
    public void testEvaluateAndGetValueAgree() {
        List<ConfigurationContext> contexts = Arrays.asList(
                new ConfigurationContext(),
                context("environment", "prod"),
                context("environment", "qa"),
                context("environment", "qa", "cluster", "eu1"),
                context("environment", "qa", "cluster", "us2"),
                context("cluster", "eu1"),
                context("user", "joe"));

        Map<String, Context<String>> nullNested = new HashMap<>();
        nullNested.put("qa", null);
        Context<String> withNull = new Context<>("base", new Modifiers<>("environment", nullNested));

        for (ConfigurationContext context : contexts) {
            Assert.assertEquals(withNull.getValue(context), withNull.evaluate(context).getValue());
            Assert.assertEquals(ruled.getValue(context), ruled.evaluate(context).getValue());
            Assert.assertEquals(plain.getValue(context), plain.evaluate(context).getValue());
        }
    }

    private static ConfigurationContext context(String... typesAndValues) {
        ConfigurationContext context = new ConfigurationContext();
        for (int index = 0; index < typesAndValues.length; index += 2) {
            context.put(typesAndValues[index], typesAndValues[index + 1]);
        }
        return context;
    }
}
