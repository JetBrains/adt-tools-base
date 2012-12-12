/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class Utf8DetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new Utf8Detector();
    }

    public void test() throws Exception {
        assertEquals(
            "res/layout/encoding.xml:1: Warning: iso-latin-1: Not using UTF-8 as the file encoding. This can lead to subtle bugs with non-ascii characters [EnforceUTF8]\n" +
            "<?xml version=\"1.0\" encoding=\"iso-latin-1\"?>\n" +
            "                              ~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",
            lintProject("res/layout/encoding.xml"));
    }

    public void testWithR() throws Exception {
        assertEquals(
            "res/layout/encoding2.xml:1: Warning: iso-latin-1: Not using UTF-8 as the file encoding. This can lead to subtle bugs with non-ascii characters [EnforceUTF8]\n" +
            "<?xml version=\"1.0\" encoding=\"iso-latin-1\"?>\n" +
            "                              ~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",
            // encoding2.xml = encoding.xml but with \n => \r
            lintProject("res/layout/encoding2.xml"));
    }

    public void testNegative() throws Exception {
        // Make sure we don't get warnings for a correct file
        assertEquals(
            "No warnings.",
            lintProject("res/layout/layout1.xml"));
    }

}
