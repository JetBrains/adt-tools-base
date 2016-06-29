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
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.util.Set;

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