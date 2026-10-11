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
package io.github.lichtsinn.ageris.helidon;

import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.helidon.service.registry.Service;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stands in for a service of an application that offers a feature flag manager of its own, with the weight any service
 * has unless it says otherwise. It only offers one while a test has set it.
 */
@Service.Singleton
class ApplicationFlags implements Supplier<Optional<FeatureFlagManager>> {

    private static volatile FeatureFlagManager own;

    static void offer(FeatureFlagManager manager) {
        own = manager;
    }

    @Override
    public Optional<FeatureFlagManager> get() {
        return Optional.ofNullable(own);
    }
}
