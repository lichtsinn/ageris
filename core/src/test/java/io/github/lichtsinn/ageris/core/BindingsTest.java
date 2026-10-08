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

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Unit tests for {@link Bindings}.
 */
public class BindingsTest {

    private final Bindings bindings = new Bindings();

    @Test
    public void testBindingByTypeIsFoundUnderTheNameOfItsType() throws JsonMappingException {
        Object bound = new Object();
        bindings.bind(Object.class, bound);

        Assert.assertSame(bound, bindings.findInjectableValue("java.lang.Object", null, null, null));
    }

    @Test
    public void testBindingByNameIsFoundUnderThatName() throws JsonMappingException {
        Object bound = new Object();
        bindings.bind("backup", bound);

        Assert.assertSame(bound, bindings.findInjectableValue("backup", null, null, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullTypeIsRejected() {
        bindings.bind((Class<Object>) null, new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullNameIsRejected() {
        bindings.bind((String) null, new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullBindingByTypeIsRejected() {
        bindings.bind(Object.class, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullBindingByNameIsRejected() {
        bindings.bind("backup", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondBindingForTheSameTypeIsRejected() {
        bindings.bind(Object.class, new Object());
        bindings.bind(Object.class, new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecondBindingForTheSameNameIsRejected() {
        bindings.bind("backup", new Object());
        bindings.bind("backup", new Object());
    }

    @Test
    public void testMissingBindingFailsNamingWhatWasAskedFor() {
        try {
            bindings.findInjectableValue("java.util.List", null, null, null);
            Assert.fail("A missing binding must not be answered.");
        } catch (JsonMappingException exception) {
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("java.util.List"));
        }
    }

    /**
     * Lookup is by the declared type exactly, so a binding registered under a concrete type does not
     * answer a request for its supertype.
     */
    @Test(expected = JsonMappingException.class)
    public void testBindingUnderConcreteTypeDoesNotAnswerForItsSupertype() throws JsonMappingException {
        bindings.bind(ArrayList.class, new ArrayList<String>());

        bindings.findInjectableValue("java.util.List", null, null, null);
    }

    @Test(expected = JsonMappingException.class)
    public void testIdThatIsNotANameIsMissing() throws JsonMappingException {
        bindings.findInjectableValue(Integer.valueOf(1), null, null, null);
    }
}
