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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.utils.MsDosDateTimeUtils;
import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * The Central Directory Header contains information about files stored in the zip. Instances of
 * this class contain information for files that already are in the zip and, for which the data was
 * read from the Central Directory. But some instances of this class are used for new files.
 * Because instances of this class can refer to files not yet on the zip, some of the fields may
 * not be filled in, or may be filled in with default values.
 */
public class CentralDirectoryHeader implements Cloneable {
    /**
     * Name of the file.
     */
    @NonNull
    private String mName;

    /**
     * CRC32 of the data. 0 if not yet computed.
     */
    private long mCrc32;

    /**
     * Size of the file compressed. 0 if the file has no data.
     */
    private long mCompressedSize;

    /**
     * Size of the file uncompressed. 0 if the file has no data.
     */
    private long mUncompressedSize;

    /**
     * The compression method.
     */
    @NonNull
    private CompressionMethod mMethod;

    /**
     * Code of the program that made the zip. We actually don't care about this.
     */
    private long mMadeBy;

    /**
     * Version needed to extract the zip.
     */
    private long mVersionExtract;

    /**
     * General-purpose bit flag.
     */
    @NonNull
    private GPFlags mGpBit;

    /**
     * Last modification time in MS-DOS format (see {@link MsDosDateTimeUtils#packTime(long)}).
     */
    private long mLastModTime;

    /**
     * Last modification time in MS-DOS format (see {@link MsDosDateTimeUtils#packDate(long)}).
     */
    private long mLastModDate;

    /**
     * Extra data field contents. This field follows a specific structure according to the
     * specification, but we don't use its contents.
     */
    @NonNull
    private byte[] mExtraField;

    /**
     * File comment.
     */
    @NonNull
    private byte[] mComment;

    /**
     * File internal attributes.
     */
    private long mInternalAttributes;

    /**
     * File external attributes.
     */
    private long mExternalAttributes;

    /**
     * Offset in the file where the data is located. This will be -1 if the header corresponds to
     * a new file that is not yet written in the zip and, therefore, has no written data.
     */
    private long mOffset;

    /**
     * Creates data for a file.
     *
     * @param name the file name
     * @param compressedSize the compressed file size
     * @param uncompressedSize the uncompressed file size
     * @param method the compression method used on the file
     */
    CentralDirectoryHeader(@NonNull String name, long compressedSize, long uncompressedSize,
            @NonNull CompressionMethod method) {
        mName = name;
        mCompressedSize = compressedSize;
        mUncompressedSize = uncompressedSize;
        mMethod = method;
        mCrc32 = 0;

        if (method == CompressionMethod.STORE) {
            Preconditions.checkArgument(uncompressedSize == compressedSize,
                    "File data with STORE but compressed size != uncompressed size ("
                            + compressedSize + " vs " + uncompressedSize + " )");
        }

        /*
         * Set sensible defaults for the rest.
         */
        mMadeBy = 0;
        mGpBit = GPFlags.makeDefault();
        mLastModTime = MsDosDateTimeUtils.packCurrentTime();
        mLastModDate = MsDosDateTimeUtils.packCurrentDate();
        mExtraField = new byte[0];
        mComment = new byte[0];
        mInternalAttributes = 0;
        mExternalAttributes = 0;
        mOffset = -1;

        if (name.endsWith("/") || method == CompressionMethod.DEFLATE) {
            /*
             * Directories and compressed files only in version 2.0.
             */
            mVersionExtract = 20;
        } else {
            mVersionExtract = 10;
        }
    }

    /**
     * Obtains the name of the file.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Obtains the size of the uncompressed file.
     *
     * @return the size of the file
     */
    public long getUncompressedSize() {
        return mUncompressedSize;
    }

    /**
     * Obtains the size of the compressed file.
     *
     * @return the size of the file
     */
    public long getCompressedSize() {
        return mCompressedSize;
    }

    /**
     * Sets the compression method. Can only be called if the compression method is
     * {@link CompressionMethod#DEFLATE}.
     *
     * @param compressedSize the compressed data size
     */
    public void setCompressedSize(long compressedSize) {
        Preconditions.checkState(mMethod == CompressionMethod.DEFLATE, "Cannot set the compressed "
                + "size if compression method is not DEFLATE.");

        mCompressedSize = compressedSize;
    }

    /**
     * Obtains the compression method to use.
     *
     * @return the compression method
     */
    @NonNull
    public CompressionMethod getMethod() {
        return mMethod;
    }

    /**
     * Sets the compression method. The compression method can only be set to
     * {@link CompressionMethod#STORE} if the compressed and uncompressed sizes are equal.
     *
     * @param method the compression method
     */
    public void setMethod(@NonNull CompressionMethod method) {
        if (method == CompressionMethod.STORE) {
            Preconditions.checkState(mCompressedSize == mUncompressedSize, "Cannot mark a file "
                    + "as STORE if the compressed size (%s) is not the same as the uncompressed "
                    + "size (%s).", mCompressedSize, mUncompressedSize);
        }

        mMethod = method;
    }

