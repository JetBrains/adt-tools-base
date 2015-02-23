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

public class HandleTest extends TestCase {
  public void testHandleEquality() {
    byte[] handleBytes = { -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 };
    Handle handle1 = new Handle(handleBytes);
    Handle handle2 = new Handle(handleBytes);
    assertEquals(handle1, handle2);
  }

  public void testHandleNonEquality() {
    byte[] handleBytes = { -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 };
    Handle handle = new Handle(handleBytes);

    // Check that we're getting a different handle than the zero-bytes handle.
    byte[] zeroBytes = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    Handle zeroHandle = new Handle(zeroBytes);
    assertNotSame(zeroHandle, handle);

    // Check that we're getting a different handle if only the last byte differs.
    byte[] handleLastDiffBytes = { -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 0 };
    Handle handleLastDiff = new Handle(handleLastDiffBytes);
    assertNotSame(handleLastDiff, handle);

    // Check that we're getting a different handle if only the first byte differs.
    byte[] handleFirstDiffBytes = { 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 };
    Handle handleFirstDiff = new Handle(handleFirstDiffBytes);
    assertNotSame(handleFirstDiff, handle);
  }
}
