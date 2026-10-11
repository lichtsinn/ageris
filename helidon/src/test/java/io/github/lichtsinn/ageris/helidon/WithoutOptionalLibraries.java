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
package io.github.lichtsinn.ageris.helidon;

import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import java.util.Map;

/**
 * A small application for {@link WithoutOptionalLibrariesTest}, which runs it in a Java process of its own, without the
 * OpenFeature libraries and without the health library of Helidon. It uses both styles and says what it got.
 */
public final class WithoutOptionalLibraries {

    private WithoutOptionalLibraries() {
    }

    /**
     * @param args not used
     */
    public static void main(String[] args) {
        ConfigurationFetcher fetcher = new Files().fetcher();
        Map<String, String> settings = Map.of("ageris.application", "myapp", "ageris.feature-flags.files.0", "/featureflags.json");
        Config config = Config.just(ConfigSources.create(settings));

        try (AgerisHelidon ageris = AgerisHelidon.builder().config(config.get("ageris")).fetcher(fetcher).build()) {
            System.out.println("imperative: " + ageris.featureFlagManager().isActive("enable-checkout", new ConfigurationContext()));
        }

        ServiceRegistryManager manager = ServiceRegistryManager.start(ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config).putContractInstance(ConfigurationFetcher.class, fetcher).build());
        System.out.println("declarative: " + manager.registry().get(FeatureFlagManager.class).isActive("enable-checkout", new ConfigurationContext()));
        manager.shutdown();
    }
}
