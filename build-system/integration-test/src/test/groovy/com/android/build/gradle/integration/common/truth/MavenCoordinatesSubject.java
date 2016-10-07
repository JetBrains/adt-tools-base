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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Preconditions;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;


public class MavenCoordinatesSubject extends Subject<MavenCoordinatesSubject, MavenCoordinates> {

    static class Factory extends
            SubjectFactory<MavenCoordinatesSubject, MavenCoordinates> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public MavenCoordinatesSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull MavenCoordinates subject) {
            return new MavenCoordinatesSubject(failureStrategy, subject);
        }
    }

    public MavenCoordinatesSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull MavenCoordinates subject) {
        super(failureStrategy, subject);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEqualTo(String groupId, String artifactId, String version) {
        isEqualTo(groupId, artifactId, version, null, null);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEqualTo(
            @Nullable String groupId,
            @Nullable String artifactId,
            @Nullable String version,
            @Nullable String packaging,
            @Nullable String classifier) {
        Preconditions.checkState(groupId != null || artifactId != null || version != null || packaging != null || classifier != null);

        MavenCoordinates coordinates = getSubject();

        if (groupId != null && !groupId.equals(coordinates.getGroupId())) {
            failWithRawMessage("Not true that groupId of %s is equal to %s. It is %s",
                    getDisplaySubject(), groupId, coordinates.getGroupId());
        }

        if (artifactId != null && !artifactId.equals(coordinates.getArtifactId())) {
            failWithRawMessage("Not true that artifactId of %s is equal to %s. It is %s",
                    getDisplaySubject(), artifactId, coordinates.getArtifactId());
        }

        if (version != null && !version.equals(coordinates.getVersion())) {
            failWithRawMessage("Not true that version of %s is equal to %s. It is %s",
                    getDisplaySubject(), version, coordinates.getVersion());
        }

        if (packaging != null && !packaging.equals(coordinates.getPackaging())) {
            failWithRawMessage("Not true that packaging of %s is equal to %s. It is %s",
                    getDisplaySubject(), packaging, coordinates.getPackaging());
        }

        if (classifier != null && !classifier.equals(coordinates.getClassifier())) {
            failWithRawMessage("Not true that classifier of %s is equal to %s. It is %s",
                    getDisplaySubject(), classifier, coordinates.getClassifier());
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasVersion(@NonNull String version) {
        MavenCoordinates coordinates = getSubject();

        if (!version.equals(coordinates.getVersion())) {
            failWithRawMessage("Not true that version of %s is equal to %s. It is %s",
                    getDisplaySubject(), version, coordinates.getVersion());
        }
    }
}
