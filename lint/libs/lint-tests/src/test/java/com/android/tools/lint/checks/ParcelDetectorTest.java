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

@SuppressWarnings({"javadoc", "override", "MethodMayBeStatic"})
public class ParcelDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ParcelDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/bytecode/MyParcelable1.java:6: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                + "public class MyParcelable1 implements Parcelable {\n"
                + "             ~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/MyParcelable1.java.txt=>src/test/bytecode/MyParcelable1.java",
                "bytecode/MyParcelable2.java.txt=>src/test/bytecode/MyParcelable2.java",
                "bytecode/MyParcelable3.java.txt=>src/test/bytecode/MyParcelable3.java",
                "bytecode/MyParcelable4.java.txt=>src/test/bytecode/MyParcelable4.java",
                "bytecode/MyParcelable5.java.txt=>src/test/bytecode/MyParcelable5.java",
                "bytecode/MyParcelable1.class.data=>bin/classes/test/bytecode/MyParcelable1.class",
                "bytecode/MyParcelable2.class.data=>bin/classes/test/bytecode/MyParcelable2.class",
                "bytecode/MyParcelable2$1.class.data=>bin/classes/test/bytecode/MyParcelable2$1.class",
                "bytecode/MyParcelable3.class.data=>bin/classes/test/bytecode/MyParcelable3.class",
                "bytecode/MyParcelable4.class.data=>bin/classes/test/bytecode/MyParcelable4.class",
                "bytecode/MyParcelable5.class.data=>bin/classes/test/bytecode/MyParcelable5.class"
                ));
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testInterfaceOnSuperClass() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=171522
        assertEquals(""
                + "src/test/pkg/ParcelableDemo.java:14: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                + "    private static class JustParcelable implements Parcelable {\n"
                + "                         ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:19: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                + "    private static class JustParcelableSubclass extends JustParcelable {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:22: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                + "    private static class ParcelableThroughAbstractSuper extends AbstractParcelable {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ParcelableDemo.java:27: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                + "    private static class ParcelableThroughInterface implements MoreThanParcelable {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "4 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/ParcelableDemo.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.os.Parcel;\n"
                                + "import android.os.Parcelable;\n"
                                + "\n"
                                + "public class ParcelableDemo {\n"
                                + "    private interface MoreThanParcelable extends Parcelable {\n"
                                + "        void somethingMore();\n"
                                + "    }\n"
                                + "\n"
                                + "    private abstract static class AbstractParcelable implements Parcelable {\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class JustParcelable implements Parcelable {\n"
                                + "        public int describeContents() {return 0;}\n"
                                + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class JustParcelableSubclass extends JustParcelable {\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class ParcelableThroughAbstractSuper extends AbstractParcelable {\n"
                                + "        public int describeContents() {return 0;}\n"
                                + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class ParcelableThroughInterface implements MoreThanParcelable {\n"
                                + "        public int describeContents() {return 0;}\n"
                                + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                + "        public void somethingMore() {}\n"
                                + "    }\n"
                                + "}")
                ));
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testSpans() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=192841
        assertEquals("No warnings.",

                lintProject(
                        java("src/test/pkg/TestSpan.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.text.TextPaint;\n"
                                + "import android.text.style.URLSpan;\n"
                                + "\n"
                                + "public class TestSpan extends URLSpan {\n"
                                + "    public TestSpan(String url) {\n"
                                + "        super(url);\n"
                                + "    }\n"
                                + "}")
                ));
    }
}
