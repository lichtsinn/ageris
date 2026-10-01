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
package io.github.lichtsinn.ageris.core.utils;

import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link DefaultClassFinder}.
 */
public class DefaultClassFinderTest {

    private static final String CONFIG_NAME = "io.github.lichtsinn.ageris.core.configs.NumberConfig";

    private final DefaultClassFinder classFinder = new DefaultClassFinder();

    @Test
    public void testFindClassReturnsConfigClass() throws ClassNotFoundException {
        Assert.assertEquals(NumberConfig.class, classFinder.findClass(CONFIG_NAME));
    }

    @Test
    public void testFindClassReturnsBooleanClass() throws ClassNotFoundException {
        Assert.assertEquals(Boolean.class, classFinder.findClass("java.lang.Boolean"));
    }
}