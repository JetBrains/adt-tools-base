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

package com.android.repository;

import junit.framework.TestCase;

import java.util.Arrays;

public class RevisionTest extends TestCase {

    public final void testRevision() {

        assertEquals("5", Revision.parseRevision("5").toString());
        assertEquals("5.0", Revision.parseRevision("5.0").toString());
        assertEquals("5.0.0", Revision.parseRevision("5.0.0").toString());
        assertEquals("5.1.4", Revision.parseRevision("5.1.4").toString());
        assertEquals("5.0.0", Revision.parseRevision("5", Revision.Precision.MICRO).toString());
        assertEquals("5.1.0", Revision.parseRevision("5.1", Revision.Precision.MICRO).toString());

        Revision p = new Revision(5);
        assertEquals(5, p.getMajor());
        assertEquals(Revision.IMPLICIT_MINOR_REV, p.getMinor());
        assertEquals(Revision.IMPLICIT_MICRO_REV, p.getMicro());
        assertEquals(Revision.NOT_A_PREVIEW, p.getPreview());
        assertFalse(p.isPreview());
        assertEquals("5", p.toShortString());
        assertEquals(p, Revision.parseRevision("5"));
        assertEquals("5", p.toString());
        assertEquals(p, Revision.parseRevision("5"));
        assertEquals("[5]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[5]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(5, 0);
        assertEquals(5, p.getMajor());
        assertEquals(0, p.getMinor());
        assertEquals(Revision.IMPLICIT_MICRO_REV, p.getMicro());
        assertEquals(Revision.NOT_A_PREVIEW, p.getPreview());
        assertFalse(p.isPreview());
        assertEquals("5", p.toShortString());
        assertEquals(new Revision(5), Revision.parseRevision("5"));
        assertEquals("5.0", p.toString());
        assertEquals(p, Revision.parseRevision("5.0"));
        assertEquals("[5, 0]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[5, 0]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(5, 0, 0);
        assertEquals(5, p.getMajor());
        assertEquals(0, p.getMinor());
        assertEquals(0, p.getMicro());
        assertEquals(Revision.NOT_A_PREVIEW, p.getPreview());
        assertFalse(p.isPreview());
        assertEquals("5", p.toShortString());
        assertEquals(new Revision(5), Revision.parseRevision("5"));
        assertEquals("5.0.0", p.toString());
        assertEquals(p, Revision.parseRevision("5.0.0"));
        assertEquals("[5, 0, 0]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[5, 0, 0]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(5, 0, 0, 6);
        assertEquals(5, p.getMajor());
        assertEquals(Revision.IMPLICIT_MINOR_REV, p.getMinor());
        assertEquals(Revision.IMPLICIT_MICRO_REV, p.getMicro());
        assertEquals(6, p.getPreview());
        assertTrue(p.isPreview());
        assertEquals("5 rc6", p.toShortString());
        assertEquals("5.0.0 rc6", p.toString());
        assertEquals(p, Revision.parseRevision("5.0.0 rc6"));
        assertEquals("5.0.0-rc6", Revision.parseRevision("5.0.0-rc6").toString());
        assertEquals("[5, 0, 0]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[5, 0, 0, 6]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(6, 7, 0);
        assertEquals(6, p.getMajor());
        assertEquals(7, p.getMinor());
        assertEquals(0, p.getMicro());
        assertEquals(0, p.getPreview());
        assertFalse(p.isPreview());
        assertEquals("6.7", p.toShortString());
        assertFalse(p.equals(Revision.parseRevision("6.7")));
        assertEquals(new Revision(6, 7), Revision.parseRevision("6.7"));
        assertEquals("6.7.0", p.toString());
        assertEquals(p, Revision.parseRevision("6.7.0"));
        assertEquals("[6, 7, 0]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[6, 7, 0]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(10, 11, 12, Revision.NOT_A_PREVIEW);
        assertEquals(10, p.getMajor());
        assertEquals(11, p.getMinor());
        assertEquals(12, p.getMicro());
        assertEquals(0, p.getPreview());
        assertFalse(p.isPreview());
        assertEquals("10.11.12", p.toShortString());
        assertEquals("10.11.12", p.toString());
        assertEquals("[10, 11, 12]",    Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[10, 11, 12, 0]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));

        p = new Revision(10, 11, 12, 13);
        assertEquals(10, p.getMajor());
        assertEquals(11, p.getMinor());
        assertEquals(12, p.getMicro());
        assertEquals(13, p.getPreview());
        assertTrue  (p.isPreview());
        assertEquals("10.11.12 rc13", p.toShortString());
        assertEquals("10.11.12 rc13", p.toString());
        assertEquals(p, Revision.parseRevision("10.11.12 rc13"));
        assertEquals(p, Revision.parseRevision("   10.11.12 rc13"));
        assertEquals(p, Revision.parseRevision("10.11.12 rc13   "));
        assertEquals(p, Revision.parseRevision("   10.11.12   rc13   "));
        assertEquals("[10, 11, 12]",     Arrays.toString(p.toIntArray(false /*includePreview*/)));
        assertEquals("[10, 11, 12, 13]", Arrays.toString(p.toIntArray(true  /*includePreview*/)));
    }

    public final void testParse() {
        Revision revision = Revision.parseRevision("1");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 0);
        assertEquals(revision.getMicro(), 0);
        assertFalse(revision.isPreview());

        revision = Revision.parseRevision("1.2");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 2);
        assertEquals(revision.getMicro(), 0);
        assertFalse(revision.isPreview());

        revision = Revision.parseRevision("1.2.3");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 2);
        assertEquals(revision.getMicro(), 3);
        assertFalse(revision.isPreview());

        revision = Revision.parseRevision("1.2.3-rc4");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 2);
        assertEquals(revision.getMicro(), 3);
        assertTrue(revision.isPreview());
        assertEquals(revision.getPreview(), 4);

        revision = Revision.parseRevision("1.2.3-alpha5");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 2);
        assertEquals(revision.getMicro(), 3);
        assertTrue(revision.isPreview());
        assertEquals(revision.getPreview(), 5);

