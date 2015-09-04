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

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import junit.framework.TestCase;

import java.io.File;

public class ClassObjTest extends TestCase {

  Snapshot mSnapshot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    File file = new File(getClass().getResource("/dialer.android-hprof").getFile());
    mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
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

  public void testComparison() {
    ClassObj a = new ClassObj(1, null, "TestClassA", 0);
    ClassObj b = new ClassObj(1, null, "TestClassB", 0);
    ClassObj c = new ClassObj(2, null, "TestClassC", 0);
    ClassObj aAlt = new ClassObj(3, null, "TestClassA", 0);

    assertEquals(0, a.compareTo(a));
    assertEquals(0, a.compareTo(b)); // This is a weird test case, since IDs are supposed to be unique.
    assertTrue(c.compareTo(a) > 0);
    assertTrue(aAlt.compareTo(a) > 0);
  }

  public void testSubClassNameClash() {
    ClassObj superClass = new ClassObj(1, null, "TestClassA", 0);
    ClassObj subClass1 = new ClassObj(2, null, "SubClass", 0);
    ClassObj subClass2 = new ClassObj(3, null, "SubClass", 0);
    superClass.addSubclass(subClass1);
    superClass.addSubclass(subClass2);

    assertEquals(2, superClass.getSubclasses().size());
    assertTrue(superClass.getSubclasses().contains(subClass1));
    assertTrue(superClass.getSubclasses().contains(subClass2));
  }
}
