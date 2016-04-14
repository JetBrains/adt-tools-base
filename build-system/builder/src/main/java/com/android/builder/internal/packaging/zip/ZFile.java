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
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.utils.CachedFileContents;
import com.android.builder.internal.packaging.zip.utils.LittleEndianUtils;
import com.android.builder.internal.packaging.zip.utils.RandomAccessFileUtils;
import com.android.builder.internal.utils.IOExceptionFunction;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.android.utils.FileUtils;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Closer;
import com.google.common.primitives.Ints;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * The {@code ZFile} provides the main interface for interacting with zip files. A {@code ZFile}
 * can be created on a new file or in an existing file. Once created, files can be added or removed
 * from the zip file.
 * <p>
 * Changes in the zip file are always deferred. Any change requested is made in memory and written
 * to disk only when {@link #update()} or {@link #close()} is invoked.
 * <p>
 * Zip files are open initially in read-only mode and will switch to read-write when needed. This
 * is done automatically. Because modifications to the file are done in-memory, the zip file can
 * be manipulated when closed. When invoking {@link #update()} or {@link #close()} the zip file
 * will be reopen and changes will be written. However, the zip file cannot be modified outside
 * the control of {@code ZFile}. So, if a {@code ZFile} is closed, modified outside and then a file
 * is added or removed from the zip file, when reopening the zip file, {@link ZFile} will detect
 * the outside modification and will fail.
 * <p>
 * In memory manipulation means that files added to the zip file are kept in memory until written
 * to disk. This provides much faster operation and allows better zip file allocation (see below).
 * It may, however, increase the memory footprint of the application. When adding large files, if
 * memory consumption is a concern, a call to {@link #update()} will actually write the file to
 * disk and discard the memory buffer.
 * <p>
 * {@code ZFile} keeps track of allocation inside of the zip file. If a file is deleted, its space
 * is marked as freed and will be reused for an added file if it fits in the space. Allocation of
 * files to empty areas is done using a <em>best fit</em> algorithm. When adding a file, if it
 * doesn't fit in any free area, the zip file will be extended.
 * <p>
 * {@code ZFile} provides a fast way to merge data from another zip file
 * (see {@link #mergeFrom(ZFile, Set)}) avoiding recompression and copying of equal files. When
 * merging, patterns of files may be provided that are ignored. This allows handling special files
 * in the merging process, such as files in {@code META-INF}.
 * <p>
 * When adding files to the zip file, unless files are explicitly required to be stored, files will
 * be deflated. However, deflating will not occur if the deflated file is larger then the stored
 * file, <em>e.g.</em> if compression would yield a bigger file.
 * <p>
 * Because {@code ZFile} was designed to be used in a build system and not as general-purpose
 * zip utility, it is very strict (and unforgiving) about the zip format and unsupported features.
 * <p>
 * {@code ZFile} supports <em>alignment</em>. Alignment means that file data (not entries -- the
 * local header must be discounted) must start at offsets that are multiple of a number -- the
 * alginment. Alignment is defined by setting rules in an {@link AlignmentRules} object that can
 * be obtained using {@link #getAlignmentRules()}.
 * <p>
 * When a file is added to the zip, the alignment rules will be checked and alignment will be
 * honored when positioning the file in the zip. This means that unused spaces in the zip may
 * be generated as a result. However, alignment of existing entries will not be changed.
 * <p>
 * Entries can be realigned individually (see {@link StoredEntry#realign()} or the full zip file
 * may be realigned (see {@link #realign()}). When realigning the full zip entries that are already
 * realigned will not be affected.
 * <p>
 * Because realignment may cause files to move in the zip, realignment is done in-memory meaning
 * that files that need to change location will moved to memory and will only be flushed when
 * either {@link #update()} or {@link #close()} are called.
 * <p>
 * To allow whole-apk signing, the {@code ZFile} allows the central directory location to be
 * offset by a fixed amount. This amount can be set using the {@link #setExtraDirectoryOffset(long)}
 * method. Setting a non-zero value will add extra (unused) space in the zip file before the
 * central directory. This value can be changed at any time and it will force the central directory
 * rewritten when the file is updated or closed.
 * <p>
 * {@code ZFile} provides an extension mechanism to allow objects to register with the file
 * and be notified when changes to the file happen. This should be used
 * to add extra features to the zip file while providing strong decoupling. See
 * {@link ZFileExtension}, {@link ZFile#addZFileExtension(ZFileExtension)} and
 * {@link ZFile#removeZFileExtension(ZFileExtension)}.
 * <p>
 * This class is <strong>not</strong> thread-safe. Neither are any of the classes associated with
 * it in this package.
 */
public class ZFile implements Closeable {

    /**
     * The file separator in paths in the zip file. This is fixed by the zip specification
     * (section 4.4.17).
     */
    public static final char SEPARATOR = '/';

    /**
     * Minimum size the EOCD can have.
     */
    private static final int MIN_EOCD_SIZE = 22;

    /**
     * Number of bytes of the Zip64 EOCD locator record.
     */
    private static final int ZIP64_EOCD_LOCATOR_SIZE = 20;

    /**
     * How many bytes to look back from the end of the file to look for the EOCD signature.
     */
    private static final int LAST_BYTES_TO_READ = 65535 + MIN_EOCD_SIZE;

    /**
     * Signature of the Zip64 EOCD locator record.
     */
    private static final int ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50;

    /**
     * Size of buffer for I/O operations.
     */
    private static final int IO_BUFFER_SIZE = 1024 * 1024;

    /**
     * File zip file.
     */
    @NonNull
    private final File mFile;

    /**
     * The random access file used to access the zip file. This will be {@code null} if and only
     * if {@link #mState} is {@link ZipFileState#CLOSED}.
     */
    @Nullable
    private RandomAccessFile mRaf;

    /**
     * The map containing the in-memory contents of the zip file. It keeps track of which parts of
     * the zip file are used and which are not.
     */
    @NonNull
    private final FileUseMap mMap;

    /**
     * The EOCD entry. Will be {@code null} if there is no EOCD (because the zip is new) or the
     * one that exists on disk is no longer valid (because the zip has been changed).
     */
    @Nullable
    private FileUseMapEntry<Eocd> mEocdEntry;

    /**
     * The Central Directory entry. Will be {@code null} if there is no Central Directory (because
     * the zip is new) or because the one that exists on disk is no longer valid (because the zip
     * has been changed).
     */
    @Nullable
    private FileUseMapEntry<CentralDirectory> mDirectoryEntry;

    /**
     * All entries in the zip file. It includes in-memory changes and may not reflect what is
     * written on disk.
     */
    @NonNull
    private final Map<String, FileUseMapEntry<StoredEntry>> mEntries;

    /**
     * Current state of the zip file.
     */
    @NonNull
    private ZipFileState mState;

    /**
     * Are the in-memory changes that have not been written to the zip file?
     */
    private boolean mDirty;

    /**
     * Non-{@code null} only if the file is currently closed. Used to detect if the zip is
     * modified outside this object's control. If the file has never been written, this will
     * be {@code null} even if it is closed.
     */
    @Nullable
    private CachedFileContents<Object> mClosedControl;

    /**
     * The set of alignment rules.
     */
    @NonNull
    private final AlignmentRules mAlignmentRules;

    /**
     * Extensions registered with the file.
     */
    @NonNull
    private final List<ZFileExtension> mExtensions;

    /**
     * When notifying extensions, extensions may request that some runnables are executed. This
     * list collects all runnables by the order they were requested. Together with
     * {@link #mIsNotifying}, it is used to avoid reordering notifications.
     */
    @NonNull
    private final List<IOExceptionRunnable> mToRun;

    /**
     * {@code true} when {@link #notify(IOExceptionFunction)} is notifying extensions. Used
     * to avoid reordering notifications.
     */
    private boolean mIsNotifying;

    /**
     * An extra offset for the central directory location. {@code 0} if the central directory
     * should be written in its standard location.
     */
    private long mExtraDirectoryOffset;


    /**
     * Creates a new zip file. If the zip file does not exist, then no file is created at this
     * point and {@code ZFile} will contain an empty structure. However, an (empty) zip file will
     * be created if either {@link #update()} or {@link #close()} are used. If a zip file exists,
     * it will be parsed and read.
     *
     * @param file the zip file
     * @throws IOException some file exists but could not be read
     */
    public ZFile(@NonNull File file) throws IOException {
        mFile = file;
        mMap = new FileUseMap(0);
        mDirty = false;
        mClosedControl = null;
        mAlignmentRules = new AlignmentRules();
        mExtensions = Lists.newArrayList();
        mToRun = Lists.newArrayList();

        if (file.exists()) {
            mState = ZipFileState.OPEN_RO;
            mRaf = new RandomAccessFile(file, "r");
        } else {
            mState = ZipFileState.CLOSED;
            mRaf = null;
            mDirty = true;
        }

        mEntries = Maps.newHashMap();
        mExtraDirectoryOffset = 0;

        try {
            if (mState != ZipFileState.CLOSED) {
                long rafSize = mRaf.length();
                if (rafSize > Integer.MAX_VALUE) {
                    throw new IOException("File exceeds size limit of " + Integer.MAX_VALUE + ".");
                }

                mMap.extend(Ints.checkedCast(rafSize));
                readData();

                notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
                    @Nullable
                    @Override
                    public IOExceptionRunnable apply(ZFileExtension input) throws IOException {
                        return input.open();
                    }
                });
            }
        } catch (IOException e) {
            throw new IOException("Failed to read zip file '" + file.getAbsolutePath() + "'.", e);
        }
    }

    /**
     * Obtains all entries in the file. Entries themselves may be or not written in disk. However,
     * all of them can be open for reading.
     *
     * @return all entries in the zip
     */
    @NonNull
    public Set<StoredEntry> entries() {
        Set<StoredEntry> entries = Sets.newHashSet();
        for (FileUseMapEntry<StoredEntry> mapEntry : mEntries.values()) {
            entries.add(mapEntry.getStore());
        }

        return entries;
    }

    /**
     * Obtains an entry at a given path in the zip.
     *
     * @param path the path
     * @return the entry at the path or {@code null} if none exists
     */
    @Nullable
    public StoredEntry get(@NonNull String path) {
        FileUseMapEntry<StoredEntry> found = mEntries.get(path);
        if (found == null) {
            return null;
        }

        return found.getStore();
    }

    /**
     * Reads all the data in the zip file, except the contents of the entries themselves. This
     * method will populate the directory and maps in the instance variables.
     *
     * @throws IOException failed to read the zip file
     */
    private void readData() throws IOException {
        Preconditions.checkState(mState != ZipFileState.CLOSED, "mState == ZipFileState.CLOSED");
        Preconditions.checkState(mRaf != null, "mRaf == null");

        readEocd();
        readCentralDirectory();

        /*
         * Compute where the last file ends. We will need this to compute thee extra offset.
         */
        long entryEndOffset;
        long directoryStartOffset;

        if (mDirectoryEntry != null) {
            CentralDirectory directory = mDirectoryEntry.getStore();
            assert directory != null;

            entryEndOffset = 0;

            for (StoredEntry entry : directory.getEntries().values()) {
                long start = entry.getCentralDirectoryHeader().getOffset();
                long end = start + entry.getInFileSize();

                FileUseMapEntry<StoredEntry> mapEntry = mMap.add(start, end, entry);
                mEntries.put(entry.getCentralDirectoryHeader().getName(), mapEntry);

                if (end > entryEndOffset) {
                    entryEndOffset = end;
                }
            }

            directoryStartOffset = mDirectoryEntry.getStart();
        } else {
            /*
             * No directory means an empty zip file. Use the start of the EOCD to compute
             * an existing offset.
             */
            Verify.verifyNotNull(mEocdEntry);
            assert mEocdEntry != null;
            directoryStartOffset = mEocdEntry.getStart();
            entryEndOffset = 0;
        }

        long extraOffset = directoryStartOffset - entryEndOffset;
        Verify.verify(extraOffset >= 0, "extraOffset (%s) < 0", extraOffset);
        setExtraDirectoryOffset(extraOffset);
    }

    /**
     * Finds the EOCD marker and reads it. It will populate the {@link #mEocdEntry} variable.
     *
     * @throws IOException failed to read the EOCD
     */
    private void readEocd() throws IOException {
        Preconditions.checkState(mState != ZipFileState.CLOSED, "mState == ZipFileState.CLOSED");
        Preconditions.checkState(mRaf != null, "mRaf == null");

        /*
         * Read the last part of the zip into memory. If we don't find the EOCD signature by then,
         * the file is corrupt.
         */
        int lastToRead = LAST_BYTES_TO_READ;
        if (lastToRead > mRaf.length()) {
            lastToRead = Ints.checkedCast(mRaf.length());
        }

        byte[] last = new byte[lastToRead];
        directFullyRead(mRaf.length() - lastToRead, last);

        byte[] eocdSignature = new byte[] { 0x06, 0x05, 0x4b, 0x50 };

        /*
         * Start endIdx at the first possible location where the signature can be located and then
         * move backwards. Because the EOCD must have at least MIN_EOCD size, the first byte of the
         * signature (and first byte of the EOCD) must be located at last.length - MIN_EOCD_SIZE.
         *
         * Because the EOCD signature may exist in the file comment, when we find a signature we
         * will try to read the Eocd. If we fail, we continue searching for the signature. However,
         * we will keep the last exception in case we don't find any signature.
         */
        Eocd eocd = null;
        int foundEocdSignature = -1;
        IOException errorFindingSignature = null;
        int eocdStart = -1;

        for (int endIdx = last.length - MIN_EOCD_SIZE; endIdx >= 0; endIdx--) {
            /*
             * Remember: little endian...
             */
            if (last[endIdx] == eocdSignature[3]
                    && last[endIdx + 1] == eocdSignature[2]
                    && last[endIdx + 2] == eocdSignature[1]
                    && last[endIdx + 3] == eocdSignature[0]) {

                /*
                 * We found a signature. Try to read the EOCD record.
                 */

                foundEocdSignature = endIdx;
                ByteSource eocdBytes = ByteSource.wrap(last).slice(foundEocdSignature, last.length);

                try {
                    eocd = new Eocd(eocdBytes);
                    eocdStart = Ints.checkedCast(mRaf.length() - lastToRead + foundEocdSignature);

                    /*
                     * Make sure the EOCD takes the whole file up to the end.
                     */
                    if (eocdStart + eocd.getEocdSize() != mRaf.length()) {
                        throw new IOException("EOCD starts at " + eocdStart + " and has "
                                + eocd.getEocdSize() + " but file ends at " + mRaf.length() + ".");
                    }
                } catch (IOException e) {
                    errorFindingSignature = e;
                    foundEocdSignature = -1;
                    eocd = null;
                }
            }
        }

        if (foundEocdSignature == -1) {
            throw new IOException("EOCD signature not found in the last "
                    + lastToRead + " bytes of the file.", errorFindingSignature);
        }

        Verify.verify(eocdStart >= 0);

        /*
         * Look for the Zip64 central directory locator. If we find it, then this file is a Zip64
         * file and we do not support it.
         */
        int zip64LocatorStart = eocdStart - ZIP64_EOCD_LOCATOR_SIZE;
        if (zip64LocatorStart >= 0) {
            byte possibleZip64Locator[] = new byte[4];
            directFullyRead(zip64LocatorStart, possibleZip64Locator);
            if (LittleEndianUtils.readUnsigned4Le(ByteSource.wrap(possibleZip64Locator)) ==
                    ZIP64_EOCD_LOCATOR_SIGNATURE) {
                throw new IOException("Zip64 EOCD locator found but Zip64 format is not "
                        + "supported.");
            }
        }

        mEocdEntry = mMap.add(eocdStart, eocdStart + eocd.getEocdSize(), eocd);
    }

    /**
     * Reads the zip's central directory and populates the {@link #mDirectoryEntry} variable. This
     * method can only be called after the EOCD has been read. If the central directory is empty
     * (if there are no files on the zip archive), then {@link #mDirectoryEntry} will be set to
     * {@code null}.
     *
     * @throws IOException failed to read the central directory
     */
    private void readCentralDirectory() throws IOException {
        Preconditions.checkNotNull(mEocdEntry, "mEocdEntry == null");
        Preconditions.checkNotNull(mEocdEntry.getStore(), "mEocdEntry.getStore() == null");
        Preconditions.checkState(mState != ZipFileState.CLOSED, "mState == ZipFileState.CLOSED");
        Preconditions.checkState(mRaf != null, "mRaf == null");
        Preconditions.checkState(mDirectoryEntry == null, "mDirectoryEntry != null");

        Eocd eocd = mEocdEntry.getStore();

        long dirSize = eocd.getDirectorySize();
        if (dirSize > Integer.MAX_VALUE) {
            throw new IOException("Cannot read central directory with size " + dirSize + ".");
        }

        if (eocd.getDirectoryOffset() + dirSize != mEocdEntry.getStart()) {
            throw new IOException("Central directory is stored in [" + eocd.getDirectoryOffset()
                    + " - " + (eocd.getDirectoryOffset() + dirSize) + "] and EOCD starts at "
                    + mEocdEntry.getStart() + ".");
        }

        byte[] directoryData = new byte[Ints.checkedCast(dirSize)];
        directFullyRead(eocd.getDirectoryOffset(), directoryData);

        CentralDirectory directory = CentralDirectory.makeFromData(ByteSource.wrap(directoryData),
                eocd.getTotalRecords(), this);
        if (eocd.getDirectorySize() > 0) {
            mDirectoryEntry = mMap.add(eocd.getDirectoryOffset(), eocd.getDirectoryOffset()
                    + eocd.getDirectorySize(), directory);
        }
    }

    /**
     * Opens a portion of the zip for reading. The zip must be open for this method to be invoked.
     * Note that if the zip has not been updated, the individual zip entries may not have been
     * written yet.
     *
     * @param start the index within the zip file to start reading
     * @param end the index within the zip file to end reading (the actual byte pointed by
     * <em>end</em> will not be read)
     * @return a stream that will read the portion of the file; no decompression is done, data is
     * returned <em>as is</em>
     * @throws IOException failed to open the zip file
     */
    @NonNull
    public InputStream directOpen(final long start, final long end) throws IOException {
        Preconditions.checkState(mState != ZipFileState.CLOSED, "mState == ZipFileState.CLOSED");
        Preconditions.checkState(mRaf != null, "mRaf == null");
        Preconditions.checkArgument(start >= 0, "start < 0");
        Preconditions.checkArgument(end >= start, "end < start");
        Preconditions.checkArgument(end <= mRaf.length(), "end > mRaf.length()");

        return new InputStream() {
            long mCurr = start;

            @Override
            public int read() throws IOException {
                if (mCurr == end) {
                    return -1;
                }

                byte b[] = new byte[1];
                int r = directRead(mCurr, b);
                if (r > 0) {
                    mCurr++;
                    return b[0];
                } else {
                    return -1;
                }
            }

            @Override
            public int read(@NonNull byte[] b, int off, int len) throws IOException {
                Preconditions.checkNotNull(b, "b == null");
                Preconditions.checkArgument(off >= 0, "off < 0");
                Preconditions.checkArgument(off <= b.length, "off > b.length");
                Preconditions.checkArgument(len >= 0, "len < 0");
                Preconditions.checkArgument(off + len <= b.length, "off + len > b.length");

                long availableToRead = end - mCurr;
                long toRead = Math.min(len, availableToRead);

                if (toRead == 0) {
                    return -1;
                }

                if (toRead > Integer.MAX_VALUE) {
                    throw new IOException("Cannot read " + toRead + " bytes.");
                }

                int r = directRead(mCurr, b, off, Ints.checkedCast(toRead));
                if (r > 0) {
                    mCurr += r;
                }

                return r;
            }
        };
    }

    /**
     * Deletes an entry from the zip. This method does not actually delete anything on disk. It
     * just changes in-memory structures. Use {@link #update()} to update the contents on disk.
     *
     * @param entry the entry to delete
     * @param notify should listeners be notified of the deletion? This will only be
     * {@code false} if the entry is being removed as part of a replacement
     * @throws IOException failed to delete the entry
     */
    void delete(@NonNull final StoredEntry entry, boolean notify) throws IOException {
        String path = entry.getCentralDirectoryHeader().getName();
        FileUseMapEntry<StoredEntry> mapEntry = mEntries.get(path);
        Preconditions.checkNotNull(mapEntry, "mapEntry == null");
        Preconditions.checkArgument(entry == mapEntry.getStore(), "entry != mapEntry.getStore()");

        mDirty = true;

        mMap.remove(mapEntry);
        mEntries.remove(path);

        if (notify) {
            notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
                @Override
                public IOExceptionRunnable apply(ZFileExtension input) throws IOException {
                    return input.removed(entry);
                }
            });
        }
    }

    /**
     * Updates the file writing new entries and removing deleted entries. This will force
     * reopening the file as read/write if the file wasn't open in read/write mode.
     */
    public void update() throws IOException {
        notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
            @Nullable
            @Override
            public IOExceptionRunnable apply(@Nullable ZFileExtension input) throws IOException {
                Verify.verifyNotNull(input);
                assert input != null;
                return input.beforeUpdate();
            }
        });

        if (!mDirty) {
            return;
        }

        reopenRw();

        /*
         * Write new files in the zip. We identify new files because they don't have an offset
         * in the zip where they are written although we already know, by their location in the
         * file map, where they will be written to.
         */
        for (FileUseMapEntry<StoredEntry> entry : mEntries.values()) {
            StoredEntry entryStore = entry.getStore();
            assert entryStore != null;
            if (entryStore.getCentralDirectoryHeader().getOffset() == -1) {
                writeEntry(entry.getStore(), entry.getStart());
            }
        }

        deleteDirectoryAndEocd();
        mMap.truncate();

        computeCentralDirectory();
        computeEocd();

        notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
            @Nullable
            @Override
            public IOExceptionRunnable apply(@Nullable ZFileExtension input) throws IOException {
                Verify.verifyNotNull(input);
                assert input != null;
                input.entriesWritten();
                return null;
            }
        });

        appendCentralDirectory();
        appendEocd();

        Verify.verifyNotNull(mRaf);
        mRaf.setLength(mMap.size());

        mDirty = false;

        notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
            @Nullable
            @Override
            public IOExceptionRunnable apply(@Nullable ZFileExtension input) throws IOException {
                Verify.verifyNotNull(input);
                assert input != null;
                input.updated();
                return null;
            }
        });
    }

    /**
     * Updates the file and closes it.
     */
    @Override
    public void close() throws IOException {
        update();
        innerClose();

        notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
            @Override
            public IOExceptionRunnable apply(ZFileExtension input) throws IOException {
                input.closed();
                return null;
            }
        });
    }

    /**
     * Removes the Central Directory and EOCD from the file. This will free space for new entries
     * as well as allowing the zip file to be truncated if files have been removed.
     */
    private void deleteDirectoryAndEocd() {
        if (mDirectoryEntry != null) {
            mMap.remove(mDirectoryEntry);
            mDirectoryEntry = null;
        }

        if (mEocdEntry != null) {
            mMap.remove(mEocdEntry);
            mEocdEntry = null;
        }
    }

    /**
     * Writes an entry's data in the zip file. This includes everything: the local header and
     * the data itself. After writing, the entry is updated with the offset and its source replaced
     * with a source that reads from the zip file.
     *
     * @param entry the entry to write
     * @param offset the offset at which the entry should be written
     * @throws IOException failed to write the entry
     */
    private void writeEntry(@NonNull StoredEntry entry, long offset) throws IOException {
        Preconditions.checkArgument(entry.getDataDescriptorType()
                == DataDescriptorType. NO_DATA_DESCRIPTOR, "Cannot write entries with a data "
                + "descriptor.");
        Preconditions.checkNotNull(mRaf, "mRaf == null");
        Preconditions.checkState(mState == ZipFileState.OPEN_RW, "mState != ZipFileState.OPEN_RW");

        /*
         * Place the cursor and write the local header.
         */
        byte[] headerData = entry.toHeaderData();
        directWrite(offset, headerData);

        /*
         * Get the source data. If a compressed source exists, that's the one we want.
         */
        EntrySource source = entry.getSource();
        if (source.innerCompressed() != null) {
            source = source.innerCompressed();
            assert source != null;
        }

        Verify.verify(source.innerCompressed() == null);

        /*
         * Write the source data.
         */
        byte[] chunk = new byte[IO_BUFFER_SIZE];
        int r;
        long writeOffset = offset + headerData.length;
        InputStream is = source.open();
        while ((r = is.read(chunk)) >= 0) {
            directWrite(writeOffset, chunk, 0, r);
            writeOffset += r;
        }

        is.close();

        /*
         * Set the entry's offset and create the entry source.
         */
        entry.getCentralDirectoryHeader().setOffset(offset);
        entry.createSourceFromZip();
    }

    /**
     * Computes the central directory. The central directory must not have been computed yet. When
     * this method finishes, the central directory has been computed {@link #mDirectoryEntry},
     * unless the directory is empty in which case {@link #mDirectoryEntry}
     * is left as {@code null}. Nothing is written to disk as a result of this method's invocation.
     *
     * @throws IOException failed to append the central directory
     */
    private void computeCentralDirectory() throws IOException {
        Preconditions.checkState(mState == ZipFileState.OPEN_RW, "mState != ZipFileState.OPEN_RW");
        Preconditions.checkNotNull(mRaf, "mRaf == null");
        Preconditions.checkState(mDirectoryEntry == null, "mDirectoryEntry == null");

        Set<StoredEntry> newStored = Sets.newHashSet();
        for (FileUseMapEntry<StoredEntry> mapEntry : mEntries.values()) {
            newStored.add(mapEntry.getStore());
        }

        CentralDirectory newDirectory = CentralDirectory.makeFromEntries(newStored, this);
        byte[] newDirectoryBytes = newDirectory.toBytes();
        long directoryOffset = mMap.size() + mExtraDirectoryOffset;

        mMap.extend(directoryOffset+ newDirectoryBytes.length);

        if (newDirectoryBytes.length > 0) {
            mDirectoryEntry = mMap.add(directoryOffset, directoryOffset + newDirectoryBytes.length,
                    newDirectory);
        }
    }

    /**
     * Writes the central directory to the end of the zip file. {@link #mDirectoryEntry} may be
     * {@code null} only if there are no files in the archive.
     *
     * @throws IOException failed to append the central directory
     */
    private void appendCentralDirectory() throws IOException {
        Preconditions.checkState(mState == ZipFileState.OPEN_RW, "mState != ZipFileState.OPEN_RW");
        Preconditions.checkNotNull(mRaf, "mRaf == null");

        if (mEntries.isEmpty()) {
            Preconditions.checkState(mDirectoryEntry == null, "mDirectoryEntry != null");
            return;
        }

        Preconditions.checkNotNull(mDirectoryEntry, "mDirectoryEntry != null");

        CentralDirectory newDirectory = mDirectoryEntry.getStore();
        Verify.verifyNotNull(newDirectory, "newDirectory != null");

        byte[] newDirectoryBytes = newDirectory.toBytes();
        long directoryOffset = mDirectoryEntry.getStart();

        /*
         * It is fine to seek beyond the end of file. Seeking beyond the end of file will not extend
         * the file. Even if we do not have any directory data to write, the extend() call below
         * will force the file to be extended leaving exactly mExtraDirectoryOffset bytes empty at
         * the beginning.
         */
        directWrite(directoryOffset, newDirectoryBytes);
    }

    /**
     * Obtains the byte array representation of the central directory. The central directory must
     * have been already computed. If there are no entries in the zip, the central directory will be
     * empty.
     *
     * @return the byte representation, or an empty array if there are no entries in the zip
     * @throws IOException failed to compute the central directory byte representation
     */
    @NonNull
    public byte[] getCentralDirectoryBytes() throws IOException {
        if (mEntries.isEmpty()) {
            Preconditions.checkState(mDirectoryEntry == null, "mDirectoryEntry != null");
            return new byte[0];
        }

        Preconditions.checkNotNull(mDirectoryEntry, "mDirectoryEntry == null");

        CentralDirectory cd = mDirectoryEntry.getStore();
        Verify.verifyNotNull(cd, "cd == null");
        return cd.toBytes();
    }

    /**
     * Computes the EOCD. This creates a new {@link #mEocdEntry}. The
     * central directory must already be written. If {@link #mDirectoryEntry} is {@code null}, then
     * the zip file must not have any entries.
     *
     * @throws IOException failed to write the EOCD
     */
    private void computeEocd() throws IOException {
        Preconditions.checkState(mState == ZipFileState.OPEN_RW, "mState != ZipFileState.OPEN_RW");
        Preconditions.checkNotNull(mRaf, "mRaf == null");
        if (mDirectoryEntry == null) {
            Preconditions.checkState(mEntries.isEmpty(),
                    "mDirectoryEntry == null && !mEntries.isEmpty()");
        }

        long dirStart;
        long dirSize = 0;

        if (mDirectoryEntry != null) {
            CentralDirectory directory = mDirectoryEntry.getStore();
            assert directory != null;

            dirStart = mDirectoryEntry.getStart();
            dirSize = mDirectoryEntry.getSize();
            Verify.verify(directory.getEntries().size() == mEntries.size());
        } else {
            /*
             * If we do not have a directory, then we must leave any requested offset empty.
             */
            dirStart = mExtraDirectoryOffset;
        }

        Eocd eocd = new Eocd(mEntries.size(), dirStart, dirSize);

        byte[] eocdBytes = eocd.toBytes();
        long eocdOffset = mMap.size();

        mMap.extend(eocdOffset + eocdBytes.length);

        mEocdEntry = mMap.add(eocdOffset, eocdOffset + eocdBytes.length, eocd);
    }

    /**
     * Writes the EOCD to the end of the zip file. This creates a new {@link #mEocdEntry}. The
     * central directory must already be written. If {@link #mDirectoryEntry} is {@code null}, then
     * the zip file must not have any entries.
     *
     * @throws IOException failed to write the EOCD
     */
    private void appendEocd() throws IOException {
        Preconditions.checkState(mState == ZipFileState.OPEN_RW, "mState != ZipFileState.OPEN_RW");
        Preconditions.checkNotNull(mRaf, "mRaf == null");
        Preconditions.checkNotNull(mEocdEntry, "mEocdEntry == null");

        Eocd eocd = mEocdEntry.getStore();
        Verify.verifyNotNull(eocd, "eocd == null");

        byte[] eocdBytes = eocd.toBytes();
        long eocdOffset = mEocdEntry.getStart();

        directWrite(eocdOffset, eocdBytes);
    }

    /**
     * Obtains the byte array representation of the EOCD. The EOCD must have already been computed
     * for this method to be invoked.
     *
     * @return the byte representation of the EOCD
     * @throws IOException failed to obtain the byte representation of the EOCD
     */
    @NonNull
    public byte[] getEocdBytes() throws IOException {
        Preconditions.checkNotNull(mEocdEntry, "mEocdEntry == null");

        Eocd eocd = mEocdEntry.getStore();
        Verify.verifyNotNull(eocd, "eocd == null");
        return eocd.toBytes();
    }

    /**
     * Closes the file, if it is open.
     *
     * @throws IOException failed to close the file
     */
    private void innerClose() throws IOException {
        if (mState == ZipFileState.CLOSED) {
            return;
        }

        Verify.verifyNotNull(mRaf, "mRaf == null");

        mRaf.close();
        mRaf = null;
        mState = ZipFileState.CLOSED;
        if (mClosedControl == null) {
            mClosedControl = new CachedFileContents<Object>(mFile);
        }

        mClosedControl.closed(null);
    }

    /**
     * Opens (or reopens) the zip file as read-write. This method will ensure that
     * {@link #mRaf} is not null and open for writing.
     *
     * @throws IOException failed to open the file, failed to close it or the file was closed and
     * has been modified outside the control of this object
     */
    private void reopenRw() throws IOException {
        if (mState == ZipFileState.OPEN_RW) {
            return;
        }

        boolean wasClosed;
        if (mState == ZipFileState.OPEN_RO) {
            /*
             * ReadAccessFile does not have a way to reopen as RW so we have to close it and
             * open it again.
             */
            innerClose();
            wasClosed = false;
        } else {
            wasClosed = true;
        }

        Verify.verify(mState == ZipFileState.CLOSED, "mState != ZpiFileState.CLOSED");
        Verify.verify(mRaf == null, "mRaf != null");

        if (mClosedControl != null && !mClosedControl.isValid()) {
            throw new IOException("File '" + mFile.getAbsolutePath() + "' has been modified "
                    + "by an external application.");
        }

        mRaf = new RandomAccessFile(mFile, "rw");
        mState = ZipFileState.OPEN_RW;

        if (wasClosed) {
            notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
                @Nullable
                @Override
                public IOExceptionRunnable apply(ZFileExtension input) throws IOException {
                    return input.open();
                }
            });
        }
    }

    /**
     * Adds a file to the archive.
     * <p>
     * Adding the file will not update the archive immediately. Updating will only happen
     * when the {@link #update()} method is invoked.
     * <p>
     * Adding a file with the same name as an existing file will replace that file in the
     * archive.
     *
     * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes
     * and the name should not end in slash
     * @param source the source for the file's data
     * @param method the compression method to use for the file; even if
     * {@link CompressionMethod#DEFLATE} is provided, {@link CompressionMethod#STORE} will be used
     * if the result is smaller
     * @throws IOException failed to read the source data
     */
    public void add(@NonNull String name, @NonNull EntrySource source,
            @NonNull CompressionMethod method) throws IOException {
        /*
         * Create the data structure with information about the file. Assume we will store (and
         * not compress) the file. We may need to change this later on.
         */
        CentralDirectoryHeader newFileData = new CentralDirectoryHeader(name, source.size(),
                source.size(), CompressionMethod.STORE);

        /*
         * If we could be deflating, compress upfront so we can know whether the compressed data
         * is smaller or larger than the uncompressed data. storeData will either be {@code null}
         * if we didn't even try to read from the source, or will contain the raw data or
         * compressed data if we read from the source. newMethod will have the actual method that
         * will be used.
         */
        byte[] storeData = null;
        if (method == CompressionMethod.DEFLATE) {
            ByteArrayOutputStream sourceDataBytes = new ByteArrayOutputStream();

            InputStream sourceIn = source.open();
            ByteStreams.copy(sourceIn, sourceDataBytes);

            storeData = sourceDataBytes.toByteArray();

            byte[] deflatedData = deflate(storeData);
            if (deflatedData.length < storeData.length) {
                storeData = deflatedData;
                newFileData.setMethod(CompressionMethod.DEFLATE);
                newFileData.setCompressedSize(deflatedData.length);
            }

            newFileData.setCrc32(Hashing.crc32().hashBytes(storeData).padToLong());
        }

        /*
         * If we are changing the data we're storing (by compressing), replace the source with
         * a new one.
         */
        if (storeData != null) {
            source = new ByteArrayEntrySource(storeData);
            if (newFileData.getMethod() == CompressionMethod.DEFLATE) {
                source = new InflaterEntrySource(source, newFileData.getUncompressedSize());
            }
        }

        add(newFileData, source);
    }

    /**
     * Adds a new file to the archive. This will not write anything to the zip file, the change is
     * in-memory only.
     *
     * @param newFileData the data for the new file, including correct sizes and CRC32 data. The
     * offset should be set to {@code -1} because the data should not exist anywhere.
     * @param source the data source
     * @throws IOException failed to add the file
     */
    private void add(@NonNull CentralDirectoryHeader newFileData, @NonNull EntrySource source)
            throws IOException {
        /*
         * If there is a file with the same name in the archive, remove it. We remove it by
         * calling delete() on the entry (this is the public API to remove a file from the archive).
         * StoredEntry.delete() will call ZFile.delete(StoredEntry) to perform data structure
         * cleanup.
         */
        FileUseMapEntry<StoredEntry> toReplace = mEntries.get(newFileData.getName());
        final StoredEntry replaceStore;
        if (toReplace != null) {
            replaceStore = toReplace.getStore();
            assert replaceStore != null;
            replaceStore.delete(false);
        } else {
            replaceStore = null;
        }

        /*
         * Create the new entry and sets its data source. Offset should be set to -1 automatically
         * because this is a new file. With offset set to -1, StoredEntry does not try to verify the
         * local header. Since this is a new file, there is no local header and not checking it is
         * what we want to happen.
         */
        Verify.verify(newFileData.getOffset() == -1);
        final StoredEntry newEntry = new StoredEntry(newFileData, this);
        newEntry.setSource(source);

        /*
         * Find a location in the zip where this entry will be added to and create the map entry.
         * But before looking for the new location, delete the Central Directory and EOCD to make
         * space for the new entry. We don't want to add the entry *after* the Central Directory
         * because we would have to update the Central Directory when updating the file and this
         * would create a hole in the zip. Me no like holes. Holes are evil.
         */
        deleteDirectoryAndEocd();
        long size = newEntry.getInFileSize();
        int localHeaderSize = newEntry.getLocalHeaderSize();
        int alignment = mAlignmentRules.alignment(newEntry.getCentralDirectoryHeader().getName());
        long newOffset = mMap.locateFree(size, localHeaderSize, alignment);
        long newEnd = newOffset + newEntry.getInFileSize();
        if (newEnd > mMap.size()) {
            mMap.extend(newEnd);
        }

        FileUseMapEntry<StoredEntry> fileUseMapEntry = mMap.add(newOffset, newEnd, newEntry);
        mEntries.put(newFileData.getName(), fileUseMapEntry);

        mDirty = true;

        notify(new IOExceptionFunction<ZFileExtension, IOExceptionRunnable>() {
            @Nullable
            @Override
            public IOExceptionRunnable apply(ZFileExtension input) {
                return input.added(newEntry, replaceStore);
            }
        });
    }

    /**
     * Performs in-memory deflation of a byte array.
     *
     * @param in the input data
     * @return the deflated data
     * @throws IOException failed to deflate
     */
    @NonNull
    private static byte[] deflate(@NonNull byte[] in) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

        Closer closer = Closer.create();
        try {
            DeflaterOutputStream dos = closer.register(new DeflaterOutputStream(output, deflater));
            dos.write(in);
        } catch (IOException e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }

        return output.toByteArray();
    }

    /**
     * Adds all files from another zip file, maintaining their compression. Files specified in
     * <em>src</em> that are already on this file will replace the ones in this file. However, if
     * their sizes and checksums are equal, they will be ignored.
     * <p>
     * This method will not perform any changes in itself, it will only update in-memory data
     * structures. To actually write the zip file, invoke either {@link #update()} or
     * {@link #close()}.
     *
     * @param src the source archive
     * @param ignorePatterns file name patterns in <em>src</em> that should be ignored by merging;
     * merging will behave as if these files were not there; file name matching is done by using
     * {@code matches()}
     * @throws IOException failed to read from <em>src</em> or write on the output
     */
    public void mergeFrom(@NonNull ZFile src, @NonNull Set<Pattern> ignorePatterns)
            throws IOException {
        nextEntry: for (StoredEntry fromEntry : src.entries()) {
            for (Pattern p : ignorePatterns) {
                if (p.matcher(fromEntry.getCentralDirectoryHeader().getName()).matches()) {
                    continue nextEntry;
                }
            }

            boolean replaceCurrent = true;
            String path = fromEntry.getCentralDirectoryHeader().getName();
            FileUseMapEntry<StoredEntry> currentEntry = mEntries.get(path);

            if (currentEntry != null) {
                long fromSize = fromEntry.getCentralDirectoryHeader().getUncompressedSize();
                long fromCrc = fromEntry.getCentralDirectoryHeader().getCrc32();

                StoredEntry currentStore = currentEntry.getStore();
                assert currentStore != null;

                long currentSize = currentStore.getCentralDirectoryHeader().getUncompressedSize();
                long currentCrc = currentStore.getCentralDirectoryHeader().getCrc32();

                if (fromSize == currentSize && fromCrc == currentCrc) {
                    replaceCurrent = false;
                }
            }

            if (replaceCurrent) {
                CentralDirectoryHeader fromCdr = fromEntry.getCentralDirectoryHeader();
                CentralDirectoryHeader newFileData = new CentralDirectoryHeader(
                        fromCdr.getName(), fromCdr.getCompressedSize(),
                        fromCdr.getUncompressedSize(),
                        fromEntry.getCentralDirectoryHeader().getMethod());

                /*
                 * Read the data (read directly the compressed source if there is one).
                 */
                EntrySource fromSource = fromEntry.getSource();
                boolean usingCompressed;
                EntrySource compressedSource = fromSource.innerCompressed();
                if (compressedSource == null) {
                    Verify.verify(newFileData.getMethod() == CompressionMethod.STORE);
                    usingCompressed = false;
                } else {
                    fromSource = compressedSource;
                    usingCompressed = true;
                }

                InputStream fromInput = fromSource.open();
                long sourceSize = fromSource.size();
                if (sourceSize > Integer.MAX_VALUE) {
                    throw new IOException("Cannot read source with " + sourceSize + " bytes.");
                }

                byte data[] = new byte[Ints.checkedCast(sourceSize)];
                int read = 0;
                while (read < data.length) {
                    int r = fromInput.read(data, read, data.length - read);
                    Verify.verify(r >= 0, "There should be at least 'size' bytes in the stream.");
                    read += r;
                }

                /*
                 * Build the new source and wrap it around an inflater source if data came from
                 * a compressed source.
                 */
                EntrySource newSource = new ByteArrayEntrySource(data);
                if (usingCompressed) {
                    newSource = new InflaterEntrySource(newSource, fromCdr.getUncompressedSize());
                }

                /*
                 * Add will replace any current entry with the same name.
                 */
                add(newFileData, newSource);
            }
        }
    }

    /**
     * Forcibly marks this zip file as touched, forcing it to be updated when {@link #update()}
     * or {@link #close()} are invoked.
     */
    public void touch() {
        mDirty = true;
    }

    /**
     * Obtains the set of alignment rules in use by this file. Note that changes to the rules
     * will only apply in general to new files (see class description for details).
     *
     * @return the rules that can be changed
     */
    @NonNull
    public AlignmentRules getAlignmentRules() {
        return mAlignmentRules;
    }

    /**
     * Realigns all entries in the zip. This is equivalent to call {@link StoredEntry#realign()}
     * for all entries in the zip file.
     *
     * @return has any entry been changed? Note that for entries that have not yet been written on
     * the file, realignment does not count as a change as nothing needs to be updated in the file;
     * entries that have been updated may have been recreated and the existing references outside
     * of {@code ZFile} may refer to {@link StoredEntry}s that are no longer valid
     * @throws IOException failed to realign the zip; some entries in the zip may have been lost
     * due to the I/O error
     */
    public boolean realign() throws IOException {
        boolean anyChanges = false;
        for (StoredEntry entry : entries()) {
            anyChanges |= entry.realign();
        }

        return anyChanges;
    }

    /**
     * Realigns a stored entry, if necessary. Realignment is done by removing and re-adding the file
     * if it was not aligned.
     *
     * @param entry the entry to realign
     * @return has the entry been changed? Note that if the entry has not yet been written on the
     * file, realignment does not count as a change as nothing needs to be updated in the file
     * @throws IOException failed to read/write an entry; the entry may no longer exist in the
     * file
     */
    boolean realign(@NonNull StoredEntry entry) throws IOException {
        int expectedAlignment = mAlignmentRules.alignment(
                entry.getCentralDirectoryHeader().getName());

        FileUseMapEntry<StoredEntry> mapEntry = mEntries.get(
                entry.getCentralDirectoryHeader().getName());
        Verify.verify(entry == mapEntry.getStore());
        long currentDataOffset = mapEntry.getStart() + entry.getLocalHeaderSize();

        long disalignment = currentDataOffset % expectedAlignment;
        if (disalignment == 0) {
            /*
             * Good. File is aligned properly.
             */
            return false;
        }

        if (entry.getCentralDirectoryHeader().getOffset() == -1) {
            /*
             * File is not aligned but it is not written. We do not really need to do much other
             * than find another place in the map.
             */
            mMap.remove(mapEntry);
            long newStart = mMap.locateFree(mapEntry.getSize(), entry.getLocalHeaderSize(),
                    expectedAlignment);
            mapEntry = mMap.add(newStart, newStart + entry.getInFileSize(), entry);
            mEntries.put(entry.getCentralDirectoryHeader().getName(), mapEntry);

            /*
             * Just for safety. We're modifying the in-memory structures but the file should
             * already be marked as dirty.
             */
            Verify.verify(mDirty);

            return false;

        }

        /*
         * Get the entry data source, but check if we have a compressed one (we don't want to
         * inflate & deflate).
         */
        EntrySource source = entry.getSource();
        boolean sourceDeflated = false;
        if (source.innerCompressed() != null) {
            source = source.innerCompressed();
            assert source != null;
            sourceDeflated = true;
            Verify.verify(entry.getCentralDirectoryHeader().getMethod()
                    == CompressionMethod.DEFLATE);
        } else {
            Verify.verify(entry.getCentralDirectoryHeader().getMethod() == CompressionMethod.STORE);
        }

        InputStream is = source.open();
        boolean threw = true;
        byte entryData[] = null;
        try {
            entryData = ByteStreams.toByteArray(is);
            threw = false;
        } finally {
            Closeables.close(is, threw);
        }

        CentralDirectoryHeader cdh;
        try {
            cdh = entry.getCentralDirectoryHeader().clone();
        } catch (CloneNotSupportedException e) {
            Verify.verify(false);
            return false;
        }

        cdh.setOffset(-1);

        EntrySource newSource = new ByteArrayEntrySource(entryData);
        if (sourceDeflated) {
            newSource = new InflaterEntrySource(newSource, cdh.getUncompressedSize());
        }

        /*
         * Add the new file. This will replace the existing one.
         */
        add(cdh, newSource);
        return true;
    }

    /**
     * Adds an extension to this zip file.
     *
     * @param extension the listener to add
     */
    public void addZFileExtension(@NonNull ZFileExtension extension) {
        mExtensions.add(extension);
    }

    /**
     * Removes an extension from this zip file.
     *
     * @param extension the listener to remove
     */
    public void removeZFileExtension(@NonNull ZFileExtension extension) {
        mExtensions.remove(extension);
    }

    /**
     * Notifies all extensions, collecting their execution requests and running them.
     *
     * @param function the function to apply to all listeners, it will generally invoke the
     * notification method on the listener and return the result of that invocation
     * @throws IOException failed to process some extensions
     */
    private void notify(@NonNull IOExceptionFunction<ZFileExtension, IOExceptionRunnable> function)
            throws IOException {
        for (ZFileExtension fl : Lists.newArrayList(mExtensions)) {
            IOExceptionRunnable r = function.apply(fl);
            if (r != null) {
                mToRun.add(r);
            }
        }

        if (!mIsNotifying) {
            mIsNotifying = true;

            try {
                while (!mToRun.isEmpty()) {
                    IOExceptionRunnable r = mToRun.remove(0);
                    r.run();
                }
            } finally {
                mIsNotifying = false;
            }
        }
    }

    /**
     * Directly writes data in the zip file. <strong>Incorrect use of this method may corrupt the
     * zip file</strong>. Invoking this method may force the zip to be reopened in read/write
     * mode.
     *
     * @param offset the offset at which data should be written
     * @param data the data to write, may be an empty array
     * @param start start offset in  {@code data} where data to write is located
     * @param count number of bytes of data to write
     * @throws IOException failed to write the data
     */
    public void directWrite(long offset, @NonNull byte[] data, int start, int count)
            throws IOException {
        Preconditions.checkArgument(offset >= 0, "offset < 0");
        Preconditions.checkArgument(start >= 0, "start >= 0");
        Preconditions.checkArgument(count >= 0, "count >= 0");

        if (data.length == 0) {
            return;
        }

        Preconditions.checkArgument(start <= data.length, "start > data.length");
        Preconditions.checkArgument(start + count <= data.length, "start + count > data.length");

        reopenRw();
        assert mRaf != null;

        mRaf.seek(offset);
        mRaf.write(data, start, count);
    }

    /**
     * Same as {@code directWrite(offset, data, 0, data.length)}.
     *
     * @param offset the offset at which data should be written
     * @param data the data to write, may be an empty array
     * @throws IOException failed to write the data
     */
    public void directWrite(long offset, @NonNull byte[] data) throws IOException {
        directWrite(offset, data, 0, data.length);
    }

    /**
     * Directly reads data from the zip file. Invoking this method may force the zip to be reopened
     * in read/write mode.
     *
     * @param offset the offset at which data should be written
     * @param data the array where read data should be stored
     * @param start start position in the array where to write data to
     * @param count how many bytes of data can be written
     * @return how many bytes of data have been written or {@code -1} if there are no more bytes
     * to be read
     * @throws IOException failed to write the data
     */
    public int directRead(long offset, @NonNull byte[] data, int start, int count)
            throws IOException {
        Preconditions.checkArgument(offset >= 0, "offset < 0");
        Preconditions.checkArgument(start >= 0, "start >= 0");
        Preconditions.checkArgument(count >= 0, "count >= 0");

        if (data.length == 0) {
            return 0;
        }

        Preconditions.checkArgument(start <= data.length, "start > data.length");
        Preconditions.checkArgument(start + count <= data.length, "start + count > data.length");

        /*
         * Only force a reopen if the file is closed.
         */
        if (mRaf == null) {
            reopenRw();
            assert mRaf != null;
        }

        mRaf.seek(offset);
        return mRaf.read(data, start, count);
    }

    /**
     * Same as {@code directRead(offset, data, 0, data.length)}.
     *
     * @param offset the offset at which data should be read
     * @param data receives the read data, may be an empty array
     * @throws IOException failed to read the data
     */
    public int directRead(long offset, @NonNull byte[] data) throws IOException {
        return directRead(offset, data, 0, data.length);
    }

    /**
     * Reads exactly @code data.length} bytes of data, failing if it was not possible to read all
     * the requested data.
     *
     * @param offset the offset at which to start reading
     * @param data the array that receives the data read
     * @throws IOException failed to read some data or there is not enough data to read
     */
    public void directFullyRead(long offset, @NonNull byte[] data) throws IOException {
        Preconditions.checkArgument(offset >= 0, "offset < 0");
        Preconditions.checkNotNull(mRaf, "File is closed");

        mRaf.seek(offset);
        RandomAccessFileUtils.fullyRead(mRaf, data);
    }

    /**
     * Adds all files and directories recursively.
     *
     * @param file a file or directory; if it is a directory, all files and directories will be
     * added recursively
     * @param method a function that decides what compression method to apply to each file
     * @throws IOException failed to some (or all ) of the files
     */
    public void addAllRecursively(@NonNull File file,
            @NonNull Function<File, CompressionMethod> method) throws IOException {
        /*
         * The case of file.isFile() is different because if file.isFile() we will add it to the
         * zip in the root. However, if file.isDirectory() we won't add it and add its chilren.
         */
        if (file.isFile()) {
            CompressionMethod cm = Verify.verifyNotNull(method.apply(file),
                    "method.apply() returned null");

            add(file.getName(), new FileEntrySource(file), cm);
            return;
        }

        for (File f : Files.fileTreeTraverser().preOrderTraversal(file).skip(1)) {
            String path = FileUtils.relativePath(f, file);
            path = FileUtils.toSystemIndependentPath(path);

            EntrySource source;
            CompressionMethod cm;
            if (f.isDirectory()) {
                source = new ByteArrayEntrySource(new byte[0]);
                cm = CompressionMethod.STORE;
            } else {
                source = new FileEntrySource(f);
                cm = method.apply(f);
                Verify.verifyNotNull(cm, "method.apply() returned null");
            }

            add(path, source, cm);
        }
    }

    /**
     * Obtains the offset at which the central directory exists, or at which it will be written
     * if the zip file were to be flushed immediately.
     *
     * @return the offset, in bytes, where the central directory is or will be written; this value
     * includes any extra offset for the central directory
     */
    public long getCentralDirectoryOffset() {
        if (mDirectoryEntry != null) {
            return mDirectoryEntry.getStart();
        }

        /*
         * If there are no entries, the central directory is written at the start of the file.
         */
        if (mEntries.isEmpty()) {
            return mExtraDirectoryOffset;
        }

        /*
         * The Central Directory is written after all entries. This will be at the end of the file
         * if the
         */
        return mMap.usedSize() + mExtraDirectoryOffset;
    }

    /**
     * Obtains the size of the central directory, if the central directory is written in the zip
     * file.
     *
     * @return the size of the central directory or {@code -1} if the central directory has not
     * been computed
     */
    public long getCentralDirectorySize() {
        if (mDirectoryEntry != null) {
            return mDirectoryEntry.getSize();
        }

        if (mEntries.isEmpty()) {
            return 0;
        }

        return 1;
    }

    /**
     * Obtains the offset of the EOCD record, if the EOCD has been written to the file.
     *
     * @return the offset of the EOCD or {@code -1} if none exists yet
     */
    public long getEocdOffset() {
        if (mEocdEntry == null) {
            return -1;
        }

        return mEocdEntry.getStart();
    }

    /**
     * Obtains the size of the EOCD record, if the EOCD has been written to the file.
     *
     * @return the size of the EOCD of {@code -1} it none exists yet
     */
    public long getEocdSize() {
        if (mEocdEntry == null) {
            return -1;
        }

        return mEocdEntry.getSize();
    }

    /**
     * Sets an extra offset for the central directory. See class description for details. Changing
     * this value will mark the file as dirty and force a rewrite of the central directory when
     * updated.
     *
     * @param offset the offset or {@code 0} to write the central directory at its current location
     */
    public void setExtraDirectoryOffset(long offset) {
        Preconditions.checkArgument(offset >= 0, "offset < 0");

        if (mExtraDirectoryOffset != offset) {
            mExtraDirectoryOffset = offset;
            mDirty = true;
        }
    }

    /**
     * Obtains the extra offset for the central directory. See class description for details.
     *
     * @return the offset or {@code 0} if no offset is set
     */
    public long getExtraDirectoryOffset() {
        return mExtraDirectoryOffset;
    }
}
