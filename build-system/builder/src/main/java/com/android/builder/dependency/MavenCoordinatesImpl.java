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

package com.android.builder.dependency;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.List;

/**
 * Serializable implementation of MavenCoordinates for use in the model.
 */
@Immutable
public class MavenCoordinatesImpl implements MavenCoordinates, Serializable {
    @NonNull
    private final String groupId;
    @NonNull
    private final String artifactId;
    @NonNull
    private final String version;
    @NonNull
    private final String packaging;
    @Nullable
    private final String classifier;

    public MavenCoordinatesImpl(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version) {
        this(groupId, artifactId, version, null /*packaging*/, null /*classifier*/);
    }

    public MavenCoordinatesImpl(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @Nullable String packaging,
            @Nullable String classifier) {
        this.groupId = groupId.intern();
        this.artifactId = artifactId.intern();
        this.version = version.intern();
        this.packaging = packaging != null ? packaging.intern() : SdkConstants.EXT_JAR;
        this.classifier = classifier != null ? classifier.intern() : null;
    }

    @NonNull
    @Override
    public String getGroupId() {
        return groupId;
    }

    @NonNull
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @NonNull
    @Override
    public String getVersion() {
        return version;
    }

    @NonNull
    @Override
    public String getPackaging() {
        return packaging;
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    public boolean compareWithoutVersion(@NonNull MavenCoordinates coordinates) {
        return this == coordinates ||
                Objects.equal(groupId, coordinates.getGroupId()) &&
                        Objects.equal(artifactId, coordinates.getArtifactId()) &&
                        Objects.equal(packaging, coordinates.getPackaging()) &&
                        Objects.equal(classifier, coordinates.getClassifier());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MavenCoordinatesImpl that = (MavenCoordinatesImpl) o;
        return Objects.equal(groupId, that.groupId) &&
                Objects.equal(artifactId, that.artifactId) &&
                Objects.equal(version, that.version) &&
                Objects.equal(packaging, that.packaging) &&
                Objects.equal(classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(groupId, artifactId, version, packaging, classifier);
    }
    @Override
    public String toString() {
        List<String> segments = Lists.newArrayList(groupId, artifactId, packaging);
        if (!Strings.isNullOrEmpty(classifier)) {
            segments.add(classifier);
        }
        segments.add(version);
        return Joiner.on(':').join(segments);
    }
}
