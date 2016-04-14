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
package com.android.build.gradle.internal.tasks;

import com.android.build.gradle.internal.LibraryCache;
import com.android.utils.FileUtils;
import com.google.common.io.Files;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

@ParallelizableTask
public class PrepareLibraryTask extends DefaultAndroidTask {
    private File bundle;
    private File explodedDir;

    @TaskAction
    public void prepare() {
        //LibraryCache.getCache().unzipLibrary(this.name, project, getBundle(), getExplodedDir())
        LibraryCache.unzipAar(getBundle(), getExplodedDir(), getProject());
        // verify the we have a classes.jar, if we don't just create an empty one.
        File classesJar = new File(new File(getExplodedDir(), "jars"), "classes.jar");
        if (classesJar.exists()) {
            return;
        }
        try {
            Files.createParentDirs(classesJar);
            JarOutputStream jarOutputStream = new JarOutputStream(
                    new BufferedOutputStream(new FileOutputStream(classesJar)), new Manifest());
            jarOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create missing classes.jar", e);
        }

    }

    @InputFile
    public File getBundle() {
        return bundle;
    }

    @OutputDirectory
    public File getExplodedDir() {
        return explodedDir;
    }

    public void setBundle(File bundle) {
        this.bundle = bundle;
    }

    public void setExplodedDir(File explodedDir) {
        this.explodedDir = explodedDir;
    }

}
