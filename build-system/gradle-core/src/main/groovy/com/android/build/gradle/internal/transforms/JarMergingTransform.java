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

package com.android.build.gradle.internal.transforms;

import static com.android.utils.FileUtils.deleteIfExists;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.signing.SignedJarBuilder;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * A transform that merges all the incoming inputs (folders and jars) into a single jar in a
 * single combined output.
 *
 * This only packages the class files. It ignores other files.
 */
public class JarMergingTransform extends Transform {

    @NonNull
    private final ImmutableSet<Scope> scopes;

    public JarMergingTransform(@NonNull Set<Scope> scopes) {
        this.scopes = ImmutableSet.copyOf(scopes);
    }

    @NonNull
    @Override
    public String getName() {
        return "jarMerging";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return scopes;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedStreams,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws TransformException, IOException {
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // all the output will be the same since the transform type is COMBINED.
        // and format is SINGLE_JAR so output is a jar
        File jarFile = outputProvider.getContentLocation("combined", getOutputTypes(), getScopes(),
                Format.JAR);
        FileUtils.mkdirs(jarFile.getParentFile());
        deleteIfExists(jarFile);

        JarMerger jarMerger = new JarMerger(jarFile);

        try {
            jarMerger.setFilter(new SignedJarBuilder.IZipEntryFilter() {
                @Override
                public boolean checkEntry(String archivePath)
                        throws ZipAbortException {
                    return archivePath.endsWith(SdkConstants.DOT_CLASS);
                }
            });

            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    jarMerger.addJar(jarInput.getFile());
                }

                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    jarMerger.addFolder(directoryInput.getFile());
                }
            }
        } catch (FileNotFoundException e) {
            throw new TransformException(e);
        } catch (IOException e) {
            throw new TransformException(e);
        } finally {
            jarMerger.close();
        }
    }
}
