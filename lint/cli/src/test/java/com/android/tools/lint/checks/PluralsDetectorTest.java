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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class PluralsDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new PluralsDetector();
    }

    public void test1() throws Exception {
        assertEquals(""
                + "res/values-pl/plurals2.xml:3: Warning: For locale \"pl\" the following quantities should also be defined: many [MissingQuantity]\n"
                + "    <plurals name=\"numberOfSongsAvailable\">\n"
                + "    ^\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                 "res/values/plurals.xml",
                 "res/values/plurals2.xml",
                 "res/values-pl/plurals2.xml"));
    }

    public void test2() throws Exception {
        assertEquals(""
                + "res/values-zh-rCN/plurals3.xml:3: Warning: For language \"zh\" the following quantities are not relevant: one [MissingQuantity]\n"
                + "  <plurals name=\"draft\">\n"
                + "  ^\n"
                + "res/values-cs/plurals3.xml:3: Warning: For locale \"cs\" the following quantities should also be defined: few [MissingQuantity]\n"
                + "  <plurals name=\"draft\">\n"
                + "  ^\n"
                + "res/values-zh-rCN/plurals3.xml:7: Warning: For language \"zh\" the following quantities are not relevant: one [MissingQuantity]\n"
                + "  <plurals name=\"title_day_dialog_content\">\n"
                + "  ^\n"
                + "0 errors, 3 warnings\n",

                lintProject(
                        "res/values-zh-rCN/plurals3.xml",
                        "res/values-cs/plurals3.xml"));
    }
}
