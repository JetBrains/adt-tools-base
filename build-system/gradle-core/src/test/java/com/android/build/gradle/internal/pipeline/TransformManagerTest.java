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

package com.android.build.gradle.internal.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.Transform.Type;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransformManagerTest {

    @Mock
    private TaskFactory taskFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void simpleTransform() {
        VariantScope variantScope = getVariantScope();

        TransformManager transformManager = new TransformManager(new AndroidTaskRegistry());

        // create a stream and add it to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform

        Transform t = transformBuilder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, variantScope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertEquals(1, streams.size());

        ImmutableList<TransformStream> classStreams = transformManager
                .getStreamsByContent(ContentType.CLASSES);
        assertEquals(1, classStreams.size());
        TransformStream classStream = Iterables.getOnlyElement(classStreams);
        assertEquals(EnumSet.of(ContentType.CLASSES), classStream.getContentTypes());
        assertEquals(Collections.singletonList(variantScope.getTaskName("")), classStream.getDependencies());

        // check the stream was consumed.
        assertFalse(streams.contains(projectClass));

        // check the task contains the stream
        // TODO?
    }

    @Test
    public void referencedScope() {
        VariantScope variantScope = getVariantScope();

        TransformManager transformManager = new TransformManager(new AndroidTaskRegistry());

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);


        // add a new transform
        Transform t = transformBuilder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, variantScope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertEquals(2, streams.size());

        // check the class stream was consumed.
        assertFalse(streams.contains(projectClass));
        // check the referenced stream is still present
        assertTrue(streams.contains(libClasses));

        // check the task contains the stream
        // TODO?
    }

    @Test
    public void splitStream() throws Exception {
        VariantScope variantScope = getVariantScope();

        TransformManager transformManager = new TransformManager(new AndroidTaskRegistry());

        // create streams and add them to the pipeline
        TransformStream projectClassAndResources = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES, ContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClassAndResources);


        // add a new transform
        Transform t = transformBuilder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, variantScope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertEquals(2, streams.size());

        // check the class stream was consumed.
        assertFalse(streams.contains(projectClassAndResources));

        // check we now have 2 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        ImmutableList<TransformStream> classStreams = transformManager
                .getStreamsByContent(ContentType.CLASSES);
        assertEquals(1, classStreams.size());
        TransformStream classStream = Iterables.getOnlyElement(classStreams);
        assertEquals(EnumSet.of(ContentType.CLASSES), classStream.getContentTypes());

        ImmutableList<TransformStream> resStreams = transformManager
                .getStreamsByContent(ContentType.RESOURCES);
        assertEquals(1, resStreams.size());
        // check content
        TransformStream resStream = Iterables.getOnlyElement(resStreams);
        assertEquals(EnumSet.of(ContentType.RESOURCES), resStream.getContentTypes());
        assertEquals(projectClassAndResources.getDependencies(), resStream.getDependencies());
        assertEquals(projectClassAndResources.getFiles().get(), resStream.getFiles().get());

        // check the task contains the stream
        // TODO?
    }

    @Test
    public void combinedScopes() throws Exception {
        VariantScope variantScope = getVariantScope();

        TransformManager transformManager = new TransformManager(new AndroidTaskRegistry());

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);


        // add a new transform
        Transform t = transformBuilder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.COMBINED)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, variantScope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertEquals(1, streams.size());

        // check the class stream was consumed.
        assertFalse(streams.contains(projectClass));
        assertFalse(streams.contains(libClasses));

        // check we now have 1 streams, containing both scopes.
        ImmutableList<TransformStream> classStreams = transformManager
                .getStreamsByContent(ContentType.CLASSES);
        assertEquals(1, classStreams.size());
        TransformStream classStream = Iterables.getOnlyElement(classStreams);
        assertEquals(EnumSet.of(ContentType.CLASSES), classStream.getContentTypes());
        assertEquals(EnumSet.of(Scope.PROJECT,
                Scope.EXTERNAL_LIBRARIES), classStream.getScopes());

        // check the task contains the stream
        // TODO?
    }

    @Test
    public void combinedTypes() {
        // TODO
    }

    @NonNull
    private static VariantScope getVariantScope() {
        GradleVariantConfiguration mockConfig = mock(GradleVariantConfiguration.class);
        when(mockConfig.getDirName()).thenReturn("config dir name");

        GlobalScope globalScope = mock(GlobalScope.class);
        when(globalScope.getBuildDir()).thenReturn(new File("build dir"));

        VariantScope variantScope = mock(VariantScope.class);
        when(variantScope.getVariantConfiguration()).thenReturn(mockConfig);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getTaskName(Mockito.anyString())).thenReturn("task name");
        return variantScope;
    }

    private static Builder transformBuilder() {
        return new Builder();
    }

    private static final class Builder {
        private String name;
        private final Set<ContentType> inputTypes = EnumSet.noneOf(ContentType.class);
        private Set<ContentType> outputTypes;
        private final Set<Scope> scopes = EnumSet.noneOf(Scope.class);
        private final Set<Scope> refedScopes = EnumSet.noneOf(Scope.class);
        private Type transformType;
        private Format format = Format.SINGLE_FOLDER;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setInputTypes(@NonNull ContentType... types) {
            inputTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder setOutputTypes(@NonNull ContentType... types) {
            if (outputTypes == null) {
                outputTypes = EnumSet.noneOf(ContentType.class);
            }
            outputTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder setScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        public Builder setReferencedScopes(@NonNull Scope... scopes) {
            this.refedScopes.addAll(Arrays.asList(scopes));
            return this;
        }

        public Builder setTransformType(
                Type transformType) {
            this.transformType = transformType;
            return this;
        }

        @NonNull
        Transform build() {
            final String name = this.name != null ? this.name : "transform name";
            Assert.assertFalse(this.inputTypes.isEmpty());
            final Set<ContentType> inputTypes = Sets.immutableEnumSet(this.inputTypes);
            final Set<ContentType> outputTypes = this.outputTypes != null ?
                    Sets.immutableEnumSet(this.outputTypes) : inputTypes;
            final Set<Scope> scopes = Sets.immutableEnumSet(this.scopes);
            final Set<Scope> refedScopes = Sets.immutableEnumSet(this.scopes);
            final Type transformType = this.transformType;
            final Format format = this.format;

            return new Transform() {
                @NonNull
                @Override
                public String getName() {
                    return name;
                }

                @NonNull
                @Override
                public Set<ContentType> getInputTypes() {
                    return inputTypes;
                }

                @NonNull
                @Override
                public Set<ContentType> getOutputTypes() {
                    return outputTypes;
                }

                @NonNull
                @Override
                public Set<Scope> getScopes() {
                    return scopes;
                }

                @NonNull
                @Override
                public Set<Scope> getReferencedScopes() {
                    return refedScopes;
                }

                @NonNull
                @Override
                public Type getTransformType() {
                    return transformType;
                }

                @NonNull
                @Override
                public Format getOutputFormat() {
                    return format;
                }

                @NonNull
                @Override
                public Collection<File> getSecondaryFileInputs() {
                    return Collections.emptyList();
                }

                @NonNull
                @Override
                public Collection<File> getSecondaryFileOutputs() {
                    return Collections.emptyList();
                }

                @NonNull
                @Override
                public Map<String, Object> getParameterInputs() {
                    return Collections.emptyMap();
                }

                @Override
                public boolean isIncremental() {
                    return false;
                }

                @Override
                public void transform(
                        @NonNull Map<TransformInput, TransformOutput> inputs,
                        @NonNull List<TransformInput> referencedInputs,
                        boolean isIncremental) throws IOException, TransformException {
                }
            };
        }
    }
}