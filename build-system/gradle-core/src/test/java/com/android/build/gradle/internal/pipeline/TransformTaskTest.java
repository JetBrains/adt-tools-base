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

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TransformTaskTest extends TaskTestUtils {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void nonIncWithJarInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(
                Iterables.getOnlyElement(projectClass.getJarFiles().get()));
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithJarInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithReferencedJarInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(
                Iterables.getOnlyElement(projectClass.getJarFiles().get()));
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithReferencedJarInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(QualifiedContent.DefaultContentType.CLASSES.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).isEmpty();

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleJarInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void nonIncWithFolderInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(
                Iterables.getOnlyElement(projectClass.getFolders().get()));
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithFolderInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithReferencedFolderInputInOriginalStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(
                Iterables.getOnlyElement(projectClass.getFolders().get()));
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void nonIncWithReferencedFolderInputInIntermediateStream()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getInputs()).isEmpty();

        Collection<TransformInput> inputs = t.getReferencedInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).isEmpty();
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(singleDirectoryInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incTaskWithNonIncTransformWithJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("input file");
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(jarFile)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void incTaskWithNonIncTransformWithJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        Files.write("foo", jarFile, Charsets.UTF_8);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void incTaskWithNonIncTransformWithFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = new File("input file");
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data

        File addedFile = new File(rootFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incTaskWithNonIncTransformWithFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data

        File addedFile = new File(rootFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();

        // and the jar input should be status NOTCHANGED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void incrementalJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        // Don't create deleted files. This is handled in a separate test.
        final File addedFile = new File("jar file1");
        final File changedFile = new File("jar file2");
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedFile, Status.ADDED,
                changedFile, Status.CHANGED);
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJars(new Supplier<Collection<File>>() {
                    @Override
                    public Collection<File> get() {
                        // this should not contain the removed jar files.
                        return ImmutableList.of(addedFile, changedFile);
                    }
                })
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(changedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File addedJar = output.getContentLocation("added", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(addedJar.getParentFile());
        Files.write("foo", addedJar, Charsets.UTF_8);
        File changedJar = output.getContentLocation("changed", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(changedJar.getParentFile());
        Files.write("foo", changedJar, Charsets.UTF_8);
        // no need to create a deleted jar. It's handled by a separate test.
        final ImmutableMap<File, Status> jarMap = ImmutableMap.of(
                addedJar, Status.ADDED,
                changedJar, Status.CHANGED);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedJar)
                .modifiedFile(changedJar)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // and the jar input should be status ADDED
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(jarMap.size());

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarMap.keySet());
            assertThat(jarInput.getStatus()).isSameAs(jarMap.get(file));
        }
    }

    @Test
    public void incrementalFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File addedFile = new File(rootFolder, "added");
        File modifiedFile = new File(rootFolder, "modified");
        File removedFile = new File(rootFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);

        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void incrementalFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File addedFile = new File(outputFolder, "added");
        File modifiedFile = new File(outputFolder, "modified");
        File removedFile = new File(outputFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(outputFolder);

        Map<File, Status> changedFiles = singleDirectoryInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, Status.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, Status.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
    }

    @Test
    public void deletedJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(jarFile)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File deletedJar = new File("deleted jar");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(deletedJar)
                .build());

        // in this case we cannot know what types/scopes the missing jar is associated with
        // so we expect non-incremental mode.
        assertThat(t.isIncrementalInputs()).isFalse();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);
        JarInput jarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(jarInput.getFile()).isEqualTo(jarFile);
        assertThat(jarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void deletedJarInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File jarFile = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);
        mkdirs(jarFile.getParentFile());
        // have to write content to create the file.
        Files.write("foo", jarFile, Charsets.UTF_8);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File deletedJarFile = output.getContentLocation("deleted", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.JAR);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(deletedJarFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(2);

        List<File> jarLocations = ImmutableList.of(jarFile, deletedJarFile);
        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(jarLocations);

            if (file.equals(jarFile)) {
                assertThat(jarInput.getStatus()).isSameAs(Status.NOTCHANGED);
            } else {
                assertThat(jarInput.getStatus()).isSameAs(Status.REMOVED);
            }
        }
    }

    @Test
    public void deletedFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        OriginalStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File deletedFolder = new File("deleted");
        File removedFile = new File(deletedFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(removedFile)
                .build());

        // in this case we cannot know what types/scopes the missing file/folder is associated with
        // so we expect non-incremental mode.
        assertThat(t.isIncrementalInputs()).isFalse();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);
        DirectoryInput directoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(directoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(directoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void deletedFolderInputInIntermediateStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream projectClass = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = projectClass.asOutput();
        File outputFolder = output.getContentLocation("foo", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File deletedOutputFolder = output.getContentLocation("foo2", projectClass.getContentTypes(),
                projectClass.getScopes(), Format.DIRECTORY);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        File removedFile = new File(deletedOutputFolder, "removed");
        File removedFile2 = new File(deletedOutputFolder, "removed2");
        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(removedFile)
                .removedFile(removedFile2)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(2);

        List<File> folderLocations = ImmutableList.of(outputFolder, deletedOutputFolder);
        for (DirectoryInput directoryInput : directoryInputs) {
            File file = directoryInput.getFile();
            assertThat(file).isIn(folderLocations);
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();

            if (file.equals(outputFolder)) {
                assertThat(changedFiles).isEmpty();
            } else {
                assertThat(changedFiles).hasSize(2);
                assertThat(changedFiles).containsEntry(removedFile, Status.REMOVED);
                assertThat(changedFiles).containsEntry(removedFile2, Status.REMOVED);
            }
        }
    }

    @Test
    public void incrementalTestComplexOriginalStreamOnly()
            throws TransformException, InterruptedException, IOException {
        // test with multiple scopes, both with multiple streams, and consumed and referenced scopes.

        File scope1Jar = new File("jar file1");
        File scope3Jar = new File("jar file2");
        File scope1RootFolder = new File("folder file1");
        File scope3RootFolder = new File("folder file2");

        OriginalStream scope1 = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(scope1Jar)
                .setFolder(scope1RootFolder)
                .setDependency("my dependency")
                .build();

        File scope2Root = Files.createTempDir();
        scope2Root.deleteOnExit();
        IntermediateStream scope2 = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT_LOCAL_DEPS)
                .setRootLocation(scope2Root)
                .setDependency("my dependency")
                .build();

        // use the output version of this stream to create some content.
        // only these jars could be detected as deleted.
        TransformOutputProvider output2 = scope2.asOutput();
        File scope2RootFolder = output2.getContentLocation("foo", scope2.getContentTypes(),
                scope2.getScopes(), Format.DIRECTORY);
        mkdirs(scope2RootFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File scope2Jar = output2.getContentLocation("foo2", scope2.getContentTypes(),
                scope2.getScopes(), Format.JAR);

        OriginalStream scope3 = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setJar(scope3Jar)
                .setFolder(scope3RootFolder)
                .setDependency("my dependency")
                .build();

        File scope4Root = Files.createTempDir();
        scope4Root.deleteOnExit();
        IntermediateStream scope4 = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.EXTERNAL_LIBRARIES)
                .setRootLocation(scope4Root)
                .setDependency("my dependency")
                .build();

        // use the output version of this stream to create some content.
        // only these jars could be detected as deleted.
        TransformOutputProvider output4 = scope4.asOutput();
        File scope4RootFolder = output4.getContentLocation("foo", scope4.getContentTypes(),
                scope4.getScopes(), Format.DIRECTORY);
        mkdirs(scope4RootFolder);
        // for this one just get the location. It won't be created, but we know the location
        // is correct.
        File scope4Jar = output4.getContentLocation("foo2", scope4.getContentTypes(),
                scope4.getScopes(), Format.JAR);


        final ImmutableMap<File, Status> inputJarMap1 = ImmutableMap.of(
                scope1Jar, Status.ADDED,
                scope2Jar, Status.REMOVED);

        final ImmutableMap<File, Status> inputJarMap2 = ImmutableMap.of(
                scope3Jar, Status.ADDED,
                scope4Jar, Status.REMOVED);

        transformManager.addStream(scope1);
        transformManager.addStream(scope2);
        transformManager.addStream(scope3);
        transformManager.addStream(scope4);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.PROJECT_LOCAL_DEPS)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES, Scope.SUB_PROJECTS)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3); // the new output and the 2 referenced ones.

        // call the task with incremental data
        File addedFile1 = new File(scope1RootFolder, "added");
        File removedFile2 = new File(scope2RootFolder, "removed");
        File addedFile3 = new File(scope3RootFolder, "added");
        File removedFile4 = new File(scope4RootFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(scope1Jar)
                .removedFile(scope2Jar)
                .addedFile(scope3Jar)
                .removedFile(scope4Jar)
                .addedFile(addedFile1)
                .addedFile(addedFile3)
                .removedFile(removedFile2)
                .removedFile(removedFile4)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);
        // we don't care much about the separation of the 3 inputs, so we'll mix the jar
        // and folder inputs in single lists.
        List<JarInput> jarInputs = Lists.newArrayListWithCapacity(2);
        List<DirectoryInput> directoryInputs = Lists.newArrayListWithCapacity(2);
        for (TransformInput input : inputs) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        assertThat(jarInputs).hasSize(2);

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(inputJarMap1.keySet());
            assertThat(jarInput.getStatus()).isSameAs(inputJarMap1.get(file));
        }

        assertThat(directoryInputs).hasSize(2);

        for (DirectoryInput directoryInput : directoryInputs) {
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();
            assertThat(changedFiles).hasSize(1);

            File file = directoryInput.getFile();
            assertThat(file).isAnyOf(scope1RootFolder, scope2RootFolder);

            if (file.equals(scope1RootFolder)) {
                assertThat(changedFiles).containsEntry(addedFile1, Status.ADDED);
            } else if (file.equals(scope2RootFolder)) {
                assertThat(changedFiles).containsEntry(removedFile2, Status.REMOVED);
            }
        }

        // now check on the referenced inputs.
        Collection<TransformInput> referencedInputs = t.getReferencedInputs();
        assertThat(referencedInputs).hasSize(2);
        // we don't care much about the separation of the 3 inputs, so we'll mix the jar
        // and folder inputs in single lists.
        jarInputs = Lists.newArrayListWithCapacity(2);
        directoryInputs = Lists.newArrayListWithCapacity(2);
        for (TransformInput input : referencedInputs) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        assertThat(jarInputs).hasSize(2);

        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            assertThat(file).isIn(inputJarMap2.keySet());
            assertThat(jarInput.getStatus()).isSameAs(inputJarMap2.get(file));
        }

        assertThat(directoryInputs).hasSize(2);

        for (DirectoryInput directoryInput : directoryInputs) {
            Map<File, Status> changedFiles = directoryInput.getChangedFiles();
            assertThat(changedFiles).hasSize(1);

            File file = directoryInput.getFile();
            assertThat(file).isAnyOf(scope3RootFolder, scope4RootFolder);

            if (file.equals(scope3RootFolder)) {
                assertThat(changedFiles).containsEntry(addedFile3, Status.ADDED);
            } else if (file.equals(scope4RootFolder)) {
                assertThat(changedFiles).containsEntry(removedFile4, Status.REMOVED);
            }
        }
    }

    @Test
    public void secondaryFileAddedWithJarInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = Files.createTempDir();
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(jarFile)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        File addedFile = new File(jarFile, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .addedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // Also check that the regular inputs are not marked as anything special
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<JarInput> jarInputs = input.getJarInputs();
        assertThat(jarInputs).isNotNull();
        assertThat(jarInputs).hasSize(1);

        JarInput singleJarInput = Iterables.getOnlyElement(jarInputs);
        assertThat(singleJarInput.getFile()).isEqualTo(jarFile);
        assertThat(singleJarInput.getStatus()).isSameAs(Status.NOTCHANGED);
    }

    @Test
    public void secondaryFileAddedWithFolderInputInOriginalStream()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        File addedFile = new File(rootFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .addedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // Also check that the regular inputs are not marked as anything special
        Collection<TransformInput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput input = Iterables.getOnlyElement(inputs);
        Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
        assertThat(directoryInputs).isNotNull();
        assertThat(directoryInputs).hasSize(1);

        DirectoryInput singleDirectoryInput = Iterables.getOnlyElement(directoryInputs);
        assertThat(singleDirectoryInput.getFile()).isEqualTo(rootFolder);
        assertThat(singleDirectoryInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void secondaryFileModified()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(jarFile)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .modifiedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // checks on the inputs are done in the "secondary file added" tests
    }

    @Test
    public void secondaryFileRemoved()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File jarFile = new File("jar file");
        TransformStream projectClass = OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setJar(jarFile)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestTransform t = TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setSecondaryFile(secondaryFile)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // including a normal, in stream changed file
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(jarFile)
                .removedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        // checks on the inputs are done in the "secondary file added" tests
    }

    @Test
    public void streamWithTooManyScopes()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();

        IntermediateStream stream = IntermediateStream.builder()
                .addContentTypes(DefaultContentType.CLASSES)
                .addScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .setRootLocation(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(stream);

        // use the output version of this stream to create some content.
        TransformOutputProvider output = stream.asOutput();
        File outputFolder = output.getContentLocation("foo", stream.getContentTypes(),
                stream.getScopes(), Format.DIRECTORY);
        mkdirs(outputFolder);

        // create the transform
        TestTransform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // expect an exception at runtime.
        exception.expect(RuntimeException.class);
        exception.expectMessage("error");
        transformTask.transform(inputBuilder().build());
    }

    static InputFileBuilder fileBuilder() {
        return new InputFileBuilder();
    }

    /**
     * Builder to create a mock of InputFileDetails.
     */
    static class InputFileBuilder {
        private boolean added = false;
        private boolean modified = false;
        private boolean removed = false;
        private File file = null;

        InputFileBuilder added() {
            this.added = true;
            return this;
        }

        InputFileBuilder modified() {
            this.modified = true;
            return this;
        }

        InputFileBuilder removed() {
            this.removed = true;
            return this;
        }

        InputFileBuilder setFile(File file) {
            this.file = file;
            return this;
        }

        InputFileDetails build() {
            assertTrue(added ^ modified ^ removed);
            assertNotNull(file);

            return new InputFileDetails() {

                @Override
                public boolean isAdded() {
                    return added;
                }

                @Override
                public boolean isModified() {
                    return modified;
                }

                @Override
                public boolean isRemoved() {
                    return removed;
                }

                @Override
                public File getFile() {
                    return file;
                }

                @Override
                public String toString() {
                    return Objects.toStringHelper(this)
                            .add("file", getFile())
                            .add("added", isAdded())
                            .add("modified", isModified())
                            .add("removed", isRemoved())
                            .toString();
                }
            };
        }
    }

    static InputBuilder inputBuilder() {
        return new InputBuilder();
    }

    /**
     * Builder to create a mock of IncrementalTaskInputs
     */
    static class InputBuilder {
        private boolean incremental = false;
        private final List<InputFileDetails> files = Lists.newArrayList();

        InputBuilder incremental() {
            this.incremental = true;
            return this;
        }

        InputBuilder addedFile(@NonNull File file) {
            files.add(fileBuilder().added().setFile(file).build());
            return this;
        }

        InputBuilder modifiedFile(@NonNull File file) {
            files.add(fileBuilder().modified().setFile(file).build());
            return this;
        }

        InputBuilder removedFile(@NonNull File file) {
            files.add(fileBuilder().removed().setFile(file).build());
            return this;
        }

        IncrementalTaskInputs build() {
            return new IncrementalTaskInputs() {

                @Override
                public boolean isIncremental() {
                    return incremental;
                }

                @Override
                public void outOfDate(Action<? super InputFileDetails> action) {
                    for (InputFileDetails details : files) {
                        if (details.isAdded() || details.isModified()) {
                            action.execute(details);
                        }
                    }
                }

                @Override
                public void removed(Action<? super InputFileDetails> action) {
                    for (InputFileDetails details : files) {
                        if (details.isRemoved()) {
                            action.execute(details);
                        }
                    }
                }
            };
        }
    }

}
