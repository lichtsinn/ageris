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
 * Listener that a configuration manager calls after it has loaded new content.
 *
 * It is called on the thread that applied the update, which for a refresh is the thread of the loader, after the
 * configurations were replaced. It should return quickly: the next refresh of the manager waits for it, and so does
 * {@link ConfigurationLoader#awaitFirstLoad(java.time.Duration)} on the first load.
 */
public interface ConfigurationListener {

    /**
     * Called after the configurations of a manager were replaced.
     *
     * @param update the update that was applied
     */
    void onUpdate(ConfigurationUpdate update);
}
