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

/**
 * Status of a configuration manager: whether it has loaded, and why its last refresh failed if it did.
 */
public final class ConfigurationStatus {

    /**
     * State of a configuration manager.
     */
    public enum State {
        /** No load has succeeded. Lookups return defaults. */
        NOT_LOADED,
        /** The last refresh succeeded. */
        LOADED,
        /** A load succeeded earlier, the last refresh failed, and the configurations loaded before are served. */
        STALE
    }

    private final State state;
    private final Exception failure;

    /**
     * Creates a status.
     *
     * @param state state of the manager
     * @param failure exception of the last failed refresh, null if the last refresh succeeded or none has run
     */
    ConfigurationStatus(State state, Exception failure) {
        this.state = state;
        this.failure = failure;
    }

    /**
     * @return state of the manager
     */
    public State getState() {
        return state;
    }

    /**
     * @return exception of the last failed refresh, or null if the last refresh succeeded or none has run
     */
    public Exception getFailure() {
        return failure;
    }

    @Override
    public String toString() {
        return failure == null ? state.toString() : state + " (" + failure + ")";
    }
}
