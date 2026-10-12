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
package io.github.lichtsinn.ageris.openfeature;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.FeatureFlagMapper;
import io.github.lichtsinn.ageris.core.JsonConfigManager;
import io.github.lichtsinn.ageris.core.metrics.FeatureFlagMetrics;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;

/**
 * Tests that a lookup through the provider creates little. A lookup runs on every request, so what it creates turns into
 * work for the garbage collector. What the OpenFeature client creates on top is not measured here.
 */
public class AgerisProviderAllocationTest {

    private static final int WARM_UP = 300_000;
    private static final int MEASURED = 200_000;
    private static final int ROUNDS = 5;
    private static final int LIMIT = 176;

    private static final String FLAGS = "{ \"feature-flags\": { \"enable-checkout\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"environment\", \"contexts\": { \"qa\": { \"value\": false, \"modifiers\": {"
            + " \"type\": \"user\", \"contexts\": { \"joe\": { \"value\": true } },"
            + " \"rules\": [ { \"percent\": 10, \"value\": true } ] } } } } } } }";

    @Test
    public void testBooleanLookupCreatesLittle() throws IOException {
        FeatureFlagManager flags = new FeatureFlagManager();
        flags.updateConfigurations(new FeatureFlagMapper("feature-flags", false, new ObjectMapper(), new FeatureFlagMetrics()).readValue(FLAGS));
        AgerisProvider provider = new AgerisProvider(flags, new JsonConfigManager());
        EvaluationContext caller = new ImmutableContext("user-9", Collections.singletonMap("environment", new Value("qa")));
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

        int active = 0;
        for (int round = 0; round < WARM_UP; round++) {
            if (provider.getBooleanEvaluation("enable-checkout", false, caller).getValue()) {
                active++;
            }
        }
        /* The smallest of several rounds counts. How far the JVM has optimized the code by a given round differs from run
         * to run and can add a few bytes to one of them; a lookup that really creates more does so in every round. */
        long thread = Thread.currentThread().getId();
        long perCall = Long.MAX_VALUE;
        for (int measurement = 0; measurement < ROUNDS; measurement++) {
            long before = threads.getThreadAllocatedBytes(thread);
            for (int round = 0; round < MEASURED; round++) {
                if (provider.getBooleanEvaluation("enable-checkout", false, caller).getValue()) {
                    active++;
                }
            }
            perCall = Math.min(perCall, (threads.getThreadAllocatedBytes(thread) - before) / MEASURED);
        }

        Assert.assertEquals(WARM_UP + ROUNDS * MEASURED, active);
        /* The answer, the evaluation behind it and its two matches. The caller's context is read, not copied. */
        Assert.assertTrue("A boolean lookup created " + perCall + " bytes", perCall <= LIMIT);
    }
}
