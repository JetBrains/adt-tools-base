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
package com.android.tools.rpclib.binary;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EncoderTest extends TestCase {
  public void testEncodeBool() throws IOException {
    final boolean[] input = new boolean[]{true, false};
    final byte[] expected = new byte[]{(byte)0x01, (byte)0x00};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (boolean bool : input) {
      e.bool(bool);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt8() throws IOException {
    final byte[] input = new byte[]{0, 127, -128, -1};
    final byte[] expected = new byte[]{(byte)0x00, (byte)0x7f, (byte)0x80, (byte)0xff};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (byte s8 : input) {
      e.int8(s8);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint8() throws IOException {
    final short[] input = new short[]{0x00, 0x7f, 0x80, 0xff};
    final byte[] expected = new byte[]{(byte)0x00, (byte)0x7f, (byte)0x80, (byte)0xff};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (short u8 : input) {
      e.uint8(u8);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt16() throws IOException {
    final short[] input = new short[]{0, 32767, -32768, -1};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00,
      (byte)0xff, (byte)0x7f,
      (byte)0x00, (byte)0x80,
      (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (short s16 : input) {
      e.int16(s16);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint16() throws IOException {
    final int[] input = new int[]{0, 0xbeef, 0xc0de};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00,
      (byte)0xef, (byte)0xbe,
      (byte)0xde, (byte)0xc0
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (int u16 : input) {
      e.uint16(u16);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt32() throws IOException {
    final int[] input = new int[]{0, 2147483647, -2147483648, -1};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x80,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (int s32 : input) {
      e.int32(s32);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint32() throws IOException {
    final long[] input = new long[]{0, 0x01234567, 0x10abcdef};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x67, (byte)0x45, (byte)0x23, (byte)0x01,
      (byte)0xef, (byte)0xcd, (byte)0xab, (byte)0x10
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (long u32 : input) {
      e.uint32(u32);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt64() throws IOException {
    final long[] input = new long[]{0L, 9223372036854775807L, -9223372036854775808L, -1L};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,

      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,

      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x80,

      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (long s64 : input) {
      e.int64(s64);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint64() throws IOException {
    final long[] input = new long[]{0L, 0x0123456789abcdefL, 0xfedcba9876543210L};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,

      (byte)0xef, (byte)0xcd, (byte)0xab, (byte)0x89,
      (byte)0x67, (byte)0x45, (byte)0x23, (byte)0x01,

      (byte)0x10, (byte)0x32, (byte)0x54, (byte)0x76,
      (byte)0x98, (byte)0xba, (byte)0xdc, (byte)0xfe
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (long u64 : input) {
      e.uint64(u64);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeFloat32() throws IOException {
    final float[] input = new float[]{0.F, 1.F, 64.5F};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
      (byte)0x00, (byte)0x00, (byte)0x81, (byte)0x42,
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (float f32 : input) {
      e.float32(f32);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeFloat64() throws IOException {
    final double[] input = new double[]{0.D, 1.D, 64.5D};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,

      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,

      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x20, (byte)0x50, (byte)0x40
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (double f64 : input) {
      e.float64(f64);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeString() throws IOException {
    final String[] input = new String[]{null, "Hello", "", "World", "こんにちは世界"};
    final byte[] expected = new byte[]{
      0x00, 0x00, 0x00, 0x00, // null string
      0x05, 0x00, 0x00, 0x00, 'H', 'e', 'l', 'l', 'o',
      0x00, 0x00, 0x00, 0x00, // empty string
      0x05, 0x00, 0x00, 0x00, 'W', 'o', 'r', 'l', 'd',

      0x15, 0x00, 0x00, 0x00,
      (byte)0xe3, (byte)0x81, (byte)0x93, (byte)0xe3, (byte)0x82, (byte)0x93, (byte)0xe3,
      (byte)0x81, (byte)0xab, (byte)0xe3, (byte)0x81, (byte)0xa1, (byte)0xe3, (byte)0x81,
      (byte)0xaf, (byte)0xe4, (byte)0xb8, (byte)0x96, (byte)0xe7, (byte)0x95, (byte)0x8c
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (String str : input) {
      e.string(str);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeObject() throws IOException {
    final byte[] dummyObjectTypeIDBytes = new byte[]{
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09
    };
    class DummyObject implements BinaryObject {
      final String dummy = "dummy";
      @Override
      public ObjectTypeID type() {
        return new ObjectTypeID(dummyObjectTypeIDBytes);
      }
      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        assert d.string().equals(dummy);
      }
      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        e.string(dummy);
      }
    }

    final BinaryObject dummyObject = new DummyObject();
    final BinaryObject[] input = new BinaryObject[]{null, dummyObject, dummyObject};
    byte[] expected = null;

    ByteArrayOutputStream expectedStream = new ByteArrayOutputStream();

    // null BinaryObject:
    expectedStream.write(new byte[]{(byte)0xff, (byte)0xff}); // BinaryObject.NULL_ID

    // dummyObject:
    expectedStream.write(new byte[]{0x00, 0x00}); // dummyObject reference
    expectedStream.write(dummyObjectTypeIDBytes); // dummyObject.type()
    expectedStream.write(new byte[]{0x05, 0x00, 0x00, 0x00, 'd', 'u', 'm', 'm', 'y'});

    // dummyObject again, only by reference this time:
    expectedStream.write(new byte[]{0x00, 0x00}); // dummyObject reference
    expected = expectedStream.toByteArray();

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    for (BinaryObject obj : input) {
      e.object(obj);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }
}
