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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;

import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;

@SuppressWarnings({"javadoc", "ConstantConditions"})
public class ApiLookupTest extends AbstractCheckTest {
    private final ApiLookup mDb = ApiLookup.get(new TestLintClient());

    public void test1() {
        assertEquals(5, mDb.getFieldVersion("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertTrue(mDb.getFieldVersion("android/R$attr", "absListViewStyle") <= 1);
        assertEquals(11, mDb.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals(5, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        assertEquals(4, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "setTargetDensity", "(Landroid/util/DisplayMetrics;)V"));
        assertEquals(7, mDb.getClassVersion("android/app/WallpaperInfo"));
        assertEquals(11, mDb.getClassVersion("android/widget/StackView"));
        assertTrue(mDb.getClassVersion("ava/text/ChoiceFormat") <= 1);

        // Class lookup: Unknown class
        assertEquals(-1, mDb.getClassVersion("foo/Bar"));
        // Field lookup: Unknown class
        assertEquals(-1, mDb.getFieldVersion("foo/Bar", "FOOBAR"));
        // Field lookup: Unknown field
        assertEquals(-1, mDb.getFieldVersion("android/Manifest$permission", "FOOBAR"));
        // Method lookup: Unknown class
        assertEquals(-1, mDb.getCallVersion("foo/Bar",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown name
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "foo", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown argument list
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(I)V"));
    }

    public void test2() {
        // Regression test:
        // This used to return 11 because of some wildcard syntax in the signature
        assertTrue(mDb.getCallVersion("java/lang/Object", "getClass", "()") <= 1);
    }

    public void testIssue26467() {
        assertTrue(mDb.getCallVersion("java/nio/ByteBuffer", "array", "()") <= 1);
        assertEquals(9, mDb.getCallVersion("java/nio/Buffer", "array", "()"));
    }

    public void testNoInheritedConstructors() {
        assertTrue(mDb.getCallVersion("java/util/zip/ZipOutputStream", "<init>", "()") <= 1);
        assertTrue(mDb.getCallVersion("android/app/AliasActivity", "<init>", "(Landroid/content/Context;I)") <= 1);
    }

    public void testIssue35190() {
        assertEquals(9, mDb.getCallVersion("java/io/IOException", "<init>",
                "(Ljava/lang/Throwable;)V"));
    }

    public void testDeprecatedFields() {
        // Not deprecated:
        assertEquals(-1, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no deprecation
        assertEquals(9, mDb.getFieldVersion("android/Manifest$permission", "NFC"));

        // Deprecated
        assertEquals(21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_TASKS"));
        // Field both deprecated and since > 1
        assertEquals(21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "READ_SOCIAL_STREAM"));
        assertEquals(15, mDb.getFieldVersion("android/Manifest$permission", "READ_SOCIAL_STREAM"));
    }

    public void testDeprecatedCalls() {
        // Not deprecated:
        //assertEquals(12, mDb.getCallVersion("android/app/Fragment", "onInflate",
        //        "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        assertEquals(23, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate",
                "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        assertEquals(-1, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate",
                "(Landroid/content/Context;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        // Deprecated
        assertEquals(16, mDb.getCallDeprecatedIn("android/app/Service", "onStart", "(Landroid/content/Intent;I)V"));
        assertEquals(16, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate", "(Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
    }

    public void testDeprecatedClasses() {
        // Not deprecated:
        assertEquals(-1, mDb.getClassDeprecatedIn("android/app/Fragment"));
        // Deprecated
        assertEquals(9, mDb.getClassDeprecatedIn("org/xml/sax/Parser"));
    }

    public void testInheritInterfaces() {
        // The onPreferenceStartFragment is inherited via the
        // android/preference/PreferenceFragment$OnPreferenceStartFragmentCallback
        // interface
        assertEquals(11, mDb.getCallVersion("android/preference/PreferenceActivity",
                "onPreferenceStartFragment",
                "(Landroid/preference/PreferenceFragment;Landroid/preference/Preference;)"));
    }

    public void testInterfaceApi() {
        assertEquals(21, mDb.getClassVersion("android/animation/StateListAnimator"));
        assertEquals(11, mDb.getValidCastVersion("android/animation/AnimatorListenerAdapter",
                "android/animation/Animator$AnimatorListener"));
        assertEquals(19, mDb.getValidCastVersion("android/animation/AnimatorListenerAdapter",
                "android/animation/Animator$AnimatorPauseListener"));

        assertEquals(11, mDb.getValidCastVersion("android/animation/Animator",
                "java/lang/Cloneable"));
        assertEquals(22, mDb.getValidCastVersion("android/animation/StateListAnimator",
                "java/lang/Cloneable"));
    }

    public void testSuperClassCast() {
        assertEquals(22, mDb.getValidCastVersion("android/view/animation/AccelerateDecelerateInterpolator",
                "android/view/animation/BaseInterpolator"));
    }

    public void testIsValidPackage() {
        assertTrue(mDb.isValidJavaPackage("java/lang/Integer"));
        assertTrue(mDb.isValidJavaPackage("javax/crypto/Cipher"));
        assertTrue(mDb.isValidJavaPackage("java/awt/font/NumericShaper"));

        assertFalse(mDb.isValidJavaPackage("javax/swing/JButton"));
        assertFalse(mDb.isValidJavaPackage("java/rmi/Naming"));
        assertFalse(mDb.isValidJavaPackage("java/lang/instrument/Instrumentation"));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the ApiDatabase test");
        return null;
    }

    private File mCacheDir;
    @SuppressWarnings("StringBufferField")
    private StringBuilder mLogBuffer = new StringBuilder();

    @SuppressWarnings({"ConstantConditions", "IOResourceOpenedButNotSafelyClosed",
            "ResultOfMethodCallIgnored"})
    public void testCorruptedCacheHandling() throws Exception {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println("Skipping " + getName() + ": not valid while regenerating indices");
            return;
        }

        ApiLookup lookup;

        // Real cache:
        mCacheDir = new TestLintClient().getCacheDir(true);
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Custom cache dir: should also work
        mCacheDir = new File(getTempDir(), "test-cache");
        mCacheDir.mkdirs();
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Now truncate cache file
        File cacheFile = new File(mCacheDir,
                ApiLookup.getCacheFileName("api-versions.xml",
                        ApiLookup.getPlatformVersion(new LookupTestClient()))); //$NON-NLS-1$
        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half
        raf.setLength(100);  // Broken header
        raf.close();
        ApiLookup.get(new LookupTestClient());
        String message = mLogBuffer.toString();
        // NOTE: This test is incompatible with the DEBUG_FORCE_REGENERATE_BINARY and WRITE_STATS
        // flags in the ApiLookup class, so if the test fails during development and those are
        // set, clear them.
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half in the data portion
        raf.setLength(raf.length() / 2);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        // This data is now truncated: lookup returns the wrong size.
        try {
            assertNotNull(lookup);
            lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance");
            fail("Can't look up bogus data");
        } catch (Throwable t) {
            // Expected this: the database is corrupted.
        }
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file to 0 bytes
        raf.setLength(0);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();
    }

    private static final boolean CHECK_DEPRECATED = true;

    private static void assertSameApi(String desc, int expected, int actual) {
        // In the database we don't distinguish between 1 and -1 (to save diskspace)
        if (expected <= 1) {
            expected = -1;
        }
        if (actual <= 1) {
            actual = -1;
        }
        assertEquals(desc, expected, actual);
    }

    public void testFindEverything() throws Exception {
        // Load the API versions file and look up every single method/field/class in there
        // (provided since != 1) and also check the deprecated calls.

        File file = createClient().findResource("platform-tools/api/api-versions.xml");
        if (file == null || !file.exists()) {
            return;
        }

        Api info = Api.parseApi(file);
        assertNotNull(info);
        for (ApiClass cls : info.getClasses().values()) {
            int classSince = cls.getSince();
            String className = cls.getName();
            assertSameApi(className, classSince, mDb.getClassVersion(className));

            for (String method : cls.getAllMethods(info)) {
                int since = cls.getMethod(method, info);
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(method, since, mDb.getCallVersion(className, name, desc));

            }
            for (String method : cls.getAllFields(info)) {
                int since = cls.getField(method, info);
                assertSameApi(method, since, mDb.getFieldVersion(className, method));
            }

            for (Pair<String, Integer> pair : cls.getInterfaces()) {
                String interfaceName = pair.getFirst();
                int api = pair.getSecond();
                assertSameApi(interfaceName, api,
                        mDb.getValidCastVersion(className, interfaceName));
            }
        }

        if (CHECK_DEPRECATED) {
            for (ApiClass cls : info.getClasses().values()) {
                int classDeprecatedIn = cls.getDeprecatedIn();
                String className = cls.getName();
                if (classDeprecatedIn > 1) {
                    assertSameApi(className, classDeprecatedIn,
                            mDb.getClassDeprecatedIn(className));
                } else {
                    assertSameApi(className, -1, mDb.getClassDeprecatedIn(className));
                }

                for (String method : cls.getAllMethods(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    int index = method.indexOf('(');
                    String name = method.substring(0, index);
                    String desc = method.substring(index);
                    assertSameApi(method, deprecatedIn, mDb.getCallDeprecatedIn(className,
                            name, desc));
                }
                for (String method : cls.getAllFields(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    assertSameApi(method, deprecatedIn, mDb.getFieldDeprecatedIn(className,
                            method));
                }
            }
        }
    }

    public void test() {
        assertEquals(14, mDb.getFieldVersion("android/provider/ContactsContract$Settings", "DATA_SET"));
    }

    private final class LookupTestClient extends TestLintClient {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public File getCacheDir(boolean create) {
            assertNotNull(mCacheDir);
            if (create && !mCacheDir.exists()) {
                mCacheDir.mkdirs();
            }
            return mCacheDir;
        }

        @Override
        public void log(
                @NonNull Severity severity,
                @Nullable Throwable exception,
                @Nullable String format,
                @Nullable Object... args) {
            if (format != null) {
                mLogBuffer.append(String.format(format, args));
                mLogBuffer.append('\n');
            }
            if (exception != null) {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                mLogBuffer.append(writer.toString());
                mLogBuffer.append('\n');
            }
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            log(Severity.WARNING, exception, format, args);
        }
    }
}
