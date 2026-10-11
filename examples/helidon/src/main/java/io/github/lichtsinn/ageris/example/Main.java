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
import io.github.lichtsinn.ageris.helidon.AgerisHelidon;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

/**
 * A small Helidon SE application that uses Ageris for a feature flag with targeting and for a config.
 */
public final class Main {

    private Main() {
    }

    /**
     * Starts the application with the configuration in application.yaml.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Running running = start(Config.create());
        System.out.println("Try http://localhost:" + running.server().port() + "/greeting?user=anna");
    }

    /**
     * @param config the configuration of the application
     * @return the server and Ageris, both started
     */
    static Running start(Config config) {
        /* One line sets Ageris up from the "ageris" part of the configuration and loads the files. */
        AgerisHelidon ageris = AgerisHelidon.create(config.get("ageris"));

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routes(routing, ageris.featureFlagManager(), ageris.configManager()))
                .build()
                .start();
        return new Running(server, ageris);
    }

    private static void routes(HttpRouting.Builder routing, FeatureFlagManager featureFlags, ConfigManager configs) {
        routing.get("/greeting", (request, response) -> {
            String user = request.query().first("user").orElse("stranger");
            ConfigurationContext context = new ConfigurationContext();
            context.put("user", user);
            /* The feature flag decides, for this user, by the rules in featureflags.json. */
            boolean newGreeting = featureFlags.isActive("new-greeting", context);
            response.send(newGreeting ? "Welcome aboard, " + user + "!" : "Hello, " + user + ".");
        });
        routing.get("/limits", (request, response) -> {
            ConfigurationContext context = new ConfigurationContext();
            request.query().first("plan").ifPresent(plan -> context.put("plan", plan));
            /* The config for this plan, as an object of the application's own class. */
            Limits limits = configs.getConfig(Limits.class, context);
            response.send("maxItems=" + limits.maxItems() + " regions=" + limits.regions());
        });
    }

    /**
     * The running application.
     *
     * @param server the web server
     * @param ageris Ageris
     */
    record Running(WebServer server, AgerisHelidon ageris) {

        void stop() {
            server.stop();
            ageris.close();
        }
    }
}
