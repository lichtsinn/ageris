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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A small Spring Boot application that uses Ageris for a feature flag with targeting and for a config. Ageris is set up
 * by its starter from application.properties; there is no setup code.
 */
@SpringBootApplication
public class ExampleApplication {

    /**
     * Starts the application.
     *
     * @param args passed on to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
