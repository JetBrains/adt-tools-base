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
import com.android.builder.internal.packaging.zip.utils.CachedSupplier;
import com.android.builder.internal.utils.IOExceptionWrapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Ints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * End Of Central Directory record in a zip file.
 */
class Eocd {
    /**
     * Field in the record: the record signature, fixed at this value by the specification.
     */
    private static final ZipField.F4 F_SIGNATURE = new ZipField.F4(0, 0x06054b50, "EOCD signature");

    /**
     * Field in the record: the number of the disk where the EOCD is located. It has to be zero
     * because we do not support multi-file archives.
     */
    private static final ZipField.F2 F_NUMBER_OF_DISK = new ZipField.F2(F_SIGNATURE.endOffset(), 0,
            "Number of this disk");

    /**
     * Field in the record: the number of the disk where the Central Directory starts. Has to be
     * zero because we do not support multi-file archives.
     */
    private static final ZipField.F2 F_DISK_CD_START = new ZipField.F2(F_NUMBER_OF_DISK.endOffset(),
            0, "Disk where CD starts");

    /**
     * Field in the record: the number of entries in the Central Directory on this disk. Because
     * we do not support multi-file archives, this is the same as {@link #F_RECORDS_TOTAL}.
     */
    private static final ZipField.F2 F_RECORDS_DISK = new ZipField.F2(F_DISK_CD_START.endOffset(),
            "Record on disk count", new ZipFieldInvariantNonNegative());

    /**
     * Field in the record: the total number of entries in the Central Directory.
     */
    private static final ZipField.F2 F_RECORDS_TOTAL = new ZipField.F2(F_RECORDS_DISK.endOffset(),
            "Total records", new ZipFieldInvariantNonNegative(),
            new ZipFieldInvariantMaxValue(Integer.MAX_VALUE));

    /**
     * Field in the record: number of bytes of the Central Directory.
     * This is not private because it is required in unit tests.
     */
    @VisibleForTesting
    static final ZipField.F4 F_CD_SIZE = new ZipField.F4(F_RECORDS_TOTAL.endOffset(),
            "Directory size", new ZipFieldInvariantNonNegative());

    /**
     * Field in the record: offset, from the archive start, where the Central Directory starts.
     * This is not private because it is required in unit tests.
     */
    @VisibleForTesting
    static final ZipField.F4 F_CD_OFFSET = new ZipField.F4(F_CD_SIZE.endOffset(),
            "Directory offset", new ZipFieldInvariantNonNegative());

    /**
     * Field in the record: number of bytes of the file comment (located at the end of the EOCD
     * record).
     */
    private static final ZipField.F2 F_COMMENT_SIZE = new ZipField.F2(F_CD_OFFSET.endOffset(),
            "File comment size", new ZipFieldInvariantNonNegative());

    /**
     * Number of entries in the central directory.
     */
    private final int mTotalRecords;

    /**
     * Offset from the beginning of the archive where the Central Directory is located.
     */
    private final long mDirectoryOffset;

    /**
     * Number of bytes of the Central Directory.
     */
    private final long mDirectorySize;

    /**
     * Contents of the EOCD comment.
     */
    @NonNull
    private final byte[] mComment;

    /**
     * Supplier of the byte representation of the EOCD.
     */
    @NonNull
    private final CachedSupplier<byte[]> mByteSupplier;

