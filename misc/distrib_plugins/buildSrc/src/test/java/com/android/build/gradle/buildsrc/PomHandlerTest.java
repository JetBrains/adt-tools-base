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
package com.android.build.gradle.buildsrc;

import com.google.common.io.ByteStreams;
import junit.framework.TestCase;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PomHandlerTest extends TestCase {

    public void testIsRelocated() throws IOException {
        PomHandler pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/kxml2-2.3.0.pom")));
        assertNotNull(pomHandler.getRelocation());

        pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/guava-13.0.1.pom")));
        assertNull(pomHandler.getRelocation());
    }

    public void testFindParent() throws IOException {
        PomHandler pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/guava-13.0.1.pom")));
        ModuleVersionIdentifier id = pomHandler.getParentPom();
        assertNotNull(id);
        assertEquals("com.google.guava",  id.getGroup());
        assertEquals("guava-parent",  id.getName());
        assertEquals("13.0.1",  id.getVersion());

        pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/kxml2-2.3.0.pom")));
        assertNull(pomHandler.getParentPom());
    }

    public void testPackaging() throws IOException {
        PomHandler pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/oss-parent-7.pom")));
        assertEquals("pom", pomHandler.getPackaging());

        pomHandler = new PomHandler(
                getFile(getClass().getResourceAsStream("/guava-13.0.1.pom")));
        assertNull(pomHandler.getPackaging());
    }

    private File getFile(InputStream inputStream) throws IOException {
        File tmpFile = File.createTempFile("pomhandler","");
        tmpFile.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(tmpFile);
        ByteStreams.copy(inputStream, fos);
        fos.close();

        return tmpFile;
    }
}
