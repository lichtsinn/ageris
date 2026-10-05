/*
 * Copyright 2018 Medallia, Inc.
 * Modifications copyright 2026 Mario Lichtsinn; this file differs from the version
 * released by Medallia, Inc. See NOTICE.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Store for runtime JsonNode configurations.
 */
public class JsonConfigManager implements ConfigurationManager<JsonNode> {

    private final ConfigurationStore<JsonNode> store = new ConfigurationStore<>();

    private final JsonNode emptyConfiguration;

    /**
     * Creates new Json Node config manager.
     */
    public JsonConfigManager() {
        emptyConfiguration = new JsonNodeFactory(false).objectNode();
    }

    @Override
    public void updateConfigurations(Map<String, Configuration<JsonNode>> configurations) {
        store.updateConfigurations(configurations);
    }

    /**
     * @return list of configuration names from configuration store.
     */
    public List<String> getConfigurationNames() {
        return store.getConfigurationNames();
    }

    /**
     * Returns configuration store as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @return JSON of serialized configuration store
     * @throws IOException in case of JSON processing issues
     */
    public String asString(ConfigurationWriter<JsonNode> writer) throws IOException {
        return store.asString(writer);
    }

    /**
     * Returns configuration as JSON String.
     *
     * @param writer configuration writer to be used for serialization
     * @param configName name of configuration to be serialized by configuration writer
     * @return JSON of serialized configuration
     * @throws IOException in case of JSON processing issues
     */
    public String asString(ConfigurationWriter<JsonNode> writer, String configName) throws IOException {
        return store.asString(writer, configName);
    }

    /**
     * Return JsonNode config object (graph) if config store contains config for given config name, empty config JsonNode otherwise.
     *
     * @param name name of configuration
     * @param runtimeContext context to be used for evaluation of JsonNode configuration
     * @return JsonNode configuration hierarchy
     */
    public JsonNode getConfig(String name, ConfigurationContext runtimeContext) {
        return store.getValue(name, runtimeContext, emptyConfiguration);
    }
}
