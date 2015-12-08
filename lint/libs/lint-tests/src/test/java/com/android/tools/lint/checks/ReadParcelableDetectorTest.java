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

@SuppressWarnings({"javadoc", "override", "MethodMayBeStatic"})
public class ReadParcelableDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ReadParcelableDetector();
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=196457
        assertEquals(""
                + "src/test/pkg/ParcelableDemo.java:10: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Parcelable error1   = in.readParcelable(null);\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:11: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Parcelable[] error2 = in.readParcelableArray(null);\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:12: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Bundle error3       = in.readBundle(null);\n"
                + "                                 ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:13: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Object[] error4     = in.readArray(null);\n"
                + "                                 ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:14: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        SparseArray error5  = in.readSparseArray(null);\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:15: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Object error6       = in.readValue(null);\n"
                + "                                 ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:16: Warning: Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example getClass().getClassLoader() instead. [ParcelClassLoader]\n"
                + "        Parcelable error7   = in.readPersistableBundle(null);\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:17: Warning: Using the default class loader will not work if you are restoring your own classes. Consider using for example readBundle(getClass().getClassLoader()) instead. [ParcelClassLoader]\n"
                + "        Bundle error8       = in.readBundle();\n"
                + "                                 ~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:18: Warning: Using the default class loader will not work if you are restoring your own classes. Consider using for example readPersistableBundle(getClass().getClassLoader()) instead. [ParcelClassLoader]\n"
                + "        Parcelable error9   = in.readPersistableBundle();\n"
                + "                                 ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 9 warnings\n",

                lintProject(
                        java("src/test/pkg/ParcelableDemo.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.os.Bundle;\n"
                                + "import android.os.Parcel;\n"
                                + "import android.os.Parcelable;\n"
                                + "import android.util.SparseArray;\n"
                                + "\n"
                                + "public class ParcelableDemo {\n"
                                + "    private void testParcelable(Parcel in) {\n"
                                + "        Parcelable error1   = in.readParcelable(null);\n"
                                + "        Parcelable[] error2 = in.readParcelableArray(null);\n"
                                + "        Bundle error3       = in.readBundle(null);\n"
                                + "        Object[] error4     = in.readArray(null);\n"
                                + "        SparseArray error5  = in.readSparseArray(null);\n"
                                + "        Object error6       = in.readValue(null);\n"
                                + "        Parcelable error7   = in.readPersistableBundle(null);\n"
                                + "        Bundle error8       = in.readBundle();\n"
                                + "        Parcelable error9   = in.readPersistableBundle();\n"
                                + "\n"
                                + "        Parcelable ok      = in.readParcelable(getClass().getClassLoader());\n"
                                + "    }\n"
                                + "}")
                ));
    }
}
