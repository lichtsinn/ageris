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
package io.github.lichtsinn.ageris.spring;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.InMemoryAgeris;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests the OpenFeature provider the starter offers.
 */
public class AgerisOpenFeatureAutoConfigurationTest {

    private final Files files = new Files();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgerisAutoConfiguration.class, AgerisOpenFeatureAutoConfiguration.class))
            .withBean(ConfigurationFetcher.class, files::fetcher)
            .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json");

    @Test
    public void testProviderAnswersFromTheManagers() {
        runner.withPropertyValues("ageris.json-configs.files=/json-configs.json").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            AgerisProvider provider = context.getBean(AgerisProvider.class);

            Assert.assertTrue(provider.getBooleanEvaluation("enable-checkout", false, new ImmutableContext()).getValue());
            Assert.assertEquals("hello", provider.getStringEvaluation("greeting", "default", new ImmutableContext()).getValue());
        });
    }

    @Test
    public void testTargetingKeyTypeComesFromTheProperties() {
        files.put("/featureflags.json", "{ \"feature-flags\": { \"for-customers\": { \"value\": false, \"modifiers\": {"
                + " \"type\": \"customer\", \"contexts\": { \"acme\": { \"value\": true } } } } } }");

        runner.withPropertyValues("ageris.json-configs.files=/json-configs.json", "ageris.openfeature.targeting-key-type=customer").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertTrue(context.getBean(AgerisProvider.class).getBooleanEvaluation("for-customers", false, new ImmutableContext("acme")).getValue());
        });
    }

    @Test
    public void testNoProviderWithOnlyOneOfTheTwoManagers() {
        runner.run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertEquals(1, context.getBeanNamesForType(FeatureFlagManager.class).length);
            Assert.assertEquals(0, context.getBeanNamesForType(AgerisProvider.class).length);
        });
    }

    @Test
    public void testNoProviderWithoutTheProviderModule() {
        runner.withPropertyValues("ageris.json-configs.files=/json-configs.json").withClassLoader(new FilteredClassLoader(AgerisProvider.class))
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertEquals(0, context.getBeanNamesForType(FeatureProvider.class).length);
                });
    }

    @Test
    public void testNoProviderWithoutTheOpenFeatureSdk() {
        runner.withPropertyValues("ageris.json-configs.files=/json-configs.json").withClassLoader(new FilteredClassLoader(FeatureProvider.class))
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertEquals(1, context.getBeanNamesForType(FeatureFlagManager.class).length);
                });
    }

    @Test
    public void testApplicationWithItsOwnManagersAndNoPropertiesIsLeftAlone() {
        InMemoryAgeris own = new InMemoryAgeris();

        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AgerisAutoConfiguration.class, AgerisOpenFeatureAutoConfiguration.class))
                .withBean(FeatureFlagManager.class, own::featureFlagManager).withBean(JsonConfigManager.class, own::jsonConfigManager).run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertEquals(0, context.getBeanNamesForType(AgerisProvider.class).length);
                });
    }

    @Test
    public void testProviderOfTheApplicationWins() {
        InMemoryAgeris other = new InMemoryAgeris();
        AgerisProvider own = new AgerisProvider(other.featureFlagManager(), other.jsonConfigManager());

        runner.withPropertyValues("ageris.json-configs.files=/json-configs.json").withBean(AgerisProvider.class, () -> own).run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertSame(own, context.getBean(AgerisProvider.class));
        });
    }
}
