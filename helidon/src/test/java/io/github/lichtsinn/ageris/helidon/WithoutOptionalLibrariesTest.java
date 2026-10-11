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

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests that an application without the OpenFeature libraries and without the health library of Helidon is not bothered
 * by the provider and the health check this module offers. The libraries are on the classpath of the tests, so the
 * application runs in a Java process of its own, with a classpath that leaves them out.
 */
public class WithoutOptionalLibrariesTest {

    @Test
    public void testBothStylesWorkWithoutTheOptionalLibraries() throws IOException, InterruptedException {
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> !entry.contains("openfeature") && !entry.contains("helidon-health"))
                .collect(Collectors.joining(File.pathSeparator));
        Assert.assertNotEquals("The test would prove nothing if no library was left out", System.getProperty("java.class.path"), classpath);

        Process process = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", classpath,
                WithoutOptionalLibraries.class.getName()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        Assert.assertTrue(output, process.waitFor(60, TimeUnit.SECONDS));
        Assert.assertEquals(output, 0, process.exitValue());
        Assert.assertTrue(output, output.contains("imperative: true"));
        Assert.assertTrue(output, output.contains("declarative: true"));
        Assert.assertFalse(output, output.contains("NoClassDefFoundError"));
    }
}
