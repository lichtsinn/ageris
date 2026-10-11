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
package io.github.lichtsinn.ageris.example;

import io.github.lichtsinn.ageris.core.ConfigManager;
import io.github.lichtsinn.ageris.core.ConfigurationContext;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints of the example. The managers are beans that the starter of Ageris offers.
 */
@RestController
class ExampleController {

    private final FeatureFlagManager featureFlags;
    private final ConfigManager configs;

    ExampleController(FeatureFlagManager featureFlags, ConfigManager configs) {
        this.featureFlags = featureFlags;
        this.configs = configs;
    }

    @GetMapping("/greeting")
    String greeting(@RequestParam(defaultValue = "stranger") String user) {
        ConfigurationContext context = new ConfigurationContext();
        context.put("user", user);
        /* The feature flag decides, for this user, by the rules in featureflags.json. */
        boolean newGreeting = featureFlags.isActive("new-greeting", context);
        return newGreeting ? "Welcome aboard, " + user + "!" : "Hello, " + user + ".";
    }

    @GetMapping("/limits")
    String limits(@RequestParam(required = false) String plan) {
        ConfigurationContext context = new ConfigurationContext();
        if (plan != null) {
            context.put("plan", plan);
        }
        /* The config for this plan, as an object of the application's own class. */
        Limits limits = configs.getConfig(Limits.class, context);
        return "maxItems=" + limits.maxItems() + " regions=" + limits.regions();
    }
}
