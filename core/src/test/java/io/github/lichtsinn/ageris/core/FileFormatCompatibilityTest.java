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
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests that configuration files in the format of earlier releases keep their meaning. The files under
 * src/test/resources/compatibility are never edited; see the README there.
 */
public class FileFormatCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testFeatureFlagsOfRelease020() throws IOException {
        FeatureFlagManager flags = new FeatureFlagManager();
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, objectMapper, new FeatureFlagMetrics())
                .readValue(resource("0.2.0/feature-flags.json")));

        List<String> names = flags.getConfigurationNames();
        Collections.sort(names);
        Assert.assertEquals(Arrays.asList("enable-by-cluster", "enable-everywhere", "enable-international-welcome", "enable-with-empty-contexts"), names);

        Assert.assertTrue(flags.isActive("enable-international-welcome", context("environment", "qa", "user", "joe")));
        Assert.assertFalse(flags.isActive("enable-international-welcome", context("environment", "qa", "user", "anna")));
        Assert.assertFalse(flags.isActive("enable-international-welcome", context("environment", "production", "user", "joe")));
        Assert.assertFalse(flags.isActive("enable-international-welcome", context("user", "joe")));
        Assert.assertFalse(flags.isActive("enable-international-welcome", new ConfigurationContext()));

        Assert.assertTrue(flags.isActive("enable-everywhere", context("environment", "qa")));
        Assert.assertTrue(flags.isActive("enable-everywhere", new ConfigurationContext()));

        Assert.assertTrue(flags.isActive("enable-by-cluster", context("environment", "production", "cluster", "us-1")));
        Assert.assertFalse(flags.isActive("enable-by-cluster", context("environment", "production", "cluster", "eu-1", "user", "ben")));
        Assert.assertTrue(flags.isActive("enable-by-cluster", context("environment", "production", "cluster", "eu-1", "user", "anna")));
        Assert.assertFalse(flags.isActive("enable-by-cluster", context("environment", "qa", "cluster", "us-1")));

        Assert.assertTrue(flags.isActive("enable-with-empty-contexts", context("environment", "qa")));

        Assert.assertFalse(flags.isActive("not-defined", context("environment", "qa")));
        Assert.assertTrue(flags.isActive("not-defined", context("environment", "qa"), true));

        Evaluation<Boolean> matched = flags.evaluate("enable-by-cluster", context("user", "anna", "cluster", "eu-1", "environment", "production"));
        Assert.assertEquals(Boolean.TRUE, matched.getValue());
        Assert.assertEquals(Reason.MATCH, matched.getReason());
        Assert.assertEquals(Arrays.asList("environment", "cluster", "user"), Arrays.asList(matched.getMatches().keySet().toArray()));
        Assert.assertEquals(Reason.STATIC, flags.evaluate("enable-everywhere", context("environment", "qa")).getReason());
        Assert.assertEquals(Reason.DEFAULT, flags.evaluate("enable-with-empty-contexts", context("environment", "qa")).getReason());
        Assert.assertEquals(Reason.NOT_FOUND, flags.evaluate("not-defined", context("environment", "qa")).getReason());
    }

    @Test
    public void testTypedConfigsOfRelease020() throws IOException {
        ConfigManager configs = new ConfigManager();
        configs.updateConfigurations(new ConfigurationMapper<>("configs", false, objectMapper, new ConfigMetrics(), new DefaultClassFinder())
                .readValue(resource("0.2.0/configs.json")));

        Assert.assertEquals(1, configs.getConfig(NumberConfig.class, new ConfigurationContext()).getNumber());
        Assert.assertEquals(1, configs.getConfig(NumberConfig.class, context("environment", "production")).getNumber());
        Assert.assertEquals(2, configs.getConfig(NumberConfig.class, context("environment", "qa")).getNumber());
        Assert.assertEquals(2, configs.getConfig(NumberConfig.class, context("environment", "qa", "cluster", "us-1")).getNumber());
        Assert.assertEquals(3, configs.getConfig(NumberConfig.class, context("environment", "qa", "cluster", "eu-1")).getNumber());
        Assert.assertEquals(ImmutableMap.of("environment", "qa", "cluster", "eu-1"),
                configs.evaluate(NumberConfig.class, context("cluster", "eu-1", "environment", "qa")).getMatches());
    }

    @Test
    public void testJsonConfigsOfRelease020() throws IOException {
        JsonConfigManager jsonConfigs = new JsonConfigManager();
        jsonConfigs.updateConfigurations(new JsonConfigMapper("configs", false, objectMapper, new JsonConfigMetrics())
                .readValue(resource("0.2.0/json-configs.json")));

        Assert.assertEquals(10, jsonConfigs.getConfig("limits", new ConfigurationContext()).get("max").asInt());
        Assert.assertEquals(10, jsonConfigs.getConfig("limits", context("plan", "free")).get("max").asInt());
        Assert.assertEquals(100, jsonConfigs.getConfig("limits", context("plan", "pro")).get("max").asInt());
        Assert.assertEquals(2, jsonConfigs.getConfig("limits", context("plan", "pro")).get("hosts").size());
        Assert.assertEquals(0, jsonConfigs.getConfig("not-defined", context("plan", "pro")).size());
        Assert.assertEquals(Collections.singletonList("limits"), jsonConfigs.getConfigurationNames());
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = FileFormatCompatibilityTest.class.getResourceAsStream("/compatibility/" + name)) {
            Assert.assertNotNull("Missing frozen file " + name, stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
