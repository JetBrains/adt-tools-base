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
 * Test class for {@see GradleCoordinate}
 */
public class GradleCoordinateTest extends BaseTestCase {
  public void testParseCoordinateString() throws Exception {
    GradleCoordinate expected = new GradleCoordinate("a.b.c", "package", 5, 4, 2);
    GradleCoordinate actual = GradleCoordinate.parseCoordinateString("a.b.c:package:5.4.2");
    assertEquals(expected, actual);

    expected = new GradleCoordinate("a.b.c", "package", 5, 4, GradleCoordinate.PLUS_REV);
    actual = GradleCoordinate.parseCoordinateString("a.b.c:package:5.4.+");
    assertEquals(expected, actual);

    expected = new GradleCoordinate("a.b.c", "package", 5, GradleCoordinate.PLUS_REV);
    actual = GradleCoordinate.parseCoordinateString("a.b.c:package:5.+");
    assertEquals(expected, actual);

    expected = new GradleCoordinate("a.b.c", "package", GradleCoordinate.PLUS_REV);
    actual = GradleCoordinate.parseCoordinateString("a.b.c:package:+");
    assertEquals(expected, actual);
  }

  public void testToString() throws Exception {
    String expected = "a.b.c:package:5.4.2";
    String actual = new GradleCoordinate("a.b.c", "package", 5, 4, 2).toString();
    assertEquals(expected, actual);

    expected = "a.b.c:package:5.4.+";
    actual = new GradleCoordinate("a.b.c", "package", 5, 4, GradleCoordinate.PLUS_REV).toString();
    assertEquals(expected, actual);

    expected = "a.b.c:package:5.+";
    actual = new GradleCoordinate("a.b.c", "package", 5, GradleCoordinate.PLUS_REV).toString();
    assertEquals(expected, actual);

    expected = "a.b.c:package:+";
    actual = new GradleCoordinate("a.b.c", "package", GradleCoordinate.PLUS_REV).toString();
    assertEquals(expected, actual);
  }

  public void testIsSameArtifact() throws Exception {
    GradleCoordinate a = new GradleCoordinate("a.b.c", "package", 5, 4, 2);
    GradleCoordinate b = new GradleCoordinate("a.b.c", "package", 5, 5, 5);
    assertTrue(a.isSameArtifact(b));
    assertTrue(b.isSameArtifact(a));

    a = new GradleCoordinate("a.b", "package", 5, 4, 2);
    b = new GradleCoordinate("a.b.c", "package", 5, 5, 5);
    assertFalse(a.isSameArtifact(b));
    assertFalse(b.isSameArtifact(a));

    a = new GradleCoordinate("a.b.c", "package", 5, 4, 2);
    b = new GradleCoordinate("a.b.c", "feature", 5, 5, 5);
    assertFalse(a.isSameArtifact(b));
    assertFalse(b.isSameArtifact(a));
  }

  public void testCompareTo() throws Exception {
    GradleCoordinate a = new GradleCoordinate("a.b.c", "package", 5, 4, 2);
    GradleCoordinate b = new GradleCoordinate("a.b.c", "package", 5, 5, 5);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);

    a = new GradleCoordinate("a.b.c", "package", 5, 4, 10);
    b = new GradleCoordinate("a.b.c", "package", 5, 4, GradleCoordinate.PLUS_REV);
    assertTrue(a.compareTo(b) > 0);

    a = new GradleCoordinate("a.b.c", "package", 5, 6, GradleCoordinate.PLUS_REV);
    b = new GradleCoordinate("a.b.c", "package", 6, 0, 0);
    assertTrue(a.compareTo(b) < 0);

    a = new GradleCoordinate("a.b.c", "package", 5, 6, 0);
    b = new GradleCoordinate("a.b.c", "package", 5, 6, 0);
    assertTrue(a.compareTo(b) == 0);

    a = new GradleCoordinate("a.b.c", "package", 5, 4, 2);
    b = new GradleCoordinate("a.b.c", "feature", 5, 4, 2);

    assertTrue( (a.compareTo(b) < 0) == ("package".compareTo("feature") < 0));

    a = new GradleCoordinate("a.b.c", "package", 5, 6, 0);
    b = new GradleCoordinate("a.b.c", "package", 5, 6, GradleCoordinate.PLUS_REV);
    assertTrue(a.compareTo(b) > 0);

    a = new GradleCoordinate("a.b.c", "package", 5, 6, 0);
    b = new GradleCoordinate("a.b.c", "package", 5, GradleCoordinate.PLUS_REV);
    assertTrue(a.compareTo(b) > 0);
  }
}
