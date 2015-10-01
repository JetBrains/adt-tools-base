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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TestTransform.TestAsInputTransform;
import com.android.build.gradle.internal.pipeline.TestTransform.TestCombinedTransform;
import com.android.build.gradle.internal.pipeline.TestTransform.TestForkTransform;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformInput.FileStatus;
import com.android.build.transform.api.TransformOutput;
import com.android.utils.FileUtils;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.truth.MapSubject;

import org.gradle.api.Action;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TransformTaskTest extends TaskTestUtils {

    @Test
    public void asInputTransformNonIncremental()
            throws IOException, TransformException, InterruptedException {
        // create a stream and add it to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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
        TransformStream ouputStream = streams.get(0);

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        // check that was passed to the transform.
        assertThat(t.isIncremental()).isFalse();
        assertThat(t.getReferencedInputs()).isEmpty();

        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();
        assertThat(tInput.getFiles()).containsExactlyElementsIn(projectClass.getFiles().get());
        assertThat(tInput.getContentTypes()).containsExactlyElementsIn(
                projectClass.getContentTypes());
        assertThat(tInput.getScopes()).containsExactlyElementsIn(projectClass.getScopes());
        assertThat(tInput.getFormat()).isEqualTo(projectClass.getFormat());
        assertThat(tInput.getChangedFiles()).isEmpty();

        TransformOutput tOutput = Iterables.getOnlyElement(inputs.entrySet()).getValue();
        assertThat(tOutput.getOutFile()).isEqualTo(Iterables.getOnlyElement(
                ouputStream.getFiles().get()));
        assertThat(tOutput.getContentTypes()).containsExactlyElementsIn(
                ouputStream.getContentTypes());
        assertThat(tOutput.getScopes()).containsExactlyElementsIn(ouputStream.getScopes());
        assertThat(tOutput.getFormat()).isEqualTo(ouputStream.getFormat());
    }

    @Test
    public void asInputTransformIncrementalWithNonIncrementalTransform()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = new File("input file");
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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
                .addedFile(new File(rootFolder, "added"))
                .build());

        // check that was passed to the transform. Should be non-incremental since the
        // transform isn't.
        assertThat(t.isIncrementalInputs()).isFalse();
    }

    @Test
    public void asInputTransformIncremental()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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
        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();
        Map<File, FileStatus> changedFiles = tInput.getChangedFiles();
        assertThat(changedFiles).hasSize(3);
        assertThat(changedFiles).containsEntry(addedFile, FileStatus.ADDED);
        assertThat(changedFiles).containsEntry(modifiedFile, FileStatus.CHANGED);
        assertThat(changedFiles).containsEntry(removedFile, FileStatus.REMOVED);
    }

    @Test
    public void asInputWithTwoInputStreamsIncremental()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File projectFolder = Files.createTempDir();
        projectFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(projectFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        File libFolder = Files.createTempDir();
        libFolder.deleteOnExit();
        TransformStream libClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(libFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .setTransformType(AsInputTransform.class)
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

        // get the two output streams.
        TransformStream projectOutput = streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();

        TransformStream libOutput = streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.SUB_PROJECTS)
                .withDependency(TASK_NAME)
                .withParentStream(libClass)
                .test();


        // call the task with incremental data
        File projectFile = new File(projectFolder, "added");
        File libFile = new File(libFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(projectFile)
                .addedFile(libFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);
        Set<Entry<TransformInput, TransformOutput>> entries = inputs.entrySet();

        // compares the TransformInput/TransformOutput withe original Streams and changed
        // files
        testTransformInputOutput(entries,
                ImmutableList.of(projectClass, libClass),
                ImmutableList.of(projectOutput, libOutput),
                ImmutableList
                        .<List<File>>of(ImmutableList.of(projectFile), ImmutableList.of(libFile)));
    }

    @Test
    public void referencedInputStreamIncremental()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File projectFolder = Files.createTempDir();
        projectFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(projectFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        File libFolder = Files.createTempDir();
        libFolder.deleteOnExit();
        TransformStream libClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.SUB_PROJECTS)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(libFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(libClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.SUB_PROJECTS)
                .setTransformType(AsInputTransform.class)
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

        // get the two output streams.
        TransformStream projectOutput = streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();

        // unconsumed lib stream
        assertThat(streams).contains(libClass);

        // call the task with incremental data
        File projectFile = new File(projectFolder, "added");
        File libFile = new File(libFolder, "added");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(projectFile)
                .addedFile(libFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // don't test everything, the rest is tested in the tests above.
        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);

        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();
        assertScopedContentsAreEquals(tInput, projectOutput);

        Map<File, FileStatus> changedFiles = tInput.getChangedFiles();
        assertThat(changedFiles).hasSize(1);
        assertThat(changedFiles).containsEntry(projectFile, FileStatus.ADDED);

        assertThat(t.getReferencedInputs()).hasSize(1);
        TransformInput referencedLib = Iterables.getOnlyElement(t.getReferencedInputs());
        assertThat(referencedLib).isNotNull();
        assertScopedContentsAreEquals(referencedLib, libClass);

        changedFiles = referencedLib.getChangedFiles();
        assertThat(changedFiles).hasSize(1);
        assertThat(changedFiles).containsEntry(libFile, FileStatus.ADDED);
    }

    // IJ's inpection cannot deal with .named() in assertThat(X).named("").isNotNull()!
    @SuppressWarnings("ConstantConditions")
    @Test
    public void multiFolderAsInputIncremental()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.MULTI_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).named("transform task").isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // substream folders must exist when the transform is run.
        File subStream1 = new File(rootFolder, "subStream1");
        FileUtils.mkdirs(subStream1);
        File subStream2 = new File(rootFolder, "subStream2");
        FileUtils.mkdirs(subStream2);

        File addedFile1 = new File(subStream1, "added");
        File modifiedFile1 = new File(subStream1, "modified");
        File removedFile1 = new File(subStream1, "removed");
        File addedFile2 = new File(subStream2, "added");
        File modifiedFile2 = new File(subStream2, "modified");
        File removedFile2 = new File(subStream2, "removed");

        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile1)
                .addedFile(addedFile2)
                .modifiedFile(modifiedFile1)
                .modifiedFile(modifiedFile2)
                .removedFile(removedFile1)
                .removedFile(removedFile2)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // there should be 2 input/output, one for each substream.
        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);

        // get the substream1.
        Entry<TransformInput, TransformOutput> entry = getEntryByFile(inputs, subStream1);
        assertThat(entry).named("substream1 entry").isNotNull();

        Map<File, FileStatus> changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream1Subject = assertThat(changedFiles).named("substream1 changed files");
        subStream1Subject.hasSize(3);
        subStream1Subject.containsEntry(addedFile1, FileStatus.ADDED);
        subStream1Subject.containsEntry(modifiedFile1, FileStatus.CHANGED);
        subStream1Subject.containsEntry(removedFile1, FileStatus.REMOVED);

        assertThat(entry.getValue()).named("subStream1 output").isNotNull();
        assertThat(entry.getValue().getOutFile().getName())
                .named("subStream1 output filename")
                .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());

        // get the substream2.
        entry = getEntryByFile(inputs, subStream2);
        assertThat(entry).named("subStream2 entry").isNotNull();
        changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream2Subject = assertThat(changedFiles).named("subStream2 changed files");
        subStream2Subject.hasSize(3);
        subStream2Subject.containsEntry(addedFile2, FileStatus.ADDED);
        subStream2Subject.containsEntry(modifiedFile2, FileStatus.CHANGED);
        subStream2Subject.containsEntry(removedFile2, FileStatus.REMOVED);

        assertThat(entry.getValue()).named("subStream2 output").isNotNull();
        assertThat(entry.getValue().getOutFile().getName())
                .named("subStream2 output filename")
                .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());
    }

    // IJ's inpection cannot deal with .named() in assertThat(X).named("").isNotNull()!
    @SuppressWarnings("ConstantConditions")
    @Test
    public void multiFolderForkInputIncremental()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.MULTI_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestForkTransform t = (TestForkTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT)
                .setTransformType(ForkTransform.class)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).named("transform task").isNotNull();

        // get the current output Stream in the transform manager. Should be two since we fork.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // call the task with incremental data
        // substream folders must exist when the transform is run.
        File subStream1 = new File(rootFolder, "subStream1");
        FileUtils.mkdirs(subStream1);
        File subStream2 = new File(rootFolder, "subStream2");
        FileUtils.mkdirs(subStream2);

        File addedFile1 = new File(subStream1, "added");
        File modifiedFile1 = new File(subStream1, "modified");
        File removedFile1 = new File(subStream1, "removed");
        File addedFile2 = new File(subStream2, "added");
        File modifiedFile2 = new File(subStream2, "modified");
        File removedFile2 = new File(subStream2, "removed");

        // looks like IJ cannot handle the .named() on the transform.isnotnull above.
        //noinspection ConstantConditions
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile1)
                .addedFile(addedFile2)
                .modifiedFile(modifiedFile1)
                .modifiedFile(modifiedFile2)
                .removedFile(removedFile1)
                .removedFile(removedFile2)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // there should be 2 input/output, one for each substream.
        Map<TransformInput, Collection<TransformOutput>> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);

        // get the substream1.
        Entry<TransformInput, Collection<TransformOutput>> entry = getEntryByFile(inputs, subStream1);
        assertThat(entry).named("substream1 entry").isNotNull();


        Map<File, FileStatus> changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream1Subject = assertThat(changedFiles).named("substream1 changed files");
        subStream1Subject.hasSize(3);
        subStream1Subject.containsEntry(addedFile1, FileStatus.ADDED);
        subStream1Subject.containsEntry(modifiedFile1, FileStatus.CHANGED);
        subStream1Subject.containsEntry(removedFile1, FileStatus.REMOVED);

        assertThat(entry.getValue()).named("subStream1 output collection").isNotNull();
        // There should be 2 outputs for the entry.
        assertThat(entry.getValue()).named("substream1 output collection").hasSize(2);
        for (TransformOutput output : entry.getValue()) {
            assertThat(output.getOutFile().getName())
                    .named("subStream1 output filename")
                    .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());
            assertThat(output.getContentTypes())
                    .named("subStream1 output type")
                    .containsAnyOf(ContentType.DEX, ContentType.CLASSES);
            assertThat(output.getContentTypes())
                    .named("subStream1 output type size").hasSize(1);
        }

        // get the substream2.
        entry = getEntryByFile(inputs, subStream2);
        assertThat(entry).named("subStream2 entry").isNotNull();
        changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream2Subject = assertThat(changedFiles).named("subStream2 changed files");
        subStream2Subject.hasSize(3);
        subStream2Subject.containsEntry(addedFile2, FileStatus.ADDED);
        subStream2Subject.containsEntry(modifiedFile2, FileStatus.CHANGED);
        subStream2Subject.containsEntry(removedFile2, FileStatus.REMOVED);

        assertThat(entry.getValue()).named("subStream2 output collection").isNotNull();
        // There should be 2 outputs for the entry.
        assertThat(entry.getValue()).named("subStream2 output collection").hasSize(2);
        for (TransformOutput output : entry.getValue()) {
            assertThat(output.getOutFile().getName())
                    .named("subStream2 output filename")
                    .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());
            assertThat(output.getContentTypes())
                    .named("subStream2 output type")
                    .containsAnyOf(ContentType.DEX, ContentType.CLASSES);
            assertThat(output.getContentTypes())
                    .named("subStream2 output type size").hasSize(1);
        }
    }

    // IJ's inpection cannot deal with .named() in assertThat(X).named("").isNotNull()!
    @SuppressWarnings("ConstantConditions")
    @Test
    public void multiFolderWithMissingFolder()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.MULTI_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).named("transform task").isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // call the task with incremental data
        // substream folders must exist when the transform is run.
        File subStream1 = new File(rootFolder, "subStream1");
        FileUtils.mkdirs(subStream1);

        // create a 2nd stream that does not exist anymore
        File subStream2 = new File(rootFolder, "subStream2");
        File removedFile2 = new File(subStream2, "removed");
        File removedFile2b = new File(subStream2, "removed_bis");

        transformTask.transform(inputBuilder()
                .incremental()
                .removedFile(removedFile2)
                .removedFile(removedFile2b)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // there should be 2 input/output, one for each substream.
        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(2);

        // get the substream1.
        Entry<TransformInput, TransformOutput> entry = getEntryByFile(inputs, subStream1);
        assertThat(entry).named("substream1 entry").isNotNull();

        Map<File, FileStatus> changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream1Subject = assertThat(changedFiles).named("substream1 changed files");
        subStream1Subject.hasSize(0);

        assertThat(entry.getValue()).named("subStream1 output").isNotNull();
        assertThat(entry.getValue().getOutFile().getName())
                .named("subStream1 output filename")
                .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());

        // get the substream2.
        entry = getEntryByFile(inputs, subStream2);
        assertThat(entry).named("subStream2 entry").isNotNull();
        changedFiles = entry.getKey().getChangedFiles();
        MapSubject subStream2Subject = assertThat(changedFiles).named("subStream2 changed files");
        subStream2Subject.hasSize(2);
        subStream2Subject.containsEntry(removedFile2, FileStatus.REMOVED);
        subStream2Subject.containsEntry(removedFile2b, FileStatus.REMOVED);

        assertThat(entry.getValue()).named("subStream2 output").isNotNull();
        assertThat(entry.getValue().getOutFile().getName())
                .named("subStream2 output filename")
                .isEqualTo(Iterables.getOnlyElement(entry.getKey().getFiles()).getName());
    }

    private static <T> Entry<TransformInput, T> getEntryByFile(
            @NonNull Map<TransformInput, T> map,
            @NonNull File rootFile) {
        for (Entry<TransformInput, T> entry : map.entrySet()) {
            if (rootFile.equals(Iterables.getOnlyElement(entry.getKey().getFiles()))) {
                return entry;
            }
        }

        return null;
    }


    @Test
    public void forkInput() throws TransformException, InterruptedException, IOException {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        File projectFolder = Files.createTempDir();
        TransformStream projectClass = TransformStream.builder()
                .addContentTypes(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(projectFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // add a new transform
        TestForkTransform t = (TestForkTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setOutputTypes(ContentType.CLASSES, ContentType.DEX)
                .setScopes(Scope.PROJECT)
                .setTransformType(ForkTransform.class)
                .build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the two output streams.
        TransformStream newDex = streamTester()
                .withContentTypes(ContentType.DEX)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();
        TransformStream newClass = streamTester()
                .withContentTypes(ContentType.CLASSES)
                .withDependency(TASK_NAME)
                .withParentStream(projectClass)
                .test();


        // call the task with incremental data
        File addedFile = new File(projectFolder, "added");
        File modifiedFile = new File(projectFolder, "modified");
        File removedFile = new File(projectFolder, "removed");
        transformTask.transform(inputBuilder()
                .incremental()
                .addedFile(addedFile)
                .modifiedFile(modifiedFile)
                .removedFile(removedFile)
                .build());

        // check that was passed to the transform.
        assertThat(t.isIncrementalInputs()).isTrue();

        // there should be a single item in the input map.
        Map<TransformInput, Collection<TransformOutput>> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);
        Entry<TransformInput, Collection<TransformOutput>> entry = Iterables.getOnlyElement(
                inputs.entrySet());

        // test the input matches the original stream
        TransformInput transformInput = entry.getKey();
        assertScopedContentsAreEquals(transformInput, projectClass);
        assertThat(transformInput.getFiles()).containsExactlyElementsIn(
                projectClass.getFiles().get());

        Collection<TransformOutput> outputs = entry.getValue();
        assertThat(outputs).hasSize(2);
        // make sure we have 2 outputs that match what we expect
        findMatchingScopedIn(newDex, outputs);
        findMatchingScopedIn(newClass, outputs);
    }

    @Test
    public void secondaryFileAdded()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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

        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);
        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();

        // check the changed files is empty.
        assertThat(tInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void secondaryFileModified()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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
                .modifiedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);
        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();

        // check the changed files is empty.
        assertThat(tInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void secondaryFileRemoved()
            throws TransformException, InterruptedException, IOException {
        // create a stream and add it to the pipeline
        File rootFolder = Files.createTempDir();
        rootFolder.deleteOnExit();
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(rootFolder)
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform, with a 2ndary file.
        File secondaryFile = new File("secondary file");
        TestAsInputTransform t = (TestAsInputTransform) TestTransform.builder()
                .setIncremental(true)
                .setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(AsInputTransform.class)
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
                .removedFile(secondaryFile)
                .build());

        // check that was passed to the transform. Incremental should be off due
        // to secondary file
        assertThat(t.isIncrementalInputs()).isFalse();

        Map<TransformInput, TransformOutput> inputs = t.getInputs();
        assertThat(inputs).hasSize(1);
        TransformInput tInput = Iterables.getOnlyElement(inputs.entrySet()).getKey();

        // check the changed files is empty.
        assertThat(tInput.getChangedFiles()).isEmpty();
    }

    @Test
    public void asInputJarTransform() throws TransformException, InterruptedException, IOException {
        testJarTransform(
                TestAsInputTransform.class,
                null,
                new Action<TestAsInputTransform>() {
            @Override
            public void execute(TestAsInputTransform transform) {
                Map<TransformInput, TransformOutput> inputs = transform.getInputs();
                validateJarOutput(Iterables.getOnlyElement(inputs.entrySet()).getValue());
            }
        });
    }

    @Test
    public void combinedJarTransform() throws TransformException, InterruptedException, IOException {
        testJarTransform(
                TestCombinedTransform.class,
                null,
                new Action<TestCombinedTransform>() {
            @Override
            public void execute(TestCombinedTransform transform) {
                validateJarOutput(transform.getOutput());
            }
        });
    }

    @Test
    public void forkInputJarTransform() throws TransformException, InterruptedException, IOException {
        testJarTransform(
                TestForkTransform.class,
                EnumSet.of(ContentType.CLASSES, ContentType.DEX),
                new Action<TestForkTransform>() {
            @Override
            public void execute(TestForkTransform transform) {
                Map<TransformInput, Collection<TransformOutput>> inputs = transform.getInputs();
                Collection<TransformOutput> outputs = Iterables.getOnlyElement(inputs.entrySet())
                        .getValue();
                for (TransformOutput output : outputs) {
                    validateJarOutput(output);
                }
            }
        });
    }

    private <T extends TestTransform> void testJarTransform(
            @NonNull Class<T> transformClass,
            @Nullable Set<ContentType> outputTypes,
            @NonNull Action<T> action)
            throws TransformException, InterruptedException, IOException {

        // create a stream and add it to the pipeline
        TransformStream projectClass = TransformStream.builder()
                .addContentType(ContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFormat(Format.SINGLE_FOLDER)
                .setFiles(new File("input file"))
                .setDependency("my dependency")
                .build();
        transformManager.addStream(projectClass);

        // create the transform
        TestTransform.Builder builder = TestTransform.builder();
        builder.setInputTypes(ContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setTransformType(transformClass)
                .setFormat(Format.JAR);
        if (outputTypes != null) {
            builder.setOutputTypes(outputTypes);
        }

        //noinspection unchecked
        T t = (T) builder.build();

        // add the transform to the manager
        AndroidTask<TransformTask> task = transformManager.addTransform(
                taskFactory, scope, t);
        // and get the real gradle task object
        TransformTask transformTask = (TransformTask) taskFactory.named(task.getName());
        assertThat(transformTask).isNotNull();

        // get the current output Stream in the transform manager.
        List<TransformStream> streams = transformManager.getStreams();

        // call the task with a non-incremental build.
        transformTask.transform(inputBuilder().build());

        action.execute(t);
    }

    private static void validateJarOutput(@NonNull TransformOutput transformOutput) {
        File outFile = transformOutput.getOutFile();
        assertThat(outFile.getName()).named("output file filename").isEqualTo("classes.jar");
        assertThat(outFile.exists()).named("output file should not exist").isFalse();
        assertThat(outFile.getParentFile().isDirectory()).named(
                "output file parent folder should exist").isTrue();
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

    /**
     * tests that the data passed to a AS_INPUT transform transform() method matches the
     * input/output streams on the tranform.
     * @param entries the map passed to transform()
     * @param inputs the input streams
     * @param outputs the output streams
     * @param changedFiles the list of changed files that we should be expected, ordered as the inputs.
     */
    private static void testTransformInputOutput(
            @NonNull Set<Entry<TransformInput, TransformOutput>> entries,
            @NonNull List<TransformStream> inputs,
            @NonNull List<TransformStream> outputs,
            @NonNull List<List<File>> changedFiles) {

        // loop on the inputs and then loop on the keys and find a matching one,
        // per scope/content-type/etc..
        // it's not super efficient but we can't do better.
        for (int i = 0, count = inputs.size() ; i < count ; i++) {
            TransformStream input = inputs.get(i);
            boolean found = false;
            for (Entry<TransformInput, TransformOutput> entry : entries) {
                TransformInput transformInput = entry.getKey();
                if (compareScopedContents(input, transformInput)) {
                    // check for the changed files.
                    assertThat(transformInput.getChangedFiles().keySet()).containsExactlyElementsIn(
                            changedFiles.get(i));

                    TransformOutput transformOutput = entry.getValue();
                    if (transformOutput != null) {
                        // search for a matching output transform Stream, and then compare it to the
                        // transform output.
                        TransformStream outputMatch = findOutputFor(input, outputs);
                        assertThat(outputMatch).isNotNull();
                        assertScopedContentsAreEquals(outputMatch, transformOutput);

                        assertThat(transformOutput.getOutFile())
                                .isEqualTo(Iterables.getOnlyElement(outputMatch.getFiles().get()));
                    }

                    found = true;
                    break;
                }
            }

            // didn't find a match?
            if (!found) {
                fail("Failed to find match for " + input.toString());
            }
        }
    }

    /**
     * compare to scope content and return true if equals.
     * @param content1 scoped content 1
     * @param content2 scoped content 2
     * @return true if equals
     */
    private static boolean compareScopedContents(
            @NonNull ScopedContent content1,
            @NonNull ScopedContent content2) {
        return content1.getContentTypes().equals(content2.getContentTypes()) &&
                content1.getScopes().equals(content2.getScopes()) &&
                content1.getFormat().equals(content2.getFormat());
    }

    /**
     * asserts that 2 scoped contents are equals.
     * @param tested the scoped content to test
     * @param expected the expected scoped content.
     */
    private static void assertScopedContentsAreEquals(
            @NonNull ScopedContent tested,
            @NonNull ScopedContent expected) {
        assertThat(tested.getContentTypes()).containsExactlyElementsIn(expected.getContentTypes());
        assertThat(tested.getScopes()).containsExactlyElementsIn(expected.getScopes());
        assertThat(tested.getFormat()).isEqualTo(expected.getFormat());
    }

    private static TransformStream findOutputFor(
            @NonNull TransformStream input,
            @NonNull List<TransformStream> outputs) {
        for (TransformStream output : outputs) {
            if (output.getParentStream() == input) {
                return output;
            }
        }

        return null;
    }

    private static void findMatchingScopedIn(
            @NonNull ScopedContent match,
            @NonNull Collection<TransformOutput> outputs) {
        for (TransformOutput output : outputs) {
            if (compareScopedContents(match, output)) {
                return;
            }
        }

        fail(String.format("Failed to find %s in %s", match, outputs));
    }
}
