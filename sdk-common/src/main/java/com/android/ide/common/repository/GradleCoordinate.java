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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a maven coordinate and allows for comparison at any level.
 */
public class GradleCoordinate implements Comparable<GradleCoordinate> {

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

  public static final int PLUS_REV = -1;

  private final String myGroupId;
  private final String myArtifactId;

  private final List<Integer> myRevisions = new ArrayList<Integer>(3);
  private final boolean myIsAnyRevision;

  private static final Pattern MAVEN_PATTERN = Pattern.compile("([\\w\\d\\.-]+):([\\w\\d\\.-]+):([\\d+\\.\\+]+)");
  private static final Pattern REVISION_PATTERN = Pattern.compile("(\\d+|\\+)");

  /**
   * Constructor
   * @param groupId
   * @param artifactId
   * @param revisions
   */
  public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId, @NonNull Integer... revisions) {
    this(groupId, artifactId, Arrays.asList(revisions));
  }

  /**
   * Constructor
   * @param groupId
   * @param artifactId
   * @param revisions
   */
  public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId, @NonNull List<Integer> revisions) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myRevisions.addAll(revisions);

    // If the major revision is "+" then we'll accept any revision
    myIsAnyRevision = (!myRevisions.isEmpty() && myRevisions.get(0) == PLUS_REV);
  }

  /**
   * Create a GradleCoordinate from a string of the form groupId:artifactId:MajorRevision.MinorRevision.(MicroRevision|+)
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

    return new GradleCoordinate(groupId, artifactId, revisions);
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "%s:%s:%s", myGroupId, myArtifactId, getFullRevision());
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
   * Returns true if and only if the given coordinate refers to the same group and artifact.
   * @param o the coordinate to compare with
   * @return true iff the other group and artifact match the group and artifact of this coordinate.
   */
  public boolean isSameArtifact(@NonNull GradleCoordinate o) {
    return o.myGroupId.equals(myGroupId) && o.myArtifactId.equals(myArtifactId);
  }

  @Override
  public boolean equals(@NonNull Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleCoordinate that = (GradleCoordinate)o;

    if (!myRevisions.equals(that.myRevisions)) return false;
    if (!myArtifactId.equals(that.myArtifactId)) return false;
    if (!myGroupId.equals(that.myGroupId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId.hashCode();
    result = 31 * result + myArtifactId.hashCode();
    for (Integer i : myRevisions) {
      result = 31 * result + i;
    }
    return result;
  }

  @Override
  public int compareTo(@NonNull GradleCoordinate that) {
    // Make sure we're comparing apples to apples. If not, compare artifactIds
    if (!this.isSameArtifact(that)) {
      return this.myArtifactId.compareTo(that.myArtifactId);
    }

    // Specific version should beat "any version"
    if (myIsAnyRevision) {
      return -1;
    } else if (that.myIsAnyRevision) {
      return 1;
    }

    for (int i = 0; i < myRevisions.size(); ++i) {
      int delta = myRevisions.get(i) - that.myRevisions.get(i);
      if (delta != 0) {
        return delta;
      }
    }
    return 0;
  }
}
