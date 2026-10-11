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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * The properties of an application that set Ageris up.
 *
 * @param application Name of the application, as the fetcher of the configuration files uses it.
 * @param path Directory that holds the configuration files. Not needed if the application defines a ConfigurationFetcher bean.
 * @param refreshInterval Time between two refreshes of the configuration files.
 * @param firstLoadTimeout How long the start of the application waits for the first load of the configuration files.
 * @param failOnFirstLoadFailure Whether the application fails to start when the first load fails or takes too long.
 * @param featureFlags The feature flag manager.
 * @param configs The config manager, for configs of the application's own classes and for policies.
 * @param jsonConfigs The JSON config manager.
 * @param openfeature The OpenFeature provider.
 */
@ConfigurationProperties("ageris")
@SuppressWarnings("PMD.LongVariable") // The names are the names of the properties, and those are better long than unclear.
public record AgerisProperties(String application,
                               String path,
                               @DefaultValue("10s") Duration refreshInterval,
                               @DefaultValue("30s") Duration firstLoadTimeout,
                               @DefaultValue("true") boolean failOnFirstLoadFailure,
                               @DefaultValue Files featureFlags,
                               @DefaultValue Files configs,
                               @DefaultValue Files jsonConfigs,
                               @DefaultValue OpenFeature openfeature) {

    /**
     * The files of one manager.
     *
     * @param files Names of the configuration files of this manager, such as /featureflags.json. The manager only exists if this is set.
     */
    public record Files(@DefaultValue List<String> files) {

        /**
         * Creates the files of one manager.
         *
         * @param files names of the configuration files
         */
        public Files {
            files = List.copyOf(files);
        }
    }

    /**
     * Settings of the OpenFeature provider.
     *
     * @param targetingKeyType Context type that the targeting key of an OpenFeature caller is put under.
     */
    public record OpenFeature(@DefaultValue("user") String targetingKeyType) {
    }
}
