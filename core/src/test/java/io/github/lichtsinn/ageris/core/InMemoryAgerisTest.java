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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.common.EnvironmentConfigurationContext;
import io.github.lichtsinn.ageris.core.configs.DiscountConfig;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.configs.PriceSource;
import io.github.lichtsinn.ageris.core.exception.ConfigInstantiationException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link InMemoryAgeris}.
 */
public class InMemoryAgerisTest {

    private final InMemoryAgeris ageris = new InMemoryAgeris();
    private final ConfigurationContext any = new ConfigurationContext();
    private final ConfigurationContext qa = new EnvironmentConfigurationContext("qa");

    /** A config whose own class is not the class the code under test asks for. */
    private static final class SpecialNumberConfig extends NumberConfig {
        SpecialNumberConfig() {
            super(99);
        }
    }

    @Test
    public void testFlagIsOnAfterItWasSetOn() {
        ageris.setFlag("enable-checkout", true);

        Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", any));
    }

    @Test
    public void testFlagIsOffAfterItWasSetOffEvenWithADefaultOfOn() {
        ageris.setFlag("enable-checkout", false);

        Assert.assertFalse(ageris.featureFlagManager().isActive("enable-checkout", any, true));
    }

    @Test
    public void testFlagCanBeChanged() {
        ageris.setFlag("enable-checkout", true);

        ageris.setFlag("enable-checkout", false);

        Assert.assertFalse(ageris.featureFlagManager().isActive("enable-checkout", any));
    }

    @Test
    public void testUnsetFlagIsOffAndHonorsTheCallersDefault() {
        Assert.assertFalse(ageris.featureFlagManager().isActive("enable-checkout", any));
        Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", any, true));
    }

    @Test
    public void testRemovedFlagFallsBackToTheCallersDefault() {
        ageris.setFlag("enable-checkout", false);

        ageris.removeFlag("enable-checkout");

        Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", any, true));
        Assert.assertEquals(Collections.emptyList(), ageris.featureFlagManager().getConfigurationNames());
    }

    @Test
    public void testFlagAppliesToEveryContext() {
        ageris.setFlag("enable-checkout", true);

        Assert.assertTrue(ageris.featureFlagManager().isActive("enable-checkout", qa));
    }

    @Test
    public void testConfigIsReturnedAsTheSameObject() {
        NumberConfig config = new NumberConfig(7);

        ageris.setConfig(config);

        Assert.assertSame(config, ageris.configManager().getConfig(NumberConfig.class, any));
    }

    @Test
    public void testConfigCanBeReplaced() {
        ageris.setConfig(new NumberConfig(7));

        ageris.setConfig(new NumberConfig(8));

        Assert.assertEquals(8, ageris.configManager().getConfig(NumberConfig.class, any).getNumber());
        Assert.assertEquals(Collections.singletonList(NumberConfig.class.getName()), ageris.configManager().getConfigurationNames());
    }

    @Test
    public void testUnsetOrRemovedConfigComesFromItsNoArgumentConstructor() {
        Assert.assertEquals(-1, ageris.configManager().getConfig(NumberConfig.class, any).getNumber());

        ageris.setConfig(new NumberConfig(7));
        ageris.removeConfig(NumberConfig.class);

        Assert.assertEquals(-1, ageris.configManager().getConfig(NumberConfig.class, any).getNumber());
    }

    @Test
    public void testConfigAppliesToEveryContext() {
        ageris.setConfig(new NumberConfig(7));

        Assert.assertEquals(7, ageris.configManager().getConfig(NumberConfig.class, qa).getNumber());
    }

    @Test
    public void testPolicyBuiltWithAFakeBindingIsReturnedAndUsesIt() {
        DiscountConfig policy = new DiscountConfig(25, new PriceSource(200));

        ageris.setConfig(policy);

        Assert.assertSame(policy, ageris.configManager().getConfig(DiscountConfig.class, any));
        Assert.assertEquals(150, ageris.configManager().getConfig(DiscountConfig.class, any).discountedPrice());
    }

    @Test(expected = ConfigInstantiationException.class)
    public void testRemovedPolicyWithoutNoArgumentConstructorFailsAsInProduction() {
        ageris.setConfig(new DiscountConfig(25, new PriceSource(200)));
        ageris.removeConfig(DiscountConfig.class);

        ageris.configManager().getConfig(DiscountConfig.class, any);
    }

