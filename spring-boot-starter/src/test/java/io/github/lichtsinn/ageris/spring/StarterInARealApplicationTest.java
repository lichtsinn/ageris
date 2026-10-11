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

import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts a small Spring Boot application the way one is started for real, with nothing but the starter on its classpath
 * and properties. This is what shows that Spring Boot finds the starter by itself.
 */
public class StarterInARealApplicationTest {

    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void testApplicationGetsALoadedManagerFromPropertiesAlone() throws IOException {
        Path application = directory.getRoot().toPath().resolve("myapp");
        Files.createDirectories(application);
        Files.write(application.resolve("featureflags.json"),
                "{ \"feature-flags\": { \"enable-checkout\": { \"value\": true } } }".getBytes(StandardCharsets.UTF_8));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SmallApplication.class).web(WebApplicationType.NONE)
                .properties("ageris.application=myapp", "ageris.path=" + directory.getRoot(), "ageris.feature-flags.files=/featureflags.json",
                        "spring.main.banner-mode=off")
                .run()) {
            Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("enable-checkout", new ConfigurationContext()));
            Assert.assertTrue("Health is there, since the health support of Spring Boot is on the classpath", context.containsBean("agerisHealthIndicator"));
        }
    }

    @Test
    public void testApplicationWithoutPropertiesIsLeftAlone() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SmallApplication.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off").run()) {
            Assert.assertEquals(0, context.getBeanNamesForType(FeatureFlagManager.class).length);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class SmallApplication {
    }
}
