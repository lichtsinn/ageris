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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.FeatureFlagMapper;
import io.github.lichtsinn.ageris.core.InMemoryAgeris;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.JsonConfigMapper;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.metrics.JsonConfigMetrics;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the lookups of {@link AgerisProvider} the way an application makes them: through an OpenFeature client. For the
 * feature flag enable-checkout, user-9 is inside 10 percent and user-15 is outside.
 */
public class AgerisProviderLookupTest {

    private static final String FLAGS = "{ \"feature-flags\": {"
            + " \"always-on\": { \"value\": true },"
            + " \"enable-checkout\": { \"value\": false, \"modifiers\": { \"type\": \"environment\", \"contexts\": { \"qa\": { \"value\": false,"
            + " \"modifiers\": { \"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true } },"
            + " \"rules\": [ { \"in\": [\"anna\"], \"value\": true }, { \"percent\": 10, \"value\": true } ] } } } } },"
            + " \"for-customers\": { \"value\": false, \"modifiers\": { \"type\": \"customer\", \"contexts\": { \"acme\": { \"value\": true } } } },"
            + " \"by-tier\": { \"value\": false, \"modifiers\": { \"type\": \"tier\", \"contexts\": { \"3\": { \"value\": true } } } },"
            + " \"shared-name\": { \"value\": true } } }";

    private static final String CONFIGS = "{ \"configs\": {"
            + " \"greeting\": { \"value\": \"hello\", \"modifiers\": { \"type\": \"user\", \"rules\": [ { \"in\": [\"anna\"], \"value\": \"hallo\" } ] } },"
            + " \"max-items\": { \"value\": 10, \"modifiers\": { \"type\": \"environment\", \"contexts\": { \"qa\": { \"value\": 99 } } } },"
            + " \"ratio\": { \"value\": 0.25 },"
            + " \"too-big\": { \"value\": 12345678901 },"
            + " \"limits\": { \"value\": { \"max\": 10, \"hosts\": [\"a\", \"b\"] } },"
            + " \"nothing\": { \"value\": null },"
            + " \"enabled\": { \"value\": true },"
            + " \"shared-name\": { \"value\": \"a text\" } } }";

    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    private final List<AgerisProvider> providers = new ArrayList<>();
    private Client client;

