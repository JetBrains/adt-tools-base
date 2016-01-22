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
package com.android.ide.common.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GradleVersionTest {

    @Test
    public void testParseOneSegment() {
        GradleVersion version = GradleVersion.parse("2");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("2", version.toString());
    }

    @Test
    public void testParseOneSegmentWithPreview() {
        GradleVersion version = GradleVersion.parse("2-alpha1");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(1, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("2-alpha1", version.toString());
    }

    @Test
    public void testParseOneSegmentWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("2-alpha1-SNAPSHOT");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(1, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-alpha1-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseOneSegmentWithSnapshot() {
        GradleVersion version = GradleVersion.parse("2-SNAPSHOT");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseTwoSegments() {
        GradleVersion version = GradleVersion.parse("1.2");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithPreview() {
        GradleVersion version = GradleVersion.parse("1.2-alpha3");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(3, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2-alpha3", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2-alpha3-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(3, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2-alpha3-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseThreeSegments() {
        GradleVersion version = GradleVersion.parse("1.2.3");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreview() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha4");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3-alpha4", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha4-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2.3-alpha4-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2.3-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion1() {
        GradleVersion.parse("a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion2() {
        GradleVersion.parse("a.b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion3() {
        GradleVersion.parse("a.b.c");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion4() {
        GradleVersion.parse("1.2.3-foo-bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion5() {
        GradleVersion.parse("1.2.3-1-2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion6() {
        GradleVersion.parse("1.2.3-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion7() {
        GradleVersion.parse("1.2.3.4");
    }

    @Test
    public void testCompare() {
        assertEquals(0, GradleVersion.parse("1.0.0").compareTo("1.0.0"));
        assertEquals(0, GradleVersion.parse("1.0.0-alpha1").compareTo("1.0.0-alpha1"));
        assertEquals(0, GradleVersion.parse("1.0.0-SNAPSHOT").compareTo("1.0.0-SNAPSHOT"));

        assertTrue(GradleVersion.parse("1.0.1").compareTo("1.0.0") > 0);

        assertTrue(GradleVersion.parse("1.0.1").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.1.0").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.1.1").compareTo("1.0.0") > 0);

        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.1") < 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.1.0") < 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.1.1") < 0);

        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.0-alpha1") > 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.0-SNAPSHOT") > 0);
        assertTrue(GradleVersion.parse("1.0.0-alpha2").compareTo("1.0.0-alpha1") > 0);
        assertTrue(GradleVersion.parse("1.0.0-beta1").compareTo("1.0.0-alpha2") > 0);
        assertTrue(GradleVersion.parse("1.0.0-rc1").compareTo("1.0.0-alpha2") > 0);
        assertTrue(GradleVersion.parse("2.0.0-alpha1").compareTo("1.0.0-alpha1") > 0);
    }

    @Test
    public void testCompareWithExcludeAllComparison() {
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0"));
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0-alpha1"));
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0-SNAPSHOT"));
    }
}