    /**
     * Creates a new EOCD, reading it from a byte source. This method will parse the byte source
     * and obtain the EOCD. It will check that the byte source starts with the EOCD signature.
     *
     * @param bytes the byte source with the EOCD data
     * @throws IOException failed to read information or the EOCD data is corrupt or invalid
     */
    Eocd(@NonNull ByteSource bytes) throws IOException {
        Preconditions.checkNotNull(bytes, "bytes == null");

        /*
         * Read the EOCD record.
         */
        F_SIGNATURE.verify(bytes);
        F_NUMBER_OF_DISK.verify(bytes);
        F_DISK_CD_START.verify(bytes);
        long totalRecords1 = F_RECORDS_DISK.read(bytes);
        long totalRecords2 = F_RECORDS_TOTAL.read(bytes);
        long directorySize = F_CD_SIZE.read(bytes);
        long directoryOffset = F_CD_OFFSET.read(bytes);
        long commentSize = F_COMMENT_SIZE.read(bytes);

        /*
         * Some sanity checks.
         */
        if (totalRecords1 !=  totalRecords2) {
            throw new IOException("Zip states records split in multiple disks, which is not "
                    + "supported.");
        }

        Verify.verify(totalRecords1 <= Integer.MAX_VALUE);

        mTotalRecords = Ints.checkedCast(totalRecords1);
        mDirectorySize = directorySize;
        mDirectoryOffset = directoryOffset;

        if (bytes.size() < F_COMMENT_SIZE.endOffset() + commentSize) {
            throw new IOException("Corrupt EOCD record: not enough data for comment (comment "
                    + "size is " + commentSize + ").");
        }

        mComment = bytes.slice(F_COMMENT_SIZE.endOffset(), commentSize).read();
        mByteSupplier = new CachedSupplier<byte[]>() {
            @Override
            protected byte[] compute() throws IOException {
                return computeByteRepresentation();
            }
        };
    }

    /**
     * Creates a new EOCD. This is used when generating an EOCD for an Central Directory that has
     * just been generated. The EOCD will be generated without any comment.
     *
     * @param totalRecords total number of records in the directory
     * @param directoryOffset offset, since beginning of archive, where the Central Directory is
     * located
     * @param directorySize number of bytes of the Central Directory
     */
    Eocd(int totalRecords, long directoryOffset, long directorySize) {
        Preconditions.checkArgument(totalRecords >= 0, "totalRecords < 0");
        Preconditions.checkArgument(directoryOffset >= 0, "directoryOffset < 0");
        Preconditions.checkArgument(directorySize >= 0, "directorySize < 0");

        mTotalRecords = totalRecords;
        mDirectoryOffset = directoryOffset;
        mDirectorySize = directorySize;
        mComment = new byte[0];
        mByteSupplier = new CachedSupplier<byte[]>() {
            @Override
            protected byte[] compute() {
                try {
                    return computeByteRepresentation();
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            }
        };
    }

    /**
     * Obtains the number of records in the Central Directory.
     *
     * @return the number of records
     */
    int getTotalRecords() {
        return mTotalRecords;
    }

    /**
     * Obtains the offset since the beginning of the zip archive where the Central Directory is
     * located.
     *
     * @return the offset where the Central Directory is located
     */
    long getDirectoryOffset() {
        return mDirectoryOffset;
    }

    /**
     * Obtains the size of the Central Directory.
     *
     * @return the number of bytes that make up the Central Directory
     */
    long getDirectorySize() {
        return mDirectorySize;
    }

    /**
     * Obtains the size of the EOCD.
     *
     * @return the size, in bytes, of the EOCD
     */
    long getEocdSize() {
        return F_COMMENT_SIZE.endOffset() + mComment.length;
    }

    /**
     * Generates the EOCD data.
     *
     * @return a byte representation of the EOCD that has exactly {@link #getEocdSize()} bytes
     * @throws IOException failed to generate the EOCD data
     */
    @NonNull
    byte[] toBytes() throws IOException {
        return mByteSupplier.get();
    }

    /**
     * Computes the byte representation of the EOCD.
     *
     * @return a byte representation of the EOCD that has exactly {@link #getEocdSize()} bytes
     * @throws IOException failed to generate the EOCD data
     */
    @NonNull
    private byte[] computeByteRepresentation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        F_SIGNATURE.write(out);
        F_NUMBER_OF_DISK.write(out);
        F_DISK_CD_START.write(out);
        F_RECORDS_DISK.write(out, mTotalRecords);
        F_RECORDS_TOTAL.write(out, mTotalRecords);
        F_CD_SIZE.write(out, mDirectorySize);
        F_CD_OFFSET.write(out, mDirectoryOffset);
        F_COMMENT_SIZE.write(out, mComment.length);
        out.write(mComment);

        return out.toByteArray();
    }
}
