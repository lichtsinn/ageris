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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.github.lichtsinn.ageris.core.common.EnvironmentConfigurationContext;
import io.github.lichtsinn.ageris.core.configs.DiscountConfig;
import io.github.lichtsinn.ageris.core.configs.PriceSource;
import io.github.lichtsinn.ageris.core.exception.ConfigInstantiationException;
import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests bindings end to end: bound on {@link Ageris}, received by a config, used through {@link ConfigManager}.
 */
public class AgerisBindingsTest {

    private static final String CONFIGS = "{ \"configs\": { \"io.github.lichtsinn.ageris.core.configs.DiscountConfig\": {"
            + " \"value\": { \"percent\": 25 }, \"modifiers\": { \"type\": \"environment\", \"contexts\": {"
            + " \"qa\": { \"value\": { \"percent\": 50 } } } } } } }";
    private static final String NO_CONFIGS = "{ \"configs\": { } }";

    private final ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
    private final PriceSource prices = new PriceSource(200);

    @Test
    public void testConfigLoadedThroughAgerisUsesItsBindingAndTheContext() {
        Ageris ageris = ageris(CONFIGS);
        ageris.bind(PriceSource.class, prices);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        load(ageris);

        Assert.assertEquals(150, configManager.getConfig(DiscountConfig.class, new ConfigurationContext()).discountedPrice());
        Assert.assertEquals(100, configManager.getConfig(DiscountConfig.class, new EnvironmentConfigurationContext("qa")).discountedPrice());
    }

    @Test
    public void testBindingRegisteredAfterTheManagerWasBuiltStillApplies() {
        Ageris ageris = ageris(CONFIGS);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        ageris.bind(PriceSource.class, prices);

        load(ageris);

        Assert.assertSame(prices, configManager.getConfig(DiscountConfig.class, new ConfigurationContext()).prices());
    }

    @Test
    public void testBindingByNameIsAccepted() {
        ageris(CONFIGS).bind("backupPrices", prices);
    }

    @Test(expected = ConfigInstantiationException.class)
    public void testConfigWithBindingsAndNoEntryInTheFileIsNotInvented() {
        Ageris ageris = ageris(NO_CONFIGS);
        ageris.bind(PriceSource.class, prices);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

        load(ageris);

        configManager.getConfig(DiscountConfig.class, new ConfigurationContext());
    }

    @Test
    public void testSerializingLoadedConfigsLeavesTheBindingOut() throws IOException {
        Ageris ageris = ageris(CONFIGS);
        ageris.bind(PriceSource.class, prices);
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        load(ageris);

        String serialized = configManager.asString(new ConfigurationWriter<>("configs", new ObjectMapper()));

        Assert.assertTrue(serialized, serialized.contains("percent"));
        Assert.assertFalse(serialized, serialized.contains("prices"));
        Assert.assertFalse(serialized, serialized.contains("basePrice"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullBindingIsRejected() {
        ageris(CONFIGS).bind(PriceSource.class, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondBindingForTheSameTypeIsRejected() {
        Ageris ageris = ageris(CONFIGS);
        ageris.bind(PriceSource.class, prices);
        ageris.bind(PriceSource.class, new PriceSource(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondBindingForTheSameNameIsRejected() {
        Ageris ageris = ageris(CONFIGS);
        ageris.bind("backupPrices", prices);
        ageris.bind("backupPrices", new PriceSource(1));
    }

    private Ageris ageris(String content) {
        ConfigurationFetcher fetcher = (fileNames, application) -> ImmutableMap.of("/configs.json", content);
        return new Ageris(fetcher, executorService, new ObjectMapper(), sha256());
    }

    /** Starts the loader and runs the one scheduled refresh on this thread. */
    private void load(Ageris ageris) {
        ageris.createLoader(Duration.ofSeconds(10)).start();
        ArgumentCaptor<Runnable> refresh = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(refresh.capture(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(TimeUnit.class));
        refresh.getValue().run();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
