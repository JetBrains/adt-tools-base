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
 * 1-alpha1, 1.0.0-rc2) or "snapshot" (e.g. 1-SNAPSHOT, 1.0.0-alpha1-SNAPSHOT).
 */
public class GradleVersion implements Comparable<GradleVersion> {

    private static final Pattern PREVIEW_PATTERN = Pattern.compile("([a-zA-z]+)([\\d]+)?");

    private String mRawValue;

    private final int mMajor;

    private final int mMinor;

    private final int mMicro;

    private final int mPreview;

    private final String mPreviewType;

    private boolean mSnapshot;

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
        int minor = 0;
        int micro = 0;
        int segmentCount = versionSegments.size();

        try {
            if (segmentCount > 0 && segmentCount <= 3) {
                major = Integer.parseInt(versionSegments.get(0));
                if (segmentCount > 1) {
                    minor = Integer.parseInt(versionSegments.get(1));
                }
                if (segmentCount == 3) {
                    micro = Integer.parseInt(versionSegments.get(2));
                }

                int preview = 0;
                String previewType = null;
                boolean snapshot = false;

                if (afterFirstDash != null) {

                    List<String> previewSegments = Splitter.on(dash).splitToList(afterFirstDash);
                    int previewSegmentCount = previewSegments.size();
                    snapshot = "SNAPSHOT"
                            .equalsIgnoreCase(previewSegments.get(previewSegmentCount - 1));

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
                return new GradleVersion(value, major, minor, micro, preview, previewType,
                        snapshot);
            }
        } catch (NumberFormatException e) {
            throw parsingFailure(value, e);
        }
        throw parsingFailure(value);
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
        this((major + "." + minor + "." + micro), major, minor, micro, 0, null, false);
    }

    private GradleVersion(@NonNull String rawValue,
            int major,
            int minor,
            int micro,
            int preview,
            @Nullable String previewType,
            boolean snapshot) {
        mRawValue = rawValue;
        mMajor = major;
        mMinor = minor;
        mMicro = micro;
        mPreview = preview;
        mPreviewType = previewType;
        mSnapshot = snapshot;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMicro() {
        return mMicro;
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
        return compareTo(version, true, true);
    }

    public int compareTo(@NonNull GradleVersion version, boolean includePreview,
            boolean includeSnapshot) {
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
        if (includePreview) {
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
        }
        if (includeSnapshot) {
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
