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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link Rule}. For the seed used here, user-9 is in bucket 293, user-15 in 1505, user-0 in 7962 and Zoë in 6525,
 * of 10,000.
 */
public class RuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SEED = Rule.seed("enable-checkout");

    @Test
    public void testInMatchesListedValuesOnly() throws IOException {
        Rule<Boolean> rule = rule("{ \"in\": [\"anna\", \"ben\"], \"value\": true }");

        Assert.assertTrue(rule.matches("anna", SEED));
        Assert.assertTrue(rule.matches("ben", SEED));
        Assert.assertFalse(rule.matches("Anna", SEED));
        Assert.assertFalse(rule.matches("carla", SEED));
        Assert.assertFalse(rule.matches("", SEED));
    }

    @Test
    public void testPercentUsesTheBucketOfTheValue() throws IOException {
        Assert.assertTrue(rule("{ \"percent\": 10, \"value\": true }").matches("user-9", SEED));
        Assert.assertFalse(rule("{ \"percent\": 10, \"value\": true }").matches("user-15", SEED));
        Assert.assertTrue(rule("{ \"percent\": 30, \"value\": true }").matches("user-15", SEED));
        Assert.assertFalse(rule("{ \"percent\": 30, \"value\": true }").matches("user-0", SEED));
    }

    @Test
    public void testPercentWithTwoDecimalsIsExact() throws IOException {
        /* user-9 is in bucket 293: inside 2.94 percent, outside 2.93 percent. */
        Assert.assertTrue(rule("{ \"percent\": 2.94, \"value\": true }").matches("user-9", SEED));
        Assert.assertFalse(rule("{ \"percent\": 2.93, \"value\": true }").matches("user-9", SEED));
        /* Other ways of writing ten. */
        Assert.assertTrue(rule("{ \"percent\": 10.00, \"value\": true }").matches("user-9", SEED));
        Assert.assertFalse(rule("{ \"percent\": 10.00, \"value\": true }").matches("user-15", SEED));
        Assert.assertTrue(rule("{ \"percent\": 1e1, \"value\": true }").matches("user-9", SEED));
        Assert.assertFalse(rule("{ \"percent\": 1e1, \"value\": true }").matches("user-15", SEED));
    }

    @Test
    public void testPercentZeroMatchesNobodyAndHundredEverybody() throws IOException {
        Rule<Boolean> nobody = rule("{ \"percent\": 0, \"value\": true }");
        Rule<Boolean> everybody = rule("{ \"percent\": 100, \"value\": true }");
        for (int user = 0; user < 1000; user++) {
            Assert.assertFalse(nobody.matches("user-" + user, SEED));
            Assert.assertTrue(everybody.matches("user-" + user, SEED));
        }
        Assert.assertTrue(everybody.matchesEveryone());
        Assert.assertFalse(nobody.matchesEveryone());
        Assert.assertFalse(rule("{ \"percent\": 100, \"in\": [\"anna\"], \"value\": true }").matchesEveryone());
    }

    @Test
    public void testRaisingAPercentageNeverRemovesACaller() throws IOException {
        List<Rule<Boolean>> rising = new ArrayList<>();
        for (int percentage : new int[] {1, 5, 10, 25, 50, 99}) {
            rising.add(rule("{ \"percent\": " + percentage + ", \"value\": true }"));
        }
        for (int user = 0; user < 5000; user++) {
            boolean matched = false;
            for (Rule<Boolean> rule : rising) {
                boolean matches = rule.matches("user-" + user, SEED);
                Assert.assertFalse("user-" + user + " dropped out", matched && !matches);
                matched = matches;
            }
        }
    }

    @Test
    public void testInAndPercentTogetherMustBothHold() throws IOException {
        Rule<Boolean> narrow = rule("{ \"in\": [\"user-15\"], \"percent\": 10, \"value\": true }");
        Rule<Boolean> wide = rule("{ \"in\": [\"user-15\"], \"percent\": 30, \"value\": true }");

        /* user-15 is listed but outside 10 percent; user-9 is inside 10 percent but not listed. */
        Assert.assertFalse(narrow.matches("user-15", SEED));
        Assert.assertFalse(narrow.matches("user-9", SEED));
        Assert.assertTrue(wide.matches("user-15", SEED));
        Assert.assertFalse(wide.matches("user-9", SEED));
    }

    @Test
    public void testValuesOutsideAsciiAreHashedAsUtf8() throws IOException {
        /* Zoë is in bucket 6525. */
        Assert.assertTrue(rule("{ \"percent\": 66, \"value\": true }").matches("Zoë", SEED));
        Assert.assertFalse(rule("{ \"percent\": 65, \"value\": true }").matches("Zoë", SEED));
    }

    @Test
    public void testSeedOfANullNameIsTheSeedOfAnEmptyOne() {
        Assert.assertEquals(Rule.seed(""), Rule.seed(null));
        Assert.assertEquals(1303119314, Rule.seed("enable-checkout"));
    }

    @Test
    public void testRuleHoldsANodeThatCanBranchFurther() throws IOException {
        Rule<Boolean> rule = rule("{ \"in\": [\"eu-1\"], \"value\": false, \"modifiers\":"
                + " { \"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true } } } }");
        ConfigurationContext joe = new ConfigurationContext();
        joe.put("user", "joe");

        Assert.assertEquals(Boolean.TRUE, rule.node().getValue(joe, SEED));
        Assert.assertEquals(Boolean.FALSE, rule.node().getValue(new ConfigurationContext(), SEED));
    }

    @Test
    public void testCommentIsIgnored() throws IOException {
        Assert.assertTrue(rule("{ \"comment\": \"first customers\", \"in\": [\"anna\"], \"value\": true }").matches("anna", SEED));
    }

    @Test
    public void testRuleWithoutAMatchIsBuiltButSaysSo() throws IOException {
        Assert.assertFalse(rule("{ \"value\": true }").hasMatch());
        Assert.assertTrue(rule("{ \"percent\": 0, \"value\": true }").hasMatch());
        Assert.assertTrue(rule("{ \"in\": [\"anna\"], \"value\": true }").hasMatch());
    }

    @Test
    public void testUnknownKeyIsNamed() {
        assertRefused(MAPPER, "{ \"startsWith\": \"eu-\", \"value\": true }", "Unknown key \"startsWith\" in a rule");
        assertRefused(MAPPER, "{ \"percnt\": 10, \"value\": true }", "Unknown key \"percnt\" in a rule");
    }

    @Test
    public void testUnknownKeyIsNamedEvenByALenientMapper() {
        ObjectMapper lenient = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        assertRefused(lenient, "{ \"startsWith\": \"eu-\", \"value\": true }", "Unknown key \"startsWith\" in a rule");
    }

    @Test
    public void testMalformedRulesAreRefused() {
        assertRefused(MAPPER, "{ \"in\": [], \"value\": true }", "\"in\" needs at least one value");
        assertRefused(MAPPER, "{ \"in\": [\"anna\", 5], \"value\": true }", "\"in\" may only hold texts");
        assertRefused(MAPPER, "{ \"percent\": 150, \"value\": true }", "\"percent\" must be between 0 and 100");
        assertRefused(MAPPER, "{ \"percent\": -1, \"value\": true }", "\"percent\" must be between 0 and 100");
        assertRefused(MAPPER, "{ \"percent\": 0.125, \"value\": true }", "\"percent\" may have at most two decimals");
        assertRefused(MAPPER, "{ \"percent\": \"10\", \"value\": true }", "\"percent\" must be a number in a rule, but was the text \"10\"");
        assertRefused(MAPPER, "{ \"in\": [\"anna\"], \"percent\": \"\", \"value\": true }", "\"percent\" must be a number");
        assertRefused(MAPPER, "{ \"percent\": true, \"value\": true }", "\"percent\" must be a number");
        assertRefused(MAPPER, "{ \"percent\": 1e400, \"value\": true }", "\"percent\" must be between 0 and 100");
        assertRefused(MAPPER, "{ \"in\": [\"anna\"] }", "value");
        assertRefused(MAPPER, "{ \"in\": [\"anna\"], \"value\": null }", "value");
    }

    @Test
    public void testRuleCanBeCreatedInCode() {
        Rule<Boolean> listed = new Rule<>(Arrays.asList("anna", "ben"), null, true, null);
        Rule<Boolean> whole = new Rule<>(null, 10, true, null);
        Rule<Boolean> fraction = new Rule<>(null, 2.94, true, null);
        Rule<Boolean> decimal = new Rule<>(null, new BigDecimal("2.93"), true, null);

        Assert.assertTrue(listed.matches("ben", SEED));
        Assert.assertFalse(listed.matches("carla", SEED));
        Assert.assertTrue(whole.matches("user-9", SEED));
        Assert.assertFalse(whole.matches("user-15", SEED));
        Assert.assertTrue(fraction.matches("user-9", SEED));
        Assert.assertFalse(decimal.matches("user-9", SEED));
    }

    @Test
    public void testRuleIsWrittenWithItsOwnKeysOnly() throws IOException {
        Assert.assertEquals("{\"in\":[\"anna\",\"ben\"],\"value\":true}",
                MAPPER.writeValueAsString(rule("{ \"in\": [\"anna\", \"ben\"], \"value\": true }")));
        Assert.assertEquals("{\"percent\":10,\"value\":true}", MAPPER.writeValueAsString(rule("{ \"percent\": 10, \"value\": true }")));
    }

    private static Rule<Boolean> rule(String json) throws IOException {
        return MAPPER.readValue(json, new TypeReference<Rule<Boolean>>() { });
    }

    private static void assertRefused(ObjectMapper mapper, String json, String expectedInMessage) {
        try {
            mapper.readValue(json, new TypeReference<Rule<Boolean>>() { });
            Assert.fail("Expected the rule to be refused: " + json);
        } catch (IOException exception) {
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(expectedInMessage));
        }
    }
}