    @Test
    public void testConfigSetUnderANamedTypeIsFoundUnderThatType() {
        SpecialNumberConfig special = new SpecialNumberConfig();

        ageris.setConfig(NumberConfig.class, special);

        Assert.assertSame(special, ageris.configManager().getConfig(NumberConfig.class, any));
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testConfigThatIsNotAnInstanceOfItsTypeIsRejected() {
        ageris.setConfig((Class) NumberConfig.class, "not a number config");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassPassedAsConfigIsRejected() {
        ageris.setConfig(NumberConfig.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnonymousSubclassPassedWithoutItsTypeIsRejected() {
        ageris.setConfig(new NumberConfig(42) { });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMockPassedWithoutItsTypeIsRejected() {
        ageris.setConfig(Mockito.mock(NumberConfig.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLambdaPassedWithoutItsTypeIsRejected() {
        Runnable config = () -> { };
        ageris.setConfig(config);
    }

    @Test
    public void testMockPassedWithItsTypeIsFound() {
        NumberConfig mock = Mockito.mock(NumberConfig.class);
        Mockito.when(mock.getNumber()).thenReturn(42);

        ageris.setConfig(NumberConfig.class, mock);

        Assert.assertEquals(42, ageris.configManager().getConfig(NumberConfig.class, any).getNumber());
    }

    @Test
    public void testJsonConfigIsReturned() {
        JsonNode node = JsonNodeFactory.instance.objectNode().put("hosts", 3);

        ageris.setJsonConfig("db", node);

        Assert.assertSame(node, ageris.jsonConfigManager().getConfig("db", any));
        Assert.assertSame(node, ageris.jsonConfigManager().getConfig("db", qa));
    }

    @Test
    public void testJsonConfigCanBeReplacedAndRemoved() {
        ageris.setJsonConfig("db", JsonNodeFactory.instance.objectNode().put("hosts", 3));

        ageris.setJsonConfig("db", JsonNodeFactory.instance.objectNode().put("hosts", 5));
        Assert.assertEquals(5, ageris.jsonConfigManager().getConfig("db", any).get("hosts").asInt());

        ageris.removeJsonConfig("db");
        Assert.assertEquals(0, ageris.jsonConfigManager().getConfig("db", any).size());
    }

    @Test
    public void testListenerIsCalledOncePerChangingCall() {
        List<List<String>> seen = new ArrayList<>();
        ageris.featureFlagManager().addListener(update -> seen.add(update.getConfigurationNames()));

        ageris.setFlag("first", true);
        ageris.setFlag("second", true);
        ageris.setFlag("first", true);
        ageris.removeFlag("first");

        Assert.assertEquals(Arrays.asList(
                Collections.singletonList("first"),
                Arrays.asList("first", "second"),
                Arrays.asList("first", "second"),
                Collections.singletonList("second")), seen);
    }

    @Test
    public void testRemovingAnAbsentEntryCallsNoListener() {
        List<String> calls = new ArrayList<>();
        ageris.featureFlagManager().addListener(update -> calls.add("flags"));
        ageris.configManager().addListener(update -> calls.add("configs"));
        ageris.jsonConfigManager().addListener(update -> calls.add("json"));

        ageris.removeFlag("absent");
        ageris.removeConfig(NumberConfig.class);
        ageris.removeJsonConfig("absent");

        Assert.assertEquals(Collections.emptyList(), calls);
    }

    @Test
    public void testACallConcernsOnlyItsOwnManager() {
        List<String> calls = new ArrayList<>();
        ageris.featureFlagManager().addListener(update -> calls.add("flags"));
        ageris.configManager().addListener(update -> calls.add("configs"));
        ageris.jsonConfigManager().addListener(update -> calls.add("json"));

        ageris.setFlag("enable-checkout", true);
        ageris.setConfig(new NumberConfig(7));
        ageris.setJsonConfig("db", JsonNodeFactory.instance.objectNode());

        Assert.assertEquals(Arrays.asList("flags", "configs", "json"), calls);
    }

    @Test(timeout = 10000)
    public void testListenerMaySetAnotherValue() {
        ageris.featureFlagManager().addListener(update -> ageris.setConfig(new NumberConfig(update.getConfigurationNames().size())));

        ageris.setFlag("first", true);
        ageris.setFlag("second", true);

        Assert.assertEquals(2, ageris.configManager().getConfig(NumberConfig.class, any).getNumber());
    }

    @Test
    public void testValueThatWasSetIsStaticAndOneThatWasNotIsNotFound() {
        ageris.setFlag("enable-checkout", true);

        Assert.assertEquals(Evaluation.Reason.STATIC, ageris.featureFlagManager().evaluate("enable-checkout", qa).getReason());
        Assert.assertEquals(Evaluation.Reason.NOT_FOUND, ageris.featureFlagManager().evaluate("enable-search", qa).getReason());
    }

    @Test
    public void testSettingValuesReportsNoStatusChange() {
        List<State> states = new ArrayList<>();
        ageris.featureFlagManager().addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                states.add(status.getState());
            }
        });

        ageris.setFlag("enable-checkout", true).removeFlag("enable-checkout");

        Assert.assertEquals(Collections.emptyList(), states);
    }

    @Test
    public void testManagersAreLoadedFromTheStart() {
        Assert.assertEquals(State.LOADED, ageris.featureFlagManager().getStatus().getState());
        Assert.assertEquals(State.LOADED, ageris.configManager().getStatus().getState());
        Assert.assertEquals(State.LOADED, ageris.jsonConfigManager().getStatus().getState());
    }

    @Test
    public void testGettersReturnTheSameManagersEveryTime() {
        FeatureFlagManager flags = ageris.featureFlagManager();
        ConfigManager configs = ageris.configManager();
        JsonConfigManager jsonConfigs = ageris.jsonConfigManager();

        ageris.setFlag("enable-checkout", true).setConfig(new NumberConfig(7));

        Assert.assertSame(flags, ageris.featureFlagManager());
        Assert.assertSame(configs, ageris.configManager());
        Assert.assertSame(jsonConfigs, ageris.jsonConfigManager());
        Assert.assertTrue(flags.isActive("enable-checkout", any));
    }

    @Test
    public void testEntriesKeepTheOrderInWhichTheyWereFirstSet() {
        ageris.setFlag("zebra", true).setFlag("apple", true).setFlag("zebra", false);

        Assert.assertEquals(Arrays.asList("zebra", "apple"), ageris.featureFlagManager().getConfigurationNames());
    }

    @Test
    public void testCallsCanBeChained() {
        InMemoryAgeris chained = new InMemoryAgeris()
                .setFlag("enable-checkout", true)
                .setConfig(new NumberConfig(7))
                .setConfig(NumberConfig.class, new SpecialNumberConfig())
                .setJsonConfig("db", JsonNodeFactory.instance.objectNode())
                .removeFlag("enable-checkout")
                .removeJsonConfig("db")
                .removeConfig(DiscountConfig.class);

        Assert.assertEquals(99, chained.configManager().getConfig(NumberConfig.class, any).getNumber());
        Assert.assertFalse(chained.featureFlagManager().isActive("enable-checkout", any));
    }

    @Test
    public void testTwoHelpersAreIndependent() {
        InMemoryAgeris other = new InMemoryAgeris();

        ageris.setFlag("enable-checkout", true);
        /* Applies the other helper's own entries, which would include the flag above if the two shared any. */
        other.setFlag("enable-search", true);

        Assert.assertFalse(other.featureFlagManager().isActive("enable-checkout", any));
        Assert.assertEquals(Collections.singletonList("enable-search"), other.featureFlagManager().getConfigurationNames());
        Assert.assertEquals(Collections.singletonList("enable-checkout"), ageris.featureFlagManager().getConfigurationNames());
        Assert.assertNotSame(ageris.featureFlagManager(), other.featureFlagManager());
    }

    @Test
    public void testConcurrentSetsLoseNoEntry() throws InterruptedException, ExecutionException {
        int threads = 8;
        int flagsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int id = thread;
            results.add(executor.submit(() -> {
                start.await();
                for (int flag = 0; flag < flagsPerThread; flag++) {
                    ageris.setFlag("flag-" + id + "-" + flag, true);
                }
                return null;
            }));
        }
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        for (Future<Object> result : results) {
            /* Rethrows whatever a task threw, which a wrong count alone would not explain. */
            result.get();
        }

        Assert.assertEquals(threads * flagsPerThread, ageris.featureFlagManager().getConfigurationNames().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullFlagNameIsRejected() {
        ageris.setFlag(null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullFlagNameIsRejectedOnRemoval() {
        ageris.removeFlag(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullConfigIsRejected() {
        ageris.setConfig((Object) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullConfigTypeIsRejected() {
        ageris.setConfig(null, new NumberConfig(7));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullConfigForATypeIsRejected() {
        ageris.setConfig(NumberConfig.class, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullConfigTypeIsRejectedOnRemoval() {
        ageris.removeConfig(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullJsonConfigNameIsRejected() {
        ageris.setJsonConfig(null, JsonNodeFactory.instance.objectNode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullJsonConfigIsRejected() {
        ageris.setJsonConfig("db", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullJsonConfigNameIsRejectedOnRemoval() {
        ageris.removeJsonConfig(null);
    }
}
