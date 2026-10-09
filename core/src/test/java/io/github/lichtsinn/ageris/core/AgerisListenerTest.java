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
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the listeners a manager calls while Ageris refreshes it.
 */
public class AgerisListenerTest {

    private static final String NUMBER_CONFIG = "io.github.lichtsinn.ageris.core.configs.NumberConfig";
    private static final String GOOD = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"number\" : 7 } } } }";
    private static final String CHANGED = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"number\" : 8 } } } }";
    private static final String BAD = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\" : { \"badproperty\" : 1 } } } }";

    private final ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
    private final AtomicReference<String> content = new AtomicReference<>(GOOD);
    private final Ageris ageris = new Ageris((fileNames, application) -> ImmutableMap.of("/configs.json", content.get()),
            executorService, new ObjectMapper(), sha256());
    private final List<Integer> numbersSeen = new ArrayList<>();

    @Test
    public void testListenerIsCalledOnTheFirstLoadWithTheNewValueVisible() {
        managerWithListener();

        startLoader().run();

        Assert.assertEquals(Collections.singletonList(7), numbersSeen);
    }

    @Test
    public void testListenerIsCalledWhenTheContentChanges() {
        managerWithListener();
        Runnable refresh = startLoader();
        refresh.run();

        content.set(CHANGED);
        refresh.run();

        Assert.assertEquals(Arrays.asList(7, 8), numbersSeen);
    }

    @Test
    public void testListenerIsNotCalledForUnchangedContent() {
        managerWithListener();
        Runnable refresh = startLoader();

        refresh.run();
        refresh.run();
        refresh.run();

        Assert.assertEquals(Collections.singletonList(7), numbersSeen);
    }

    @Test
    public void testListenerIsNotCalledForAFailedRefreshOrForTheRevertThatFollows() {
        managerWithListener();
        Runnable refresh = startLoader();
        refresh.run();

        content.set(BAD);
        refresh.run();
        content.set(GOOD);
        refresh.run();

        Assert.assertEquals(Collections.singletonList(7), numbersSeen);
    }

    @Test
    public void testForcedReloadOfUnchangedContentReplacesEntriesButCallsNoListener() {
        ageris.setMaximumSkips(0);
        ConfigManager configManager = managerWithListener();
        Runnable refresh = startLoader();
        refresh.run();
        NumberConfig first = configManager.getConfig(NumberConfig.class, new ConfigurationContext());

        refresh.run();

        Assert.assertNotSame(first, configManager.getConfig(NumberConfig.class, new ConfigurationContext()));
        Assert.assertEquals(Collections.singletonList(7), numbersSeen);
    }

    @Test
    public void testListenerIsCalledAgainWhenContentChangesAfterAFailure() {
        managerWithListener();
        Runnable refresh = startLoader();
        refresh.run();
        content.set(BAD);
        refresh.run();

        content.set(CHANGED);
        refresh.run();

        Assert.assertEquals(Arrays.asList(7, 8), numbersSeen);
    }

    /**
     * Tests that a listener throwing an error does not end the refresh. The executor cancels a scheduled task
     * that throws, silently and for good.
     */
    @Test
    public void testListenerThrowingAnErrorDoesNotEndTheRefresh() {
        ConfigManager configManager = managerWithListener();
        configManager.addListener(update -> {
            throw new NoClassDefFoundError("listener broke");
        });
        ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(10));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(captor.capture(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));

        captor.getValue().run();
        content.set(CHANGED);
        captor.getValue().run();

        Assert.assertEquals(Arrays.asList(7, 8), numbersSeen);
        Assert.assertTrue(loader.awaitFirstLoad(Duration.ZERO));
    }

    @Test
    public void testStatusListenerThrowingAnErrorDoesNotEndTheRefresh() {
        ConfigManager configManager = managerWithListener();
        configManager.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                throw new NoClassDefFoundError("listener broke");
            }
        });
        Runnable refresh = startLoader();

        refresh.run();
        content.set(BAD);
        refresh.run();
        content.set(CHANGED);
        refresh.run();

        Assert.assertEquals(Arrays.asList(7, 8), numbersSeen);
        Assert.assertEquals(State.LOADED, configManager.getStatus().getState());
    }

    @Test
    public void testStatusChangesAreReportedThroughRefreshes() {
        List<State> states = new ArrayList<>();
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        configManager.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                states.add(status.getState());
            }
        });
        Runnable refresh = startLoader();

        refresh.run();
        refresh.run();
        content.set(BAD);
        refresh.run();
        refresh.run();
        content.set(GOOD);
        refresh.run();

        Assert.assertEquals(Arrays.asList(State.LOADED, State.STALE, State.LOADED), states);
    }

    private ConfigManager managerWithListener() {
        ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();
        configManager.addListener(update -> numbersSeen.add(configManager.getConfig(NumberConfig.class, new ConfigurationContext()).getNumber()));
        return configManager;
    }

    private Runnable startLoader() {
        ageris.createAndStartLoader(Duration.ofSeconds(10));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(captor.capture(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
        return captor.getValue();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
