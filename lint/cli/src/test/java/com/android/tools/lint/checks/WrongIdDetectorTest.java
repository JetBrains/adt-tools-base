/*
 * Copyright (C) 2011 The Android Open Source Project
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
public class WrongIdDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WrongIdDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "res/layout/layout1.xml:14: Error: The id \"button5\" is not defined anywhere. Did you mean one of {button1, button2, button3, button4} ? [UnknownId]\n" +
            "        android:layout_alignBottom=\"@+id/button5\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:17: Error: The id \"my_id3\" is not defined anywhere. Did you mean my_id2 ? [UnknownId]\n" +
            "        android:layout_alignRight=\"@+id/my_id3\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:18: Error: The id \"my_id1\" is defined but not assigned to any views. Did you mean my_id2 ? [UnknownId]\n" +
            "        android:layout_alignTop=\"@+id/my_id1\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:15: Warning: The id \"my_id2\" is not referring to any views in this layout [UnknownIdInLayout]\n" +
            "        android:layout_alignLeft=\"@+id/my_id2\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 1 warnings\n" +
            "",

            lintProject(
                    "wrongid/layout1.xml=>res/layout/layout1.xml",
                    "wrongid/layout2.xml=>res/layout/layout2.xml",
                    "wrongid/ids.xml=>res/values/ids.xml"
        ));
    }

    public void testSingleFile() throws Exception {
        assertEquals(
            "res/layout/layout1.xml:14: Warning: The id \"button5\" is not referring to any views in this layout [UnknownIdInLayout]\n" +
            "        android:layout_alignBottom=\"@+id/button5\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:15: Warning: The id \"my_id2\" is not referring to any views in this layout [UnknownIdInLayout]\n" +
            "        android:layout_alignLeft=\"@+id/my_id2\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:17: Warning: The id \"my_id3\" is not referring to any views in this layout [UnknownIdInLayout]\n" +
            "        android:layout_alignRight=\"@+id/my_id3\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:18: Warning: The id \"my_id1\" is not referring to any views in this layout [UnknownIdInLayout]\n" +
            "        android:layout_alignTop=\"@+id/my_id1\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 4 warnings\n" +
            "",

            lintFiles("wrongid/layout1.xml=>res/layout/layout1.xml"));
    }

    public void testSuppressed() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "wrongid/ignorelayout1.xml=>res/layout/layout1.xml",
                    "wrongid/layout2.xml=>res/layout/layout2.xml",
                    "wrongid/ids.xml=>res/values/ids.xml"
        ));
    }

    public void testSuppressedSingleFile() throws Exception {
        assertEquals(
            "No warnings.",

            lintFiles("wrongid/ignorelayout1.xml=>res/layout/layout1.xml"));
    }

    public void testNewIdPrefix() throws Exception {
        assertEquals(
                "No warnings.",

                lintFiles("res/layout/default_item_badges.xml",
                          "res/layout/detailed_item.xml"));
    }

    public void testSiblings() throws Exception {
        assertEquals(""
                + "res/layout/siblings.xml:55: Error: @id/button5 is not a sibling in the same RelativeLayout [NotSibling]\n"
                + "        android:layout_alignTop=\"@id/button5\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/siblings.xml:56: Error: @id/button6 is not a sibling in the same RelativeLayout [NotSibling]\n"
                + "        android:layout_toRightOf=\"@id/button6\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/siblings.xml:63: Error: @+id/button5 is not a sibling in the same RelativeLayout [NotSibling]\n"
                + "        android:layout_alignTop=\"@+id/button5\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/siblings.xml:64: Error: @+id/button6 is not a sibling in the same RelativeLayout [NotSibling]\n"
                + "        android:layout_toRightOf=\"@+id/button6\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "4 errors, 0 warnings\n",

                lintFiles("res/layout/siblings.xml"));
    }
}
