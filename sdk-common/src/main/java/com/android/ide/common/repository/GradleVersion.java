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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supports versions in the given formats: <ul> <li>major (e.g. 1)</li> <li>major.minor (e.g.
 * 1.0)</li> <li>major.minor.micro (e.g. 1.1.1)</li> </ul> A version can also be a "preview" (e.g.
 * 1-alpha1, 1.0.0-rc2) or an unreleased version (or "snapshot") (e.g. 1-SNAPSHOT,
 * 1.0.0-alpha1-SNAPSHOT).
 */
public class GradleVersion implements Comparable<GradleVersion> {

    private static final Pattern PREVIEW_PATTERN = Pattern.compile("([a-zA-z]+)([\\d]+)?");

    private final String mRawValue;

    @NonNull
    private final VersionSegment mMajorSegment;

    @Nullable
    private final VersionSegment mMinorSegment;

    @Nullable
    private final VersionSegment mMicroSegment;

    private final int mPreview;

    @Nullable
    private final String mPreviewType;

    private final boolean mSnapshot;

    /**
     * Parses the given version. This method does the same as {@link #parse(String)}, but it does
     * not throw exceptions if the given value does not conform with any of the supported version
     * formats.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object, or {@code null} if the given value does not
     * conform with any of the supported version formats.
     */
    @Nullable
    public static GradleVersion tryParse(@NonNull String value) {
        try {
            return parse(value);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Parses the given version.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object.
     * @throws IllegalArgumentException if the given value does not conform with any of the
     *                                  supported version formats.
     */
    @NonNull
    public static GradleVersion parse(@NonNull String value) {
        String version = value;
        String afterFirstDash = null;
        char dash = '-';
        int dashIndex = value.indexOf(dash);
        if (dashIndex != -1) {
            if (dashIndex < value.length() - 1) {
                afterFirstDash = value.substring(dashIndex + 1);
            }
            version = value.substring(0, dashIndex);
        }
        List<String> versionSegments = Splitter.on('.').splitToList(version);
        VersionSegment majorSegment;
        VersionSegment minorSegment = null;
        VersionSegment microSegment = null;

        int segmentCount = versionSegments.size();

        try {
            if (segmentCount > 0 && segmentCount <= 3) {
                majorSegment = new VersionSegment(versionSegments.get(0));
                if (segmentCount > 1) {
                    minorSegment = new VersionSegment(versionSegments.get(1));
                }
                if (segmentCount == 3) {
                    microSegment = new VersionSegment(versionSegments.get(2));
                }

                int preview = 0;
                String previewType = null;
                boolean snapshot = false;

                if (afterFirstDash != null) {

                    List<String> previewSegments = Splitter.on(dash).splitToList(afterFirstDash);
                    int previewSegmentCount = previewSegments.size();
                    String last = previewSegments.get(previewSegmentCount - 1);
                    snapshot = "SNAPSHOT".equalsIgnoreCase(last);

                    if (previewSegmentCount > 2 || (previewSegmentCount == 2 && !snapshot)) {
                        throw parsingFailure(value);
                    }

                    if (previewSegmentCount == 2 || (previewSegmentCount == 1 && !snapshot)) {
                        Matcher matcher = PREVIEW_PATTERN.matcher(previewSegments.get(0));
                        if (matcher.matches()) {
                            previewType = matcher.group(1);
                            if (matcher.groupCount() == 2) {
                                preview = Integer.parseInt(matcher.group(2));
                            }
                        } else {
                            throw parsingFailure(value);
                        }
                    }
                }
                return new GradleVersion(value, majorSegment, minorSegment, microSegment, preview,
                        previewType, snapshot);
            }
        } catch (NumberFormatException e) {
            throw parsingFailure(value, e);
        }
        throw parsingFailure(value);
    }

    private static int parseSegment(@NonNull String segment) {
        if ("+".equals(segment)) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(segment);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value) {
        return parsingFailure(value, null);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value,
            @Nullable Throwable cause) {
        return new IllegalArgumentException(String.format("'%1$s' is not a valid version", value),
                cause);
    }

    public GradleVersion(int major, int minor, int micro) {
        this((major + "." + minor + "." + micro), new VersionSegment(major),
                new VersionSegment(minor), new VersionSegment(micro), 0, null, false);
    }

    private GradleVersion(@NonNull String rawValue,
            @NonNull VersionSegment majorSegment,
            @Nullable VersionSegment minorSegment,
            @Nullable VersionSegment microSegment,
            int preview,
            @Nullable String previewType,
            boolean snapshot) {
        mRawValue = rawValue;
        mMajorSegment = majorSegment;
        mMinorSegment = minorSegment;
        mMicroSegment = microSegment;
        mPreview = preview;
        mPreviewType = previewType;
        mSnapshot = snapshot;
    }

    public int getMajor() {
        return valueOf(mMajorSegment);
    }

    @NonNull
    public VersionSegment getMajorSegment() {
        return mMajorSegment;
    }

    public int getMinor() {
        return valueOf(mMinorSegment);
    }

    @Nullable
    public VersionSegment getMinorSegment() {
        return mMinorSegment;
    }

    public int getMicro() {
        return valueOf(mMicroSegment);
    }

    private static int valueOf(@Nullable VersionSegment segment) {
        return segment != null ? segment.getValue() : 0;
    }

    @Nullable
    public VersionSegment getMicroSegment() {
        return mMicroSegment;
    }

    public int getPreview() {
        return mPreview;
    }

    @Nullable
    public String getPreviewType() {
        return mPreviewType;
    }

    public boolean isSnapshot() {
        return mSnapshot;
    }

    public int compareTo(@NonNull String version) {
        return compareTo(parse(version));
    }

    @Override
    public int compareTo(@NonNull GradleVersion version) {
        return compareTo(version, false);
    }

    public int compareIgnoringQualifiers(@NonNull String version) {
        return compareIgnoringQualifiers(parse(version));
    }

    public int compareIgnoringQualifiers(@NonNull GradleVersion version) {
        return compareTo(version, true);
    }

    private int compareTo(@NonNull GradleVersion version, boolean ignoreQualifiers) {
        int delta = getMajor() - version.getMajor();
        if (delta != 0) {
            return delta;
        }
        delta = getMinor() - version.getMinor();
        if (delta != 0) {
            return delta;
        }
        delta = getMicro() - version.getMicro();
        if (delta != 0) {
            return delta;
        }
        if (!ignoreQualifiers) {
            if (mPreviewType == null) {
                if (version.mPreviewType != null) {
                    return 1;
                }
            } else if (version.mPreviewType == null) {
                return -1;
            } else {
                delta = mPreviewType.compareToIgnoreCase(version.mPreviewType);
            }
            if (delta != 0) {
                return delta;
            }

            delta = mPreview - version.mPreview;
            if (delta != 0) {
                return delta;
            }
            delta = mSnapshot == version.mSnapshot ? 0 : (mSnapshot ? -1 : 1);
        }
        return delta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradleVersion that = (GradleVersion) o;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMajorSegment, mMinorSegment, mMicroSegment, mPreview, mPreviewType,
                mSnapshot);
    }

    @Override
    public String toString() {
        return mRawValue;
    }

    public static class VersionSegment {

        private static final String PLUS = "+";

        @NonNull
        private final String mText;

        private final int mValue;

        VersionSegment(int value) {
            mText = String.valueOf(value);
            mValue = value;
        }

        VersionSegment(@NonNull String text) {
            mText = text;
            mValue = PLUS.equals(text) ? Integer.MAX_VALUE : Integer.parseInt(text);
        }

        @NonNull
        public String getText() {
            return mText;
        }

        public int getValue() {
            return mValue;
        }

        public boolean acceptsGreaterValue() {
            return PLUS.equals(mText);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VersionSegment that = (VersionSegment) o;
            return Objects.equal(mText, that.mText);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mText);
        }

        @Override
        public String toString() {
            return mText;
        }
    }
}
