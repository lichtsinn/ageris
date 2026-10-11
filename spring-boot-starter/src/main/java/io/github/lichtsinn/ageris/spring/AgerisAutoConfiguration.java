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

import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.filesystem.ConfigurationFetcherMetrics;
import io.github.lichtsinn.ageris.core.filesystem.FilesystemConfigurationFetcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.FileSystems;

/**
 * Sets up Ageris from the properties of an application: the managers whose files are set, and a loader that is started
 * with the application and stopped with it. Without any files set, nothing is created.
 */
@AutoConfiguration
@ConditionalOnFiles({"feature-flags", "configs", "json-configs"})
@EnableConfigurationProperties(AgerisProperties.class)
public class AgerisAutoConfiguration {

    private static final String NO_FETCHER = "Set ageris.path to the directory of the configuration files, or define a ConfigurationFetcher bean";

    /**
     * @return a filter that keeps the setup of Ageris out of lazy initialization: an application that turns it on would
     *     otherwise start without loading anything, and meet a mistake in a file at its first lookup
     */
    @Bean
    static LazyInitializationExcludeFilter agerisSetupIsNeverLazy() {
        return LazyInitializationExcludeFilter.forBeanTypes(AgerisSetup.class);
    }

    /**
     * @param properties the properties of the application
     * @return a fetcher that reads the configuration files from the directory the application named
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("ageris.path")
    ConfigurationFetcher agerisConfigurationFetcher(AgerisProperties properties) {
        if (properties.path().isBlank()) {
            throw new IllegalStateException(NO_FETCHER);
        }
        return new FilesystemConfigurationFetcher(FileSystems.getDefault(), properties.path(), false, new ConfigurationFetcherMetrics());
    }

    /**
     * @param fetcher the fetcher of the application, or the one for its directory
     * @return the Ageris of the application
     */
    @Bean
    @ConditionalOnMissingBean
    Ageris ageris(ObjectProvider<ConfigurationFetcher> fetcher) {
        final ConfigurationFetcher available = fetcher.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(NO_FETCHER);
        }
        return new Ageris(available);
    }

    @Bean(destroyMethod = "close")
    AgerisSetup agerisSetup(Ageris ageris, AgerisProperties properties, ObjectProvider<AgerisCustomizer> customizers) {
        return new AgerisSetup(ageris, properties, customizers.orderedStream().toList());
    }

    /* The loader is stopped by the setup that started it, so Spring must not also call its shutdown method. */
    @Bean(destroyMethod = "")
    ConfigurationLoader agerisConfigurationLoader(AgerisSetup setup) {
        return setup.loader();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnFiles("feature-flags")
    FeatureFlagManager featureFlagManager(AgerisSetup setup) {
        return setup.featureFlagManager();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnFiles("configs")
    ConfigManager configManager(AgerisSetup setup) {
        return setup.configManager();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnFiles("json-configs")
    JsonConfigManager jsonConfigManager(AgerisSetup setup) {
        return setup.jsonConfigManager();
    }
}
