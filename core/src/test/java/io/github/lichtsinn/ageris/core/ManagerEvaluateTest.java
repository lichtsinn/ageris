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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.github.lichtsinn.ageris.core.Evaluation.Reason;
import io.github.lichtsinn.ageris.core.configs.DiscountConfig;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.exception.ConfigInstantiationException;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import io.github.lichtsinn.ageris.core.structure.Context;
import io.github.lichtsinn.ageris.core.structure.Modifiers;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for the evaluate methods of the three configuration managers.
 */
public class ManagerEvaluateTest {

    private static final String NUMBER_CONFIG = "io.github.lichtsinn.ageris.core.configs.NumberConfig";

    /* Each "ruled" entry has a base value, one for environment qa, and one for environment qa on cluster eu1. */
    private static final String FLAGS = "{ \"feature-flags\": {"
            + " \"plain\": { \"value\": true },"
            + " \"ruled\": { \"value\": false, \"modifiers\": { \"type\": \"environment\", \"contexts\": {"
            + "   \"qa\": { \"value\": true, \"modifiers\": { \"type\": \"cluster\", \"contexts\": {"
            + "     \"eu1\": { \"value\": false } } } } } } } } }";
    private static final String CONFIGS = "{ \"configs\": {"
            + " \"" + NUMBER_CONFIG + "\": { \"value\": { \"number\": 1 }, \"modifiers\": { \"type\": \"environment\", \"contexts\": {"
            + "   \"qa\": { \"value\": { \"number\": 2 }, \"modifiers\": { \"type\": \"cluster\", \"contexts\": {"
            + "     \"eu1\": { \"value\": { \"number\": 3 } } } } } } } } } }";
    private static final String JSON_CONFIGS = "{ \"configs\": {"
            + " \"plain\": { \"value\": { \"number\": 1 } },"
            + " \"ruled\": { \"value\": { \"number\": 1 }, \"modifiers\": { \"type\": \"environment\", \"contexts\": {"
            + "   \"qa\": { \"value\": { \"number\": 2 }, \"modifiers\": { \"type\": \"cluster\", \"contexts\": {"
            + "     \"eu1\": { \"value\": { \"number\": 3 } } } } } } } } } }";

    /** A config class that no test stores, with a no-argument constructor to fall back on. */
    public static class UnsetConfig {
        private final int number = 5;

        public int getNumber() {
            return number;
        }
    }

    private final ConfigurationContext empty = new ConfigurationContext();
    private final ConfigurationContext prod = context("environment", "prod");
    private final ConfigurationContext qa = context("environment", "qa");
    private final ConfigurationContext qaEu1 = context("environment", "qa", "cluster", "eu1");
    private final ConfigurationContext qaUs2 = context("environment", "qa", "cluster", "us2");
    private final List<ConfigurationContext> contexts = Arrays.asList(empty, prod, qa, qaEu1, qaUs2,
            context("cluster", "eu1"), context("user", "joe"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FeatureFlagManager flags = new FeatureFlagManager();
    private final ConfigManager configs = new ConfigManager();
    private final JsonConfigManager jsonConfigs = new JsonConfigManager();

    @Before
    public void load() throws IOException {
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, objectMapper, new FeatureFlagMetrics()).readValue(FLAGS));
        configs.updateConfigurations(new ConfigurationMapper<>("configs", false, objectMapper, new ConfigMetrics(), new DefaultClassFinder())
                .readValue(CONFIGS));
        jsonConfigs.updateConfigurations(new JsonConfigMapper("configs", false, objectMapper, new JsonConfigMetrics()).readValue(JSON_CONFIGS));
    }

    @Test
    public void testFlagReasons() {
        assertEvaluation(Boolean.FALSE, Reason.NOT_FOUND, Collections.emptyMap(), flags.evaluate("missing", qa));
        assertEvaluation(Boolean.TRUE, Reason.NOT_FOUND, Collections.emptyMap(), flags.evaluate("missing", qa, true));
        assertEvaluation(Boolean.TRUE, Reason.STATIC, Collections.emptyMap(), flags.evaluate("plain", qa));
        assertEvaluation(Boolean.FALSE, Reason.DEFAULT, Collections.emptyMap(), flags.evaluate("ruled", prod));
        assertEvaluation(Boolean.TRUE, Reason.MATCH, ImmutableMap.of("environment", "qa"), flags.evaluate("ruled", qa));
        assertEvaluation(Boolean.TRUE, Reason.MATCH, ImmutableMap.of("environment", "qa"), flags.evaluate("ruled", qaUs2));
        assertEvaluation(Boolean.FALSE, Reason.MATCH, ImmutableMap.of("environment", "qa", "cluster", "eu1"), flags.evaluate("ruled", qaEu1));
    }

    @Test
    public void testFlagFoundIgnoresTheCallersDefault() {
        assertEvaluation(Boolean.FALSE, Reason.DEFAULT, Collections.emptyMap(), flags.evaluate("ruled", prod, true));
    }

