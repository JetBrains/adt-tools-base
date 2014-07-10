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

package com.android.tools.perflib.heap;

import com.google.common.io.Closeables;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;

public class HprofParserTest extends TestCase {

    State mState;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        String file = getClass().getResource("/dialer.android-hprof").getFile();

        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        DataInputStream dis = new DataInputStream(bis);
        try {
            mState = (new HprofParser(dis)).parse();
        } finally {
            Closeables.close(dis, false);
        }
    }

    public void testHierarchy() {
        ClassObj application = mState.findClass("android.app.Application");
        assertNotNull(application);

        ClassObj contextWrapper = application.getSuperClassObj();
        assertNotNull(contextWrapper);
        assertEquals("android.content.ContextWrapper", contextWrapper.getClassName());
        contextWrapper.getSubclasses().contains(application);

        ClassObj context = contextWrapper.getSuperClassObj();
        assertNotNull(context);
        assertEquals("android.content.Context", context.getClassName());
        context.getSubclasses().contains(contextWrapper);

        ClassObj object = context.getSuperClassObj();
        assertNotNull(object);
        assertEquals("java.lang.Object", object.getClassName());
        object.getSubclasses().contains(context);

        ClassObj none = object.getSuperClassObj();
        assertNull(none);
    }
}
