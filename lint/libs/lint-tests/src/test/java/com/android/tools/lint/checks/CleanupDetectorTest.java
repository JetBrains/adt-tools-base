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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class CleanupDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CleanupDetector();
    }

    public void testRecycle() throws Exception {
        assertEquals(
            "src/test/pkg/RecycleTest.java:56: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]\n" +
            "  final TypedArray a = getContext().obtainStyledAttributes(attrs,\n" +
            "                                    ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:63: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]\n" +
            "  final TypedArray a = getContext().obtainStyledAttributes(new int[0]);\n" +
            "                                    ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:79: Warning: This VelocityTracker should be recycled after use with #recycle() [Recycle]\n" +
            "  VelocityTracker tracker = VelocityTracker.obtain();\n" +
            "                                            ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:92: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event1 = MotionEvent.obtain(null);\n" +
            "                                   ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:93: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event2 = MotionEvent.obtainNoHistory(null);\n" +
            "                                   ~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:98: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event2 = MotionEvent.obtainNoHistory(null); // Not recycled\n" +
            "                                   ~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:103: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled\n" +
            "                                   ~~~~~~\n" +
            /* Not implemented in AST visitor; not a typical user error and easy to diagnose if it's done
            "src/test/pkg/RecycleTest.java:113: Warning: This MotionEvent has already been recycled [Recycle]\n" +
            "  int contents2 = event1.describeContents(); // BAD, after recycle\n" +
            "                         ~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:117: Warning: This TypedArray has already been recycled [Recycle]\n" +
            "  example = a.getString(R.styleable.MyView_exampleString); // BAD, after recycle\n" +
            "              ~~~~~~~~~\n" +
            */
            "src/test/pkg/RecycleTest.java:129: Warning: This Parcel should be recycled after use with #recycle() [Recycle]\n" +
            "  Parcel myparcel = Parcel.obtain();\n" +
            "                           ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:190: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]\n" +
            "        final TypedArray a = getContext().obtainStyledAttributes(attrs,  // Not recycled\n" +
            "                                          ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 9 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk4.xml=>AndroidManifest.xml",
                "project.properties19=>project.properties",
                "bytecode/RecycleTest.java.txt=>src/test/pkg/RecycleTest.java",
                "bytecode/RecycleTest.class.data=>bin/classes/test/pkg/RecycleTest.class"
            ));
    }

    public void testCommit() throws Exception {
        assertEquals(""
                + "src/test/pkg/CommitTest.java:25: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "        getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                             ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:30: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "        FragmentTransaction transaction2 = getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                                                                ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:39: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "        getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                             ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:65: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "        getSupportFragmentManager().beginTransaction();\n"
                + "                                    ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "project.properties19=>project.properties",
                    "bytecode/CommitTest.java.txt=>src/test/pkg/CommitTest.java",
                    "bytecode/CommitTest.class.data=>bin/classes/test/pkg/CommitTest.class",
                    // Stubs just to be able to do type resolution without needing the full appcompat jar
                    "appcompat/Fragment.java.txt=>src/android/support/v4/app/Fragment.java",
                    "appcompat/DialogFragment.java.txt=>src/android/support/v4/app/DialogFragment.java",
                    "appcompat/FragmentTransaction.java.txt=>src/android/support/v4/app/FragmentTransaction.java",
                    "appcompat/FragmentManager.java.txt=>src/android/support/v4/app/FragmentManager.java"
            ));
    }

    public void testCommit2() throws Exception {
        assertEquals(""
                + "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "bytecode/DialogFragment.class.data=>bin/classes/test/pkg/DialogFragment.class",
                        // Stubs just to be able to do type resolution without needing the full appcompat jar
                        "appcompat/Fragment.java.txt=>src/android/support/v4/app/Fragment.java",
                        "appcompat/DialogFragment.java.txt=>src/android/support/v4/app/DialogFragment.java",
                        "appcompat/FragmentTransaction.java.txt=>src/android/support/v4/app/FragmentTransaction.java",
                        "appcompat/FragmentManager.java.txt=>src/android/support/v4/app/FragmentManager.java"
                ));
    }

    public void testCommit3() throws Exception {
        assertEquals("" +
                "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "bytecode/CommitTest2.java.txt=>src/test/pkg/CommitTest2.java",
                        "bytecode/CommitTest2$MyDialogFragment.class.data=>bin/classes/test/pkg/CommitTest2$MyDialogFragment.class",
                        "bytecode/CommitTest2.class.data=>bin/classes/test/pkg/CommitTest2.class",
                        // Stubs just to be able to do type resolution without needing the full appcompat jar
                        "appcompat/Fragment.java.txt=>src/android/support/v4/app/Fragment.java",
                        "appcompat/DialogFragment.java.txt=>src/android/support/v4/app/DialogFragment.java",
                        "appcompat/FragmentTransaction.java.txt=>src/android/support/v4/app/FragmentTransaction.java",
                        "appcompat/FragmentManager.java.txt=>src/android/support/v4/app/FragmentManager.java"
                ));
    }

    public void testCommit4() throws Exception {
        assertEquals("" +
                "src/test/pkg/CommitTest3.java:35: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "    getCompatFragmentManager().beginTransaction();\n"
                + "                               ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "bytecode/CommitTest3.java.txt=>src/test/pkg/CommitTest3.java",
                        "bytecode/CommitTest3.class.data=>bin/classes/test/pkg/CommitTest3.class",
                        "bytecode/CommitTest3$MyDialogFragment.class.data=>bin/classes/test/pkg/CommitTest3$MyDialogFragment.class",
                        "bytecode/CommitTest3$MyCompatDialogFragment.class.data=>bin/classes/test/pkg/CommitTest3$MyCompatDialogFragment.class",
                        // Stubs just to be able to do type resolution without needing the full appcompat jar
                        "appcompat/Fragment.java.txt=>src/android/support/v4/app/Fragment.java",
                        "appcompat/DialogFragment.java.txt=>src/android/support/v4/app/DialogFragment.java",
                        "appcompat/FragmentTransaction.java.txt=>src/android/support/v4/app/FragmentTransaction.java",
                        "appcompat/FragmentManager.java.txt=>src/android/support/v4/app/FragmentManager.java"
                ));
    }

    public void testCommitChainedCalls() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=135204
        assertEquals(""
                + "src/test/pkg/TransactionTest.java:8: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n"
                + "        android.app.FragmentTransaction transaction2 = getFragmentManager().beginTransaction();\n"
                + "                                                                            ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "src/test/pkg/TransactionTest.java.txt=>src/test/pkg/TransactionTest.java",
                        // Stubs just to be able to do type resolution without needing the full appcompat jar
                        "appcompat/Fragment.java.txt=>src/android/support/v4/app/Fragment.java",
                        "appcompat/DialogFragment.java.txt=>src/android/support/v4/app/DialogFragment.java",
                        "appcompat/FragmentTransaction.java.txt=>src/android/support/v4/app/FragmentTransaction.java",
                        "appcompat/FragmentManager.java.txt=>src/android/support/v4/app/FragmentManager.java"
                ));
    }

    public void testSurfaceTexture() throws Exception {
        assertEquals(
            "src/test/pkg/SurfaceTextureTest.java:18: Warning: This SurfaceTexture should be freed up after use with #release() [Recycle]\n" +
            "        SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released\n" +
            "                                 ~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/SurfaceTextureTest.java:25: Warning: This SurfaceTexture should be freed up after use with #release() [Recycle]\n" +
            "        SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released\n" +
            "                                 ~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/SurfaceTextureTest.java:32: Warning: This Surface should be freed up after use with #release() [Recycle]\n" +
            "        Surface surface = new Surface(texture); // Warn: surface not released\n" +
            "                          ~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 3 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "project.properties19=>project.properties",
                    "src/test/pkg/SurfaceTextureTest.java.txt=>src/test/pkg/SurfaceTextureTest.java"
            ));
    }

    public void testContentProviderClient() throws Exception {
        assertEquals(
                "src/test/pkg/ContentProviderClientTest.java:8: Warning: This ContentProviderClient should be freed up after use with #release() [Recycle]\n" +
                "        ContentProviderClient client = resolver.acquireContentProviderClient(\"test\"); // Warn\n" +
                "                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "src/test/pkg/ContentProviderClientTest.java.txt=>src/test/pkg/ContentProviderClientTest.java"
                ));
    }

    public void testDatabaseCursor() throws Exception {
        assertEquals(
                "src/test/pkg/CursorTest.java:14: Warning: This Cursor should be freed up after use with #close() [Recycle]\n" +
                "        Cursor cursor = db.query(\"TABLE_TRIPS\",\n" +
                "                           ~~~~~\n" +
                "src/test/pkg/CursorTest.java:23: Warning: This Cursor should be freed up after use with #close() [Recycle]\n" +
                "        Cursor cursor = db.query(\"TABLE_TRIPS\",\n" +
                "                           ~~~~~\n" +
                "src/test/pkg/CursorTest.java:74: Warning: This Cursor should be freed up after use with #close() [Recycle]\n" +
                "        Cursor query = provider.query(uri, null, null, null, null);\n" +
                "                                ~~~~~\n" +
                "src/test/pkg/CursorTest.java:75: Warning: This Cursor should be freed up after use with #close() [Recycle]\n" +
                "        Cursor query2 = resolver.query(uri, null, null, null, null);\n" +
                "                                 ~~~~~\n" +
                "src/test/pkg/CursorTest.java:76: Warning: This Cursor should be freed up after use with #close() [Recycle]\n" +
                "        Cursor query3 = client.query(uri, null, null, null, null);\n" +
                "                               ~~~~~\n" +
                "0 errors, 5 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties19=>project.properties",
                        "src/test/pkg/CursorTest.java.txt=>src/test/pkg/CursorTest.java"
                ));
    }

    public void testDatabaseCursorReassignment() throws Exception {
        //noinspection ClassNameDiffersFromFileName,SpellCheckingInspection
        assertEquals("No warnings.",
                lintProject(java("src/test/pkg/CursorTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.database.Cursor;\n"
                        + "import android.database.sqlite.SQLiteException;\n"
                        + "import android.net.Uri;\n"
                        + "\n"
                        + "public class CursorTest extends Activity {\n"
                        + "    public void testSimple() {\n"
                        + "        Cursor cursor;\n"
                        + "        try {\n"
                        + "            cursor = getContentResolver().query(Uri.parse(\"blahblah\"),\n"
                        + "                    new String[]{\"_id\", \"display_name\"}, null, null, null);\n"
                        + "        } catch (SQLiteException e) {\n"
                        + "            // Fallback\n"
                        + "            cursor = getContentResolver().query(Uri.parse(\"blahblah\"),\n"
                        + "                    new String[]{\"_id2\", \"display_name\"}, null, null, null);\n"
                        + "        }\n"
                        + "        assert cursor != null;\n"
                        + "        cursor.close();\n"
                        + "    }\n"
                        + "}\n")));
    }

    // Shared preference tests

    public void test() throws Exception {
        assertEquals(
                "src/test/pkg/SharedPrefsTest.java:54: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        SharedPreferences.Editor editor = preferences.edit();\n" +
                        "                                          ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest.java:62: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        SharedPreferences.Editor editor = preferences.edit();\n" +
                        "                                          ~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 2 warnings\n" +
                        "",

                lintProject("src/test/pkg/SharedPrefsTest.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest.java"));
    }

    public void test2() throws Exception {
        // Regression test 1 for http://code.google.com/p/android/issues/detail?id=34322
        assertEquals(
                "src/test/pkg/SharedPrefsTest2.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        SharedPreferences.Editor editor = preferences.edit();\n" +
                        "                                          ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest2.java:17: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        Editor editor = preferences.edit();\n" +
                        "                        ~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 2 warnings\n",

                lintProject("src/test/pkg/SharedPrefsTest2.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest2.java"));
    }

    public void test3() throws Exception {
        // Regression test 2 for http://code.google.com/p/android/issues/detail?id=34322
        assertEquals(
                "src/test/pkg/SharedPrefsTest3.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        Editor editor = preferences.edit();\n" +
                        "                        ~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings\n",

                lintProject("src/test/pkg/SharedPrefsTest3.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest3.java"));
    }

    public void test4() throws Exception {
        // Regression test 3 for http://code.google.com/p/android/issues/detail?id=34322
        assertEquals(""
                        + "src/test/pkg/SharedPrefsTest4.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n"
                        + "        Editor editor = preferences.edit();\n"
                        + "                        ~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",

                lintProject("src/test/pkg/SharedPrefsTest4.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest4.java"));
    }

    public void test5() throws Exception {
        // Check fields too: http://code.google.com/p/android/issues/detail?id=39134
        assertEquals(
                "src/test/pkg/SharedPrefsTest5.java:16: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        mPreferences.edit().putString(PREF_FOO, \"bar\");\n" +
                        "        ~~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:17: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        mPreferences.edit().remove(PREF_BAZ).remove(PREF_FOO);\n" +
                        "        ~~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:26: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        preferences.edit().putString(PREF_FOO, \"bar\");\n" +
                        "        ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:27: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);\n" +
                        "        ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:32: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        preferences.edit().putString(PREF_FOO, \"bar\");\n" +
                        "        ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:33: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);\n" +
                        "        ~~~~~~~~~~~~~~~~~~\n" +
                        "src/test/pkg/SharedPrefsTest5.java:38: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n" +
                        "        Editor editor = preferences.edit().putString(PREF_FOO, \"bar\");\n" +
                        "                        ~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 7 warnings\n",

                lintProject("src/test/pkg/SharedPrefsTest5.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest5.java"));
    }

    public void test6() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=68692
        assertEquals(""
                        + "src/test/pkg/SharedPrefsTest7.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n"
                        + "        settings.edit().putString(MY_PREF_KEY, myPrefValue);\n"
                        + "        ~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",

                lintProject("src/test/pkg/SharedPrefsTest7.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest7.java"));
    }

    public void test7() throws Exception {
        assertEquals("No warnings.", // minSdk < 9: no warnings

                lintProject("src/test/pkg/SharedPrefsTest8.java.txt=>" +
                        "src/test/pkg/SharedPrefsTest8.java"));
    }

    public void test8() throws Exception {
        assertEquals(""
                        + "src/test/pkg/SharedPrefsTest8.java:11: Warning: Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [CommitPrefEdits]\n"
                        + "        editor.commit();\n"
                        + "        ~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",

                lintProject(
                        "apicheck/minsdk11.xml=>AndroidManifest.xml",
                        "src/test/pkg/SharedPrefsTest8.java.txt=>src/test/pkg/SharedPrefsTest8.java"));
    }

    public void testChainedCalls() throws Exception {
        assertEquals(""
                        + "src/test/pkg/Chained.java:24: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]\n"
                        + "        PreferenceManager\n"
                        + "        ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(java("src/test/pkg/Chained.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "import android.preference.PreferenceManager;\n"
                        + "\n"
                        + "public class Chained {\n"
                        + "    private static void falsePositive(Context context) {\n"
                        + "        PreferenceManager\n"
                        + "                .getDefaultSharedPreferences(context)\n"
                        + "                .edit()\n"
                        + "                .putString(\"wat\", \"wat\")\n"
                        + "                .commit();\n"
                        + "    }\n"
                        + "\n"
                        + "    private static void falsePositive2(Context context) {\n"
                        + "        boolean var = PreferenceManager\n"
                        + "                .getDefaultSharedPreferences(context)\n"
                        + "                .edit()\n"
                        + "                .putString(\"wat\", \"wat\")\n"
                        + "                .commit();\n"
                        + "    }\n"
                        + "\n"
                        + "    private static void truePositive(Context context) {\n"
                        + "        PreferenceManager\n"
                        + "                .getDefaultSharedPreferences(context)\n"
                        + "                .edit()\n"
                        + "                .putString(\"wat\", \"wat\");\n"
                        + "    }\n"
                        + "}\n")));
    }

    @SuppressWarnings("ALL") // sample code with warnings
    public void testCommitDetector() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/CommitTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.app.FragmentManager;\n"
                                + "import android.app.FragmentTransaction;\n"
                                + "import android.content.Context;\n"
                                + "\n"
                                + "public class CommitTest {\n"
                                + "    private Context mActivity;\n"
                                + "    public void selectTab1() {\n"
                                + "        FragmentTransaction trans = null;\n"
                                + "        if (mActivity instanceof Activity) {\n"
                                + "            trans = ((Activity)mActivity).getFragmentManager().beginTransaction()\n"
                                + "                    .disallowAddToBackStack();\n"
                                + "        }\n"
                                + "\n"
                                + "        if (trans != null && !trans.isEmpty()) {\n"
                                + "            trans.commit();\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public void select(FragmentManager fragmentManager) {\n"
                                + "        FragmentTransaction trans = fragmentManager.beginTransaction().disallowAddToBackStack();\n"
                                + "        trans.commit();\n"
                                + "    }"
                                + "}")));
    }

    @SuppressWarnings("ALL") // sample code with warnings
    public void testCommitDetectorOnParameters() throws Exception {
        // Handle transactions assigned to parameters (this used to not work)
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/CommitTest2.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.FragmentManager;\n"
                                + "import android.app.FragmentTransaction;\n"
                                + "\n"
                                + "@SuppressWarnings(\"unused\")\n"
                                + "public class CommitTest2 {\n"
                                + "    private void navigateToFragment(FragmentTransaction transaction,\n"
                                + "                                    FragmentManager supportFragmentManager) {\n"
                                + "        if (transaction == null) {\n"
                                + "            transaction = supportFragmentManager.beginTransaction();\n"
                                + "        }\n"
                                + "\n"
                                + "        transaction.commit();\n"
                                + "    }\n"
                                + "}")));
    }

    @SuppressWarnings("ALL") // sample code with warnings
    public void testReturn() throws Exception {
        // If you return the object to be cleaned up, it doesn'st have to be cleaned up (caller
        // may do that)
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/SharedPrefsTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Context;\n"
                                + "import android.content.SharedPreferences;\n"
                                + "import android.preference.PreferenceManager;\n"
                                + "\n"
                                + "@SuppressWarnings(\"unused\")\n"
                                + "public abstract class SharedPrefsTest extends Context {\n"
                                + "    private SharedPreferences.Editor getEditor() {\n"
                                + "        return getPreferences().edit();\n"
                                + "    }\n"
                                + "\n"
                                + "    private boolean editAndCommit() {\n"
                                + "        return getPreferences().edit().commit();\n"
                                + "    }\n"
                                + "\n"
                                + "    private SharedPreferences getPreferences() {\n"
                                + "        return PreferenceManager.getDefaultSharedPreferences(this);\n"
                                + "    }\n"
                                + "}")));
    }

    @SuppressWarnings("ALL") // sample code with warnings
    public void testCommitNow() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/CommitTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.FragmentManager;\n"
                                + "import android.app.FragmentTransaction;\n"
                                + "\n"
                                + "public class CommitTest {\n"
                                + "    public void select(FragmentManager fragmentManager) {\n"
                                + "        FragmentTransaction trans = fragmentManager.beginTransaction().disallowAddToBackStack();\n"
                                + "        trans.commitNow();\n"
                                + "    }"
                                + "}")));
    }
}