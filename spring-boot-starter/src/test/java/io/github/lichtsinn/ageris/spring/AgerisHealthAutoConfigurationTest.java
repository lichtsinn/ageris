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
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests what the starter adds to the health of an application.
 */
public class AgerisHealthAutoConfigurationTest {

    private final Files files = new Files();
    private final List<Runnable> refreshes = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgerisAutoConfiguration.class, AgerisHealthAutoConfiguration.class))
            .withPropertyValues("ageris.application=myapp", "ageris.feature-flags.files=/featureflags.json",
                    "ageris.json-configs.files=/json-configs.json");

    @Test
    public void testLoadedManagersAreUp() {
        runner.withBean(ConfigurationFetcher.class, files::fetcher).run(context -> {
            Health health = context.getBean("agerisHealthIndicator", HealthIndicator.class).health();

            Assert.assertEquals(Status.UP, health.getStatus());
            Assert.assertEquals("LOADED", health.getDetails().get("featureFlags"));
            Assert.assertEquals("LOADED", health.getDetails().get("jsonConfigs"));
            Assert.assertFalse("Only the managers that exist", health.getDetails().containsKey("configs"));
        });
    }

    @Test
    public void testStaleManagerLeavesTheApplicationUpAndShowsTheReason() {
        runner.withBean(Ageris.class, this::agerisThatRefreshesWhenTold).run(context -> {
            files.put("/featureflags.json", Files.BAD_FLAGS);
            refreshes.forEach(Runnable::run);

            Health health = context.getBean("agerisHealthIndicator", HealthIndicator.class).health();

            Assert.assertEquals(Status.UP, health.getStatus());
            String flags = String.valueOf(health.getDetails().get("featureFlags"));
            Assert.assertTrue(flags, flags.startsWith("STALE: ") && flags.contains("Cannot read enable-checkout: "));
            Assert.assertEquals("LOADED", health.getDetails().get("jsonConfigs"));
        });
    }

    @Test
    public void testManagerThatNeverLoadedTakesTheApplicationDown() {
        files.put("/featureflags.json", Files.BAD_FLAGS);

        runner.withBean(ConfigurationFetcher.class, files::fetcher)
                .withPropertyValues("ageris.first-load-timeout=50ms", "ageris.fail-on-first-load-failure=false").run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Health health = context.getBean("agerisHealthIndicator", HealthIndicator.class).health();

                    Assert.assertEquals(Status.DOWN, health.getStatus());
                    String flags = String.valueOf(health.getDetails().get("featureFlags"));
                    Assert.assertTrue(flags, flags.startsWith("NOT_LOADED: ") && flags.contains("Cannot read enable-checkout: "));
                });
    }

    @Test
    public void testNoHealthWithoutTheHealthSupportOfSpringBoot() {
        runner.withBean(ConfigurationFetcher.class, files::fetcher).withClassLoader(new FilteredClassLoader(HealthIndicator.class)).run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertFalse(context.containsBean("agerisHealthIndicator"));
        });
    }

    @Test
    public void testHealthCanBeSwitchedOff() {
        runner.withBean(ConfigurationFetcher.class, files::fetcher).withPropertyValues("management.health.ageris.enabled=false").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            Assert.assertFalse(context.containsBean("agerisHealthIndicator"));
        });
    }

    @Test
    public void testNoHealthWithoutAgeris() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AgerisAutoConfiguration.class, AgerisHealthAutoConfiguration.class))
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertFalse(context.containsBean("agerisHealthIndicator"));
                });
    }

    /** An Ageris whose loader runs a refresh when it is scheduled, and again whenever the test runs the kept tasks. */
    private Ageris agerisThatRefreshesWhenTold() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executor.scheduleWithFixedDelay(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class))).thenAnswer(invocation -> {
                    Runnable refresh = invocation.getArgument(0);
                    refreshes.add(refresh);
                    refresh.run();
                    return null;
                });
        try {
            return new Ageris(files.fetcher(), executor, new ObjectMapper(), MessageDigest.getInstance("SHA-256"));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