    @Test
    public void testConfigReasons() {
        Evaluation<UnsetConfig> missing = configs.evaluate(UnsetConfig.class, qa);
        Assert.assertEquals(5, missing.getValue().getNumber());
        Assert.assertEquals(Reason.NOT_FOUND, missing.getReason());
        Assert.assertEquals(Collections.emptyMap(), missing.getMatches());

        Evaluation<NumberConfig> byDefault = configs.evaluate(NumberConfig.class, prod);
        Assert.assertEquals(1, byDefault.getValue().getNumber());
        Assert.assertEquals(Reason.DEFAULT, byDefault.getReason());

        Evaluation<NumberConfig> matched = configs.evaluate(NumberConfig.class, qaEu1);
        Assert.assertEquals(3, matched.getValue().getNumber());
        Assert.assertEquals(Reason.MATCH, matched.getReason());
        Assert.assertEquals(ImmutableMap.of("environment", "qa", "cluster", "eu1"), matched.getMatches());
    }

    @Test
    public void testConfigWithoutModifiersIsStatic() {
        configs.updateConfigurations(ImmutableMap.of(NUMBER_CONFIG, new Configuration<>(NUMBER_CONFIG, new Context<>(new NumberConfig(9), null))));

        Evaluation<NumberConfig> evaluation = configs.evaluate(NumberConfig.class, qa);

        Assert.assertEquals(9, evaluation.getValue().getNumber());
        Assert.assertEquals(Reason.STATIC, evaluation.getReason());
    }

    @Test
    public void testEvaluatedConfigIsTheStoredObject() {
        Assert.assertSame(configs.getConfig(NumberConfig.class, qa), configs.evaluate(NumberConfig.class, qa).getValue());
    }

    @Test(expected = ConfigInstantiationException.class)
    public void testMissingConfigWithoutNoArgumentConstructorFailsAsGetConfigDoes() {
        configs.evaluate(DiscountConfig.class, qa);
    }

    @Test
    public void testStoredValueOfAnotherClassIsNotFound() {
        configs.updateConfigurations(ImmutableMap.of(NUMBER_CONFIG, new Configuration<>(NUMBER_CONFIG, new Context<>("not a number config", null))));

        Evaluation<NumberConfig> evaluation = configs.evaluate(NumberConfig.class, qa);

        Assert.assertEquals(-1, evaluation.getValue().getNumber());
        Assert.assertEquals(Reason.NOT_FOUND, evaluation.getReason());
        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
        Assert.assertEquals(-1, configs.getConfig(NumberConfig.class, qa).getNumber());
    }

    @Test
    public void testStoredValueOfAnotherClassUnderAMatchingModifierIsNotFoundWithoutMatches() {
        Context<Object> wrongInQa = new Context<>(new NumberConfig(1),
                new Modifiers<>("environment", ImmutableMap.of("qa", new Context<>("not a number config", null))));
        configs.updateConfigurations(ImmutableMap.of(NUMBER_CONFIG, new Configuration<>(NUMBER_CONFIG, wrongInQa)));

        Evaluation<NumberConfig> evaluation = configs.evaluate(NumberConfig.class, qa);

        Assert.assertEquals(-1, evaluation.getValue().getNumber());
        Assert.assertEquals(Reason.NOT_FOUND, evaluation.getReason());
        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
        Assert.assertEquals(-1, configs.getConfig(NumberConfig.class, qa).getNumber());
        Assert.assertEquals(Reason.DEFAULT, configs.evaluate(NumberConfig.class, prod).getReason());
    }

    @Test
    public void testJsonConfigReasons() {
        Evaluation<JsonNode> missing = jsonConfigs.evaluate("missing", qa);
        Assert.assertSame(jsonConfigs.getConfig("missing", qa), missing.getValue());
        Assert.assertEquals(Reason.NOT_FOUND, missing.getReason());

        Assert.assertEquals(Reason.STATIC, jsonConfigs.evaluate("plain", qa).getReason());
        Assert.assertEquals(Reason.DEFAULT, jsonConfigs.evaluate("ruled", prod).getReason());

        Evaluation<JsonNode> matched = jsonConfigs.evaluate("ruled", qaUs2);
        Assert.assertEquals(2, matched.getValue().get("number").asInt());
        Assert.assertEquals(Reason.MATCH, matched.getReason());
        Assert.assertEquals(ImmutableMap.of("environment", "qa"), matched.getMatches());
    }

    @Test
    public void testEvaluateAgreesWithTheExistingLookups() {
        for (ConfigurationContext context : contexts) {
            for (String name : Arrays.asList("plain", "ruled", "missing")) {
                Assert.assertEquals(flags.isActive(name, context), flags.evaluate(name, context).getValue());
                Assert.assertEquals(flags.isActive(name, context, true), flags.evaluate(name, context, true).getValue());
                Assert.assertSame(jsonConfigs.getConfig(name, context), jsonConfigs.evaluate(name, context).getValue());
            }
            Assert.assertSame(configs.getConfig(NumberConfig.class, context), configs.evaluate(NumberConfig.class, context).getValue());
            Assert.assertEquals(configs.getConfig(UnsetConfig.class, context).getNumber(), configs.evaluate(UnsetConfig.class, context).getValue().getNumber());
        }
    }

    private static <T> void assertEvaluation(T value, Reason reason, java.util.Map<String, String> matches, Evaluation<T> evaluation) {
        Assert.assertEquals(value, evaluation.getValue());
        Assert.assertEquals(reason, evaluation.getReason());
        Assert.assertEquals(matches, evaluation.getMatches());
    }

    private static ConfigurationContext context(String... typesAndValues) {
        ConfigurationContext context = new ConfigurationContext();
        for (int index = 0; index < typesAndValues.length; index += 2) {
            context.put(typesAndValues[index], typesAndValues[index + 1]);
        }
        return context;
    }
}