    @Before
    public void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FeatureFlagManager flags = new FeatureFlagManager();
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, mapper, new FeatureFlagMetrics()).readValue(FLAGS));
        JsonConfigManager configs = new JsonConfigManager();
        configs.updateConfigurations(new JsonConfigMapper("configs", false, mapper, new JsonConfigMetrics()).readValue(CONFIGS));
        client = client("lookups", new AgerisProvider(flags, configs));
    }

    /** Only the providers of this test are shut down: shutting the whole API down would also end its delivery of events. */
    @After
    public void tearDown() {
        providers.forEach(AgerisProvider::shutdown);
    }

    @Test
    public void testProviderIsNamedAgeris() {
        Assert.assertEquals("Ageris", new AgerisProvider(new FeatureFlagManager(), new JsonConfigManager()).getMetadata().getName());
    }

    @Test
    public void testBooleanReasons() {
        assertDetails(client.getBooleanDetails("always-on", false), true, "STATIC", null);
        assertDetails(client.getBooleanDetails("enable-checkout", true), false, "DEFAULT", null);
        assertDetails(client.getBooleanDetails("enable-checkout", false, context("joe", "environment", "qa")), true, "TARGETING_MATCH", null);
        assertDetails(client.getBooleanDetails("enable-checkout", true, context(null, "environment", "qa")), false, "TARGETING_MATCH", null);
    }

    @Test
    public void testBooleanThroughRules() {
        Assert.assertTrue(client.getBooleanValue("enable-checkout", false, context("anna", "environment", "qa")));
        Assert.assertTrue(client.getBooleanValue("enable-checkout", false, context("user-9", "environment", "qa")));
        Assert.assertFalse(client.getBooleanValue("enable-checkout", true, context("user-15", "environment", "qa")));
        Assert.assertFalse(client.getBooleanValue("enable-checkout", true, context("anna", "environment", "production")));
    }

    @Test
    public void testUnknownFlagReturnsTheDefaultAndSaysNotFound() {
        assertDetails(client.getBooleanDetails("nope", true), true, "ERROR", ErrorCode.FLAG_NOT_FOUND);
        assertDetails(client.getBooleanDetails("nope", false), false, "ERROR", ErrorCode.FLAG_NOT_FOUND);
        Assert.assertTrue(client.getBooleanDetails("nope", true).getErrorMessage().contains("nope"));
    }

    @Test
    public void testBooleanStoredAsAJsonConfigIsNotAFlag() {
        assertDetails(client.getBooleanDetails("enabled", false), false, "ERROR", ErrorCode.FLAG_NOT_FOUND);
    }

    @Test
    public void testNameUsedForAFlagAndAConfigAnswersBoth() {
        Assert.assertTrue(client.getBooleanValue("shared-name", false));
        Assert.assertEquals("a text", client.getStringValue("shared-name", "default"));
    }

    @Test
    public void testText() {
        assertDetails(client.getStringDetails("greeting", "default"), "hello", "DEFAULT", null);
        assertDetails(client.getStringDetails("greeting", "default", context("anna")), "hallo", "TARGETING_MATCH", null);
        assertDetails(client.getStringDetails("nope", "default"), "default", "ERROR", ErrorCode.FLAG_NOT_FOUND);
        assertDetails(client.getStringDetails("max-items", "default"), "default", "ERROR", ErrorCode.TYPE_MISMATCH);
        assertDetails(client.getStringDetails("limits", "default"), "default", "ERROR", ErrorCode.TYPE_MISMATCH);
        assertDetails(client.getStringDetails("nothing", "default"), "default", "ERROR", ErrorCode.TYPE_MISMATCH);
    }

    @Test
    public void testWholeNumber() {
        assertDetails(client.getIntegerDetails("max-items", -1), 10, "DEFAULT", null);
        assertDetails(client.getIntegerDetails("max-items", -1, context(null, "environment", "qa")), 99, "TARGETING_MATCH", null);
        assertDetails(client.getIntegerDetails("nope", -1), -1, "ERROR", ErrorCode.FLAG_NOT_FOUND);
        assertDetails(client.getIntegerDetails("ratio", -1), -1, "ERROR", ErrorCode.TYPE_MISMATCH);
        assertDetails(client.getIntegerDetails("too-big", -1), -1, "ERROR", ErrorCode.TYPE_MISMATCH);
        assertDetails(client.getIntegerDetails("greeting", -1), -1, "ERROR", ErrorCode.TYPE_MISMATCH);
    }

    @Test
    public void testDecimalNumber() {
        assertDetails(client.getDoubleDetails("ratio", -1.0), 0.25, "STATIC", null);
        assertDetails(client.getDoubleDetails("max-items", -1.0), 10.0, "DEFAULT", null);
        assertDetails(client.getDoubleDetails("too-big", -1.0), 12345678901.0, "STATIC", null);
        assertDetails(client.getDoubleDetails("greeting", -1.0), -1.0, "ERROR", ErrorCode.TYPE_MISMATCH);
        assertDetails(client.getDoubleDetails("nope", -1.0), -1.0, "ERROR", ErrorCode.FLAG_NOT_FOUND);
    }

    @Test
    public void testObject() {
        FlagEvaluationDetails<Value> limits = client.getObjectDetails("limits", new Value());

        Assert.assertEquals("STATIC", limits.getReason());
        Assert.assertEquals(Integer.valueOf(10), limits.getValue().asStructure().getValue("max").asInteger());
        Assert.assertEquals("b", limits.getValue().asStructure().getValue("hosts").asList().get(1).asString());
        Assert.assertEquals("hello", client.getObjectValue("greeting", new Value()).asString());
        Assert.assertEquals(Integer.valueOf(10), client.getObjectValue("max-items", new Value()).asInteger());
        Assert.assertTrue(client.getObjectValue("nothing", new Value("default")).isNull());
        Assert.assertEquals(ErrorCode.FLAG_NOT_FOUND, client.getObjectDetails("nope", new Value("default")).getErrorCode());
        Assert.assertEquals("default", client.getObjectValue("nope", new Value("default")).asString());
    }

    @Test
    public void testMismatchSaysWhatWasThere() {
        String message = client.getStringDetails("max-items", "default").getErrorMessage();

        Assert.assertTrue(message, message.contains("max-items"));
        Assert.assertTrue(message, message.contains("a text"));
        Assert.assertTrue(message, message.contains("number"));
    }

    @Test
    public void testNoVariantAndNoMetadata() {
        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("enable-checkout", false, context("joe", "environment", "qa"));

        Assert.assertNull(details.getVariant());
        Assert.assertTrue(details.getFlagMetadata() == null || details.getFlagMetadata().isEmpty());
    }

    @Test
    public void testTargetingKeyTypeCanBeChanged() {
        InMemoryAgeris unused = new InMemoryAgeris();
        FeatureFlagManager flags = new FeatureFlagManager();
        try {
            flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, new ObjectMapper(), new FeatureFlagMetrics()).readValue(FLAGS));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        Client customers = client("customers", AgerisProvider.builder(flags, unused.jsonConfigManager()).targetingKeyType("customer").build());

        Assert.assertTrue(customers.getBooleanValue("for-customers", false, new ImmutableContext("acme")));
        Assert.assertFalse(client.getBooleanValue("for-customers", true, new ImmutableContext("acme")));
    }

    @Test
    public void testNumberAttributeMatchesAsItsText() {
        Assert.assertTrue(client.getBooleanValue("by-tier", false, new ImmutableContext(Collections.singletonMap("tier", new Value(3)))));
        Assert.assertTrue(client.getBooleanValue("by-tier", false, new ImmutableContext(Collections.singletonMap("tier", new Value(3.0)))));
        Assert.assertFalse(client.getBooleanValue("by-tier", true, new ImmutableContext(Collections.singletonMap("tier", new Value(4)))));
    }

    @Test
    public void testInMemoryManagersWork() {
        InMemoryAgeris ageris = new InMemoryAgeris().setFlag("x", true).setJsonConfig("color", new ObjectMapper().getNodeFactory().textNode("red"));
        Client memory = client("memory", new AgerisProvider(ageris.featureFlagManager(), ageris.jsonConfigManager()));

        Assert.assertTrue(memory.getBooleanValue("x", false));
        Assert.assertEquals("red", memory.getStringValue("color", "default"));
        ageris.setFlag("x", false);
        Assert.assertFalse(memory.getBooleanValue("x", true));
    }

    @Test(expected = NullPointerException.class)
    public void testNullManagerIsRefused() {
        new AgerisProvider(null, new JsonConfigManager());
    }

    @Test(expected = NullPointerException.class)
    public void testNullLoaderIsRefusedByTheConstructorThatTakesOne() {
        new AgerisProvider(new FeatureFlagManager(), new JsonConfigManager(), null);
    }

    private Client client(String domain, AgerisProvider provider) {
        providers.add(provider);
        api.setProviderAndWait(domain, provider);
        return api.getClient(domain);
    }

    private static EvaluationContext context(String targetingKey, String... namesAndValues) {
        Map<String, Value> attributes = new HashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            attributes.put(namesAndValues[index], new Value(namesAndValues[index + 1]));
        }
        return targetingKey == null ? new ImmutableContext(attributes) : new ImmutableContext(targetingKey, attributes);
    }

    private static <T> void assertDetails(FlagEvaluationDetails<T> details, T value, String reason, ErrorCode errorCode) {
        Assert.assertEquals(value, details.getValue());
        Assert.assertEquals(reason, details.getReason());
        Assert.assertEquals(errorCode, details.getErrorCode());
    }
}
