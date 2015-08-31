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
public class HardcodedValuesDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new HardcodedValuesDetector();
    }

    public void testStrings() throws Exception {
        assertEquals(
            "res/layout/accessibility.xml:3: Warning: [I18N] Hardcoded string \"Button\", should use @string resource [HardcodedText]\n" +
            "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/accessibility.xml:6: Warning: [I18N] Hardcoded string \"Button\", should use @string resource [HardcodedText]\n" +
            "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 2 warnings\n",

            lintFiles("res/layout/accessibility.xml"));
    }

    public void testMenus() throws Exception {
        assertEquals(
            "res/menu/menu.xml:7: Warning: [I18N] Hardcoded string \"My title 1\", should use @string resource [HardcodedText]\n" +
            "        android:title=\"My title 1\">\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/menu/menu.xml:13: Warning: [I18N] Hardcoded string \"My title 2\", should use @string resource [HardcodedText]\n" +
            "        android:title=\"My title 2\">\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 2 warnings\n",

            lintFiles("res/menu/menu.xml"));
    }

    public void testMenusOk() throws Exception {
        assertEquals(
            "No warnings.",
            lintFiles("res/menu/titles.xml"));
    }

    public void testSuppress() throws Exception {
        // All but one errors in the file contain ignore attributes - direct, inherited
        // and lists
        assertEquals(
            "res/layout/ignores.xml:61: Warning: [I18N] Hardcoded string \"Hardcoded\", should use @string resource [HardcodedText]\n" +
            "        android:text=\"Hardcoded\"\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintFiles("res/layout/ignores.xml"));
    }

    public void testSuppressViaComment() throws Exception {
        assertEquals(""
                + "res/layout/ignores2.xml:51: Warning: [I18N] Hardcoded string \"Hardcoded\", should use @string resource [HardcodedText]\n"
                + "        android:text=\"Hardcoded\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintFiles("res/layout/ignores2.xml"));
    }

    public void testSkippingPlaceHolders() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        xml("res/layout/test.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Hello World!\" />\n"
                        + "\n"
                        + "    <Button\n"
                        + "        android:id=\"@+id/button\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"New Button\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView2\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Large Text\"\n"
                        + "        android:textAppearance=\"?android:attr/textAppearanceLarge\" />\n"
                        + "\n"
                        + "    <Button\n"
                        + "        android:id=\"@+id/button2\"\n"
                        + "        style=\"?android:attr/buttonStyleSmall\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"New Button\" />\n"
                        + "\n"
                        + "    <CheckBox\n"
                        + "        android:id=\"@+id/checkBox\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"New CheckBox\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView3\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"New Text\" />\n"
                        + "</LinearLayout>\n")));
    }

    public void testAppRestrictions() throws Exception {
        // Sample from https://developer.android.com/samples/AppRestrictionSchema/index.html
        assertEquals(""
                        + "res/xml/app_restrictions.xml:12: Warning: [I18N] Hardcoded string \"Hardcoded description\", should use @string resource [HardcodedText]\n"
                        + "        android:description=\"Hardcoded description\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/xml/app_restrictions.xml:15: Warning: [I18N] Hardcoded string \"Hardcoded title\", should use @string resource [HardcodedText]\n"
                        + "        android:title=\"Hardcoded title\"/>\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@bool/default_can_say_hello\"\n"
                                + "        android:description=\"@string/description_can_say_hello\"\n"
                                + "        android:key=\"can_say_hello\"\n"
                                + "        android:restrictionType=\"bool\"\n"
                                + "        android:title=\"@string/title_can_say_hello\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"Hardcoded default value\"\n"
                                + "        android:description=\"Hardcoded description\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"string\"\n"
                                + "        android:title=\"Hardcoded title\"/>\n"
                                + " \n"
                                + "</restrictions>"),
                        xml("res/xml/random_file.xml", ""
                                + "<myRoot xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " \n"
                                + "    <myElement\n"
                                + "        android:description=\"Hardcoded description\"\n"
                                + "        android:title=\"Hardcoded title\"/>\n"
                                + " \n"
                                + "</myRoot>")
                ));
    }
}
