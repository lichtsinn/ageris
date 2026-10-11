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

import io.github.lichtsinn.ageris.core.ConfigurationStatus;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

import java.util.Map;

/**
 * Shows in the health of an application whether the managers of Ageris are loaded. A stale manager still serves what
 * it loaded before, so it leaves the application up and shows in the details; a manager that never loaded takes it down.
 */
final class AgerisHealthCheck implements HealthCheck {

    private final Managers managers;

    private AgerisHealthCheck(Managers managers) {
        this.managers = managers;
    }

    /**
     * @param managers the managers of the application
     * @return their health check, as the type of Helidon, so that the caller needs to know nothing else about this class
     */
    static HealthCheck create(Managers managers) {
        return new AgerisHealthCheck(managers);
    }

    @Override
    public HealthCheckType type() {
        return HealthCheckType.READINESS;
    }

    @Override
    public String name() {
        return "ageris";
    }

    @Override
    public HealthCheckResponse call() {
        boolean loaded = true;
        final HealthCheckResponse.Builder response = HealthCheckResponse.builder();
        for (final Map.Entry<String, ConfigurationStatus> manager : managers.statuses().entrySet()) {
            final ConfigurationStatus status = manager.getValue();
            loaded = loaded && status.getState() != ConfigurationStatus.State.NOT_LOADED;
            response.detail(manager.getKey(), status.getFailure() == null
                    ? status.getState().name()
                    : status.getState().name() + ": " + Managers.describe(status.getFailure()));
        }
        return response.status(loaded).build();
    }
}
