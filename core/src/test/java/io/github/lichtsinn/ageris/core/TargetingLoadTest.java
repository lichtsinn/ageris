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
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests that a file with a malformed rule is refused when it is loaded, with a message that names the entry and the problem.
 */
public class TargetingLoadTest {

    @Test
    public void testMalformedRulesAreRefusedWithTheNameOfTheEntry() {
        assertRefused("\"rules\": [ { \"startsWith\": \"eu-\", \"value\": true } ]", "Unknown key \"startsWith\" in a rule");
        assertRefused("\"rules\": [ { \"value\": true } ]", "Rule 1 for user has no match");
        assertRefused("\"rules\": [ { \"percent\": 10, \"value\": true }, { \"value\": true } ]", "Rule 2 for user has no match");
        assertRefused("\"rules\": [ { \"percent\": 10 } ]", "value");
        assertRefused("\"rules\": [ { \"percent\": 150, \"value\": true } ]", "\"percent\" must be between 0 and 100");
        assertRefused("\"rules\": [ { \"percent\": 0.125, \"value\": true } ]", "\"percent\" may have at most two decimals");
        assertRefused("\"rules\": [ { \"in\": [], \"value\": true } ]", "\"in\" needs at least one value");
        assertRefused("\"rules\": [ { \"in\": [\"anna\", 5], \"value\": true } ]", "\"in\" may only hold texts");
        assertRefused("\"rules\": [ { \"percent\": \"10\", \"value\": true } ]", "\"percent\" must be a number");
        assertRefused("\"rules\": [ null ]", "Rule 1 for user has no match");
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
        assertRefused("\"rules\": []", "Configuration modifiers of type user require non-null contexts");
        assertRefused("\"contexts\": null", "Configuration modifiers of type user require non-null contexts");
        assertRefused("\"contexts\": null, \"rules\": null", "Configuration modifiers of type user require non-null contexts");
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
            Assert.assertTrue(exception.getCause() instanceof IllegalArgumentException);
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(expectedInMessage));
        }
    }
}
