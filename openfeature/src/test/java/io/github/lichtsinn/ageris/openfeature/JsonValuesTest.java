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
import dev.openfeature.sdk.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Unit tests for {@link JsonValues}.
 */
public class JsonValuesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSingleValues() throws IOException {
        Assert.assertEquals("hello", value("\"hello\"").asString());
        Assert.assertEquals(Boolean.TRUE, value("true").asBoolean());
        Assert.assertEquals(Integer.valueOf(10), value("10").asObject());
        Assert.assertEquals(Double.valueOf(12345678901.0), value("12345678901").asObject());
        Assert.assertEquals(Double.valueOf(0.25), value("0.25").asObject());
        Assert.assertEquals(Double.valueOf(1e30), value("1000000000000000000000000000000").asObject());
        Assert.assertTrue(value("null").isNull());
    }

    @Test
    public void testWholeNumbersAtTheEdgesOfAnInt() throws IOException {
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), value("2147483647").asObject());
        Assert.assertEquals(Integer.valueOf(Integer.MIN_VALUE), value("-2147483648").asObject());
        Assert.assertEquals(Double.valueOf(2147483648.0), value("2147483648").asObject());
        Assert.assertEquals(Double.valueOf(-2147483649.0), value("-2147483649").asObject());
        Assert.assertEquals(Double.valueOf(3.0), value("3.0").asObject());
    }

    @Test
    public void testObjectsAndListsAreConvertedAllTheWayDown() throws IOException {
        Value value = value("{ \"max\": 10, \"hosts\": [\"a\", \"b\"], \"nested\": { \"on\": true, \"none\": null, \"list\": [ { \"deep\": 1.5 } ] } }");

        Assert.assertTrue(value.isStructure());
        Assert.assertEquals(Integer.valueOf(10), value.asStructure().getValue("max").asInteger());
        Assert.assertEquals("b", value.asStructure().getValue("hosts").asList().get(1).asString());
        Value nested = value.asStructure().getValue("nested");
        Assert.assertEquals(Boolean.TRUE, nested.asStructure().getValue("on").asBoolean());
        Assert.assertTrue(nested.asStructure().getValue("none").isNull());
        Assert.assertEquals(Double.valueOf(1.5), nested.asStructure().getValue("list").asList().get(0).asStructure().getValue("deep").asDouble());
    }

    @Test
    public void testEmptyObjectAndEmptyList() throws IOException {
        Assert.assertTrue(value("{ }").asStructure().isEmpty());
        Assert.assertTrue(value("[ ]").asList().isEmpty());
    }

    private static Value value(String json) throws IOException {
        return JsonValues.toValue(MAPPER.readTree(json));
    }
}
