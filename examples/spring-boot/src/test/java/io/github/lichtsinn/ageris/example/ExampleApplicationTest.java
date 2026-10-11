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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Starts the example on a free port and calls it the way the README does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void greetingFollowsTheFeatureFlag() throws IOException, InterruptedException {
        Assertions.assertEquals("Welcome aboard, anna!", get("/greeting?user=anna"));
        Assertions.assertEquals("Welcome aboard, ben!", get("/greeting?user=ben"));

        int welcomed = 0;
        for (int user = 0; user < 200; user++) {
            if (get("/greeting?user=user-" + user).startsWith("Welcome aboard")) {
                welcomed++;
            }
        }
        Assertions.assertTrue(welcomed > 15 && welcomed < 70, "About 20 percent of 200 users, but was " + welcomed);
    }

    @Test
    void limitsDependOnThePlan() throws IOException, InterruptedException {
        Assertions.assertEquals("maxItems=10 regions=[eu]", get("/limits"));
        Assertions.assertEquals("maxItems=100 regions=[eu, us]", get("/limits?plan=pro"));
    }

    @Test
    void healthShowsAgeris() throws IOException, InterruptedException {
        String health = get("/actuator/health");

        Assertions.assertTrue(health.contains("\"ageris\":{"), health);
        Assertions.assertTrue(health.contains("\"featureFlags\":\"LOADED\""), health);
        Assertions.assertTrue(health.contains("\"configs\":\"LOADED\""), health);
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
