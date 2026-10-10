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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.function.BooleanSupplier;

/**
 * Tests that a lookup creates no objects, with named entries, with a list and with a percentage. A lookup runs on every
 * request, so anything it creates turns into work for the garbage collector.
 */
public class LookupAllocationTest {

    private static final String NUMBER_CONFIG = "io.github.lichtsinn.ageris.core.configs.NumberConfig";
    private static final int WARM_UP = 300_000;
    private static final int MEASURED = 200_000;

    private static final String FLAGS = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"environment\", \"contexts\": { \"qa\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true } },"
            + " \"rules\": [ { \"in\": [\"anna\", \"ben\"], \"value\": true }, { \"percent\": 10, \"value\": true } ] } } } } } } }";

    private static final String CONFIGS = "{ \"configs\": { \"" + NUMBER_CONFIG + "\": { \"value\": { \"number\": 0 }, \"modifiers\": {"
            + " \"type\": \"user\", \"rules\": [ { \"percent\": 50, \"value\": { \"number\": 1 } } ] } } } }";

    private final com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test
    public void testIsActiveCreatesNoObjects() throws IOException {
        FeatureFlagManager flags = new FeatureFlagManager();
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, new ObjectMapper(), new FeatureFlagMetrics()).readValue(FLAGS));

        assertNoObjects("named entry", flags, "enable-checkout", context("joe"));
        assertNoObjects("list", flags, "enable-checkout", context("anna"));
        assertNoObjects("percentage, plain id", flags, "enable-checkout", context("user-9"));
        assertNoObjects("percentage, id outside ASCII", flags, "enable-checkout", context("Zoë-山田"));
        assertNoObjects("nothing matches", flags, "enable-checkout", context("user-15"));
        assertNoObjects("no user", flags, "enable-checkout", context(null));
        assertNoObjects("unknown flag", flags, "missing", context("joe"));
    }

    @Test
    public void testGetConfigCreatesNoObjects() throws IOException {
        ConfigManager configs = new ConfigManager();
        configs.updateConfigurations(new ConfigurationMapper<>("configs", false, new ObjectMapper(), new ConfigMetrics(), new DefaultClassFinder())
                .readValue(CONFIGS));
        ConfigurationContext caller = context("user-9");

        long bytes = bytesFor(() -> configs.getConfig(NumberConfig.class, caller).getNumber() >= 0);

        Assert.assertTrue("getConfig created " + bytes + " bytes in " + MEASURED + " lookups", bytes < MEASURED / 4);
    }

    private void assertNoObjects(String what, FeatureFlagManager flags, String name, ConfigurationContext caller) {
        long bytes = bytesFor(() -> flags.isActive(name, caller));

        /* Less than a quarter of a byte per lookup: nothing is created once the code is compiled. */
        Assert.assertTrue(what + ": isActive created " + bytes + " bytes in " + MEASURED + " lookups", bytes < MEASURED / 4);
    }

    private long bytesFor(BooleanSupplier lookup) {
        int seen = 0;
        for (int round = 0; round < WARM_UP; round++) {
            if (lookup.getAsBoolean()) {
                seen++;
            }
        }
        long thread = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(thread);
        for (int round = 0; round < MEASURED; round++) {
            if (lookup.getAsBoolean()) {
                seen++;
            }
        }
        long after = threads.getThreadAllocatedBytes(thread);
        Assert.assertTrue(seen >= 0);
        return after - before;
    }

    private static ConfigurationContext context(String user) {
        ConfigurationContext context = new ConfigurationContext();
        context.put("environment", "qa");
        if (user != null) {
            context.put("user", user);
        }
        return context;
    }
}
