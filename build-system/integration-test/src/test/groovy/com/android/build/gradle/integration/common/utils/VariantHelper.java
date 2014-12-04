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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;

import java.io.File;
import java.util.Collection;

public class VariantHelper {

    private final Variant variant;
    private final File projectDir;
    private final String outputFileName;

    public VariantHelper(Variant variant, File projectDir, String outputFileName) {
        this.variant = variant;
        this.projectDir = projectDir;
        this.outputFileName = outputFileName;
    }

    public void test() {
        AndroidArtifact artifact = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", artifact);

        String variantName = variant.getName();
        File build = new File(projectDir,  "build");
        File apk = new File(build, "outputs/apk/" + outputFileName);

        Collection<File> sourceFolders = artifact.getGeneratedSourceFolders();
        assertEquals("Gen src Folder count", 4, sourceFolders.size());

        Collection<AndroidArtifactOutput> outputs = artifact.getOutputs();
        assertNotNull(outputs);
        assertEquals(1, outputs.size());
        AndroidArtifactOutput output = outputs.iterator().next();

        assertEquals(variantName + " output", apk, output.getMainOutputFile().getOutputFile());
        File manifest = output.getGeneratedManifest();
        assertNotNull(manifest);
    }
}
