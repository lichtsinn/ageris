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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.github.lichtsinn.ageris.core.Evaluation.Reason;
import io.github.lichtsinn.ageris.core.configs.DiscountConfig;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.configs.PriceSource;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import io.github.lichtsinn.ageris.core.structure.Context;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests for rules in modifiers, through the managers. For an entry named enable-checkout, user-9 is in bucket 293, user-15 in
 * 1505, user-0 in 7962 and carla in 4414, of 10,000. For one named spring-rollout, carla is in bucket 3411.
 */
public class TargetingTest {

    private static final String NUMBER_CONFIG = "io.github.lichtsinn.ageris.core.configs.NumberConfig";

    /** Off by default; in qa on for joe, off for mallory, on for anna and ben, and on for 10 percent of the other users. */
    private static final String SCENARIO = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"environment\", \"contexts\": { \"qa\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true }, \"mallory\": { \"value\": false } },"
            + " \"rules\": [ { \"in\": [\"anna\", \"ben\"], \"value\": true }, { \"percent\": 10, \"value\": true } ] } } } } } } }";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testScenario() throws IOException {
        FeatureFlagManager flags = flags(SCENARIO);

        Assert.assertTrue(flags.isActive("enable-checkout", context("environment", "qa", "user", "joe")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("environment", "qa", "user", "mallory")));
        Assert.assertTrue(flags.isActive("enable-checkout", context("environment", "qa", "user", "anna")));
        Assert.assertTrue(flags.isActive("enable-checkout", context("environment", "qa", "user", "user-9")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("environment", "qa", "user", "user-15")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("environment", "qa")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("environment", "production", "user", "joe")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "anna")));
    }

    @Test
    public void testNamedEntryBeatsARule() throws IOException {
        FeatureFlagManager flags = flags(flag("\"type\": \"user\", \"contexts\": { \"user-9\": { \"value\": false } },"
                + " \"rules\": [ { \"percent\": 100, \"value\": true } ]"));

        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "user-9")));
        Assert.assertTrue(flags.isActive("enable-checkout", context("user", "user-15")));
    }

    @Test
    public void testFirstMatchingRuleWins() throws IOException {
        FeatureFlagManager flags = flags(flag("\"type\": \"user\", \"rules\": [ { \"in\": [\"user-9\"], \"value\": false },"
                + " { \"percent\": 100, \"value\": true } ]"));

        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "user-9")));
        Assert.assertTrue(flags.isActive("enable-checkout", context("user", "carla")));
    }

    @Test
    public void testContextsMayBeLeftOutWhenThereAreRules() throws IOException {
        FeatureFlagManager flags = flags(flag("\"type\": \"user\", \"rules\": [ { \"in\": [\"anna\"], \"value\": true } ]"));

        Assert.assertTrue(flags.isActive("enable-checkout", context("user", "anna")));
        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "ben")));
    }

    @Test
    public void testWalkDoesNotReturnToTheLevelAbove() throws IOException {
        /* anna matches the rule, whose node is false and only knows cluster eu-1; the base value true of the entry is not used. */
        String json = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": true, \"modifiers\": { \"type\": \"user\","
                + " \"rules\": [ { \"in\": [\"anna\"], \"value\": false, \"modifiers\": { \"type\": \"cluster\","
                + " \"contexts\": { \"eu-1\": { \"value\": false } } } } ] } } } }";
        FeatureFlagManager flags = flags(json);

        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "anna", "cluster", "us-2")));
        Assert.assertTrue(flags.isActive("enable-checkout", context("user", "ben", "cluster", "us-2")));
    }

    @Test
    public void testTwoPercentagesInOneListAreCumulative() throws IOException {
        String json = "{ \"configs\": { \"enable-checkout\": { \"value\": { \"variant\": \"none\" }, \"modifiers\": { \"type\": \"user\","
                + " \"rules\": [ { \"percent\": 10, \"value\": { \"variant\": \"A\" } },"
                + " { \"percent\": 30, \"value\": { \"variant\": \"B\" } } ] } } } }";
        JsonConfigManager jsonConfigs = new JsonConfigManager();
        jsonConfigs.updateConfigurations(new JsonConfigMapper("configs", false, objectMapper, new JsonConfigMetrics()).readValue(json));

        Assert.assertEquals("A", jsonConfigs.getConfig("enable-checkout", context("user", "user-9")).get("variant").asText());
        Assert.assertEquals("B", jsonConfigs.getConfig("enable-checkout", context("user", "user-15")).get("variant").asText());
        Assert.assertEquals("none", jsonConfigs.getConfig("enable-checkout", context("user", "user-0")).get("variant").asText());
        Assert.assertEquals("none", jsonConfigs.getConfig("enable-checkout", new ConfigurationContext()).get("variant").asText());
    }

    @Test
    public void testEachEntryHasItsOwnCallers() throws IOException {
        String rules = "\"type\": \"user\", \"rules\": [ { \"percent\": 40, \"value\": true } ]";
        String json = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": { " + rules + " } },"
                + " \"spring-rollout\": { \"value\": false, \"modifiers\": { " + rules + " } } } }";
        FeatureFlagManager flags = flags(json);

        /* carla is in bucket 4414 for enable-checkout and in bucket 3411 for spring-rollout. */
        Assert.assertFalse(flags.isActive("enable-checkout", context("user", "carla")));
        Assert.assertTrue(flags.isActive("spring-rollout", context("user", "carla")));
    }

    @Test
    public void testSameCallersAtEveryLevelOfOneEntry() throws IOException {
        String tenPercent = "{ \"value\": false, \"modifiers\": { \"type\": \"user\", \"rules\": [ { \"percent\": 10, \"value\": true } ] } }";
        String json = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": { \"type\": \"environment\","
                + " \"contexts\": { \"qa\": " + tenPercent + ", \"production\": " + tenPercent + " } } } } }";
        FeatureFlagManager flags = flags(json);

        for (int user = 0; user < 500; user++) {
            Assert.assertEquals(flags.isActive("enable-checkout", context("environment", "qa", "user", "user-" + user)),
                    flags.isActive("enable-checkout", context("environment", "production", "user", "user-" + user)));
        }
    }

    @Test
    public void testRulesWorkForTypedConfigs() throws IOException {
        String json = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\": { \"number\": 0 }, \"modifiers\": { \"type\": \"plan\","
                + " \"rules\": [ { \"in\": [\"pro\", \"enterprise\"], \"value\": { \"number\": 5 } } ] } } } }";
        ConfigManager configs = new ConfigManager();
        configs.updateConfigurations(new ConfigurationMapper<>("configs", false, objectMapper, new ConfigMetrics(), new DefaultClassFinder())
                .readValue(json));

        Assert.assertEquals(5, configs.getConfig(NumberConfig.class, context("plan", "pro")).getNumber());
        Assert.assertEquals(0, configs.getConfig(NumberConfig.class, context("plan", "free")).getNumber());
        Assert.assertSame(configs.getConfig(NumberConfig.class, context("plan", "pro")), configs.getConfig(NumberConfig.class, context("plan", "enterprise")));
    }

    @Test
    public void testPolicyCanBeRolledOutToAPercentage() {
        /* For an entry named after DiscountConfig, user-18 is in bucket 275 and user-0 in bucket 5219. */
        String json = "{ \"configs\": { \"io.github.lichtsinn.ageris.core.configs.DiscountConfig\": { \"value\": { \"percent\": 0 },"
                + " \"modifiers\": { \"type\": \"user\", \"rules\": [ { \"percent\": 10, \"value\": { \"percent\": 25 } } ] } } } }";
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        Ageris ageris = new Ageris((fileNames, application) -> ImmutableMap.of("/configs.json", json), executorService, objectMapper, sha256());
        ageris.bind(PriceSource.class, new PriceSource(200));
        ConfigManager configs = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        ageris.createLoader(Duration.ofSeconds(10)).start();
        ArgumentCaptor<Runnable> refresh = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(refresh.capture(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class));
        refresh.getValue().run();

        Assert.assertEquals(150, configs.getConfig(DiscountConfig.class, context("user", "user-18")).discountedPrice());
        Assert.assertEquals(200, configs.getConfig(DiscountConfig.class, context("user", "user-0")).discountedPrice());
    }

    @Test
    public void testNullNameUsesAnEmptySalt() throws IOException {
        Context<Boolean> context = objectMapper.readValue(
                "{ \"value\": false, \"modifiers\": { \"type\": \"user\", \"rules\": [ { \"percent\": 50, \"value\": true } ] } }",
                objectMapper.getTypeFactory().constructParametricType(Context.class, Boolean.class));
        Configuration<Boolean> unnamed = new Configuration<>(null, context);
        Configuration<Boolean> empty = new Configuration<>("", context);

        int active = 0;
        for (int user = 0; user < 200; user++) {
            ConfigurationContext caller = context("user", "user-" + user);
            Assert.assertEquals(empty.getValue(caller), unnamed.getValue(caller));
            Assert.assertEquals(context.getValue(caller), unnamed.getValue(caller));
            if (unnamed.getValue(caller)) {
                active++;
            }
        }
        Assert.assertTrue("Half of 200 callers expected, give or take, but was " + active, active > 60 && active < 140);
    }

    @Test
    public void testWrittenRulesLoadToTheSameBehavior() throws IOException {
        FeatureFlagManager flags = flags(SCENARIO);

        String written = flags.asString(new ConfigurationWriter<>("feature-flags", objectMapper));
        FeatureFlagManager reloaded = flags(written);

        Assert.assertTrue(written, written.contains("\"rules\""));
        Assert.assertTrue(written, written.contains("\"percent\""));
        for (String user : new String[] {"joe", "mallory", "anna", "ben", "user-9", "user-15", "user-0"}) {
            ConfigurationContext caller = context("environment", "qa", "user", user);
            Assert.assertEquals(user, flags.isActive("enable-checkout", caller), reloaded.isActive("enable-checkout", caller));
        }
    }

    @Test
    public void testEntryWithoutRulesIsWrittenAsBefore() throws IOException {
        String written = flags(flag("\"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true } }"))
                .asString(new ConfigurationWriter<>("feature-flags", objectMapper));

        Assert.assertFalse(written, written.contains("rules"));
    }

    @Test
    public void testEvaluateReportsARuleMatchAsAMatch() throws IOException {
        FeatureFlagManager flags = flags(SCENARIO);

        Evaluation<Boolean> rolledOut = flags.evaluate("enable-checkout", context("environment", "qa", "user", "user-9"));
        Evaluation<Boolean> listed = flags.evaluate("enable-checkout", context("environment", "qa", "user", "anna"));
        Evaluation<Boolean> outside = flags.evaluate("enable-checkout", context("environment", "qa", "user", "user-15"));
        Evaluation<Boolean> elsewhere = flags.evaluate("enable-checkout", context("environment", "production", "user", "joe"));

        Assert.assertEquals(Boolean.TRUE, rolledOut.getValue());
        Assert.assertEquals(Reason.MATCH, rolledOut.getReason());
        Assert.assertEquals(ImmutableMap.of("environment", "qa", "user", "user-9"), rolledOut.getMatches());
        Assert.assertEquals(ImmutableMap.of("environment", "qa", "user", "anna"), listed.getMatches());
        Assert.assertEquals(Boolean.FALSE, outside.getValue());
        Assert.assertEquals(ImmutableMap.of("environment", "qa"), outside.getMatches());
        Assert.assertEquals(Reason.DEFAULT, elsewhere.getReason());
        Assert.assertEquals(Collections.emptyMap(), elsewhere.getMatches());
    }

    @Test
    public void testEvaluateAgreesWithTheLookupForRules() throws IOException {
        FeatureFlagManager flags = flags(SCENARIO);
        for (String environment : new String[] {"qa", "production"}) {
            for (int user = 0; user < 300; user++) {
                ConfigurationContext caller = context("environment", environment, "user", "user-" + user);
                Assert.assertEquals(flags.isActive("enable-checkout", caller), flags.evaluate("enable-checkout", caller).getValue());
            }
            for (String user : new String[] {"joe", "mallory", "anna", "ben"}) {
                ConfigurationContext caller = context("environment", environment, "user", user);
                Assert.assertEquals(flags.isActive("enable-checkout", caller), flags.evaluate("enable-checkout", caller).getValue());
            }
        }
    }

    private static String flag(String modifiers) {
        return "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": { " + modifiers + " } } } }";
    }

    private FeatureFlagManager flags(String json) throws IOException {
        FeatureFlagManager flags = new FeatureFlagManager();
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, objectMapper, new FeatureFlagMetrics()).readValue(json));
        return flags;
    }

    private static ConfigurationContext context(String... typesAndValues) {
        ConfigurationContext context = new ConfigurationContext();
        for (int index = 0; index < typesAndValues.length; index += 2) {
            context.put(typesAndValues[index], typesAndValues[index + 1]);
        }
        return context;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
