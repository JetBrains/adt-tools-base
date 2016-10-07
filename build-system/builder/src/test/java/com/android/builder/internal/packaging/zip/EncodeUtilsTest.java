/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EncodeUtilsTest {
    @Test
    public void canEncodeAsciiWithAsciiString() {
        assertTrue(EncodeUtils.canAsciiEncode("foo"));
    }

    @Test
    public void cannotEncodeAscuuWithUtf8String() {
        String greekInGreek ="\u3b53\ubb3b\ub3b7\u3bd3\ub93b\ua3ac";
        assertFalse(EncodeUtils.canAsciiEncode(greekInGreek));
    }

    @Test
    public void asciiEncodeAndDecode() {
        String text = "foo";
        GPFlags flags = GPFlags.make(false);

        byte[] encoded = EncodeUtils.encode(text, flags);
        assertArrayEquals(new byte[] { 0x66, 0x6f, 0x6f }, encoded);
        assertEquals(text, EncodeUtils.decode(encoded, flags));
    }

    @Test
    public void utf8EncodeAndDecode() {
        String kazakhCapital = "\u0410\u0441\u0442\u0430\u043d\u0430";
        GPFlags flags = GPFlags.make(true);

        byte[] encoded = EncodeUtils.encode(kazakhCapital, flags);
        assertArrayEquals(new byte[] { (byte) 0xd0, (byte) 0x90, (byte) 0xd1, (byte) 0x81,
                (byte) 0xd1, (byte) 0x82, (byte) 0xd0, (byte) 0xb0, (byte) 0xd0, (byte) 0xbd,
                (byte) 0xd0, (byte) 0xb0 }, encoded);
        assertEquals(kazakhCapital, EncodeUtils.decode(encoded, flags));
    }
}
