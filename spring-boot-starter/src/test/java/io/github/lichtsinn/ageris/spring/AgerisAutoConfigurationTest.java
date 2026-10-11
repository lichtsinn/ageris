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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lichtsinn.ageris.core.Ageris;
import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Tests what the starter sets up from the properties and beans of an application.
 */
public class AgerisAutoConfigurationTest {

    private static final ConfigurationContext NO_CONTEXT = new ConfigurationContext();
    private static final Files FILES = new Files();
    private static final List<String> CALLS = new ArrayList<>();

    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgerisAutoConfiguration.class));

    @Test
    public void testNothingIsCreatedWithoutProperties() {
        runner.run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertEquals(0, context.getBeanNamesForType(Ageris.class).length);
            Assert.assertEquals(0, context.getBeanNamesForType(FeatureFlagManager.class).length);
            Assert.assertEquals(0, context.getBeanNamesForType(ConfigurationLoader.class).length);
        });
    }

    @Test
    public void testNothingIsCreatedWithoutFiles() {
        runner.withPropertyValues("ageris.application=myapp", "ageris.path=/does/not/exist").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertEquals(0, context.getBeanNamesForType(Ageris.class).length);
        });
    }

    @Test
    public void testFilesInADirectory() throws IOException {
        write("featureflags.json", Files.FLAGS);
        write("json-configs.json", Files.JSON_CONFIGS);

        runner.withPropertyValues("ageris.application=myapp", "ageris.path=" + directory.getRoot(),
                "ageris.feature-flags.files=/featureflags.json", "ageris.json-configs.files=/json-configs.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("enable-checkout", NO_CONTEXT));
                    Assert.assertEquals("hello", context.getBean(JsonConfigManager.class).getConfig("greeting", NO_CONTEXT).asText());
                    Assert.assertEquals(State.LOADED, context.getBean(FeatureFlagManager.class).getStatus().getState());
                    Assert.assertEquals("No files for it, no manager", 0, context.getBeanNamesForType(ConfigManager.class).length);
                    Assert.assertEquals(1, context.getBeanNamesForType(ConfigurationLoader.class).length);
                });
    }

    @Test
    public void testFilesWrittenAsAListAndMoreThanOneFile() {
        FILES.put("/more.json", "{ \"feature-flags\": { \"second\": { \"value\": true } } }");

        withFetcher().withPropertyValues("ageris.application=myapp",
                "ageris.feature-flags.files[0]=/featureflags.json", "ageris.feature-flags.files[1]=/more.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("enable-checkout", NO_CONTEXT));
                    Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("second", NO_CONTEXT));
                });
    }

    @Test
    public void testFetcherOfTheApplicationNeedsNoDirectory() {
        withFetcher().withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("enable-checkout", NO_CONTEXT));
            Assert.assertEquals(1, context.getBeanNamesForType(ConfigurationFetcher.class).length);
        });
    }

    @Test
    public void testFetcherOfTheApplicationWinsOverTheDirectory() {
        withFetcher().withPropertyValues("ageris.application=myapp", "ageris.path=/does/not/exist",
                "ageris.feature-flags.files=/featureflags.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertTrue(context.getBean(FeatureFlagManager.class).isActive("enable-checkout", NO_CONTEXT));
                });
    }

    @Test
    public void testMissingApplicationIsNamed() {
        withFetcher().withPropertyValues("ageris.feature-flags.files=/featureflags.json")
                .run(context -> assertFailure(context.getStartupFailure(), "ageris.application"));
    }

    @Test
    public void testMissingDirectoryIsNamed() {
        runner.withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json")
                .run(context -> assertFailure(context.getStartupFailure(), "ageris.path"));
    }

    @Test
    public void testCustomizerBindsWhatAPolicyNeeds() {
        withFetcher().withUserConfiguration(Bindings.class)
                .withPropertyValues("ageris.application=myapp", "ageris.configs.files=/configs.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertEquals(150, context.getBean(ConfigManager.class).getConfig(Discount.class, NO_CONTEXT).price());
                });
    }

    @Test
    public void testPolicyWithoutItsBindingStopsTheStart() {
        withFetcher().withPropertyValues("ageris.application=myapp", "ageris.configs.files=/configs.json")
                .run(context -> assertFailure(context.getStartupFailure(), "No binding for"));
    }

    @Test
    public void testCustomizersRunInOrderBeforeTheManagersAreBuilt() {
        CALLS.clear();
        withFetcher().withUserConfiguration(Bindings.class, OrderedCustomizers.class)
                .withPropertyValues("ageris.application=myapp", "ageris.configs.files=/configs.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertEquals(List.of("first", "second"), CALLS);
                });
    }

    @Test
    public void testCustomizerThatFailsStopsTheStart() {
        withFetcher().withBean(AgerisCustomizer.class, () -> ageris -> {
            throw new IllegalArgumentException("a customizer that fails");
        }).withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json")
                .run(context -> assertFailure(context.getStartupFailure(), "a customizer that fails"));
    }

    @Test
    public void testAgerisOfTheApplicationIsUsedAndStillCustomized() {
        Ageris own = new Ageris(FILES.fetcher());

        runner.withBean(Ageris.class, () -> own).withUserConfiguration(Bindings.class)
                .withPropertyValues("ageris.application=myapp", "ageris.configs.files=/configs.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertSame(own, context.getBean(Ageris.class));
                    Assert.assertEquals(150, context.getBean(ConfigManager.class).getConfig(Discount.class, NO_CONTEXT).price());
                });
    }

    @Test
    public void testBeanCanUseAManagerInItsConstructor() {
        withFetcher().withUserConfiguration(EarlyReader.class)
                .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertTrue("The manager was loaded before the bean got it", context.getBean(EarlyReader.class).seenAtStart);
                });
    }

    @Test
    public void testFailedFirstLoadStopsTheStartAndSaysWhy() {
        Files files = new Files();
        files.put("/featureflags.json", Files.BAD_FLAGS);

        runner.withBean(ConfigurationFetcher.class, files::fetcher)
                .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json", "ageris.first-load-timeout=50ms")
                .run(context -> {
                    assertFailure(context.getStartupFailure(), "Cannot read enable-checkout: ");
                    assertFailure(context.getStartupFailure(), "/featureflags.json");
                    assertFailure(context.getStartupFailure(), "ageris.fail-on-first-load-failure=false");
                });
    }

    @Test
    public void testApplicationCanStartWithoutAFirstLoadAndRecovers() {
        Files files = new Files();
        files.put("/featureflags.json", Files.BAD_FLAGS);

        runner.withBean(ConfigurationFetcher.class, files::fetcher)
                .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json", "ageris.first-load-timeout=50ms",
                        "ageris.fail-on-first-load-failure=false", "ageris.refresh-interval=20ms")
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    FeatureFlagManager flags = context.getBean(FeatureFlagManager.class);
                    Assert.assertEquals(State.NOT_LOADED, flags.getStatus().getState());
                    Assert.assertFalse("A default until a load succeeds", flags.isActive("enable-checkout", NO_CONTEXT));

                    files.put("/featureflags.json", Files.FLAGS);

                    Assert.assertTrue("The loader kept trying", context.getBean(ConfigurationLoader.class).awaitFirstLoad(Duration.ofSeconds(10)));
                    Assert.assertTrue(flags.isActive("enable-checkout", NO_CONTEXT));
                });
    }

    @Test
    public void testLoaderIsShutDownWhenTheApplicationStops() {
        ScheduledExecutorService executor = executorThatRunsTheFirstRefresh();
        Ageris own = new Ageris(FILES.fetcher(), executor, new ObjectMapper(), sha256());

        runner.withBean(Ageris.class, () -> own)
                .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Mockito.verify(executor, Mockito.never()).shutdown();
                });

        Mockito.verify(executor, Mockito.times(1)).shutdown();
    }

    @Test
    public void testLoaderIsShutDownWhenTheFirstLoadFails() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Ageris own = new Ageris(FILES.fetcher(), executor, new ObjectMapper(), sha256());

        runner.withBean(Ageris.class, () -> own)
                .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json", "ageris.first-load-timeout=50ms")
                .run(context -> assertFailure(context.getStartupFailure(), "were not loaded within PT0.05S"));

        Mockito.verify(executor, Mockito.times(1)).shutdown();
    }

    private ApplicationContextRunner withFetcher() {
        return runner.withBean(ConfigurationFetcher.class, FILES::fetcher);
    }

    private void write(String fileName, String content) throws IOException {
        Path application = directory.getRoot().toPath().resolve("myapp");
        java.nio.file.Files.createDirectories(application);
        java.nio.file.Files.write(application.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFailure(Throwable failure, String expectedInMessage) {
        Assert.assertNotNull("Expected the application not to start", failure);
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        Assert.assertTrue(messages.toString(), messages.toString().contains(expectedInMessage));
    }

    private static ScheduledExecutorService executorThatRunsTheFirstRefresh() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executor.scheduleWithFixedDelay(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class))).thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return null;
                });
        return executor;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Bindings {

        @Bean
        @Order(2)
        AgerisCustomizer bindings() {
            return ageris -> {
                CALLS.add("second");
                ageris.bind(IntSupplier.class, () -> 200);
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedCustomizers {

        @Bean
        @Order(1)
        AgerisCustomizer first() {
            return ageris -> CALLS.add("first");
        }
    }

    static class EarlyReader {

        private final boolean seenAtStart;

        EarlyReader(FeatureFlagManager featureFlagManager) {
            seenAtStart = featureFlagManager.isActive("enable-checkout", NO_CONTEXT);
        }
    }
}
