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