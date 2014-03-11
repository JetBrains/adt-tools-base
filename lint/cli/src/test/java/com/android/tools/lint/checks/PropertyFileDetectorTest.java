/*
 * Copyright (C) 2014 The Android Open Source Project
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

public class PropertyFileDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new PropertyFileDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "local.properties:11: Error: Colon (:) must be escaped in .property files [PropertyEscape]\n"
                + "windows.dir=C:\\my\\path\\to\\sdk\n"
                + "             ~\n"
                + "local.properties:11: Error: Windows file separators (\\) must be escaped (\\\\); use C:\\\\my\\\\path\\\\to\\\\sdk [PropertyEscape]\n"
                + "windows.dir=C:\\my\\path\\to\\sdk\n"
                + "            ~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",
                lintProject("local.properties=>local.properties"));
    }
}
