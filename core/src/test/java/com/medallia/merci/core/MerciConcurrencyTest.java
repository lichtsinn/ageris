/*
 * Copyright 2018 Medallia, Inc.
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
package com.medallia.merci.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.medallia.merci.core.fetcher.ConfigurationFetcher;
import com.medallia.merci.core.metrics.FeatureFlagMetrics;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests that configuration readers created by {@link Merci} do not share mutable state, because
 * {@link ConfigurationLoader} schedules all of them on one executor service and therefore runs them
 * concurrently.
 */
public class MerciConcurrencyTest {

    private static final String APPLICATION = "myapp-configurations";

    /** Files of the first reader. Sorted order matters: the digest is updated in key order. */
    private static final String FIRST_FILE = "/a1-featureflags.json";
    private static final String FIRST_JSON = "{ \"feature-flags\": { \"enable-a-one\": { \"value\": true } } }";

    private static final String SECOND_FILE = "/a2-featureflags.json";
    private static final String SECOND_JSON = "{ \"feature-flags\": { \"enable-a-two\": { \"value\": false } } }";

    /** File of the second reader. */
    private static final String THIRD_FILE = "/b1-featureflags.json";
    private static final String THIRD_JSON = "{ \"feature-flags\": { \"enable-b-one\": { \"value\": true } } }";

    private static final Duration LATCH_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Two readers run concurrently, and the second one completes while the first is part-way through
     * hashing its own files. Both then see byte-for-byte identical content on their next cycle, so both
     * must recognize it as unchanged and skip the update.
     *
     * When readers share one {@link MessageDigest}, they cannot: digest() resets the instance, so the
     * interleaved reader hashes content it never fetched while the other hashes only what it managed to
     * add after the reset. Neither hash matches on the next cycle, both readers believe the content
     * changed, and the same-content skip they are supposed to record never happens. In production the
     * inverse also occurs - a reader whose hash is a constant believes content never changes and delays
     * real configuration updates by up to maximumSkips refresh cycles.
     */
    @Test
    public void testConcurrentReadersDoNotShareDigestState() throws InterruptedException {
        CountDownLatch firstReaderReachedSecondFile = new CountDownLatch(1);
        CountDownLatch secondReaderFinished = new CountDownLatch(1);
        AtomicBoolean interleaveOnce = new AtomicBoolean(true);

        /* Invoked from inside the first reader, between the digest updates of its two files. */
        Runnable interleave = () -> {
            if (interleaveOnce.compareAndSet(true, false)) {
                firstReaderReachedSecondFile.countDown();
                awaitOrFail(secondReaderFinished);
            }
        };

        ConfigurationFetcher fetcher = (fileNames, application) -> {
            if (fileNames.contains(FIRST_FILE)) {
                return contentsBlockingBeforeSecondFile(interleave);
            }
            return ImmutableMap.of(THIRD_FILE, THIRD_JSON);
        };

        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        Merci merci = new Merci(fetcher, executorService, new ObjectMapper(), sha256());
        merci.setMaximumSkips(1);

        FeatureFlagMetrics firstMetrics = new FeatureFlagMetrics();
        merci.addFeatureFlagManager(APPLICATION).registerFile(FIRST_FILE).registerFile(SECOND_FILE)
                .setMetrics(firstMetrics).build();
        FeatureFlagMetrics secondMetrics = new FeatureFlagMetrics();
        merci.addFeatureFlagManager(APPLICATION).registerFile(THIRD_FILE).setMetrics(secondMetrics).build();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        merci.createLoader(Duration.ofSeconds(10)).start();
        Mockito.verify(executorService, Mockito.times(2)).scheduleWithFixedDelay(runnableCaptor.capture(),
                Matchers.anyLong(), Matchers.anyLong(), Matchers.any(TimeUnit.class));
        Runnable firstReader = runnableCaptor.getAllValues().get(0);
        Runnable secondReader = runnableCaptor.getAllValues().get(1);

        /* First cycle: the second reader runs to completion inside the first reader's digest updates. */
        Thread firstReaderThread = new Thread(firstReader, "first-configuration-reader");
        firstReaderThread.start();
        awaitOrFail(firstReaderReachedSecondFile);
        secondReader.run();
        secondReaderFinished.countDown();
        firstReaderThread.join(LATCH_TIMEOUT.toMillis());
        Assert.assertFalse("First configuration reader did not finish", firstReaderThread.isAlive());

        /* Second cycle: unchanged content, so each reader must recognize its own hash. */
        firstReader.run();
        secondReader.run();

        Assert.assertEquals("First reader did not recognize its own unchanged content",
                1, firstMetrics.getFeatureFlagSameContentsSkips());
        Assert.assertEquals("Second reader did not recognize its own unchanged content",
                1, secondMetrics.getFeatureFlagSameContentsSkips());
    }

    /**
     * Returns configuration contents whose second entry runs the provided callback when its value is first
     * read. {@link ConfigurationReader} updates the digest per entry while iterating, so this is the hook
     * that suspends a reader mid-hash.
     *
     * @param beforeSecondValue callback to run before the second file's content is returned
     * @return map of two configuration files
     */
    private static Map<String, String> contentsBlockingBeforeSecondFile(Runnable beforeSecondValue) {
        Map.Entry<String, String> first = new AbstractMap.SimpleEntry<>(FIRST_FILE, FIRST_JSON);
        Map.Entry<String, String> second = new AbstractMap.SimpleEntry<String, String>(SECOND_FILE, SECOND_JSON) {
            @Override
            public String getValue() {
                beforeSecondValue.run();
                return super.getValue();
            }
        };
        Set<Map.Entry<String, String>> entries = new LinkedHashSet<>(Arrays.asList(first, second));
        return new AbstractMap<String, String>() {
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                return entries;
            }
        };
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the other configuration reader");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
