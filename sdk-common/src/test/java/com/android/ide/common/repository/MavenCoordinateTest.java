/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.repository;

import com.android.ide.common.res2.BaseTestCase;

/**
 * Test class for {@see MavenCoordinate}
 */
public class MavenCoordinateTest extends BaseTestCase {
  public void testParseCoordinateString() throws Exception {
    MavenCoordinate expected = new MavenCoordinate("a.b.c", "package", 5, 4, 2);
    MavenCoordinate actual = MavenCoordinate.parseCoordinateString("a.b.c:package:5.4.2");
    assertEquals(expected, actual);

    expected = new MavenCoordinate("a.b.c", "package", 5, 4, MavenCoordinate.PLUS_REV);
    actual = MavenCoordinate.parseCoordinateString("a.b.c:package:5.4.+");
    assertEquals(expected, actual);
  }

  public void testToString() throws Exception {
    String expected = "a.b.c:package:5.4.2";
    String actual = new MavenCoordinate("a.b.c", "package", 5, 4, 2).toString();
    assertEquals(expected, actual);

    expected = "a.b.c:package:5.4.+";
    actual = new MavenCoordinate("a.b.c", "package", 5, 4, MavenCoordinate.PLUS_REV).toString();
    assertEquals(expected, actual);
  }

  public void testIsSameArtifact() throws Exception {
    MavenCoordinate a = new MavenCoordinate("a.b.c", "package", 5, 4, 2);
    MavenCoordinate b = new MavenCoordinate("a.b.c", "package", 5, 5, 5);
    assertTrue(a.isSameArtifact(b));
    assertTrue(b.isSameArtifact(a));

    a = new MavenCoordinate("a.b", "package", 5, 4, 2);
    b = new MavenCoordinate("a.b.c", "package", 5, 5, 5);
    assertFalse(a.isSameArtifact(b));
    assertFalse(b.isSameArtifact(a));

    a = new MavenCoordinate("a.b.c", "package", 5, 4, 2);
    b = new MavenCoordinate("a.b.c", "feature", 5, 5, 5);
    assertFalse(a.isSameArtifact(b));
    assertFalse(b.isSameArtifact(a));
  }

  public void testCompareTo() throws Exception {
    MavenCoordinate a = new MavenCoordinate("a.b.c", "package", 5, 4, 2);
    MavenCoordinate b = new MavenCoordinate("a.b.c", "package", 5, 5, 5);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);

    a = new MavenCoordinate("a.b.c", "package", 5, 4, 10);
    b = new MavenCoordinate("a.b.c", "package", 5, 4, MavenCoordinate.PLUS_REV);
    assertTrue(a.compareTo(b) < 0);

    a = new MavenCoordinate("a.b.c", "package", 5, 6, MavenCoordinate.PLUS_REV);
    b = new MavenCoordinate("a.b.c", "package", 6, 0, 0);
    assertTrue(a.compareTo(b) < 0);

    a = new MavenCoordinate("a.b.c", "package", 5, 6, 0);
    b = new MavenCoordinate("a.b.c", "package", 5, 6, 0);
    assertTrue(a.compareTo(b) == 0);

    a = new MavenCoordinate("a.b.c", "package", 5, 4, 2);
    b = new MavenCoordinate("a.b.c", "feature", 5, 4, 2);

    try {
      a.compareTo(b); // Should throw exception
      fail("Expected assertion failure.");
    } catch (AssertionError e) {

    }
  }
}
