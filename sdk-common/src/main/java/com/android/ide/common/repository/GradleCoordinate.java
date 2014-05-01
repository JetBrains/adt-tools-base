/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a maven coordinate and allows for comparison at any level.
 * <p>
 * This class does not directly implement {@link java.lang.Comparable}; instead,
 * you should use one of the specific {@link java.util.Comparator} constants based
 * on what type of ordering you need.
 */
public class GradleCoordinate {

    /**
     * Maven coordinates take the following form: groupId:artifactId:packaging:classifier:version
     * where
     *   groupId is dot-notated alphanumeric
     *   artifactId is the name of the project
     *   packaging is optional and is jar/war/pom/aar/etc
     *   classifier is optional and provides filtering context
     *   version uniquely identifies a version.
     *
     * We only care about coordinates of the following form: groupId:artifactId:revision
     * where revision is a series of '.' separated numbers optionally terminated by a '+' character.
     */

    /**
     * List taken from <a href="http://maven.apache.org/pom.html#Maven_Coordinates">http://maven.apache.org/pom.html#Maven_Coordinates</a>
     */
    public enum ArtifactType {
        POM("pom"),
        JAR("jar"),
        MAVEN_PLUGIN("maven-plugin"),
        EJB("ejb"),
        WAR("war"),
        EAR("ear"),
        RAR("rar"),
        PAR("par"),
        AAR("aar");

        private final String myId;

        ArtifactType(String id) {
            myId = id;
        }

        @Nullable
        public static ArtifactType getArtifactType(@Nullable String name) {
            if (name != null) {
                for (ArtifactType type : ArtifactType.values()) {
                    if (type.myId.equalsIgnoreCase(name)) {
                        return type;
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return myId;
        }
    }

    public static final int PLUS_REV = -1;

    private final String myGroupId;

    private final String myArtifactId;

    private final ArtifactType myArtifactType;

    private final List<Integer> myRevisions = new ArrayList<Integer>(3);

    private final boolean myIsAnyRevision;

    private static final Pattern MAVEN_PATTERN =
            Pattern.compile("([\\w\\d\\.-]+):([\\w\\d\\.-]+):([\\d+\\.\\+]+)(@[\\w-]+)?");

    private static final Pattern REVISION_PATTERN = Pattern.compile("(\\d+|\\+)");

    /**
     * Constructor
     */
    public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId,
            @NonNull Integer... revisions) {
        this(groupId, artifactId, Arrays.asList(revisions), null);
    }

    /**
     * Constructor
     */
    public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId,
            @NonNull List<Integer> revisions, @Nullable ArtifactType type) {
        myGroupId = groupId;
        myArtifactId = artifactId;
        myRevisions.addAll(revisions);

        // If the major revision is "+" then we'll accept any revision
        myIsAnyRevision = (!myRevisions.isEmpty() && myRevisions.get(0) == PLUS_REV);

        myArtifactType = type;
    }

    /**
     * Create a GradleCoordinate from a string of the form groupId:artifactId:MajorRevision.MinorRevision.(MicroRevision|+)
     *
     * @param coordinateString the string to parse
     * @return a coordinate object or null if the given string was malformed.
     */
    @Nullable
    public static GradleCoordinate parseCoordinateString(@NonNull String coordinateString) {
        if (coordinateString == null) {
            return null;
        }

        Matcher matcher = MAVEN_PATTERN.matcher(coordinateString);
        if (!matcher.matches()) {
            return null;
        }

        String groupId = matcher.group(1);
        String artifactId = matcher.group(2);
        String revision = matcher.group(3);
        String typeString = matcher.group(4);
        ArtifactType type = null;

        if (typeString != null) {
            // Strip off the '@' symbol and try to convert
            type = ArtifactType.getArtifactType(typeString.substring(1));
        }

        matcher = REVISION_PATTERN.matcher(revision);

        List<Integer> revisions = new ArrayList<Integer>(matcher.groupCount());

        while (matcher.find()) {
            String group = matcher.group();
            revisions.add(group.equals("+") ? PLUS_REV : Integer.parseInt(group));
            // A plus revision terminates the revision string
            if (group.equals("+")) {
                break;
            }
        }

        return new GradleCoordinate(groupId, artifactId, revisions, type);
    }

    @Override
    public String toString() {
        String s = String.format(Locale.US, "%s:%s:%s", myGroupId, myArtifactId, getFullRevision());
        if (myArtifactType != null) {
            s += "@" + myArtifactType.toString();
        }
        return s;
    }

    @Nullable
    public String getGroupId() {
        return myGroupId;
    }

    @Nullable
    public String getArtifactId() {
        return myArtifactId;
    }

    @Nullable
    public String getId() {
        if (myGroupId == null || myArtifactId == null) {
            return null;
        }

        return String.format("%s:%s", myGroupId, myArtifactId);
    }

    @Nullable
    public ArtifactType getType() {
        return myArtifactType;
    }

    public boolean acceptsGreaterRevisions() {
        return myRevisions.get(myRevisions.size() - 1) == PLUS_REV;
    }

