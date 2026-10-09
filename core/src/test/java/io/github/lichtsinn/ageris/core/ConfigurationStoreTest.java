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
import io.github.lichtsinn.ageris.core.ConfigurationStatus.State;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Unit tests for {@link ConfigurationStore}.
 */
public class ConfigurationStoreTest {

    private final ConfigurationStore<Boolean> store = new ConfigurationStore<>();
    private final IOException failure = new IOException("first failure");

    @Test
    public void testNewStoreIsNotLoaded() {
        Assert.assertEquals(State.NOT_LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
    }

    @Test
    public void testUpdateMakesTheStoreLoaded() throws IOException {
        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(State.LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
    }

    @Test
    public void testFailureBeforeAnyLoadStaysNotLoadedAndKeepsTheFailure() {
        store.failed(failure);

        Assert.assertEquals(State.NOT_LOADED, store.getStatus().getState());
        Assert.assertSame(failure, store.getStatus().getFailure());
    }

    @Test
    public void testFailureAfterALoadMakesTheStoreStaleAndKeepsItsEntries() throws IOException {
        store.updateConfigurations(flags("enable-all"));

        store.failed(failure);

        Assert.assertEquals(State.STALE, store.getStatus().getState());
        Assert.assertSame(failure, store.getStatus().getFailure());
        Assert.assertEquals(Collections.singletonList("enable-all"), store.getConfigurationNames());
    }

    @Test
    public void testSecondFailureReplacesTheRecordedFailure() throws IOException {
        IOException second = new IOException("second failure");
        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);

        store.failed(second);

        Assert.assertEquals(State.STALE, store.getStatus().getState());
        Assert.assertSame(second, store.getStatus().getFailure());
    }

    @Test
    public void testUnchangedContentAfterAFailureMakesTheStoreLoadedAgain() throws IOException {
        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);

        store.unchanged();

        Assert.assertEquals(State.LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
    }

    @Test
    public void testUpdateAfterAFailureMakesTheStoreLoadedAgain() throws IOException {
        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);

        store.updateConfigurations(flags("enable-none"));

        Assert.assertEquals(State.LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
    }

    @Test
    public void testUpdateAfterAFailedFirstLoadMakesTheStoreLoaded() throws IOException {
        store.failed(failure);

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(State.LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
    }

    private static Map<String, Configuration<Boolean>> flags(String name) throws IOException {
        return new FeatureFlagMapper("feature-flags", true, new ObjectMapper(), new FeatureFlagMetrics())
                .readValue("{ \"feature-flags\": { \"" + name + "\": { \"value\": true } } }");
    }
}
