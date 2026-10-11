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
package io.github.lichtsinn.ageris.core.structure;

import com.google.common.hash.Hashing;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Unit tests for {@link Murmur3}. The expected values pin the hash for good: changing one moves callers between buckets.
 */
public class Murmur3Test {

    @Test
    public void testKnownValues() {
        Assert.assertEquals(0x00000000, Murmur3.hash("", 0));
        Assert.assertEquals(0x514e28b7, Murmur3.hash("", 1));
        Assert.assertEquals(0xba6bd213, Murmur3.hash("test", 0));
        Assert.assertEquals(0x704b81dc, Murmur3.hash("test", 0x9747b28c));
        Assert.assertEquals(0xc0363e43, Murmur3.hash("Hello, world!", 0));
        Assert.assertEquals(0x2e4ff723, Murmur3.hash("The quick brown fox jumps over the lazy dog", 0));
    }

    @Test
    public void testMatchesGuavaForRandomTexts() {
        Random random = new Random(7);
        for (int round = 0; round < 50_000; round++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(40);
            for (int position = 0; position < length; position++) {
                int kind = random.nextInt(10);
                if (kind < 6) {
                    text.append((char) (32 + random.nextInt(95)));
                } else if (kind < 8) {
                    text.append((char) (0xA0 + random.nextInt(0x700)));
                } else if (kind < 9) {
                    text.append((char) (0x4E00 + random.nextInt(0x1000)));
                } else {
                    text.appendCodePoint(0x1F300 + random.nextInt(0x200));
                }
            }
            int seed = random.nextInt();
            Assert.assertEquals(text.toString(), reference(text.toString(), seed), Murmur3.hash(text.toString(), seed));
        }
    }

    @Test
    public void testUnpairedSurrogateIsHashedAsAQuestionMark() {
        Assert.assertEquals(reference("a?b", 5), Murmur3.hash("a\uD83Db", 5));
        Assert.assertEquals(reference("a?b", 5), Murmur3.hash("a\uDC00b", 5));
        Assert.assertEquals(reference("ab?", 5), Murmur3.hash("ab\uD83D", 5));
        Assert.assertEquals(reference("a\uD83Db", 5), Murmur3.hash("a\uD83Db", 5));
    }

    /** Where the number of UTF-8 bytes of a character changes, and where surrogates are or are not a pair. */
    @Test
    public void testValuesAtTheBoundariesOfUtf8ArePinned() {
        assertPinned(0x42f98464, "\u007F");
        assertPinned(0xd0548d97, "\u0080");
        assertPinned(0x44edcf70, "\u07FF");
        assertPinned(0xff50d636, "\u0800");
        assertPinned(0x01e3759e, "\uD7FF");
        assertPinned(0x5542e990, "\uE000");
        assertPinned(0xeb5b2721, "\uFFFF");
        assertPinned(0x4d33c3ae, "\uD800\uDC00");
        assertPinned(0xb9597b4a, "\uDBFF\uDFFF");
        assertPinned(0xca2d51db, "\uD83D");
        assertPinned(0x436da830, "\uDC00\uD83D");
        assertPinned(0x3d7ab43e, "\uD83D\uD83D\uDE00");
        assertPinned(0xce1ad73c, "ab\u00E9");
        assertPinned(0xbf7e21f4, "abc\u00E9");
        assertPinned(0x4c144a19, "abcd\u00E9");
        assertPinned(0x36a058a4, "a\u20ACb\uD83D\uDE00c");
    }

    private static void assertPinned(int expected, String text) {
        int seed = Murmur3.hash("enable-checkout", 0);
        Assert.assertEquals(text, expected, Murmur3.hash(text, seed));
        Assert.assertEquals(text, reference(text, seed), Murmur3.hash(text, seed));
    }

    @Test
    public void testBucketsArePinned() {
        int seed = Murmur3.hash("enable-checkout", 0);

        Assert.assertEquals(1303119314, seed);
        Assert.assertEquals(5344, Murmur3.bucket("joe", seed));
        Assert.assertEquals(293, Murmur3.bucket("user-9", seed));
        Assert.assertEquals(1505, Murmur3.bucket("user-15", seed));
        Assert.assertEquals(7962, Murmur3.bucket("user-0", seed));
        Assert.assertEquals(4414, Murmur3.bucket("carla", seed));
        Assert.assertEquals(6525, Murmur3.bucket("Zoë", seed));
        Assert.assertEquals(8975, Murmur3.bucket("山田", seed));
        Assert.assertEquals(3411, Murmur3.bucket("carla", Murmur3.hash("spring-rollout", 0)));
    }

    @Test
    public void testBucketsAreSpreadEvenly() {
        int seed = Murmur3.hash("enable-checkout", 0);
        int firstTenPercent = 0;
        for (int user = 0; user < 100_000; user++) {
            int bucket = Murmur3.bucket("user-" + user, seed);
            Assert.assertTrue(bucket >= 0 && bucket < Murmur3.BUCKETS);
            if (bucket < 1000) {
                firstTenPercent++;
            }
        }
        Assert.assertEquals(9907, firstTenPercent);
    }

    private static int reference(String text, int seed) {
        return Hashing.murmur3_32(seed).hashBytes(text.getBytes(StandardCharsets.UTF_8)).asInt();
    }
}
