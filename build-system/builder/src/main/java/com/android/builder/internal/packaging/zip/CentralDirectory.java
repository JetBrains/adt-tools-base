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
import com.android.builder.internal.packaging.zip.utils.MsDosDateTimeUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Representation of the central directory of a zip archive.
 */
class CentralDirectory {

    /**
     * Field in the central directory with the central directory signature.
     */
    private static final ZipField.F4 F_SIGNATURE = new ZipField.F4(0, 0x02014b50, "Signature");

    /**
     * Field in the central directory with the "made by" code.
     */
    private static final ZipField.F2 F_MADE_BY = new ZipField.F2(F_SIGNATURE.endOffset(),
            "Made by", new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the minimum version required to extract the entry.
     */
    private static final ZipField.F2 F_VERSION_EXTRACT = new ZipField.F2(F_MADE_BY.endOffset(),
            "Version to extract", new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the GP bit flag.
     */
    private static final ZipField.F2 F_GP_BIT = new ZipField.F2(F_VERSION_EXTRACT.endOffset(),
            "GP bit");

    /**
     * Field in the central directory with the code of the compression method. See
     * {@link CompressionMethod#fromCode(long)}.
     */
    private static final ZipField.F2 F_METHOD = new ZipField.F2(F_GP_BIT.endOffset(), "Method");

    /**
     * Field in the central directory with the last modification time in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packTime(long)}).
     */
    private static final ZipField.F2 F_LAST_MOD_TIME = new ZipField.F2(F_METHOD.endOffset(),
            "Last modification time");

    /**
     * Field in the central directory with the last modification date in MS-DOS format. See
     * {@link MsDosDateTimeUtils#packDate(long)}.
     */
    private static final ZipField.F2 F_LAST_MOD_DATE = new ZipField.F2(F_LAST_MOD_TIME.endOffset(),
            "Last modification date");

    /**
     * Field in the central directory with the CRC32 checksum of the entry. This will be zero for
     * directories and files with no content.
     */
    private static final ZipField.F4 F_CRC32 = new ZipField.F4(F_LAST_MOD_DATE.endOffset(),
            "CRC32");

    /**
     * Field in the central directory with the entry's compressed size, <em>i.e.</em>, the file on
     * the archive. This will be the same as the uncompressed size if the method is
     * {@link CompressionMethod#STORE}.
     */
    private static final ZipField.F4 F_COMPRESSED_SIZE = new ZipField.F4(F_CRC32.endOffset(),
            "Compressed size", new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the entry's uncompressed size, <em>i.e.</em>, the size
     * the file will have when extracted from the zip. This will be zero for directories and empty
     * files and will be the same as the compressed size if the method is
     * {@link CompressionMethod#STORE}.
     */
    private static final ZipField.F4 F_UNCOMPRESSED_SIZE = new ZipField.F4(
            F_COMPRESSED_SIZE.endOffset(), "Uncompressed size", new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the length of the file name. The file name is stored
     * after the offset field ({@link #F_OFFSET}). The number of characters in the file name are
     * stored in this field.
     */
    private static final ZipField.F2 F_FILE_NAME_LENGTH = new ZipField.F2(
            F_UNCOMPRESSED_SIZE.endOffset(), "File name length",
            new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the length of the extra field. The extra field is
     * stored after the file name ({@link #F_FILE_NAME_LENGTH}). The contents of this field are
     * partially defined in the zip specification but we do not parse it.
     */
    private static final ZipField.F2 F_EXTRA_FIELD_LENGTH = new ZipField.F2(
            F_FILE_NAME_LENGTH.endOffset(), "Extra field length",
            new ZipFieldInvariantNonNegative());

    /**
     * Field in the central directory with the length of the comment. The comment is stored after
     * the extra field ({@link #F_EXTRA_FIELD_LENGTH}). We do not parse the comment.
     */
    private static final ZipField.F2 F_COMMENT_LENGTH = new ZipField.F2(
            F_EXTRA_FIELD_LENGTH.endOffset(), "Comment length", new ZipFieldInvariantNonNegative());

    /**
     * Number of the disk where the central directory starts. Because we do not support multi-file
     * archives, this field has to have value {@code 0}.
     */
    private static final ZipField.F2 F_DISK_NUMBER_START = new ZipField.F2(
            F_COMMENT_LENGTH.endOffset(), 0, "Disk start");

    /**
     * Internal attributes. This field can only contain one bit set, the {@link #ASCII_BIT}.
     */
    private static final ZipField.F2 F_INTERNAL_ATTRIBUTES = new ZipField.F2(
            F_DISK_NUMBER_START.endOffset(), "Int attributes");

    /**
     * External attributes. This field is ignored.
     */
    private static final ZipField.F4 F_EXTERNAL_ATTRIBUTES = new ZipField.F4(
            F_INTERNAL_ATTRIBUTES.endOffset(), "Ext attributes");

    /**
     * Offset into the archive where the entry starts. This is the offset to the local header
     * (see {@link StoredEntry} for information on the local header), not to the file data itself.
     * The file data, if there is any, will be stored after the local header.
     */
    private static final ZipField.F4 F_OFFSET = new ZipField.F4(F_EXTERNAL_ATTRIBUTES.endOffset(),
            "Offset", new ZipFieldInvariantNonNegative());

    /**
     * Maximum supported version to extract.
     */
    private static final int MAX_VERSION_TO_EXTRACT = 20;

    /**
     * Bit that can be set on the internal attributes stating that the file is an ASCII file. We
     * don't do anything with this information, but we check that nothing unexpected appears in the
     * internal attributes.
     */
    private static final int ASCII_BIT = 1;

    /**
     * Contains all entries in the directory mapped from their names.
     */
    @NonNull
    private final Map<String, StoredEntry> mEntries;

    /**
     * The file where this directory belongs to.
     */
    @NonNull
    private final ZFile mFile;

    /**
     * Supplier that provides a byte representation of the central directory.
     */
    @NonNull
    private final CachedSupplier<byte[]> mBytesSupplier;

    /**
     * Creates a new, empty, central directory, for a given zip file.
     *
     * @param file the file
     */
    CentralDirectory(@NonNull ZFile file) {
        mEntries = Maps.newHashMap();
        mFile = file;
        mBytesSupplier = new CachedSupplier<byte[]>() {
            @Override
            protected byte[] compute() throws IOException {
                return computeByteRepresentation();
            }
        };
    }

    /**
     * Reads the central directory data from a zip file, parses it, and creates the in-memory
     * structure representing the directory.
     *
     * @param bytes the data of the central directory; the directory is read from the buffer's
     * current position; when this method terminates, the buffer's position is the first byte
     * after the directory
     * @param count the number of entries expected in the central directory (usually read from the
     * {@link Eocd}).
     * @param file the zip file this central directory belongs to
     * @return the central directory
     * @throws IOException failed to read data from the zip, or the central directory is corrupted
     * or has unsupported features
     */
    static CentralDirectory makeFromData(@NonNull ByteBuffer bytes, int count, @NonNull ZFile file)
            throws IOException {
        Preconditions.checkNotNull(bytes, "bytes == null");
        Preconditions.checkArgument(count >= 0, "count < 0");

        CentralDirectory directory = new CentralDirectory(file);

        for (int i = 0; i < count; i++) {
            try {
                directory.readEntry(bytes);
            } catch (IOException e) {
                throw new IOException("Failed to read directory entry index " + i + " (total "
                        + "directory bytes read: " + bytes.position() + ").", e);
            }
        }

        return directory;
    }

    /**
     * Creates a new central directory from the entries. This is used to build a new central
     * directory from entries in the zip file.
     *
     * @param entries the entries in the zip file
     * @param file the zip file itself
     * @return the created central directory
     */
    static CentralDirectory makeFromEntries(@NonNull Set<StoredEntry> entries,
            @NonNull ZFile file) {
        CentralDirectory directory = new CentralDirectory(file);
        for (StoredEntry entry : entries) {
            CentralDirectoryHeader cdr = entry.getCentralDirectoryHeader();
            Preconditions.checkArgument(!directory.mEntries.containsKey(cdr.getName()),
                    "Duplicate filename");
            directory.mEntries.put(cdr.getName(), entry);
        }

        return directory;
    }

    /**
     * Reads the next entry from the central directory and adds it to {@link #mEntries}.
     *
     * @param bytes the central directory's data, positioned starting at the beginning of the next
     * entry to read; when finished, the buffer's position will be at the first byte after the
     * entry
     * @throws IOException failed to read the directory entry, either because of an I/O error,
     * because it is corrupt or contains unsupported features
     */
    private void readEntry(@NonNull ByteBuffer bytes) throws IOException {
        F_SIGNATURE.verify(bytes);
        long madeBy = F_MADE_BY.read(bytes);

        long versionNeededToExtract = F_VERSION_EXTRACT.read(bytes);
        if (versionNeededToExtract > MAX_VERSION_TO_EXTRACT) {
            throw new IOException("Unknown version needed to extract in zip directory entry: "
                    + versionNeededToExtract + ".");
        }

        long gpBit = F_GP_BIT.read(bytes);
        GPFlags flags = GPFlags.from(gpBit);

        long methodCode = F_METHOD.read(bytes);
        CompressionMethod method = CompressionMethod.fromCode(methodCode);
        if (method == null) {
            throw new IOException("Unknown method in zip directory entry: " + methodCode + ".");
        }

        long lastModTime;
        long lastModDate;
        if (mFile.areTimestampsIgnored()) {
            lastModTime = 0;
            lastModDate = 0;
            F_LAST_MOD_TIME.skip(bytes);
            F_LAST_MOD_DATE.skip(bytes);
        } else {
            lastModTime = F_LAST_MOD_TIME.read(bytes);
            lastModDate = F_LAST_MOD_DATE.read(bytes);
        }

        long crc32 = F_CRC32.read(bytes);
        long compressedSize = F_COMPRESSED_SIZE.read(bytes);
        long uncompressedSize = F_UNCOMPRESSED_SIZE.read(bytes);
        int fileNameLength = Ints.checkedCast(F_FILE_NAME_LENGTH.read(bytes));
        int extraFieldLength = Ints.checkedCast(F_EXTRA_FIELD_LENGTH.read(bytes));
        int fileCommentLength = Ints.checkedCast(F_COMMENT_LENGTH.read(bytes));

        F_DISK_NUMBER_START.verify(bytes);
        long internalAttributes = F_INTERNAL_ATTRIBUTES.read(bytes);
        if ((internalAttributes & ~ASCII_BIT) != 0) {
            throw new IOException("Invalid internal attributes: " + internalAttributes + ".");
        }

        long externalAttributes = F_EXTERNAL_ATTRIBUTES.read(bytes);
        long entryOffset = F_OFFSET.read(bytes);

        long remainingSize = fileNameLength + extraFieldLength + fileCommentLength;

        if (bytes.remaining() < fileNameLength + extraFieldLength + fileCommentLength) {
            throw new IOException("Directory entry should have " + remainingSize
                    + " bytes remaining (name = " + fileNameLength + ", extra = "
                    + extraFieldLength + ", comment = " + fileCommentLength + "), but it has "
                    + bytes.remaining() + ".");
        }

        String fileName = EncodeUtils.decode(bytes, fileNameLength, flags);

        byte[] extraField = new byte[extraFieldLength];
        bytes.get(extraField);

        byte[] fileCommentField = new byte[fileCommentLength];
        bytes.get(fileCommentField);

        /*
         * Tricky: to create a CentralDirectoryHeader we need the future that will hold the result
         * of the compress information. But, to actually create the result of the compress
         * information we need the CentralDirectoryHeader
         */
        ListenableFuture<CentralDirectoryHeaderCompressInfo> compressInfo =
                Futures.immediateFuture(new CentralDirectoryHeaderCompressInfo(method,
                        compressedSize, versionNeededToExtract));
        CentralDirectoryHeader centralDirectoryHeader = new CentralDirectoryHeader(fileName,
                uncompressedSize, compressInfo, flags);
        centralDirectoryHeader.setMadeBy(madeBy);
        centralDirectoryHeader.setLastModTime(lastModTime);
        centralDirectoryHeader.setLastModDate(lastModDate);
        centralDirectoryHeader.setCrc32(crc32);
        centralDirectoryHeader.setInternalAttributes(internalAttributes);
        centralDirectoryHeader.setExternalAttributes(externalAttributes);
        centralDirectoryHeader.setOffset(entryOffset);
        centralDirectoryHeader.setExtraField(extraField);
        centralDirectoryHeader.setComment(fileCommentField);

        StoredEntry entry;

        try {
            entry = new StoredEntry(centralDirectoryHeader, mFile, null);
        } catch (IOException e) {
            throw new IOException("Failed to read stored entry '" + fileName + "'.", e);
        }

        if (mEntries.containsKey(fileName)) {
            throw new IOException("File file contains duplicate file '" + fileName + "'.");
        }

        mEntries.put(fileName, entry);
    }

    /**
     * Obtains all the entries in the central directory.
     *
     * @return all entries on a non-modifiable map
     */
    @NonNull
    Map<String, StoredEntry> getEntries() {
        return ImmutableMap.copyOf(mEntries);
    }

    /**
     * Obtains the byte representation of the central directory.
     *
     * @return a byte array containing the whole central directory
     * @throws IOException failed to write the byte array
     */
    byte[] toBytes() throws IOException {
        return mBytesSupplier.get();
    }

    /**
     * Computes the byte representation of the central directory.
     *
     * @return a byte array containing the whole central directory
     * @throws IOException failed to write the byte array
     */
    private byte[] computeByteRepresentation() throws IOException {

        List<StoredEntry> sorted = Lists.newArrayList(mEntries.values());
        Collections.sort(sorted, StoredEntry.COMPARE_BY_NAME);

        CentralDirectoryHeader[] cdhs = new CentralDirectoryHeader[mEntries.size()];
        CentralDirectoryHeaderCompressInfo[] compressInfos =
                new CentralDirectoryHeaderCompressInfo[mEntries.size()];
        byte[][] encodedFileNames = new byte[mEntries.size()][];
        byte[][] extraFields = new byte[mEntries.size()][];
        byte[][] comments = new byte[mEntries.size()][];

        /*
         * First collect all the data and compute the total size of the central directory.
         */
        int idx = 0;
        int total = 0;
        for (StoredEntry entry : sorted) {
            cdhs[idx] = entry.getCentralDirectoryHeader();
            compressInfos[idx] = cdhs[idx].getCompressionInfoWithWait();
            encodedFileNames[idx] = cdhs[idx].getEncodedFileName();
            extraFields[idx] = cdhs[idx].getExtraField();
            comments[idx] = cdhs[idx].getComment();

            total += F_OFFSET.endOffset() + encodedFileNames[idx].length
                    + extraFields[idx].length + comments[idx].length;
            idx++;
        }

        ByteBuffer out = ByteBuffer.allocate(total);


        for (idx = 0; idx < mEntries.size(); idx++) {
            F_SIGNATURE.write(out);
            F_MADE_BY.write(out, cdhs[idx].getMadeBy());
            F_VERSION_EXTRACT.write(out, compressInfos[idx].getVersionExtract());
            F_GP_BIT.write(out, cdhs[idx].getGpBit().getValue());
            F_METHOD.write(out, compressInfos[idx].getMethod().methodCode);

            if (mFile.areTimestampsIgnored()) {
                F_LAST_MOD_TIME.write(out, 0);
                F_LAST_MOD_DATE.write(out, 0);
            } else {
                F_LAST_MOD_TIME.write(out, cdhs[idx].getLastModTime());
                F_LAST_MOD_DATE.write(out, cdhs[idx].getLastModDate());
            }

            F_CRC32.write(out, cdhs[idx].getCrc32());
            F_COMPRESSED_SIZE.write(out, compressInfos[idx].getCompressedSize());
            F_UNCOMPRESSED_SIZE.write(out, cdhs[idx].getUncompressedSize());

            F_FILE_NAME_LENGTH.write(out, cdhs[idx].getEncodedFileName().length);
            F_EXTRA_FIELD_LENGTH.write(out, cdhs[idx].getExtraField().length);
            F_COMMENT_LENGTH.write(out, cdhs[idx].getComment().length);
            F_DISK_NUMBER_START.write(out);
            F_INTERNAL_ATTRIBUTES.write(out, cdhs[idx].getInternalAttributes());
            F_EXTERNAL_ATTRIBUTES.write(out, cdhs[idx].getExternalAttributes());
            F_OFFSET.write(out, cdhs[idx].getOffset());

            out.put(encodedFileNames[idx]);
            out.put(extraFields[idx]);
            out.put(comments[idx]);
        }

        return out.array();
    }
}
