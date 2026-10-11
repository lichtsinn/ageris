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
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.openfeature.AgerisProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Offers an OpenFeature provider for the feature flags and JSON configs of an application that has the OpenFeature
 * provider of Ageris and the OpenFeature SDK. Registering it with OpenFeature is left to the application.
 */
@AutoConfiguration(after = AgerisAutoConfiguration.class)
@ConditionalOnClass({AgerisProvider.class, FeatureProvider.class})
@ConditionalOnBean({FeatureFlagManager.class, JsonConfigManager.class})
public class AgerisOpenFeatureAutoConfiguration {

    /* Without a loader: it is started and stopped with the application, not by the provider. */
    @Bean
    @ConditionalOnMissingBean
    AgerisProvider agerisProvider(FeatureFlagManager featureFlagManager, JsonConfigManager jsonConfigManager, AgerisProperties properties) {
        return AgerisProvider.builder(featureFlagManager, jsonConfigManager).targetingKeyType(properties.openfeature().targetingKeyType()).build();
    }
}
