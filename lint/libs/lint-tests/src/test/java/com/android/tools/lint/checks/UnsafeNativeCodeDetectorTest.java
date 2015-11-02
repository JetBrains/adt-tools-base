/*
 * Copyright (C) 2015 The Android Open Source Project
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
public class UnsafeNativeCodeDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new UnsafeNativeCodeDetector();
    }

    public void testLoad() throws Exception {
        assertEquals(
                "src/test/pkg/Load.java:12: Warning: Dynamically loading code using load is risky, please use loadLibrary instead when possible [UnsafeDynamicallyLoadedCode]\n" +
                "            Runtime.getRuntime().load(\"/data/data/test.pkg/files/libhello.so\");\n" +
                "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/Load.java:14: Warning: Dynamically loading code using load is risky, please use loadLibrary instead when possible [UnsafeDynamicallyLoadedCode]\n" +
                "            System.load(\"/data/data/test.pkg/files/libhello.so\");\n" +
                "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 2 warnings\n",
                lintProject(java("src/test/pkg/Load.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.NullPointerException;\n"
                        + "import java.lang.Runtime;\n"
                        + "import java.lang.SecurityException;\n"
                        + "import java.lang.System;\n"
                        + "import java.lang.UnsatisfiedLinkError;\n"
                        + "\n"
                        + "public class Load {\n"
                        + "    public void foo() {\n"
                        + "        try {\n"
			+ "            Runtime.getRuntime().load(\"/data/data/test.pkg/files/libhello.so\");\n"
			+ "            Runtime.getRuntime().loadLibrary(\"hello\"); // ok\n"
                        + "            System.load(\"/data/data/test.pkg/files/libhello.so\");\n"
                        + "            System.loadLibrary(\"hello\"); // ok\n"
                        + "        } catch (SecurityException e) {\n"
                        + "        } catch (UnsatisfiedLinkError e) {\n"
                        + "        } catch (NullPointerException e) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")));
    }
}
