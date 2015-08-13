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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class HandleTest extends TestCase {
  static final String handleString = "000102030405060708090a0b0c0d0e0f10111213";
  static final byte[] handleBytes = {
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
    0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13
  };

  public void testHandleEquality() {
    // Check handle identity.
    Handle handle1 = new Handle(handleBytes);
    assertEquals(handle1, handle1);

    // Check equality of two handles created with the same bytes.
    Handle handle2 = new Handle(handleBytes);
    assertEquals(handle1, handle2);
  }

  public void testHandleNonEquality() {
    Handle handle = new Handle(handleBytes);
    assertFalse(handle.equals(null));

    // Check that we're getting a different handle than the zero-bytes handle.
    Handle zeroHandle = new Handle(new byte[handleBytes.length]);
    assertNotSame(zeroHandle, handle);

    // Check that we're getting a different handle if only the last byte differs.
    byte[] handleLastDiffBytes = new byte[handleBytes.length];
    System.arraycopy(handleBytes, 0, handleLastDiffBytes, 0, handleBytes.length);
    handleLastDiffBytes[handleLastDiffBytes.length-1]++;
    Handle handleLastDiff = new Handle(handleLastDiffBytes);
    assertNotSame(handleLastDiff, handle);

    // Check that we're getting a different handle if only the first byte differs.
    byte[] handleFirstDiffBytes = new byte[handleBytes.length];
    System.arraycopy(handleBytes, 0, handleFirstDiffBytes, 0, handleBytes.length);
    handleLastDiffBytes[0]++;
    Handle handleFirstDiff = new Handle(handleFirstDiffBytes);
    assertNotSame(handleFirstDiff, handle);
  }

  public void testHandleToString() {
    Handle handle = new Handle(handleBytes);
    assertEquals(handleString, handle.toString());
  }

  public void testHandleAsKey() {
    Set<Handle> set = new HashSet<Handle>();

    Handle handle1 = new Handle(handleBytes);
    set.add(handle1);
    assertTrue(set.contains(handle1));
    assertEquals(1, set.size());

    // Two handles with the same bytes should be seen as the same set elements.
    Handle sameHandle = new Handle(handleBytes);
    set.add(sameHandle);
    assertTrue(set.contains(sameHandle));
    assertEquals(1, set.size());

    // Two handles with different bytes should be seen as separate elements in a set.
    Handle zeroHandle = new Handle(new byte[20]);
    set.add(zeroHandle);
    assertTrue(set.contains(zeroHandle));
    assertEquals(2, set.size());
  }

  public void testDecodeHandle() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(handleBytes);
    Decoder d = new Decoder(input);

    Handle handle = new Handle(handleBytes);
    Handle handleFromDecoder = new Handle(d);
    assertEquals(handle, handleFromDecoder);
  }
}
