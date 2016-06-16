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

package com.android.builder.internal.packaging.zip;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.compress.DeflateExecutionCompressor;
import com.android.builder.internal.packaging.zip.utils.ByteTracker;
import com.android.builder.packaging.NativeLibrariesPackagingMode;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.zip.Deflater;

/**
 * Options to create a {@link ZFile}.
 */
public class ZFileOptions {

    /**
     * SOs are aligned at 4096-byte boundaries and identified as files ending with {@code .so}.
     */
    private static final AlignmentRule SO_RULE = AlignmentRules.constantForSuffix(".so", 4096);

    /**
     * The byte tracker.
     */
    @NonNull
    private ByteTracker mTracker;

    /**
     * The compressor to use.
     */
    @NonNull
    private Compressor mCompressor;

    /**
     * Should timestamps be zeroed?
     */
    private boolean mNoTimestamps;

    /**
     * The alignment rule to use.
     */
    @NonNull
    private AlignmentRule mAlignmentRule;

    /**
     * Should the extra field be used to cover empty space?
     */
    private boolean mCoverEmptySpaceUsingExtraField;

    /**
     * Should files be automatically sorted before update?
     */
    private boolean mAutoSortFiles;

    /**
     * Packaging mode for native libraries.
     */
    private NativeLibrariesPackagingMode mNativeLibrariesPackagingMode;

    /**
     * Creates a new options object. All options are set to their defaults.
     */
    public ZFileOptions() {
        mTracker = new ByteTracker();
        mCompressor = new DeflateExecutionCompressor(MoreExecutors.sameThreadExecutor(), mTracker,
                Deflater.DEFAULT_COMPRESSION);
        mAlignmentRule = AlignmentRules.compose();
    }

    /**
     * Obtains the ZFile's byte tracker.
     *
     * @return the byte tracker
     */
    @NonNull
    public ByteTracker getTracker() {
        return mTracker;
    }

    /**
     * Sets the byte tracker to use. Setting the tracker usually requires setting the compressor
     * to use this tracker.
     *
     * @param tracker the byte tracker
     */
    public void setTracker(@NonNull ByteTracker tracker) {
        mTracker = tracker;
    }

    /**
     * Obtains the compressor to use.
     *
     * @return the compressor
     */
    @NonNull
    public Compressor getCompressor() {
        return mCompressor;
    }

    /**
     * Sets the compressor to use.
     *
     * @param compressor the compressor
     */
    public void setCompressor(@NonNull Compressor compressor) {
        mCompressor = compressor;
    }

    /**
     * Obtains whether timestamps should be zeroed.
     *
     * @return should timestamps be zeroed?
     */
    public boolean getNoTimestamps() {
        return mNoTimestamps;
    }

    /**
     * Sets whether timestamps should be zeroed.
     *
     * @param noTimestamps should timestamps be zeroed?
     */
    public void setNoTimestamps(boolean noTimestamps) {
        mNoTimestamps = noTimestamps;
    }

    /**
     * Obtains the alignment rule.
     *
     * @return the alignment rule
     */
    @NonNull
    public AlignmentRule getAlignmentRule() {
        if (mNativeLibrariesPackagingMode == NativeLibrariesPackagingMode.COMPRESSED) {
            return mAlignmentRule;
        } else {
            return AlignmentRules.compose(mAlignmentRule, SO_RULE);
        }
    }

    /**
     * Sets the alignment rule.
     *
     * @param alignmentRule the alignment rule
     */
    public void setAlignmentRule(@NonNull AlignmentRule alignmentRule) {
        mAlignmentRule = alignmentRule;
    }

    /**
     * Obtains whether the extra field should be used to cover empty spaces. See {@link ZFile} for
     * an explanation on using the extra field for covering empty spaces.
     *
     * @return should the extra field be used to cover empty spaces?
     */
    public boolean getCoverEmptySpaceUsingExtraField() {
        return mCoverEmptySpaceUsingExtraField;
    }

    /**
     * Sets whether the extra field should be used to cover empty spaces. See {@link ZFile} for an
     * explanation on using the extra field for covering empty spaces.
     *
     * @param coverEmptySpaceUsingExtraField should the extra field be used to cover empty spaces?
     */
    public void setCoverEmptySpaceUsingExtraField(boolean coverEmptySpaceUsingExtraField) {
        mCoverEmptySpaceUsingExtraField = coverEmptySpaceUsingExtraField;
    }

    /**
     * Obtains whether files should be automatically sorted before updating the zip file. See
     * {@link ZFile} for an explanation on automatic sorting.
     *
     * @return should the file be automatically sorted?
     */
    public boolean getAutoSortFiles() {
        return mAutoSortFiles;
    }

    /**
     * Sets whether files should be automatically sorted before updating the zip file. See
     * {@link ZFile} for an explanation on automatic sorting.
     *
     * @param autoSortFiles should the file be automatically sorted?
     */
    public void setAutoSortFiles(boolean autoSortFiles) {
        mAutoSortFiles = autoSortFiles;
    }

    /**
     * Packaging mode for native libraries.
     */
    public NativeLibrariesPackagingMode getNativeLibrariesPackagingMode() {
        return mNativeLibrariesPackagingMode;
    }

    public void setNativeLibrariesPackagingMode(
            @NonNull NativeLibrariesPackagingMode nativeLibrariesPackagingMode) {
        mNativeLibrariesPackagingMode = checkNotNull(nativeLibrariesPackagingMode);
    }
}
