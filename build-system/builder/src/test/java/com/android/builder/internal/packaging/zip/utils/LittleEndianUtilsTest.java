/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip.utils;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import com.google.common.io.ByteSource;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Random;

public class LittleEndianUtilsTest {
    @Test
    public void read2Le() throws Exception {
        assertEquals(0x0102, LittleEndianUtils.readUnsigned2Le(ByteSource.wrap(
                new byte[] { 2, 1 })));
        assertEquals(0xfedc, LittleEndianUtils.readUnsigned2Le(ByteSource.wrap(
                new byte[] { (byte) 0xdc, (byte) 0xfe })));
    }

    @Test
    public void write2Le() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LittleEndianUtils.writeUnsigned2Le(out, 0x0102);
        assertArrayEquals(new byte[] { 2, 1 }, out.toByteArray());

        out = new ByteArrayOutputStream();
        LittleEndianUtils.writeUnsigned2Le(out, 0xfedc);
        assertArrayEquals(new byte[] { (byte) 0xdc, (byte) 0xfe }, out.toByteArray());
    }

    @Test
    public void readWrite2Le() throws Exception {
        Random r = new Random();

        int range = 0x0000ffff;

        int[] data = new int[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = r.nextInt(range);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int d : data) {
            LittleEndianUtils.writeUnsigned2Le(out, d);
        }

        ByteSource in = ByteSource.wrap(out.toByteArray());
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], LittleEndianUtils.readUnsigned2Le(in.slice(i * 2, 2)));
        }
    }

    @Test
    public void read4Le() throws Exception {
        assertEquals(0x01020304, LittleEndianUtils.readUnsigned4Le(ByteSource.wrap(
                new byte[] { 4, 3, 2, 1 })));
        assertEquals(0xfedcba98L, LittleEndianUtils.readUnsigned4Le(ByteSource.wrap(
                new byte[] { (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe })));
    }

    @Test
    public void write4Le() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LittleEndianUtils.writeUnsigned4Le(out, 0x01020304);
        assertArrayEquals(new byte[] { 4, 3, 2, 1 }, out.toByteArray());

        out = new ByteArrayOutputStream();
        LittleEndianUtils.writeUnsigned4Le(out, 0xfedcba98L);
        assertArrayEquals(new byte[] { (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe },
                out.toByteArray());
    }

    @Test
    public void readWrite4Le() throws Exception {
        Random r = new Random();

        long[] data = new long[1000];
        for (int i = 0; i < data.length; i++) {
            do {
                data[i] = r.nextInt() - (long) Integer.MIN_VALUE;
            } while (data[i] < 0);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long d : data) {
            LittleEndianUtils.writeUnsigned4Le(out, d);
        }

        ByteSource in = ByteSource.wrap(out.toByteArray());
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], LittleEndianUtils.readUnsigned4Le(in.slice(i * 4, 4)));
        }
    }
}
