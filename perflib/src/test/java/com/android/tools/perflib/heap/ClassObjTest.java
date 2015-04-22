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
package com.android.tools.perflib.heap;

import com.android.tools.perflib.heap.io.MemoryMappedFileBuffer;
import junit.framework.TestCase;

import java.io.File;

public class ClassObjTest extends TestCase {

  Snapshot mSnapshot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    File file = new File(getClass().getResource("/dialer.android-hprof").getFile());
    mSnapshot = (new HprofParser(new MemoryMappedFileBuffer(file))).parse();
  }

  public void testGetAllFieldsCount() {
    ClassObj application = mSnapshot.findClass("android.app.Application");
    assertNotNull(application);
    assertEquals(5, application.getAllFieldsCount());

    assertNull(application.getClassObj());

    ClassObj dialer = mSnapshot.findClass("com.android.dialer.DialerApplication");
    assertNotNull(dialer);
    assertEquals(5, dialer.getAllFieldsCount());
  }
}
