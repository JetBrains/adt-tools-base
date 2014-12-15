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

package com.android.builder.internal.incremental;

import com.android.testutils.TestUtils;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DependencyDataTest extends TestCase {

    public void testWindowsMode1() throws Exception {
        DependencyData data = getData("windows_mode1.d");

        assertEquals("C:\\path\\to\\main input.bar", data.getMainFile());

        List<String> secondaryFiles = data.getSecondaryFiles();
        assertEquals(2, secondaryFiles.size());
        assertEquals("C:\\path\\to\\some input1.bar", secondaryFiles.get(0));
        assertEquals("C:\\path\\to\\some input2.bar", secondaryFiles.get(1));

        List<String> outputs = data.getOutputFiles();
        assertEquals(1, outputs.size());

        assertEquals("C:\\path\\to\\some output.foo", outputs.get(0));

    }

    public void testWindowsMode2() throws Exception {
        DependencyData data = getData("windows_mode2.d");

        assertEquals("C:\\path\\to\\main input.bar", data.getMainFile());

        List<String> secondaryFiles = data.getSecondaryFiles();
        assertEquals(2, secondaryFiles.size());
        assertEquals("C:\\path\\to\\some input1.bar", secondaryFiles.get(0));
        assertEquals("C:\\path\\to\\some input2.bar", secondaryFiles.get(1));

        List<String> outputs = data.getOutputFiles();
        assertEquals(1, outputs.size());

        assertEquals("C:\\path\\to\\some output.foo", outputs.get(0));
    }

    public void testNoOutput() throws Exception {
        DependencyData data = getData("no_output.d");

        assertEquals(0, data.getSecondaryFiles().size());
        assertEquals(0, data.getOutputFiles().size());

        assertEquals("/path/to/main input.bar", data.getMainFile());
    }

    public void testSecondaryFiles() throws Exception {
        DependencyData data = getData("secondary_files.d");

        assertEquals("/path/to/project/src/com/example/IService.aidl", data.getMainFile());

        List<String> secondaryFiles = data.getSecondaryFiles();
        assertEquals(4, secondaryFiles.size());
        assertEquals("/path/to/project/src/com/example/ISecondaryFile1.aidl", secondaryFiles.get(0));
        assertEquals("/path/to/project/src/com/example/ISecondaryFile2.aidl", secondaryFiles.get(1));
        assertEquals("/path/to/project/src/com/example/ISecondaryFile3.aidl", secondaryFiles.get(2));
        assertEquals("/path/to/project/src/com/example/ISecondaryFile4.aidl", secondaryFiles.get(3));

        List<String> outputs = data.getOutputFiles();
        assertEquals(1, outputs.size());

        assertEquals("/path/to/project/build/source/aidl/debug/com/example/IService.java", outputs.get(0));
    }

    private DependencyData getData(String name) throws IOException {
        File depFile = new File(TestUtils.getRoot("dependencyData"), name);
        DependencyData data = DependencyData.parseDependencyFile(depFile);
        assertNotNull(data);
        return data;
    }

}
