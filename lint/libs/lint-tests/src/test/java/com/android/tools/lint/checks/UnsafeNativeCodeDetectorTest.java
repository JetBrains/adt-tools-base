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

@SuppressWarnings({"javadoc", "JavaLangImport", "ClassNameDiffersFromFileName"})
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
                        + "    public static void foo() {\n"
                        + "        try {\n"
                        + "            Runtime.getRuntime().load(\"/data/data/test.pkg/files/libhello.so\");\n"
                        + "            Runtime.getRuntime().loadLibrary(\"hello\"); // ok\n"
                        + "            System.load(\"/data/data/test.pkg/files/libhello.so\");\n"
                        + "            System.loadLibrary(\"hello\"); // ok\n"
                        + "        } catch (SecurityException ignore) {\n"
                        + "        } catch (UnsatisfiedLinkError ignore) {\n"
                        + "        } catch (NullPointerException ignore) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")));
    }

    public void testNativeCode() throws Exception {
        assertEquals(""
                + "assets/hello: Warning: Embedding non-shared library native executables into applications should be avoided when possible, as there is an increased risk that the executables could be tampered with after installation. Instead, native code should be placed in a shared library, and the features of the development environment should be used to place the shared library in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]\n"
                + "res/raw/hello: Warning: Embedding non-shared library native executables into applications should be avoided when possible, as there is an increased risk that the executables could be tampered with after installation. Instead, native code should be placed in a shared library, and the features of the development environment should be used to place the shared library in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]\n"
                + "assets/libhello-jni.so: Warning: Shared libraries should not be placed in the res or assets directories. Please use the features of your development environment to place shared libraries in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]\n"
                + "res/raw/libhello-jni.so: Warning: Shared libraries should not be placed in the res or assets directories. Please use the features of your development environment to place shared libraries in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]\n"
                + "0 errors, 4 warnings\n",
               lintProject(
                       copy("res/raw/hello"),
                       copy("res/raw/libhello-jni.so"),
                       copy("res/raw/hello", "assets/hello"),
                       copy("res/raw/libhello-jni.so", "assets/libhello-jni.so"),
                       copy("lib/armeabi/hello"),
                       copy("lib/armeabi/libhello-jni.so")));
    }

    public void testNoWorkInInteractiveMode() throws Exception {
        // Make sure we don't scan through all resource folders when just incrementally
        // editing a Java file
        assertEquals(
                "No warnings.",
                lintProjectIncrementally(
                        "src/test/pkg/Load.java",
                        java("src/test/pkg/Load.java", ""
                                + "package test.pkg;\n"
                                + "public class Load { }\n"),
                        copy("res/raw/hello"),
                        copy("res/raw/libhello-jni.so"),
                        copy("res/raw/hello", "assets/hello"),
                        copy("res/raw/libhello-jni.so", "assets/libhello-jni.so"),
                        copy("lib/armeabi/hello"),
                        copy("lib/armeabi/libhello-jni.so")));
    }
}
