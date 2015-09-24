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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.Transform.Type;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransformManagerTest extends TaskTestUtils {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void simpleTransform() {
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
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // and a new one is up
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }

    @Test
    public void missingStreams() {
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
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        exception.expect(RuntimeException.class);
        exception.expectMessage(
                "Unable to add Transform 'transform name' on variant 'null': requested streams not available: [PROJECT]/[RESOURCES]");
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
    }

    @Test
    public void referencedScope() {
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
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        // check the referenced stream is still present
        assertThat(streams).contains(libClasses);

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).containsExactly(libClasses);
    }

    @Test
    public void splitStream() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and we need to create a new stream with the unused types.
        // (class+res) -[class]-> (class, transformed) + (res, untouched)

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
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.AS_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClassAndResources);

        // check we now have 2 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withDependency(TASK_NAME)
                .withParentStream(projectClassAndResources)
                .test();
        streamTester()
                .withContentTypes(ContentType.RESOURCES)
                .withDependencies(projectClassAndResources.getDependencies())
                .withFiles(projectClassAndResources.getFiles().get())
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClassAndResources);
    }

    @Test
    public void splitReferencedStream() {
        // transform processes classes.
        // There's a (class, res) stream in a scope that's referenced. This stream should not
        // be split in two since it's not consumed.
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClassAndResources = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES, ContentType.RESOURCES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClassAndResources);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.AS_INPUT)
                .build();
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the referenced stream is still present in the list (ie not consumed, nor split)
        assertThat(streams).contains(libClassAndResources);
    }

    @Test
    public void combinedScopes() throws Exception {
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
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.COMBINED)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertEquals(1, streams.size());

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClasses);

        // check we now have 1 streams, containing both scopes.
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertNotNull(transformTask);
        assertThat(transformTask.consumedInputStreams).containsAllOf(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }

    @Test
    public void noOpTransform() throws Exception {
        // create stream and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.NO_OP)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // check the class stream was no consumed.
        assertThat(transformManager.getStreams()).containsExactly(projectClass);
    }

    @Test
    public void combinedTypes() {
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
                .addContentType(ContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES, ContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.COMBINED)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClasses);

        // check we now have 1 streams, containing both types.
        streamTester()
                .withContentTypes(ContentType.CLASSES, ContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }

    private static class BrokenTransform extends Transform implements AsInputTransform {

        @NonNull
        @Override
        public String getName() {
            return "transform";
        }

        @NonNull
        @Override
        public Set<ContentType> getInputTypes() {
            return null;
        }

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return null;
        }

        @NonNull
        @Override
        public Type getTransformType() {
            return Type.FORK_INPUT;
        }

        @NonNull
        @Override
        public Format getOutputFormat() {
            return Format.SINGLE_FOLDER;
        }

        @Override
        public boolean isIncremental() {
            return false;
        }

        @Override
        public void transform(@NonNull Map<TransformInput, TransformOutput> inputs,
                @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
                throws IOException, TransformException, InterruptedException {
        }
    }

    @Test
    public void forkTypeWithWrongImplementation() {
        Transform t = new BrokenTransform();

        exception.expect(RuntimeException.class);
        exception.expectMessage(
                "Transform with Type FORK_INPUT must be implementation of ForkTransform");
        // add the transform
        transformManager.addTransform(taskFactory, scope, t);
    }

    @Test
    public void forkInputWithTooManyContentTypes() {
        // Create the transform with too many input content type for a FORK_INPUT type
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES, ContentType.DEX)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.FORK_INPUT)
                .build();

        exception.expect(RuntimeException.class);
        exception.expectMessage(
                "FORK_INPUT mode only works since a single input type. Transform 'transform name' declared with [CLASSES, DEX]");

        // add the transform
        transformManager.addTransform(taskFactory, scope, t);
    }

    @Test
    public void forkInput() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.FORK_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX stream.
        streamTester()
                .withContentTypes(ContentType.DEX)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        // and a class stream
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }

    @Test
    public void forkInputWithMultiScopes() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .setTransformType(Type.FORK_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(4);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClass);

        // check we now have two DEX streams, one in each scope.
        streamTester()
                .withContentTypes(ContentType.DEX)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        streamTester()
                .withContentTypes(ContentType.DEX)
                .withScopes(Scope.SUB_PROJECTS)
                .withDependency(TASK_NAME)
                .withParentStream(libClass)
                .test();
        // and two class streams, one in each scope
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.SUB_PROJECTS)
                .withDependency(TASK_NAME)
                .withParentStream(libClass)
                .test();

        // check the task contains the streams
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }
    @Test
    public void forkInputWithSplitStream() {
        // test the case where the transform creates an additional stream, and the original
        // stream has more than the requested type.
        // (class+res) -[class]-> (res, untouched) + (class, transformed) +(dex, transformed)

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES, ContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT)
                .setTransformType(Type.FORK_INPUT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3);

        // check the multi-stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX stream.
        TransformStream newDex = streamTester()
                .withContentTypes(ContentType.DEX)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        // and a class stream
        TransformStream newClass = streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        // and the remaining res stream, with the original dependency, and location
        streamTester()
                .withContentTypes(ContentType.RESOURCES)
                .withDependency("my dependency")
                .withFile(new File("my file"))
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactly(newClass, newDex);
    }

    @Test
    public void keepInputFormat() {
        // test the case where an AS_INPUT transform doesn't change the format

        // create streams and add them to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream externalClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFormat(Format.MULTI_FOLDER)
                .setFiles(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(externalClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .setTransformType(Type.AS_INPUT)
                .setFormat(null) // Keep format of input streams.
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the original stream were consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(externalClass);

        // check we still have the same streams.
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .withFormat(Format.SINGLE_FOLDER)
                .withParentStream(projectClass)
                .test();
        // and a class stream
        streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.EXTERNAL_LIBRARIES)
                .withDependency(TASK_NAME)
                .withFormat(Format.MULTI_FOLDER)
                .withParentStream(externalClass)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, externalClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStreams).containsExactlyElementsIn(streams);
    }
}