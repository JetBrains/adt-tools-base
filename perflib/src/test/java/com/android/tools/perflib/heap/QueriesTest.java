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

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;

import junit.framework.TestCase;

import java.io.File;
import java.util.Collection;

public class QueriesTest extends TestCase {

    public void testCommonClassesQuery() throws Exception {
        File basic = new File(getClass().getResource("/basic.android-hprof").getFile());
        Snapshot basicSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(basic));

        File dialer = new File(getClass().getResource("/dialer.android-hprof").getFile());
        Snapshot dialerSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(dialer));

        Collection<ClassObj> classes = Queries.commonClasses(basicSnapshot, dialerSnapshot);
        assertEquals(3521, classes.size());

        ClassObj clazz1 = basicSnapshot.findClass("android.app.Application");
        assertNotNull(dialerSnapshot.findClass(clazz1.getClassName()));
        assertTrue(classes.contains(clazz1));

        // Application class in basicTest.
        ClassObj clazz2 = basicSnapshot.findClass("com.android.tests.basic.Main");
        assertNull(dialerSnapshot.findClass(clazz2.getClassName()));
        assertFalse(classes.contains(clazz2));

        // Application class in Dialer.
        ClassObj clazz3 = dialerSnapshot.findClass("com.android.dialer.DialerApplication");
        assertNull(basicSnapshot.findClass(clazz3.getClassName()));
        assertFalse(classes.contains(clazz2));
    }
}
