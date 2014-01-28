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

@SuppressWarnings("javadoc")
public class WebViewDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WebViewDetector();
    }

    public void test() throws Exception {
        assertEquals("res/layout/webview.xml:19: Error: Placing a <WebView> in a parent element that uses a wrap_content size can lead to subtle bugs; use match_parent [WebViewLayout]\n"
                + "        <WebView\n"
                + "        ^\n"
                + "    res/layout/webview.xml:16: <No location-specific message\n"
                + "1 errors, 0 warnings\n",

                lintFiles("res/layout/webview.xml"));
    }
}
