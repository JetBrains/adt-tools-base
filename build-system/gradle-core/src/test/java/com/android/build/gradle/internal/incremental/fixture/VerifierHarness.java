/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental.fixture;

import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;

import java.io.File;
import java.io.IOException;

/**
 * Facilities shared by all tests testing the {@link InstantRunVerifier}
 */
public class VerifierHarness {

    private final boolean tracing;
    private final File baseFolder;
    private final File patchesFolder;

    public VerifierHarness(boolean tracing) {
        this.tracing = tracing;
        File classes = new File(ClassEnhancement.class.getResource("/").getFile()).getParentFile();
        File incrementalTestClasses = new File(classes, "incremental-test");
        patchesFolder = new File(incrementalTestClasses, "patches");
        baseFolder = new File(incrementalTestClasses, "base");
    }

    public InstantRunVerifierStatus verify(Class clazz, String patchLevel) throws IOException {
        String fqcn = clazz.getName();
        File originalFile = new File(baseFolder, fqcn.replace('.', File.separatorChar) + ".class");
        File patchedFile;
        if (patchLevel != null) {
            File patchLevelFolder = new File(patchesFolder, patchLevel);
            patchedFile = new File(patchLevelFolder,
                    fqcn.replace('.', File.separatorChar) + ".class");
        } else {
            patchedFile = originalFile;
        }
        return InstantRunVerifier.run(originalFile, patchedFile);
    }
}
