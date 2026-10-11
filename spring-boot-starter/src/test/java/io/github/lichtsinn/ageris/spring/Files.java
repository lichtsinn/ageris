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

import io.github.lichtsinn.ageris.core.fetcher.ConfigurationFetcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration files for the tests, kept in memory and handed out through a fetcher. A file without content is one
 * that cannot be fetched.
 */
final class Files {

    static final String FLAGS = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": true } } }";
    static final String BAD_FLAGS = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": \"neither true nor false\" } } }";
    static final String CONFIGS = "{ \"configs\": { \"io.github.lichtsinn.ageris.spring.Discount\": { \"value\": { \"percent\": 25 } } } }";
    static final String JSON_CONFIGS = "{ \"configs\": { \"greeting\": { \"value\": \"hello\" } } }";

    private final Map<String, String> contents = new ConcurrentHashMap<>();

    Files() {
        contents.put("/featureflags.json", FLAGS);
        contents.put("/configs.json", CONFIGS);
        contents.put("/json-configs.json", JSON_CONFIGS);
    }

    void put(String fileName, String content) {
        contents.put(fileName, content);
    }

    ConfigurationFetcher fetcher() {
        return (fileNames, application) -> {
            final Map<String, String> fetched = new HashMap<>();
            for (final String fileName : fileNames) {
                final String content = contents.get(fileName);
                if (content == null) {
                    throw new IOException("No file " + fileName + " for " + application);
                }
                fetched.put(fileName, content);
            }
            return fetched;
        };
    }
}
