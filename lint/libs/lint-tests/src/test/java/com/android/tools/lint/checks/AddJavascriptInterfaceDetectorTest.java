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

public class AddJavascriptInterfaceDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new AddJavascriptInterfaceDetector();
    }

    public void test() throws Exception {
        assertEquals(""
            + "src/test/pkg/AddJavascriptInterfaceTest.java:16: Warning: WebView.addJavascriptInterface should not be called with minSdkVersion < 17 for security reasons: JavaScript can use reflection to manipulate application [AddJavascriptInterface]\n"
            + "            webView.addJavascriptInterface(object, string);\n"
            + "                    ~~~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/AddJavascriptInterfaceTest.java:23: Warning: WebView.addJavascriptInterface should not be called with minSdkVersion < 17 for security reasons: JavaScript can use reflection to manipulate application [AddJavascriptInterface]\n"
            + "            webView.addJavascriptInterface(object, string);\n"
            + "                    ~~~~~~~~~~~~~~~~~~~~~~\n"
            + "0 errors, 2 warnings\n",

            lintProject(
                    manifest().minSdk(10),
                    mTestFile
            ));
    }

    public void testNoWarningWhenMinSdkAt17() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject(
                    manifest().minSdk(17),
                    mTestFile
            ));
    }

    @SuppressWarnings("all")
    private TestFile mTestFile = java("src/test/pkg/AddJavascriptInterfaceTest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.webkit.WebView;\n"
            + "import android.content.Context;\n"
            + "\n"
            + "\n"
            + "public class AddJavascriptInterfaceTest {\n"
            + "    private static class WebViewChild extends WebView {\n"
            + "        WebViewChild(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static class CallAddJavascriptInterfaceOnWebView {\n"
            + "        public void addJavascriptInterfaceToWebView(WebView webView, Object object, String string) {\n"
            + "            webView.addJavascriptInterface(object, string);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static class CallAddJavascriptInterfaceOnWebViewChild {\n"
            + "        public void addJavascriptInterfaceToWebViewChild(\n"
            + "            WebViewChild webView, Object object, String string) {\n"
            + "            webView.addJavascriptInterface(object, string);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static class NonWebView {\n"
            + "        public void addJavascriptInterface(Object object, String string) { }\n"
            + "    }\n"
            + "\n"
            + "    private static class CallAddJavascriptInterfaceOnNonWebView {\n"
            + "        public void addJavascriptInterfaceToNonWebView(\n"
            + "            NonWebView webView, Object object, String string) {\n"
            + "            webView.addJavascriptInterface(object, string);\n"
            + "        }\n"
            + "    }\n"
            + "}");
}
