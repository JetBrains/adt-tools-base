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

package com.android.ide.common.res2;

import com.android.testutils.TestUtils;

import java.io.File;
import java.io.IOException;

public class ResourceSetTest extends BaseTestCase {

    public void testBaseResourceSetByCount() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();
        assertEquals(25, resourceSet.size());
    }

    public void testBaseResourceSetByName() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();

        verifyResourceExists(resourceSet,
                "drawable/icon",
                "drawable/patch",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "color/color",
                "string/basic_string",
                "string/xliff_string",
                "string/styled_string",
                "style/style",
                "array/string_array",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "attr/android:colorForegroundInverse",
                "attr/blah",
                "declare-styleable/declare_styleable",
                "dimen/dimen",
                "id/item_id",
                "integer/integer"
        );
    }

    public void testDupResourceSet() throws Exception {
        File root = TestUtils.getRoot("resources", "dupSet");

        ResourceSet set = new ResourceSet("main");
        set.addSource(new File(root, "res1"));
        set.addSource(new File(root, "res2"));
        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (DuplicateDataException e) {
            gotException = true;
        }

        checkLogger(logger);
        assertTrue(gotException);
    }

    public void testBrokenSet() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSet");

        ResourceSet set = new ResourceSet("main");
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (IOException e) {
            gotException = true;
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    public void testBrokenSet2() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSet2");

        ResourceSet set = new ResourceSet("main");
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (IOException e) {
            gotException = true;
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    static ResourceSet getBaseResourceSet() throws DuplicateDataException, IOException {
        File root = TestUtils.getRoot("resources", "baseSet");

        ResourceSet resourceSet = new ResourceSet("main");
        resourceSet.addSource(root);
        RecordingLogger logger =  new RecordingLogger();
        resourceSet.loadFromFiles(logger);

        checkLogger(logger);

        return resourceSet;
    }
}
