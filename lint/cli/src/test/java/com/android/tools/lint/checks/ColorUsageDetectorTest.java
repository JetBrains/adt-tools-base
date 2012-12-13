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
public class ColorUsageDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ColorUsageDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "src/test/pkg/WrongColor.java:9: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]\n" +
            "        paint2.setColor(R.color.blue);\n" +
            "                        ~~~~~~~~~~~~\n" +
            "src/test/pkg/WrongColor.java:11: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.red) [ResourceAsColor]\n" +
            "        textView.setTextColor(R.color.red);\n" +
            "                              ~~~~~~~~~~~\n" +
            "src/test/pkg/WrongColor.java:12: Error: Should pass resolved color instead of resource id here: getResources().getColor(android.R.color.red) [ResourceAsColor]\n" +
            "        textView.setTextColor(android.R.color.red);\n" +
            "                              ~~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n" +
            "",

            lintProject("src/test/pkg/WrongColor.java.txt=>src/test/pkg/WrongColor.java"));
    }
}
