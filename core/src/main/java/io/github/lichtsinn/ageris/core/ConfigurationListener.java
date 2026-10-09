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
 * Listener that a configuration manager calls after it has loaded new content, and when its status changes.
 *
 * It is called on the thread that applied the update or changed the status, which for a refresh is the thread of
 * the loader. It should return quickly: the next refresh of the manager waits for it, and so does
 * {@link ConfigurationLoader#awaitFirstLoad(java.time.Duration)} on the first load.
 *
 * Status changes are reported in the order they happened. If one is made while another is being reported, by
 * another thread or from inside a listener, it is reported afterwards and may be reported by that other thread.
 */
public interface ConfigurationListener {

    /**
     * Called after the configurations of a manager were replaced.
     *
     * @param update the update that was applied
     */
    void onUpdate(ConfigurationUpdate update);

    /**
     * Called when the status of a manager changes: when it has loaded for the first time, when a refresh fails and
     * leaves it stale, when it recovers, and when its first load fails. It is not called again while a manager stays
     * stale or keeps failing its first load. On an update that also changes the status, it is called before
     * {@link #onUpdate(ConfigurationUpdate)}.
     *
     * @param status the new status
     */
    default void onStatusChange(ConfigurationStatus status) {
        // Nothing by default, so that a listener may care about updates only.
    }
}
