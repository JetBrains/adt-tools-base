/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.Task;
import com.android.ide.common.process.ProcessException;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.UnrecoverableException;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * PreDex Jack libraries created from Jill tranform.
 */
public class JackPreDexTransform extends Transform {

    private AndroidBuilder androidBuilder;
    private String javaMaxHeapSize;
    private boolean jackInProcess;

    public JackPreDexTransform(AndroidBuilder androidBuilder, String javaMaxHeapSize,
            boolean jackInProcess) {
        this.androidBuilder = androidBuilder;
        this.javaMaxHeapSize = javaMaxHeapSize;
        this.jackInProcess = jackInProcess;
    }

    @NonNull
    @Override
    public String getName() {
        return "preDexJackPackagedLibraries";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        final Job<Void> job = new Job<>(getName(), new Task<Void>() {
            @Override
            public void run(@NonNull Job<Void> job, @NonNull JobContext<Void> context)
                    throws IOException {
                try {
                    runJack(transformInvocation);
                } catch (ProcessException e) {
                    throw new IOException(e);
                } catch (ConfigurationException e) {
                    throw new IOException(e);
                } catch (UnrecoverableException e) {
                    throw new IOException(e);
                } catch (CompilationException e) {
                    throw new IOException(e);
                } catch (ConfigNotSupportedException e) {
                    throw new IOException(e);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            }

        });
        try {
            SimpleWorkQueue.push(job);

            // wait for the task completion.
            if (!job.awaitRethrowExceptions()) {
                throw new RuntimeException("Jack compilation failed, see logs for details");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ConfigNotSupportedException, CompilationException, ProcessException,
            UnrecoverableException, ConfigurationException, ClassNotFoundException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);

        for (File file : TransformInputUtil.getDirectories(transformInvocation.getInputs())) {
            JackProcessOptions options = new JackProcessOptions();
            options.setImportFiles(ImmutableList.of(file));
            File outDirectory = outputProvider.getContentLocation(
                    file.getName(),
                    getOutputTypes(),
                    getScopes(),
                    Format.DIRECTORY);
            options.setDexOutputDirectory(outDirectory);
            options.setJavaMaxHeapSize(javaMaxHeapSize);

            androidBuilder.convertByteCodeUsingJack(options, jackInProcess);
        }
        for (File file : TransformInputUtil.getJarFiles(transformInvocation.getInputs())) {
            JackProcessOptions options = new JackProcessOptions();
            options.setImportFiles(ImmutableList.of(file));
            File outFile = outputProvider.getContentLocation(
                    file.getName().substring(0, file.getName().lastIndexOf('.')),
                    getOutputTypes(),
                    getScopes(),
                    Format.JAR);
            options.setOutputFile(outFile);
            options.setJavaMaxHeapSize(javaMaxHeapSize);

            androidBuilder.convertByteCodeUsingJack(options, jackInProcess);
        }
    }
}
