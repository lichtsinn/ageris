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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

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
    public void testFactoryTakesTypesAndValuesInOrder() {
        Evaluation<Boolean> evaluation = Evaluation.of(Boolean.TRUE, Reason.MATCH, "environment", "qa", "cluster", "eu1");

        Assert.assertEquals(Boolean.TRUE, evaluation.getValue());
        Assert.assertEquals(Reason.MATCH, evaluation.getReason());
        Assert.assertEquals(Arrays.asList("environment", "cluster"), new ArrayList<>(evaluation.getMatches().keySet()));
        Assert.assertEquals("qa", evaluation.getMatches().get("environment"));
        Assert.assertEquals("true (MATCH {environment=qa, cluster=eu1})", evaluation.toString());
    }

    @Test
    public void testFactoryWithoutMatches() {
        Evaluation<Boolean> evaluation = Evaluation.of(Boolean.FALSE, Reason.DEFAULT);

        Assert.assertEquals(Collections.emptyMap(), evaluation.getMatches());
        Assert.assertEquals("false (DEFAULT)", evaluation.toString());
    }

    @Test
    public void testFactoryCopiesWhatItIsGiven() {
        String[] typesAndValues = {"environment", "qa"};
        Evaluation<Boolean> evaluation = Evaluation.of(Boolean.TRUE, Reason.MATCH, typesAndValues);

        typesAndValues[1] = "production";

        Assert.assertEquals("qa", evaluation.getMatches().get("environment"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMatchesOfTheFactoryCannotBeChanged() {
        Evaluation.of(Boolean.TRUE, Reason.MATCH, "environment", "qa").getMatches().put("user", "joe");
    }

    @Test
    public void testMatchesAreTheSameMapOnEveryCall() {
        Evaluation<Boolean> evaluation = Evaluation.of(Boolean.TRUE, Reason.MATCH, "environment", "qa");

        Assert.assertSame(evaluation.getMatches(), evaluation.getMatches());
    }

    @Test
    public void testMatchesAreCompleteForEveryThreadThatAsks() throws InterruptedException {
        for (int round = 0; round < 200; round++) {
            Evaluation<Boolean> evaluation = Evaluation.of(Boolean.TRUE, Reason.MATCH, "environment", "qa", "cluster", "eu1", "user", "joe");
            List<Map<String, String>> seen = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    seen.add(new LinkedHashMap<>(evaluation.getMatches()));
                });
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
            for (Map<String, String> matches : seen) {
                Assert.assertEquals("{environment=qa, cluster=eu1, user=joe}", matches.toString());
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryRejectsATypeWithoutAValue() {
        Evaluation.of(Boolean.TRUE, Reason.MATCH, "environment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryRejectsANullReason() {
        Evaluation.of(Boolean.TRUE, null);
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
