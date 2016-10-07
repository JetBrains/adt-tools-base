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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Truth subject for testing NativeAndroidProject
 */
public class NativeAndroidProjectSubject
        extends Subject<NativeAndroidProjectSubject, NativeAndroidProject> {
    static class Factory
            extends SubjectFactory<NativeAndroidProjectSubject, NativeAndroidProject> {

        @NonNull
        public static NativeAndroidProjectSubject.Factory get() {
            return new NativeAndroidProjectSubject.Factory();
        }

        private Factory() {}

        @NonNull
        @Override
        public NativeAndroidProjectSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull NativeAndroidProject subject) {
            return new NativeAndroidProjectSubject(failureStrategy, subject);
        }
    }

    private NativeAndroidProjectSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull NativeAndroidProject subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    private Multimap<String, NativeArtifact> getArtifactsGroupedByGroupName() {
        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();
        for (NativeArtifact artifact : getSubject().getArtifacts()) {
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }
        return groupToArtifacts;
    }

    @NonNull
    private Multimap<String, NativeArtifact> getArtifactsByName() {
        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();
        for (NativeArtifact artifact : getSubject().getArtifacts()) {
            groupToArtifacts.put(artifact.getName(), artifact);
        }
        return groupToArtifacts;
    }

    @NonNull
    private List<File> getOutputs() {
        List<File> outputs = Lists.newArrayList();
        for (NativeArtifact artifact : getSubject().getArtifacts()) {
            outputs.add(artifact.getOutputFile());
        }
        return outputs;
    }

    @NonNull
    private Set<Path> getIntermediates() throws IOException {
        Set<File> intermediatesFolders = Sets.newHashSet();
        for (NativeArtifact artifact : getSubject().getArtifacts()) {
            File intermediatesBaseFolder = artifact.getOutputFile();
            File externalNativeBuildFolder;
            do {
                intermediatesBaseFolder = intermediatesBaseFolder.getParentFile();
                externalNativeBuildFolder = new File(intermediatesBaseFolder,
                        ".externalNativeBuild");
            } while(!externalNativeBuildFolder.isDirectory());
            intermediatesFolders.add(externalNativeBuildFolder);
        }

        Set<Path> intermediates = Sets.newHashSet();
        for (File intermediatesFolder : intermediatesFolders) {
            Path intermediatesPath = Paths.get(intermediatesFolder.getPath());
            intermediates.addAll( Files.find(intermediatesPath,
                    12, // Recursion depth
                    (path, attributes) -> attributes.isRegularFile()).collect(Collectors.toSet()));
        }
        return intermediates;
    }

    @NonNull
    private Set<String> getIntermediatesNames(String extension) throws IOException {
        Set<Path> intermediates = getIntermediates();
        Set<String> names = Sets.newHashSet();
        for (Path intermediate : intermediates) {
            if (intermediate.getFileName().toString().endsWith(extension)) {
                names.add(intermediate.getFileName().toString());
            }
        }
        return names;
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    private void hasExactOutputFiles(String extension, String... baseName) throws IOException {
        Set<String> intermediateNames = getIntermediatesNames(extension);
        Set<String> expected = Sets.newHashSet(baseName);
        Set<String> expectedNotFound = Sets.newHashSet();
        expectedNotFound.addAll(expected);
        expectedNotFound.removeAll(intermediateNames);
        if (!expectedNotFound.isEmpty()) {
            failWithRawMessage("Not true that %s build intermediates was %s. Set %s was missing %s",
                    getDisplaySubject(),
                    expected,
                    intermediateNames,
                    expectedNotFound);
        }

        Set<String> foundNotExpected = Sets.newHashSet();
        foundNotExpected.addAll(intermediateNames);
        foundNotExpected.removeAll(expected);
        if (!foundNotExpected.isEmpty()) {
            failWithRawMessage("Not true that %s build intermediates was %s. It had extras %s",
                    getDisplaySubject(),
                    expected,
                    foundNotExpected);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactObjectFiles(String... baseName) throws IOException {
        hasExactOutputFiles(".o", baseName);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactSharedObjectFiles(String... baseName) throws IOException {
        hasExactOutputFiles(".so", baseName);
    }


    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasBuildOutputCountEqualTo(int expectedCount) {
        List<File> buildOutputs = getOutputs();

        if (buildOutputs.size() != expectedCount) {
            failWithRawMessage("Not true that %s build output count was %s. It was %s",
                    getDisplaySubject(),
                    expectedCount,
                    buildOutputs.size());
        }
    }

    public void allBuildOutputsExist() {
        List<File> exist = Lists.newArrayList();
        List<File> dontExist = Lists.newArrayList();
        for (File buildOutput : getOutputs()) {
            if (!buildOutput.isFile()) {
                dontExist.add(buildOutput);
            } else {
                exist.add(buildOutput);
            }
        }
        if (!dontExist.isEmpty()) {
            failWithRawMessage("Not true that %s build outputs <%s> exist. Existing build outputs are <%s>",
                    getDisplaySubject(),
                    dontExist,
                    exist);
        }
    }

    public void noBuildOutputsExist() {
        List<File> exist = Lists.newArrayList();
        List<File> dontExist = Lists.newArrayList();
        for (File buildOutput : getOutputs()) {
            if (!buildOutput.isFile()) {
                dontExist.add(buildOutput);
            } else {
                exist.add(buildOutput);
            }
        }
        if (!exist.isEmpty()) {
            failWithRawMessage("Not true that %s build outputs <%s> don't exist. Nonexistent build outputs are <%s>",
                    getDisplaySubject(),
                    exist,
                    dontExist);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasArtifactGroupsNamed(String ...artifacts) {
        Set<String> expected = Sets.newHashSet(artifacts);
        Multimap<String, NativeArtifact> groups = getArtifactsGroupedByGroupName();
        if (!groups.keySet().equals(expected)) {
            failWithRawMessage("Not true that %s artifact groups are <%s>. They are <%s>",
                    getDisplaySubject(),
                    expected,
                    groups.keySet());
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasTargetsNamed(String ...artifacts) {
        Set<String> expected = Sets.newHashSet(artifacts);
        Multimap<String, NativeArtifact> groups = getArtifactsByName();
        if (!groups.keySet().equals(expected)) {
            failWithRawMessage("Not true that %s that qualified targets are <%s>. They are <%s>",
                    getDisplaySubject(),
                    expected,
                    groups.keySet());
        }
    }


    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasArtifactGroupsOfSize(long size) {
        Multimap<String, NativeArtifact> groups = getArtifactsGroupedByGroupName();
        for (String groupName : groups.keySet()) {
            if (groups.get(groupName).size() != size) {
                failWithRawMessage("Not true that %s artifact group %s has size %s. "
                        + "Actual size is <%s>",
                        getDisplaySubject(),
                        groupName,
                        size,
                        groups.get(groupName).size());
            }
        }
    }
}