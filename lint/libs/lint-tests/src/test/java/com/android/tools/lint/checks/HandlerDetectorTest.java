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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class HandlerDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new HandlerDetector();
    }

    public void testRegistered() throws Exception {
        assertEquals(""
                + "src/test/pkg/HandlerTest.java:12: Warning: This Handler class should be static or leaks might occur (test.pkg.HandlerTest.Inner) [HandlerLeak]\n"
                + "    public class Inner extends Handler { // ERROR\n"
                + "                 ~~~~~\n"
                + "src/test/pkg/HandlerTest.java:18: Warning: This Handler class should be static or leaks might occur (anonymous android.os.Handler) [HandlerLeak]\n"
                + "        Handler anonymous = new Handler() { // ERROR\n"
                + "                            ~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                    java("src/test/pkg/HandlerTest.java", ""
                            + "package test.pkg;\n"
                            + "import android.os.Looper;\n"
                            + "import android.os.Handler;\n"
                            + "import android.os.Message;\n"
                            + "\n"
                            + "public class HandlerTest extends Handler { // OK\n"
                            + "    public static class StaticInner extends Handler { // OK\n"
                            + "        public void dispatchMessage(Message msg) {\n"
                            + "            super.dispatchMessage(msg);\n"
                            + "        };\n"
                            + "    }\n"
                            + "    public class Inner extends Handler { // ERROR\n"
                            + "        public void dispatchMessage(Message msg) {\n"
                            + "            super.dispatchMessage(msg);\n"
                            + "        };\n"
                            + "    }\n"
                            + "    void method() {\n"
                            + "        Handler anonymous = new Handler() { // ERROR\n"
                            + "            public void dispatchMessage(Message msg) {\n"
                            + "                super.dispatchMessage(msg);\n"
                            + "            };\n"
                            + "        };\n"
                            + "\n"
                            + "        Looper looper = null;\n"
                            + "        Handler anonymous2 = new Handler(looper) { // OK\n"
                            + "            public void dispatchMessage(Message msg) {\n"
                            + "                super.dispatchMessage(msg);\n"
                            + "            };\n"
                            + "        };\n"
                            + "    }\n"
                            + "\n"
                            + "    public class WithArbitraryLooper extends Handler {\n"
                            + "        public WithArbitraryLooper(String unused, Looper looper) { // OK\n"
                            + "            super(looper, null);\n"
                            + "        }\n"
                            + "\n"
                            + "        public void dispatchMessage(Message msg) {\n"
                            + "            super.dispatchMessage(msg);\n"
                            + "        };\n"
                            + "    }\n"
                            + "}\n")
            ));
    }

    public void testSuppress() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/CheckActivity.java", ""
                                + "package test.pkg;\n"
                                + "import android.annotation.SuppressLint;\n"
                                + "import android.app.Activity;\n"
                                + "import android.os.Handler;\n"
                                + "import android.os.Message;\n"
                                + "\n"
                                + "public class CheckActivity extends Activity {\n"
                                + "\n"
                                + "    @SuppressWarnings(\"unused\")\n"
                                + "    @SuppressLint(\"HandlerLeak\")\n"
                                + "    Handler handler = new Handler() {\n"
                                + "\n"
                                + "        public void handleMessage(Message msg) {\n"
                                + "\n"
                                + "        }\n"
                                + "    };\n"
                                + "\n"
                                + "}")
                ));
    }
}
