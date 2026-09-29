/*
 * Copyright 2018 Medallia, Inc.
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
package io.github.lichtsinn.ageris.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.filesystem.ConfigurationFetcherMetrics;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import io.github.lichtsinn.ageris.core.filesystem.FilesystemConfigurationFetcher;
import io.github.lichtsinn.ageris.web.environment.Environment;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Example of Jetty based application for 'Ageris as a Service'.
 */
public class JettyRunner {

    /**
     * Main method of the application, initializes Ageris runner and starts it.
     *
     * @param args command-line arguments
     * @throws Exception thrown during start of Jetty server
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public static void main(String[] args) throws Exception {
        Environment environment = new Environment("local", System.getenv(), System.getProperties());
        URI resource = Thread.currentThread().getContextClassLoader() .getResource("configurations").toURI();
        String path = Paths.get(resource).toAbsolutePath().toString();
        ConfigurationFetcher fetcher = new FilesystemConfigurationFetcher(FileSystems.getDefault(), path,true, new ConfigurationFetcherMetrics());

        Ageris ageris = new Ageris(fetcher);
        ageris.setMaximumSkips(0);
        ageris.skipNonInstantiableConfiguration();

        FeatureFlagManager featureFlagManager = ageris.addFeatureFlagManager("ageris")
                .registerFile("/featureflags.json").build();
        JsonConfigManager jsonConfigManager = ageris.addJsonConfigManager("ageris")
                .registerFile("/configs.json").build();
        ConfigurationLoader loader = ageris.createLoader(Duration.ofSeconds(10));

        AgerisRunner runner = new AgerisRunner(environment, new ObjectMapper(), loader, featureFlagManager, jsonConfigManager);
        runner.start();
    }
}
