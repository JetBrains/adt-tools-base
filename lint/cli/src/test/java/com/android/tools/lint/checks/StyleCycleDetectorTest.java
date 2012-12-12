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
public class StyleCycleDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new StyleCycleDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "res/values/styles.xml:9: Error: Style DetailsPage_EditorialBuyButton should not extend itself [StyleCycle]\n" +
            "<style name=\"DetailsPage_EditorialBuyButton\" parent=\"@style/DetailsPage_EditorialBuyButton\" />\n" +
            "                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/styles.xml"));
    }

    public void test2() throws Exception {
        assertEquals(
            "res/values/stylecycle.xml:3: Error: Potential cycle: PropertyToggle is the implied parent of PropertyToggle.Base and this defines the opposite [StyleCycle]\n" +
            "  <style name=\"PropertyToggle\" parent=\"@style/PropertyToggle.Base\"></style>\n" +
            "                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/stylecycle.xml"));
    }
}
