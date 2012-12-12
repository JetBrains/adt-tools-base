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
public class DuplicateResourceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new DuplicateResourceDetector();
    }

    public void test() throws Exception {
        assertEquals(
        "res/values/customattr2.xml:2: Error: ContentFrame has already been defined in this folder [DuplicateDefinition]\n" +
        "    <declare-styleable name=\"ContentFrame\">\n" +
        "                       ~~~~~~~~~~~~~~~~~~~\n" +
        "    res/values/customattr.xml:2: Previously defined here\n" +
        "res/values/strings2.xml:19: Error: wallpaper_instructions has already been defined in this folder [DuplicateDefinition]\n" +
        "    <string name=\"wallpaper_instructions\">Tap image to set landscape wallpaper</string>\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "    res/values/strings.xml:29: Previously defined here\n" +
        "2 errors, 0 warnings\n",

        lintProject(
                "res/values/strings.xml",
                "res/values-land/strings.xml=>res/values/strings2.xml",
                "res/values-cs/strings.xml",
                "res/values/customattr.xml",
                "res/values/customattr.xml=>res/values/customattr2.xml"));
    }

    public void testOk() throws Exception {
        assertEquals(
        "No warnings.",

        lintProject(
                "res/values/strings.xml",
                "res/values-cs/strings.xml",
                "res/values-de-rDE/strings.xml",
                "res/values-es/strings.xml",
                "res/values-es-rUS/strings.xml",
                "res/values-land/strings.xml",
                "res/values-cs/arrays.xml",
                "res/values-es/donottranslate.xml",
                "res/values-nl-rNL/strings.xml"));
    }
}
