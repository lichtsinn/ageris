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

import io.github.lichtsinn.ageris.core.ConfigurationStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.Map;

/**
 * Shows in the health of an application whether the managers of Ageris are loaded. A stale manager still serves what
 * it loaded before, so it leaves the application up and shows in the details; a manager that never loaded takes it down.
 */
final class AgerisHealthIndicator implements HealthIndicator {

    private final AgerisSetup setup;

    AgerisHealthIndicator(AgerisSetup setup) {
        this.setup = setup;
    }

    @Override
    public Health health() {
        boolean loaded = true;
        final Health.Builder health = new Health.Builder();
        for (final Map.Entry<String, ConfigurationStatus> manager : setup.statuses().entrySet()) {
            final ConfigurationStatus status = manager.getValue();
            loaded = loaded && status.getState() != ConfigurationStatus.State.NOT_LOADED;
            health.withDetail(manager.getKey(), status.getFailure() == null
                    ? status.getState().name()
                    : status.getState().name() + ": " + status.getFailure().getMessage());
        }
        return loaded ? health.up().build() : health.down().build();
    }
}
