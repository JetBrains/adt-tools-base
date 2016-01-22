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

    private final int mMajor;

    private final String mMajorSegment;

    private final int mMinor;

    private final String mMinorSegment;

    private final int mMicro;

    private final String mMicroSegment;

    private final int mPreview;

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
        int major;
        String majorSegment;
        int minor = 0;
        String minorSegment = null;
        int micro = 0;
        String microSegment = null;

        int segmentCount = versionSegments.size();

        try {
            if (segmentCount > 0 && segmentCount <= 3) {
                majorSegment = versionSegments.get(0);
                major = parseSegment(majorSegment);
                if (segmentCount > 1) {
                    minorSegment = versionSegments.get(1);
                    minor = parseSegment(minorSegment);
                }
                if (segmentCount == 3) {
                    microSegment = versionSegments.get(2);
                    micro = parseSegment(microSegment);
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
                return new GradleVersion(value, major, majorSegment, minor, minorSegment, micro,
                        microSegment, preview, previewType, snapshot);
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
        this((major + "." + minor + "." + micro), major, String.valueOf(major), minor,
                String.valueOf(minor), micro, String.valueOf(micro), 0, null, false);
    }

    private GradleVersion(@NonNull String rawValue,
            int major,
            @Nullable String majorSegment,
            int minor,
            @Nullable String minorSegment,
            int micro,
            @Nullable String microSegment,
            int preview,
            @Nullable String previewType,
            boolean snapshot) {
        mRawValue = rawValue;
        mMajor = major;
        mMajorSegment = majorSegment;
        mMinor = minor;
        mMinorSegment = minorSegment;
        mMicro = micro;
        mMicroSegment = microSegment;
        mPreview = preview;
        mPreviewType = previewType;
        mSnapshot = snapshot;
    }

    public int getMajor() {
        return mMajor;
    }

    @Nullable
    public String getMajorSegment() {
        return mMajorSegment;
    }

    public int getMinor() {
        return mMinor;
    }

    @Nullable
    public String getMinorSegment() {
        return mMinorSegment;
    }

    public int getMicro() {
        return mMicro;
    }

    @Nullable
    public String getMicroSegment() {
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
        int delta = mMajor - version.mMajor;
        if (delta != 0) {
            return delta;
        }
        delta = mMinor - version.mMinor;
        if (delta != 0) {
            return delta;
        }
        delta = mMicro - version.mMicro;
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
                delta = mPreviewType.compareTo(version.mPreviewType);
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
        return mMajor == that.mMajor &&
                mMinor == that.mMinor &&
                mMicro == that.mMicro &&
                mPreview == that.mPreview &&
                mSnapshot == that.mSnapshot &&
                Objects.equal(mPreviewType, that.mPreviewType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMajor, mMinor, mMicro, mPreview, mPreviewType, mSnapshot);
    }

    @Override
    public String toString() {
        return mRawValue;
    }
}
