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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Tests that a file with a malformed rule is refused when it is loaded, with a message that names the entry and the problem.
 */
public class TargetingLoadTest {

    @Test
    public void testMalformedRulesAreRefusedWithTheNameOfTheEntry() {
        assertRefused("\"rules\": [ { \"startsWith\": \"eu-\", \"value\": true } ]", "Unknown key \"startsWith\" in a rule");
        assertRefused("\"rules\": [ { \"value\": true } ]", "Rule 1 for user has no match");
        assertRefused("\"rules\": [ { \"percent\": 10, \"value\": true }, { \"value\": true } ]", "Rule 2 for user has no match");
        assertRefused("\"rules\": [ { \"percent\": 10 } ]", "A rule needs a \"value\"");
        assertRefused("\"rules\": [ { \"percent\": 10, \"value\": null } ]", "A rule needs a \"value\"");
        assertRefused("\"rules\": [ { \"percent\": 150, \"value\": true } ]", "\"percent\" must be between 0 and 100");
        assertRefused("\"rules\": [ { \"percent\": 0.125, \"value\": true } ]", "\"percent\" may have at most two decimals");
        assertRefused("\"rules\": [ { \"in\": [], \"value\": true } ]", "\"in\" needs at least one value");
        assertRefused("\"rules\": [ { \"in\": [\"anna\", 5], \"value\": true } ]", "\"in\" may only hold texts");
        assertRefused("\"rules\": [ { \"percent\": \"10\", \"value\": true } ]", "\"percent\" must be a number");
        assertRefused("\"rules\": [ null ]", "Rule 1 for user is null");
    }

    @Test
    public void testDecimalsAreCountedAsWrittenAndNotAsADoubleKeepsThem() throws IOException {
        assertRefused("\"rules\": [ { \"percent\": 0.10000000000000001, \"value\": true } ]", "\"percent\" may have at most two decimals");
        assertRefused("\"rules\": [ { \"percent\": 100.00000000000000001, \"value\": true } ]", "\"percent\" may have at most two decimals");
        assertRefused("\"contexts\": { \"joe\": { \"value\": true, \"modifiers\": { \"type\": \"plan\", \"rules\": ["
                + " { \"percent\": 9.999999999999999999, \"value\": true } ] } } }", "\"percent\" may have at most two decimals");
        Assert.assertEquals(1, load(flag("\"rules\": [ { \"percent\": 10.000, \"value\": true }, { \"percent\": 1e1, \"value\": true } ]")));
        /* A percentage inside the value of a rule belongs to the application. */
        Assert.assertEquals(1, new JsonConfigMapper("configs", false, new ObjectMapper(), new JsonConfigMetrics()).readValue(
                "{ \"configs\": { \"limits\": { \"value\": { }, \"modifiers\": { \"type\": \"user\", \"rules\": ["
                + " { \"in\": [\"anna\"], \"value\": { \"percent\": 0.12345 } } ] } } } }").size());
    }

