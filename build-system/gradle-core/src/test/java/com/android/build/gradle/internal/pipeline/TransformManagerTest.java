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

import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.Iterables;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.util.List;

public class TransformManagerTest extends TaskTestUtils {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void simpleTransform() {
        // create a stream and add it to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
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
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void missingStreams() {
        // create a stream and add it to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        transformManager.addTransform(taskFactory, scope, t);

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage()).isEqualTo(
                "Unable to add Transform 'transform name' on variant 'null': requested streams not available: [PROJECT]+[] / [RESOURCES]");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void referencedScope() {
        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);

        TransformStream modulesClasses = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(modulesClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES, Scope.SUB_PROJECTS)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        // check the referenced stream is still present
        assertThat(streams).containsAllOf(libClasses, modulesClasses);

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).containsAllOf(libClasses, modulesClasses);
    }

    @Test
    public void splitStreamByTypes() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and we need to create a new stream with the unused types.
        // (class+res) -[class]-> (class, transformed) + (res, untouched)

        // create streams and add them to the pipeline
        OriginalStream projectClassAndResources = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClassAndResources);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
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
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependencies(projectClassAndResources.getDependencies())
                .withFolders(projectClassAndResources.getFolders().get())
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependencies(projectClassAndResources.getDependencies())
                .withFolders(projectClassAndResources.getFolders().get())
                .test();
    }

    @Test
    public void splitReferencedStreamByTypes() {
        // transform processes classes.
        // There's a (class, res) stream in a scope that's referenced. This stream should not
        // be split in two since it's not consumed.
        TransformStream projectClass = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClassAndResources = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClassAndResources);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES)
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
    public void splitStreamByScopes() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and we need to create a new stream with the unused types.
        // (project+libs) -[project]-> (project, transformed) + (libs, untouched)

        // create streams and add them to the pipeline
        IntermediateStream projectAndLibsClasses = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .setRootLocation(new File("folder"))
                .setDependency("my dependency")
                .build();

        transformManager.addStream(projectAndLibsClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectAndLibsClasses);

        // check we now have 2 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.EXTERNAL_LIBRARIES)
                .withDependencies(projectAndLibsClasses.getDependencies())
                .withRootLocation(projectAndLibsClasses.getRootLocation().get())
                .test();

        // we also check that the stream used by the transform only has the requested scopes.

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependencies(projectAndLibsClasses.getDependencies())
                .withRootLocation(projectAndLibsClasses.getRootLocation().get())
                .test();
    }

    @Test
    public void combinedScopes() throws Exception {
        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
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

        // check we now have 1 streams, containing both scopes.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsAllOf(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void noOpTransform() throws Exception {
        // create stream and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // check the class stream was not consumed.
        assertThat(transformManager.getStreams()).containsExactly(projectClass);

        // check the task contains no consumed streams
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).isEmpty();
        assertThat(transformTask.referencedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.outputStream).isNull();
    }

    @Test
    public void combinedTypes() {
        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses = OriginalStream.builder()
                .addContentType(DefaultContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
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
                .withContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInput() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX/RES stream.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInputWithMultiScopes() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        TransformStream libClass = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClass);

        // check we now have a single stream with CLASS/DEX and both scopes.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the streams
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInputWithSplitStream() {
        // test the case where the transform creates an additional stream, and the original
        // stream has more than the requested type.
        // (class+res) -[class]-> (res, untouched) + (class, transformed) +(dex, transformed)

        // create streams and add them to the pipeline
        TransformStream projectClass = OriginalStream.builder()
                .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setJar(new File("my file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the multi-stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX/CLASS stream.
        TransformStream outStream = streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        // and the remaining res stream, with the original dependency, and location
        streamTester()
                .withContentTypes(DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependency("my dependency")
                .withJar(new File("my file"))
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency("my dependency")
                .withJar(new File("my file"))
                .test();
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(outStream);
    }
}