    public String getFullRevision() {
        StringBuilder revision = new StringBuilder();
        for (int i : myRevisions) {
            if (revision.length() > 0) {
                revision.append('.');
            }
            revision.append((i == PLUS_REV) ? "+" : i);
        }

        return revision.toString();
    }

    /**
     * Returns the major version (X in X.2.3), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMajorVersion() {
        return myRevisions.isEmpty() ? Integer.MIN_VALUE : myRevisions.get(0);
    }

    /**
     * Returns the minor version (X in 1.X.3), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMinorVersion() {
        return myRevisions.size() < 2 ? Integer.MIN_VALUE : myRevisions.get(1);
    }

    /**
     * Returns the major version (X in 1.2.X), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMicroVersion() {
        return myRevisions.size() < 3 ? Integer.MIN_VALUE : myRevisions.get(2);
    }

    /**
     * Returns true if and only if the given coordinate refers to the same group and artifact.
     *
     * @param o the coordinate to compare with
     * @return true iff the other group and artifact match the group and artifact of this
     * coordinate.
     */
    public boolean isSameArtifact(@NonNull GradleCoordinate o) {
        return o.myGroupId.equals(myGroupId) && o.myArtifactId.equals(myArtifactId);
    }

    @Override
    public boolean equals(@NonNull Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GradleCoordinate that = (GradleCoordinate) o;

        if (!myRevisions.equals(that.myRevisions)) {
            return false;
        }
        if (!myArtifactId.equals(that.myArtifactId)) {
            return false;
        }
        if (!myGroupId.equals(that.myGroupId)) {
            return false;
        }
        if ((myArtifactType == null) != (that.myArtifactType == null)) {
            return false;
        }
        if (myArtifactType != null && !myArtifactType.equals(that.myArtifactType)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = myGroupId.hashCode();
        result = 31 * result + myArtifactId.hashCode();
        for (Integer i : myRevisions) {
            result = 31 * result + i;
        }
        if (myArtifactType != null) {
            result = 31 * result + myArtifactType.hashCode();
        }
        return result;
    }

    /**
     * Comparator which compares Gradle versions - and treats a + version as lower
     * than a specific number in the same place. This is typically useful when trying
     * to for example order coordinates by "most specific".
     */
    public static final Comparator<GradleCoordinate> COMPARE_PLUS_LOWER =
            new Comparator<GradleCoordinate>() {
                @Override
                public int compare(@NonNull GradleCoordinate a, @NonNull GradleCoordinate b) {
                    // Make sure we're comparing apples to apples. If not, compare artifactIds
                    if (!a.isSameArtifact(b)) {
                        return a.myArtifactId.compareTo(b.myArtifactId);
                    }

                    // Specific version should beat "any version"
                    if (a.myIsAnyRevision) {
                        return -1;
                    } else if (b.myIsAnyRevision) {
                        return 1;
                    }

                    int sizeA = a.myRevisions.size();
                    int sizeB = b.myRevisions.size();
                    int common = Math.min(sizeA, sizeB);
                    for (int i = 0; i < common; ++i) {
                        int delta = a.myRevisions.get(i) - b.myRevisions.get(i);
                        if (delta != 0) {
                            return delta;
                        }
                    }
                    return sizeA < sizeB ? -1 : sizeB < sizeA ? 1 : 0;
                }
            };

    /**
     * Comparator which compares Gradle versions - and treats a + version as higher
     * than a specific number. This is typically useful when seeing if a dependency
     * is met, e.g. if you require version 0.7.3, comparing it with 0.7.+ would consider
     * 0.7.+ higher and therefore satisfying the version requirement.
     */
    public static final Comparator<GradleCoordinate> COMPARE_PLUS_HIGHER =
            new Comparator<GradleCoordinate>() {
                @Override
                public int compare(@NonNull GradleCoordinate a, @NonNull GradleCoordinate b) {
                    // Make sure we're comparing apples to apples. If not, compare artifactIds
                    if (!a.isSameArtifact(b)) {
                        return a.myArtifactId.compareTo(b.myArtifactId);
                    }

                    // Plus is always highest
                    if (a.myIsAnyRevision) {
                        return 1;
                    } else if (b.myIsAnyRevision) {
                        return -1;
                    }

                    int sizeA = a.myRevisions.size();
                    int sizeB = b.myRevisions.size();
                    int common = Math.min(sizeA, sizeB);
                    for (int i = 0; i < common; ++i) {
                        int revision1 = a.myRevisions.get(i);
                        if (revision1 == PLUS_REV) {
                            revision1 = Integer.MAX_VALUE;
                        }
                        int revision2 = b.myRevisions.get(i);
                        if (revision2 == PLUS_REV) {
                            revision2 = Integer.MAX_VALUE;
                        }
                        int delta = revision1 - revision2;
                        if (delta != 0) {
                            return delta;
                        }
                    }
                    return sizeA < sizeB ? -1 : sizeB < sizeA ? 1 : 0;
                }
            };
}
