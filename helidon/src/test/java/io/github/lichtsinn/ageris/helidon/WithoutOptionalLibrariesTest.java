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
                .filter(entry -> !isOptional(new File(entry)))
                .collect(Collectors.joining(File.pathSeparator));
        Assert.assertNotEquals("The test would prove nothing if no library was left out", System.getProperty("java.class.path"), classpath);

        File log = File.createTempFile("without-optional-libraries", ".log");
        Process process = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", classpath,
                WithoutOptionalLibraries.class.getName()).redirectErrorStream(true).redirectOutput(log).start();
        boolean ended = process.waitFor(60, TimeUnit.SECONDS);
        process.destroyForcibly();
        String output = new String(java.nio.file.Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        Assert.assertTrue(log.delete());

        Assert.assertTrue("The application did not end: " + output, ended);
        Assert.assertEquals(output, 0, process.exitValue());
        Assert.assertTrue(output, output.contains("health library: false"));
        Assert.assertTrue(output, output.contains("OpenFeature SDK: false"));
        Assert.assertTrue(output, output.contains("provider of Ageris: false"));
        Assert.assertTrue(output, output.contains("imperative: true"));
        Assert.assertTrue(output, output.contains("declarative: true"));
        Assert.assertFalse(output, output.contains("NoClassDefFoundError"));
    }

    /** By the name of the jar, or for the module next door by the folders it is built into; never by the path that leads there. */
    private static boolean isOptional(File entry) {
        String name = entry.getName();
        File module = entry.getParentFile() == null ? null : entry.getParentFile().getParentFile();
        boolean nextDoor = "classes".equals(name) && module != null && "openfeature".equals(module.getName());
        return nextDoor || name.startsWith("helidon-health") || name.startsWith("ageris-openfeature") || name.startsWith("sdk-");
    }
}
