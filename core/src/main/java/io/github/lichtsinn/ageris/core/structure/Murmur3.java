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

/**
 * The murmur3 hash, x86 32-bit variant, over the UTF-8 bytes of a text. A percentage in a rule puts a caller's value into one of
 * {@link #BUCKETS} buckets with it. It creates no objects.
 *
 * The algorithm and the number of buckets must never change: a change would move callers between buckets.
 */
final class Murmur3 {

    /** Number of buckets a hash is reduced to. A percentage with two decimals is a whole number of buckets. */
    static final int BUCKETS = 10_000;

    private static final int FIRST_FACTOR = 0xcc9e2d51;
    private static final int SECOND_FACTOR = 0x1b873593;
    private static final int BLOCK_BITS = 32;
    private static final int BYTE_BITS = 8;
    private static final int ONE_BYTE_LIMIT = 0x80;
    private static final int TWO_BYTE_LIMIT = 0x800;
    private static final int THREE_BYTE_LIMIT = 0x10000;

    private Murmur3() {
        // Static methods only.
    }

    /**
     * @param text text to hash, as its UTF-8 bytes; an unpaired surrogate counts as a question mark, as in String.getBytes
     * @param seed seed of the hash
     * @return the 32-bit hash
     */
    static int hash(String text, int seed) {
        int hash = seed;
        /* The bytes not hashed yet, the first one in the lowest eight bits. Up to three left over and four new ones fit a long. */
        long pending = 0;
        int pendingBits = 0;
        int byteCount = 0;
        final int charCount = text.length();
        int position = 0;
        while (position < charCount) {
            final int codePoint = codePointAt(text, position);
            position += Character.charCount(codePoint);
            pending |= (utf8(codePoint) & 0xFFFFFFFFL) << pendingBits;
            pendingBits += BYTE_BITS * utf8Length(codePoint);
            byteCount += utf8Length(codePoint);
            if (pendingBits >= BLOCK_BITS) {
                hash = Integer.rotateLeft(hash ^ scramble((int) pending), 13) * 5 + 0xe6546b64;
                pending >>>= BLOCK_BITS;
                pendingBits -= BLOCK_BITS;
            }
        }
        /* The last one to three bytes. With none left this changes nothing, since zero scrambles to zero. */
        return finish(hash ^ scramble((int) pending) ^ byteCount);
    }

    /**
     * @param value a caller's value
     * @param seed seed of the hash, which is the hash of the name of the configuration
     * @return the bucket of the value, from 0 to {@link #BUCKETS} - 1
     */
    static int bucket(String value, int seed) {
        return Integer.remainderUnsigned(hash(value, seed), BUCKETS);
    }

    /** The code point at a position of a text, where an unpaired surrogate counts as a question mark. */
    private static int codePointAt(String text, int position) {
        final int codePoint = text.codePointAt(position);
        return codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT && Character.isSurrogate((char) codePoint) ? '?' : codePoint;
    }

    private static int scramble(int block) {
        return Integer.rotateLeft(block * FIRST_FACTOR, 15) * SECOND_FACTOR;
    }

    private static int finish(int mixed) {
        int hash = mixed;
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }

    /** The UTF-8 bytes of a code point, the first byte in the lowest eight bits. */
    private static int utf8(int codePoint) {
        int encoded;
        if (codePoint < ONE_BYTE_LIMIT) {
            encoded = codePoint;
        } else if (codePoint < TWO_BYTE_LIMIT) {
            encoded = 0xC0 | codePoint >> 6 | (0x80 | codePoint & 0x3F) << 8;
        } else if (codePoint < THREE_BYTE_LIMIT) {
            encoded = 0xE0 | codePoint >> 12 | (0x80 | codePoint >> 6 & 0x3F) << 8 | (0x80 | codePoint & 0x3F) << 16;
        } else {
            encoded = 0xF0 | codePoint >> 18 | (0x80 | codePoint >> 12 & 0x3F) << 8 | (0x80 | codePoint >> 6 & 0x3F) << 16
                    | (0x80 | codePoint & 0x3F) << 24;
        }
        return encoded;
    }

    private static int utf8Length(int codePoint) {
        int length = 4;
        if (codePoint < ONE_BYTE_LIMIT) {
            length = 1;
        } else if (codePoint < TWO_BYTE_LIMIT) {
            length = 2;
        } else if (codePoint < THREE_BYTE_LIMIT) {
            length = 3;
        }
        return length;
    }
}
