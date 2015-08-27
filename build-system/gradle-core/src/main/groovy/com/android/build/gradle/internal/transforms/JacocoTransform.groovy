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

package com.android.build.gradle.internal.transforms
import com.android.annotations.NonNull
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.scope.VariantScopeImpl
import com.android.build.transform.api.ScopedContent
import com.android.build.transform.api.Transform
import com.android.build.transform.api.TransformException
import com.android.build.transform.api.TransformInput
import com.android.build.transform.api.TransformOutput
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import org.gradle.util.GUtil

import static com.android.utils.FileUtils.delete
import static com.android.utils.FileUtils.mkdirs

/**
 * Jacoco Transform
 */
public class JacocoTransform implements Transform {

    @NonNull
    private final Supplier<Collection<File>> jacocoClasspath
    @NonNull
    private final VariantScopeImpl scope

    public JacocoTransform(@NonNull VariantScope scope) {
        this.scope = scope
        this.jacocoClasspath = Suppliers.memoize(new Supplier<Collection<File>>() {
            @Override
            Collection<File> get() {
                return scope.getGlobalScope().getProject().getConfigurations()
                        .getByName(JacocoPlugin.ANT_CONFIGURATION_NAME).getFiles()
            }
        })
    }

    @NonNull
    @Override
    public String getName() {
        return "jacoco"
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getScopes() {
        // only run on the project classes
        return Sets.immutableEnumSet(ScopedContent.Scope.PROJECT)
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getReferencedScopes() {
        return TransformManager.EMPTY_SCOPES
    }

    @NonNull
    @Override
    public Transform.Type getTransformType() {
        // does not combine multiple input stream.
        return Transform.Type.AS_INPUT
    }

    @NonNull
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.SINGLE_FOLDER
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return jacocoClasspath.get()
    }

    @NonNull
    @Override
    Collection<File> getSecondaryFileOutputs() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return Collections.emptyMap()
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(
            @NonNull Map<TransformInput, TransformOutput> inputOutputs,
            @NonNull List<TransformInput> referencedInputs,
            boolean isIncremental) throws TransformException {

        // this is a as_input transform with single scope, so there's only one entry in the map
        TransformInput input = Iterables.getOnlyElement(inputOutputs.keySet())
        TransformOutput output = Iterables.getOnlyElement(inputOutputs.values())

        File outputDir = output.getOutFile();
        delete(outputDir);
        mkdirs(outputDir);

        AntBuilder antBuilder = scope.globalScope.project.ant

        antBuilder.taskdef(name: 'instrumentWithJacoco',
                classname: 'org.jacoco.ant.InstrumentTask',
                classpath: GUtil.asPath(jacocoClasspath.get()))
        antBuilder.instrumentWithJacoco(destdir: outputDir) {
            fileset(dir: input.files.first())
        }
    }
}
