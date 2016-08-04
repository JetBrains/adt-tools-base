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
package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import static com.android.ide.common.res2.DataSet.isIgnored;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;

import java.io.File;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Node;

public class DataSetTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testIsIgnored() throws Exception {
        assertNull("Environment variable $ANDROID_AAPT_IGNORE should not be set while "
                        + "running test; can interfere with results",
                System.getenv("ANDROID_AAPT_IGNORE"));
        assertFalse(isIgnored(new File("a.")));
        assertFalse(isIgnored(new File("foo")));
        assertFalse(isIgnored(new File("foo" + separator + "bar")));
        assertFalse(isIgnored(new File("foo")));
        assertFalse(isIgnored(new File("foo" + separator + "bar")));
        assertFalse(isIgnored(new File("layout" + separator + "main.xml")));
        assertFalse(isIgnored(new File("res" + separator + "drawable" + separator + "foo.png")));
        assertFalse(isIgnored(new File("")));

        assertTrue(isIgnored(new File(".")));
        assertTrue(isIgnored(new File("..")));
        assertTrue(isIgnored(new File(".git")));
        assertTrue(isIgnored(new File("foo" + separator + ".git")));
        assertTrue(isIgnored(new File(".svn")));
        assertTrue(isIgnored(new File("thumbs.db")));
        assertTrue(isIgnored(new File("Thumbs.db")));
        assertTrue(isIgnored(new File("foo" + separator + "Thumbs.db")));

        // Suffix
        assertTrue(isIgnored(new File("foo~")));
        assertTrue(isIgnored(new File("foo.scc")));
        assertTrue(isIgnored(new File("foo" + separator + "foo.scc")));

        // Prefix
        assertTrue(isIgnored(new File(".test")));
        assertTrue(isIgnored(new File("foo" + separator + ".test")));

        // Don't match on non-directory
        assertFalse(isIgnored(new File("_test")));
        File dir = new File(TestUtils.createTempDirDeletedOnExit(), "_test");
        assertTrue(dir.mkdirs());
        assertTrue(isIgnored(dir));
    }

    @Test
    public void testLongestPath() {
        DataSet dataSet = new DataSet("foo", false) {

            @Override
            protected DataSet createSet(String name) {
                return null;
            }

            @Override
            protected DataFile createFileAndItemsFromXml(@NonNull File file, @NonNull Node fileNode)
                    throws MergingException {
                return null;
            }

            @Override
            protected void readSourceFolder(File sourceFolder, ILogger logger)
                    throws MergingException {

            }

            @Nullable
            @Override
            protected DataFile createFileAndItems(File sourceFolder, File file, ILogger logger)
                    throws MergingException {
                return null;
            }
        };

        File res = new File(mTemporaryFolder.getRoot(), "res");
        assertTrue(res.mkdirs());

        File foo = new File(mTemporaryFolder.getRoot(), "foo");
        assertTrue(foo.mkdirs());

        File customRes = new File(mTemporaryFolder.getRoot(), "res/layouts/shared");
        assertTrue(customRes.mkdirs());

        dataSet.addSource(res);
        dataSet.addSource(foo);
        dataSet.addSource(customRes);

        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/any.xml")).getPath());
        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layout/activity.xml")).getPath());
        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layout/foo/bar/activity.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layouts/shared/any.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(),
                        "res/layouts/shared/layout/activity.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(),
                        "res/layouts/shared/layout/foo/bar/activity.xml")).getPath());
        assertNull(dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "funky/shared/layout/foo/bar/activity.xml")));
    }
}