    @Test
    public void testConfigWithoutAClassIsNamedToo() {
        try {
            new ConfigurationMapper<>("configs", false, new ObjectMapper(), new ConfigMetrics(), new DefaultClassFinder())
                    .readValue("{ \"configs\": { \"com.acme.Missing\": { \"value\": { } } } }");
            Assert.fail("Expected the file to be refused");
        } catch (IOException exception) {
            Assert.assertEquals("Cannot read com.acme.Missing: no class of that name was found", exception.getMessage());
            Assert.assertTrue(exception.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void testModifierNeedsContextsOrRules() {
        assertRefused("\"rules\": []", "Configuration modifiers of type user need \"contexts\" or at least one rule in \"rules\"");
        assertRefused("\"contexts\": null", "Configuration modifiers of type user need \"contexts\" or at least one rule in \"rules\"");
        assertRefused("\"contexts\": null, \"rules\": null", "Configuration modifiers of type user need \"contexts\" or at least one rule in \"rules\"");
    }

    @Test
    public void testMisspelledRulesKeyIsNotBlamedOnTheContextsAlone() {
        assertRefused("\"rule\": [ { \"percent\": 10, \"value\": true } ]", "need \"contexts\" or at least one rule in \"rules\"");
    }

    @Test
    public void testSameKeyTwiceInARuleIsRefused() {
        assertRefused("\"rules\": [ { \"percent\": 0, \"percent\": 100, \"value\": true } ]", "Key \"percent\" appears twice in a rule");
        assertRefused("\"rules\": [ { \"in\": [\"anna\"], \"value\": true, \"value\": false } ]", "Key \"value\" appears twice in a rule");
        assertRefused("\"rules\": [ { \"percent\": 5, \"value\": true }, { \"in\": [\"a\"], \"in\": [\"b\"], \"value\": true } ]",
                "Key \"in\" appears twice in a rule");
        /* A rule below a named entry, and a rule inside the modifiers of a rule. */
        assertRefused("\"contexts\": { \"joe\": { \"value\": true, \"modifiers\": { \"type\": \"plan\", \"rules\": ["
                + " { \"percent\": 1, \"percent\": 2, \"value\": true } ] } } }", "Key \"percent\" appears twice in a rule");
        assertRefused("\"rules\": [ { \"percent\": 5, \"value\": true, \"modifiers\": { \"type\": \"plan\", \"rules\": ["
                + " { \"in\": [\"pro\"], \"value\": true, \"comment\": \"one\", \"comment\": \"two\" } ] } } ]", "Key \"comment\" appears twice in a rule");
    }

    @Test
    public void testSameKeyTwiceOutsideARuleLoadsAsBefore() throws IOException {
        /* As in 0.2.0, where the last one counts; refusing it now would refuse files that load today. */
        Assert.assertEquals(1, load("{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"value\": true,"
                + " \"modifiers\": { \"type\": \"user\", \"type\": \"plan\", \"contexts\": { } } } } }"));
        /* Keys inside the value of a rule belong to the application, not to the rule. */
        Assert.assertEquals(1, new JsonConfigMapper("configs", false, new ObjectMapper(), new JsonConfigMetrics()).readValue(
                "{ \"configs\": { \"limits\": { \"value\": { }, \"modifiers\": { \"type\": \"user\", \"rules\": ["
                + " { \"in\": [\"anna\"], \"value\": { \"max\": 1, \"max\": 2, \"rules\": [ { \"max\": 1, \"max\": 2 } ] } } ] } } } }").size());
    }

    @Test
    public void testSameKeyTwiceInARuleSkipsOnlyThatEntryInSkipMode() throws IOException {
        String json = "{ \"feature-flags\": { \"good\": { \"value\": true }, \"bad\": { \"value\": false, \"modifiers\": { \"type\": \"user\","
                + " \"rules\": [ { \"percent\": 0, \"percent\": 100, \"value\": true } ] } } } }";

        Map<String, Configuration<Boolean>> loaded = new FeatureFlagMapper("feature-flags", true, new ObjectMapper(), new FeatureFlagMetrics()).readValue(json);

        Assert.assertEquals(Collections.singleton("good"), loaded.keySet());
    }

    @Test
    public void testSameKeyTwiceInARuleIsRefusedInYamlToo() {
        String yaml = "feature-flags:\n  enable-checkout:\n    value: false\n    modifiers:\n      type: user\n      rules:\n"
                + "        - percent: 0\n          percent: 100\n          value: true\n";
        try {
            new FeatureFlagMapper("feature-flags", false, new ObjectMapper(new YAMLFactory()), new FeatureFlagMetrics()).readValue(yaml);
            Assert.fail("Expected the file to be refused");
        } catch (IOException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void testCommentIsAllowedInModifiersAsEverywhereElse() throws IOException {
        Assert.assertEquals(1, load("{ \"feature-flags\": { \"enable-checkout\": { \"comment\": \"entry\", \"value\": false,"
                + " \"modifiers\": { \"comment\": \"by user\", \"type\": \"user\", \"contexts\": {"
                + " \"joe\": { \"comment\": \"context\", \"value\": true } } } } } }"));
    }

    @Test
    public void testNullCountsAsLeftOut() throws IOException {
        Assert.assertEquals(1, load(flag("\"contexts\": null, \"rules\": [ { \"in\": [\"anna\"], \"value\": true } ]")));
        Assert.assertEquals(1, load(flag("\"contexts\": { \"joe\": { \"value\": true } }, \"rules\": null")));
    }

    @Test
    public void testEmptyContextsStayValid() throws IOException {
        Assert.assertEquals(1, load(flag("\"contexts\": {}")));
    }

    @Test
    public void testRuleAfterEveryoneIsStillLoaded() throws IOException {
        /* Unreachable, which is logged as a warning, but not a reason to refuse the file. */
        Assert.assertEquals(1, load(flag("\"rules\": [ { \"percent\": 100, \"value\": true }, { \"in\": [\"anna\"], \"value\": false } ]")));
    }

    private static String flag(String restOfModifiers) {
        return "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": { \"type\": \"user\", "
                + restOfModifiers + " } } } }";
    }

    private static int load(String json) throws IOException {
        return new FeatureFlagMapper("feature-flags", false, new ObjectMapper(), new FeatureFlagMetrics()).readValue(json).size();
    }

    private static void assertRefused(String restOfModifiers, String expectedInMessage) {
        try {
            load(flag(restOfModifiers));
            Assert.fail("Expected the file to be refused: " + restOfModifiers);
        } catch (IOException exception) {
            Assert.assertTrue(exception.getMessage(), exception.getMessage().startsWith("Cannot read enable-checkout: "));
            Assert.assertFalse("One line: " + exception.getMessage(), exception.getMessage().contains("\n"));
            Assert.assertFalse("No place in a source that has none: " + exception.getMessage(), exception.getMessage().contains("Source: UNKNOWN"));
            Assert.assertTrue(exception.getCause() instanceof IllegalArgumentException);
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(expectedInMessage));
        }
    }
}
