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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ConfigurationStore}.
 */
public class ConfigurationStoreTest {

    private final ConfigurationStore<Boolean> store = new ConfigurationStore<>();
    private final IOException failure = new IOException("first failure");

    /** Records every callback as a line of text, in the order the callbacks arrive. */
    private static final class RecordingListener implements ConfigurationListener {
        private final List<String> events;

        RecordingListener(List<String> events) {
            this.events = events;
        }

        @Override
        public void onUpdate(ConfigurationUpdate update) {
            events.add("update " + update.getConfigurationNames());
        }

        @Override
        public void onStatusChange(ConfigurationStatus status) {
            events.add("status " + status.getState() + (status.getFailure() == null ? "" : " " + status.getFailure().getMessage()));
        }
    }

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

    @Test
    public void testListenerIsCalledWithTheNamesNowLoaded() throws IOException {
        List<List<String>> seen = new ArrayList<>();
        store.addListener(update -> seen.add(update.getConfigurationNames()));

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.singletonList(Collections.singletonList("enable-all")), seen);
    }

    @Test
    public void testListenerSeesTheNewEntriesAndTheLoadedStatus() throws IOException {
        List<Object> seen = new ArrayList<>();
        store.addListener(update -> {
            seen.add(store.getValue("enable-all", new ConfigurationContext(), Boolean.FALSE));
            seen.add(store.getStatus().getState());
        });

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Arrays.asList(Boolean.TRUE, State.LOADED), seen);
    }

    @Test
    public void testListenersAreCalledInTheOrderTheyWereAdded() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> calls.add("first"));
        store.addListener(update -> calls.add("second"));
        store.addListener(update -> calls.add("third"));

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Arrays.asList("first", "second", "third"), calls);
    }

    @Test
    public void testListenerIsNotCalledForAFailureOrUnchangedContent() throws IOException {
        List<String> calls = new ArrayList<>();
        store.updateConfigurations(flags("enable-all"));
        store.addListener(update -> calls.add("called"));

        store.failed(failure);
        store.unchanged();

        Assert.assertEquals(Collections.emptyList(), calls);
    }

    @Test
    public void testListenerIsNotCalledForAnUpdateMadeBeforeItWasAdded() throws IOException {
        List<String> calls = new ArrayList<>();
        store.updateConfigurations(flags("enable-all"));

        store.addListener(update -> calls.add("called"));

        Assert.assertEquals(Collections.emptyList(), calls);
    }

    @Test
    public void testUpdateWithoutNotificationReplacesEntriesButCallsNoListener() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> calls.add("called"));

        store.updateConfigurations(flags("enable-all"), false);

        Assert.assertEquals(Collections.emptyList(), calls);
        Assert.assertEquals(Collections.singletonList("enable-all"), store.getConfigurationNames());
        Assert.assertEquals(State.LOADED, store.getStatus().getState());
    }

    @Test
    public void testThrowingListenerDoesNotStopTheOthersOrChangeTheStatus() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> {
            throw new IllegalStateException("listener broke");
        });
        store.addListener(update -> calls.add("second"));

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.singletonList("second"), calls);
        Assert.assertEquals(State.LOADED, store.getStatus().getState());
        Assert.assertNull(store.getStatus().getFailure());
        Assert.assertEquals(Collections.singletonList("enable-all"), store.getConfigurationNames());
    }

    @Test
    public void testListenerThrowingAnErrorDoesNotStopTheOthersOrChangeTheStatus() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> {
            throw new NoClassDefFoundError("listener broke");
        });
        store.addListener(update -> calls.add("second"));

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.singletonList("second"), calls);
        Assert.assertEquals(State.LOADED, store.getStatus().getState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullListenerIsRejected() {
        store.addListener(null);
    }

    @Test
    public void testRemovedListenerIsNotCalled() throws IOException {
        List<String> calls = new ArrayList<>();
        ConfigurationListener listener = update -> calls.add("called");
        store.addListener(listener);

        store.removeListener(listener);
        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.emptyList(), calls);
    }

    @Test
    public void testRemovingAnUnregisteredListenerDoesNothing() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> calls.add("called"));

        store.removeListener(update -> calls.add("never added"));
        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.singletonList("called"), calls);
    }

    @Test
    public void testListenerRemovingItselfIsNotCalledAgainAndOthersStillAre() throws IOException {
        List<String> calls = new ArrayList<>();
        ConfigurationListener[] once = new ConfigurationListener[1];
        once[0] = update -> {
            calls.add("once");
            store.removeListener(once[0]);
        };
        store.addListener(once[0]);
        store.addListener(update -> calls.add("always"));

        store.updateConfigurations(flags("enable-all"));
        store.updateConfigurations(flags("enable-none"));

        Assert.assertEquals(Arrays.asList("once", "always", "always"), calls);
    }

    @Test
    public void testListenerAddedFromInsideAListenerIsCalledFromTheNextUpdate() throws IOException {
        List<String> calls = new ArrayList<>();
        ConfigurationListener late = update -> calls.add("late");
        ConfigurationListener[] adder = new ConfigurationListener[1];
        adder[0] = update -> {
            calls.add("adder");
            store.removeListener(adder[0]);
            store.addListener(late);
        };
        store.addListener(adder[0]);

        store.updateConfigurations(flags("enable-all"));
        Assert.assertEquals(Collections.singletonList("adder"), calls);

        store.updateConfigurations(flags("enable-none"));
        Assert.assertEquals(Arrays.asList("adder", "late"), calls);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNamesInAnUpdateCannotBeChanged() throws IOException {
        List<List<String>> seen = new ArrayList<>();
        store.addListener(update -> seen.add(update.getConfigurationNames()));
        store.updateConfigurations(flags("enable-all"));

        seen.get(0).add("another");
    }

    @Test
    public void testStatusChangeIsReportedBeforeTheUpdateOnTheFirstLoadOnly() throws IOException {
        List<String> events = new ArrayList<>();
        store.addListener(new RecordingListener(events));

        store.updateConfigurations(flags("enable-all"));
        store.updateConfigurations(flags("enable-none"));

        Assert.assertEquals(Arrays.asList("status LOADED", "update [enable-all]", "update [enable-none]"), events);
    }

    @Test
    public void testGoingStaleIsReportedOnceAndRepeatedFailuresAreNot() throws IOException {
        List<String> events = new ArrayList<>();
        store.updateConfigurations(flags("enable-all"));
        store.addListener(new RecordingListener(events));

        store.failed(failure);
        store.failed(new IOException("second failure"));

        Assert.assertEquals(Collections.singletonList("status STALE first failure"), events);
    }

    @Test
    public void testRecoveryThroughUnchangedContentIsReportedWithoutAnUpdate() throws IOException {
        List<String> events = new ArrayList<>();
        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);
        store.addListener(new RecordingListener(events));

        store.unchanged();
        store.unchanged();

        Assert.assertEquals(Collections.singletonList("status LOADED"), events);
    }

    @Test
    public void testRecoveryThroughAnUpdateIsReported() throws IOException {
        List<String> events = new ArrayList<>();
        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);
        store.addListener(new RecordingListener(events));

        store.updateConfigurations(flags("enable-none"));

        Assert.assertEquals(Arrays.asList("status LOADED", "update [enable-none]"), events);
    }

    @Test
    public void testFailedFirstLoadIsReportedOnceAndTheLoadThatFollowsToo() throws IOException {
        List<String> events = new ArrayList<>();
        store.addListener(new RecordingListener(events));

        store.failed(failure);
        store.failed(new IOException("second failure"));
        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Arrays.asList("status NOT_LOADED first failure", "status LOADED", "update [enable-all]"), events);
    }

    @Test
    public void testUpdateWithoutNotificationStillReportsAStatusChange() throws IOException {
        List<String> events = new ArrayList<>();
        store.addListener(new RecordingListener(events));

        store.updateConfigurations(flags("enable-all"), false);

        Assert.assertEquals(Collections.singletonList("status LOADED"), events);
    }

    @Test
    public void testStatusInsideTheCallbackIsTheNewStatus() throws IOException {
        List<State> seen = new ArrayList<>();
        store.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                seen.add(status.getState());
                seen.add(store.getStatus().getState());
            }
        });

        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);

        Assert.assertEquals(Arrays.asList(State.LOADED, State.LOADED, State.STALE, State.STALE), seen);
    }

    @Test
    public void testThrowingStatusListenerDoesNotStopTheOthers() throws IOException {
        List<String> events = new ArrayList<>();
        store.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                throw new NoClassDefFoundError("listener broke");
            }
        });
        store.addListener(new RecordingListener(events));

        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);

        Assert.assertEquals(Arrays.asList("status LOADED", "update [enable-all]", "status STALE first failure"), events);
        Assert.assertEquals(State.STALE, store.getStatus().getState());
    }

    /**
     * Tests that status changes reach a listener in the order they happened, also when another listener causes
     * one from inside its callback. Reported out of order, the last status a listener hears would not be the
     * status of the store.
     */
    @Test
    public void testStatusChangeCausedInsideACallbackIsReportedAfterTheCurrentOne() throws IOException {
        List<String> events = new ArrayList<>();
        Map<String, Configuration<Boolean>> repaired = flags("enable-none");
        store.updateConfigurations(flags("enable-all"));
        store.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                // Only the status is of interest here.
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                if (status.getState() == State.STALE) {
                    store.updateConfigurations(repaired, false);
                }
            }
        });
        store.addListener(new RecordingListener(events));

        store.failed(failure);

        Assert.assertEquals(Arrays.asList("status STALE first failure", "status LOADED"), events);
        Assert.assertEquals(State.LOADED, store.getStatus().getState());
    }

    @Test
    public void testUpdateNamesAreThoseOfTheUpdateThatWasApplied() throws IOException {
        List<String> events = new ArrayList<>();
        Map<String, Configuration<Boolean>> other = flags("enable-none");
        store.addListener(new ConfigurationListener() {
            @Override
            public void onUpdate(ConfigurationUpdate update) {
                events.add("update " + update.getConfigurationNames());
            }

            @Override
            public void onStatusChange(ConfigurationStatus status) {
                /* Replaces the entries before the update that caused this call is reported. */
                store.updateConfigurations(other, false);
            }
        });

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Collections.singletonList("update [enable-all]"), events);
    }

    @Test
    public void testEveryListenerHearsTheStatusChangeBeforeAnyHearsTheUpdate() throws IOException {
        List<String> events = new ArrayList<>();
        store.addListener(new RecordingListener(events));
        store.addListener(new RecordingListener(events));

        store.updateConfigurations(flags("enable-all"));

        Assert.assertEquals(Arrays.asList("status LOADED", "status LOADED", "update [enable-all]", "update [enable-all]"), events);
    }

    @Test
    public void testLambdaListenerIsNotConcernedByStatusChanges() throws IOException {
        List<String> calls = new ArrayList<>();
        store.addListener(update -> calls.add("update"));

        store.updateConfigurations(flags("enable-all"));
        store.failed(failure);
        store.unchanged();

        Assert.assertEquals(Collections.singletonList("update"), calls);
    }

    private static Map<String, Configuration<Boolean>> flags(String name) throws IOException {
        return new FeatureFlagMapper("feature-flags", true, new ObjectMapper(), new FeatureFlagMetrics())
                .readValue("{ \"feature-flags\": { \"" + name + "\": { \"value\": true } } }");
    }
}