        revision = Revision.parseRevision("1.2.3-beta6");
        assertNotNull(revision);
        assertEquals(revision.getMajor(), 1);
        assertEquals(revision.getMinor(), 2);
        assertEquals(revision.getMicro(), 3);
        assertTrue(revision.isPreview());
        assertEquals(revision.getPreview(), 6);

        try {
            Revision.parseRevision("1.2.3-preview");
            fail();
        } catch (NumberFormatException ignored) {}

        revision = Revision.safeParseRevision("1.2.3-preview");
        assertEquals(revision, Revision.NOT_SPECIFIED);
    }

    public final void testParseError() {
        String errorMsg = null;
        try {
            Revision.parseRevision("not a number");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertEquals("Invalid revision: not a number", errorMsg);

        errorMsg = null;
        try {
            Revision.parseRevision("5 .6 .7");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertEquals("Invalid revision: 5 .6 .7", errorMsg);

        errorMsg = null;
        try {
            Revision.parseRevision("5.0.0 preview 1");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertEquals("Invalid revision: 5.0.0 preview 1", errorMsg);

        errorMsg = null;
        try {
            Revision.parseRevision("  5.1.2 rc 42  ");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertEquals("Invalid revision:   5.1.2 rc 42  ", errorMsg);
    }

    public final void testCompareTo() {
        Revision s4 = new Revision(4);
        Revision i4 = new Revision(4);
        Revision g5 = new Revision(5, 1, 0, 6);
        Revision y5 = new Revision(5);
        Revision c5 = new Revision(5, 1, 0, 6);
        Revision o5 = new Revision(5, 0, 0, 7);
        Revision p5 = new Revision(5, 1, 0, 0);

        assertEquals(s4, i4);                   // 4.0.0-0 == 4.0.0-0
        assertEquals(g5, c5);                   // 5.1.0-6 == 5.1.0-6

        assertFalse(y5.equals(p5));             // 5.0.0-0 != 5.1.0-0
        assertFalse(g5.equals(p5));             // 5.1.0-6 != 5.1.0-0
        assertTrue (s4.compareTo(i4) == 0);     // 4.0.0-0 == 4.0.0-0
        assertTrue (s4.compareTo(y5)  < 0);     // 4.0.0-0  < 5.0.0-0
        assertTrue (y5.compareTo(y5) == 0);     // 5.0.0-0 == 5.0.0-0
        assertTrue (y5.compareTo(p5)  < 0);     // 5.0.0-0  < 5.1.0-0
        assertTrue (o5.compareTo(y5)  < 0);     // 5.0.0-7  < 5.0.0-0
        assertTrue (p5.compareTo(p5) == 0);     // 5.1.0-0 == 5.1.0-0
        assertTrue (c5.compareTo(p5)  < 0);     // 5.1.0-6  < 5.1.0-0
        assertTrue (p5.compareTo(c5)  > 0);     // 5.1.0-0  > 5.1.0-6
        assertTrue (p5.compareTo(o5)  > 0);     // 5.1.0-0  > 5.0.0-7
        assertTrue (c5.compareTo(o5)  > 0);     // 5.1.0-6  > 5.0.0-7
        assertTrue (o5.compareTo(o5) == 0);     // 5.0.0-7  > 5.0.0-7
    }
}