    /**
     * Obtains the CRC32 of the data.
     *
     * @return the CRC32, 0 if not yet computed
     */
    public long getCrc32() {
        return mCrc32;
    }

    /**
     * Sets the CRC32 of the data.
     *
     * @param crc32 the CRC 32
     */
    public void setCrc32(long crc32) {
        mCrc32 = crc32;
    }

    /**
     * Obtains the code of the program that made the zip.
     *
     * @return the code
     */
    public long getMadeBy() {
        return mMadeBy;
    }

    /**
     * Sets the code of the progtram that made the zip.
     *
     * @param madeBy the code
     */
    public void setMadeBy(long madeBy) {
        mMadeBy = madeBy;
    }

    /**
     * Obtains the version needed to extract the entry.
     * @return the version number
     */
    public long getVersionExtract() {
        return mVersionExtract;
    }

    /**
     * Sets the version needed to extract the entry.
     *
     * @param versionExtract the version number
     */
    public void setVersionExtract(long versionExtract) {
        mVersionExtract = versionExtract;
    }

    /**
     * Obtains the general-purpose bit flag.
     *
     * @return the bit flag
     */
    @NonNull
    public GPFlags getGpBit() {
        return mGpBit;
    }

    /**
     * Sets the general-purpose bit flag.
     *
     * @param gpBit the bit flag
     */
    public void setGpBit(@NonNull GPFlags gpBit) {
        mGpBit = gpBit;
    }

    /**
     * Obtains the last modification time of the entry.
     *
     * @return the last modification time in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packTime(long)})
     */
    public long getLastModTime() {
        return mLastModTime;
    }

    /**
     * Sets the last modification time of the entry.
     *
     * @param lastModTime the last modification time in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packTime(long)})
     */
    public void setLastModTime(long lastModTime) {
        mLastModTime = lastModTime;
    }

    /**
     * Obtains the last modification date of the entry.
     *
     * @return the last modification date in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packDate(long)})
     */
    public long getLastModDate() {
        return mLastModDate;
    }

    /**
     * Sets the last modification date of the entry.
     *
     * @param lastModDate the last modification date in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packDate(long)})
     */
    public void setLastModDate(long lastModDate) {
        mLastModDate = lastModDate;
    }

    /**
     * Obtains the data in the extra field.
     *
     * @return the data (returns an empty array if there is none)
     */
    @NonNull
    public byte[] getExtraField() {
        return mExtraField;
    }

    /**
     * Sets the data in the extra field.
     *
     * @param extraField the data to set
     */
    public void setExtraField(@NonNull byte[] extraField) {
        mExtraField = extraField;
    }

    /**
     * Obtains the entry's comment.
     *
     * @return the comment (returns an empty array if there is no comment)
     */
    @NonNull
    public byte[] getComment() {
        return mComment;
    }

    /**
     * Sets the entry's comment.
     *
     * @param comment the comment
     */
    public void setComment(@NonNull byte[] comment) {
        mComment = comment;
    }

    /**
     * Obtains the entry's internal attributes.
     *
     * @return the entry's internal attributes
     */
    public long getInternalAttributes() {
        return mInternalAttributes;
    }

    /**
     * Sets the entry's internal attributes.
     *
     * @param internalAttributes the entry's internal attributes
     */
    public void setInternalAttributes(long internalAttributes) {
        mInternalAttributes = internalAttributes;
    }

    /**
     * Obtains the entry's external attributes.
     *
     * @return the entry's external attributes
     */
    public long getExternalAttributes() {
        return mExternalAttributes;
    }

    /**
     * Sets the entry's external attributes.
     *
     * @param externalAttributes the entry's external attributes
     */
    public void setExternalAttributes(long externalAttributes) {
        mExternalAttributes = externalAttributes;
    }

    /**
     * Obtains the offset in the zip file where this entry's data is.
     *
     * @return the offset or {@code -1} if the file is new and has no data in the zip yet
     */
    public long getOffset() {
        return mOffset;
    }

    /**
     * Sets the offset in the zip file where this entry's data is.
     *
     * @param offset the offset or {@code -1} if the file is new and has no data in the zip yet
     */
    public void setOffset(long offset) {
        mOffset = offset;
    }

    @Override
    protected CentralDirectoryHeader clone() throws CloneNotSupportedException {
        CentralDirectoryHeader cdr = (CentralDirectoryHeader) super.clone();
        cdr.mExtraField = Arrays.copyOf(mExtraField, mExtraField.length);
        cdr.mComment = Arrays.copyOf(mComment, mComment.length);
        return cdr;
    }
}
