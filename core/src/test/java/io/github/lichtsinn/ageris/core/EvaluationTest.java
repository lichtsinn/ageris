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

import io.github.lichtsinn.ageris.core.Evaluation.Reason;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for {@link Evaluation}.
 */
public class EvaluationTest {

    @Test
    public void testHoldsValueReasonAndMatchesInOrder() {
        Map<String, String> matches = new LinkedHashMap<>();
        matches.put("environment", "qa");
        matches.put("cluster", "eu1");

        Evaluation<Boolean> evaluation = new Evaluation<>(Boolean.FALSE, Reason.MATCH, matches);

        Assert.assertEquals(Boolean.FALSE, evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Arrays.asList("environment", "cluster"), new ArrayList<>(evaluation.getMatches().keySet()));
        Assert.assertEquals("eu1", evaluation.getMatches().get("cluster"));
    }

    @Test
    public void testMatchesAreCopiedAndCannotBeChanged() {
        Map<String, String> matches = new LinkedHashMap<>();
        matches.put("environment", "qa");
        Evaluation<Boolean> evaluation = new Evaluation<>(Boolean.TRUE, Reason.MATCH, matches);

        matches.put("cluster", "eu1");

        Assert.assertEquals(Collections.singletonMap("environment", "qa"), evaluation.getMatches());
        try {
            evaluation.getMatches().put("user", "joe");
            Assert.fail("Expected the matches to be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            Assert.assertEquals(1, evaluation.getMatches().size());
        }
    }

    @Test
    public void testNullValueIsAllowed() {
        Assert.assertNull(new Evaluation<String>(null, Reason.NOT_FOUND, Collections.emptyMap()).getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullReasonIsRejected() {
        new Evaluation<>(Boolean.TRUE, null, Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullMatchesAreRejected() {
        new Evaluation<>(Boolean.TRUE, Reason.STATIC, null);
    }

    @Test
    public void testToStringShowsValueReasonAndMatches() {
        Assert.assertEquals("false (MATCH {environment=qa})",
                new Evaluation<>(Boolean.FALSE, Reason.MATCH, Collections.singletonMap("environment", "qa")).toString());
        Assert.assertEquals("true (STATIC)",
                new Evaluation<>(Boolean.TRUE, Reason.STATIC, Collections.emptyMap()).toString());
    }
}
