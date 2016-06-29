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
import com.android.builder.internal.packaging.zip.utils.ByteTracker;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;
import com.android.builder.internal.packaging.zip.utils.LittleEndianUtils;
import com.android.builder.internal.packaging.zip.utils.RandomAccessFileUtils;
import com.android.builder.internal.utils.CachedFileContents;
import com.android.builder.internal.utils.IOExceptionFunction;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The {@code ZFile} provides the main interface for interacting with zip files. A {@code ZFile}
 * can be created on a new file or in an existing file. Once created, files can be added or removed
 * from the zip file.
 *
 * <p>Changes in the zip file are always deferred. Any change requested is made in memory and
 * written to disk only when {@link #update()} or {@link #close()} is invoked.
 *
 * <p>Zip files are open initially in read-only mode and will switch to read-write when needed. This
 * is done automatically. Because modifications to the file are done in-memory, the zip file can
 * be manipulated when closed. When invoking {@link #update()} or {@link #close()} the zip file
 * will be reopen and changes will be written. However, the zip file cannot be modified outside
 * the control of {@code ZFile}. So, if a {@code ZFile} is closed, modified outside and then a file
 * is added or removed from the zip file, when reopening the zip file, {@link ZFile} will detect
 * the outside modification and will fail.
 *
 * <p>In memory manipulation means that files added to the zip file are kept in memory until written
 * to disk. This provides much faster operation and allows better zip file allocation (see below).
 * It may, however, increase the memory footprint of the application. When adding large files, if
 * memory consumption is a concern, a call to {@link #update()} will actually write the file to
 * disk and discard the memory buffer. Information about allocation can be obtained from a
 * {@link ByteTracker} that can be given to the file on creation.
 *
 * <p>{@code ZFile} keeps track of allocation inside of the zip file. If a file is deleted, its
 * space is marked as freed and will be reused for an added file if it fits in the space.
 * Allocation of files to empty areas is done using a <em>best fit</em> algorithm. When adding a
 * file, if it doesn't fit in any free area, the zip file will be extended.
 *
 * <p>{@code ZFile} provides a fast way to merge data from another zip file
 * (see {@link #mergeFrom(ZFile, Predicate)}) avoiding recompression and copying of equal files.
 * When merging, patterns of files may be provided that are ignored. This allows handling special
 * files in the merging process, such as files in {@code META-INF}.
 *
 * <p>When adding files to the zip file, unless files are explicitly required to be stored, files
 * will be deflated. However, deflating will not occur if the deflated file is larger then the
 * stored file, <em>e.g.</em> if compression would yield a bigger file. See {@link Compressor} for
 * details on how compression works.
 *
 * <p>Because {@code ZFile} was designed to be used in a build system and not as general-purpose
 * zip utility, it is very strict (and unforgiving) about the zip format and unsupported features.
 *
 * <p>{@code ZFile} supports <em>alignment</em>. Alignment means that file data (not entries -- the
 * local header must be discounted) must start at offsets that are multiple of a number -- the
 * alignment. Alignment is defined by an alignment rules ({@link AlignmentRule} in the
 * {@link ZFileOptions} object used to create the {@link ZFile}.
 *
 * <p>When a file is added to the zip, the alignment rules will be checked and alignment will be
 * honored when positioning the file in the zip. This means that unused spaces in the zip may
 * be generated as a result. However, alignment of existing entries will not be changed.
 *
 * <p>Entries can be realigned individually (see {@link StoredEntry#realign()} or the full zip file
 * may be realigned (see {@link #realign()}). When realigning the full zip entries that are already
 * aligned will not be affected.
 *
 * <p>Because realignment may cause files to move in the zip, realignment is done in-memory meaning
 * that files that need to change location will moved to memory and will only be flushed when
 * either {@link #update()} or {@link #close()} are called.
 *
 * <p>Alignment only applies to filed that are forced to be uncompressed. This is because alignment
 * is used to allow mapping files in the archive directly into memory and compressing defeats the
 * purpose of alignment.
 *
 * <p>Manipulating zip files with {@link ZFile} may yield zip files with empty spaces between files.
 * This happens in two situations: (1) if alignment is required, files may be shifted to conform to
 * the request alignment leaving an empty space before the previous file, and (2) if a file is
 * removed or replaced with a file that does not fit the space it was in. By default, {@link ZFile}
 * does not do any special processing in these situations. Files are indexed by their offsets from
 * the central directory and empty spaces can exist in the zip file.
 *
 * <p>However, it is possible to tell {@link ZFile} to use the extra field in the local header
 * to do cover the empty spaces. This is done by setting
 * {@link ZFileOptions#setCoverEmptySpaceUsingExtraField(boolean)} to {@code true}. This has the
 * advantage of leaving no gaps between entries in the zip, as required by some tools like Oracle's
 * {code jar} tool. However, setting this option will destroy the contents of the file's extra
 * field.
 *
 * <p>Activating {@link ZFileOptions#setCoverEmptySpaceUsingExtraField(boolean)} may lead to
 * <i>virtual files</i> being added to the zip file. Since extra field is limited to 64k, it is not
 * possible to cover any space bigger than that using the extra field. In those cases, <i>virtual
 * files</i> are added to the file. A virtual file is a file that exists in the actual zip data,
 * but is not referenced from the central directory. A zip-compliant utility should ignore these
 * files. However, zip utilities that expect the zip to be a stream, such as Oracle's jar, will
 * find these files instead of considering the zip to be corrupt.
 *
 * <p>{@code ZFile} support sorting zip files. Sorting (done through the {@link #sortZipContents()}
 * method) is a process by which all files are re-read into memory, if not already in memory,
 * removed from the zip and re-added in alphabetical order, respecting alignment rules. So, in
 * general, file {@code b} will come after file {@code a} unless file {@code a} is subject to
 * alignment that forces an empty space before that can be occupied by {@code b}. Sorting can be
 * used to minimize the changes between two zips.
 *
 * <p>Sorting in {@code ZFile} can be done manually or automatically. Manual sorting is done by
 * invoking {@link #sortZipContents()}. Automatic sorting is done by setting the
 * {@link ZFileOptions#getAutoSortFiles()} option when creating the {@code ZFile}. Automatic
 * sorting invokes {@link #sortZipContents()} immediately when doing an {@link #update()} after
 * all extensions have processed the {@link ZFileExtension#beforeUpdate()}. This has the guarantee
 * that files added by extensions will be sorted, something that does not happen if the invocation
 * is sequential, <i>i.e.</i>, {@link #sortZipContents()} called before {@link #update()}. The
 * drawback of automatic sorting is that sorting will happen every time {@link #update()} is
 * called and the file is dirty having a possible penalty in performance.
 *
 * <p>To allow whole-apk signing, the {@code ZFile} allows the central directory location to be
 * offset by a fixed amount. This amount can be set using the {@link #setExtraDirectoryOffset(long)}
 * method. Setting a non-zero value will add extra (unused) space in the zip file before the
 * central directory. This value can be changed at any time and it will force the central directory
 * rewritten when the file is updated or closed.
 *
 * <p>{@code ZFile} provides an extension mechanism to allow objects to register with the file
 * and be notified when changes to the file happen. This should be used
 * to add extra features to the zip file while providing strong decoupling. See
 * {@link ZFileExtension}, {@link ZFile#addZFileExtension(ZFileExtension)} and
 * {@link ZFile#removeZFileExtension(ZFileExtension)}.
 *
 * <p>This class is <strong>not</strong> thread-safe. Neither are any of the classes associated with
 * it in this package, except when otherwise noticed.
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
     * When extensions request re-runs, we do maximum number of cycles until we decide to stop and
     * flag a infinite recursion problem.
     */
    private static final int MAXIMUM_EXTENSION_CYCLE_COUNT = 10;

    /**
     * Minimum size for the extra field.
     */
    private static final int MINIMUM_EXTRA_FIELD_SIZE = 6;

    /**
     * Header ID for field with zip alignment.
     */
    private static final int ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID = 0xd935;

    /**
     * Explanation to write in the file contents of virtual files.
     */
    private static final String VIRTUAL_FILE_EXPLANATION =
            "If you are seeing this file, then the zip utility that was used to uncompress the "
                    + "zip file is non-compliant with the zip standard. This file exists only to "
                    + "cover unused space in the zip file and should not have been created during "
                    + "expansion. These spaces are the result of incrementally updating a zip. "
                    + "For example, removing a file and adding a bigger one that doesn't fit in "
                    + "space left by the removed file will yield an empty area in the zip file.";

    /**
     * Name prefix of a virtual file.
     */
    private static final String VIRTUAL_FILE_NAME_PREFIX = "__this_file_does_not_exist_";

    /**
     * Name suffix of a virtual file.
     */
    private static final String VIRTUAL_FILE_NAME_SUFFIX = ".txt";

    /**
     * Maximum size of the extra field.
     */
    private static final int MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE = (1 << 16);

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
     * written on disk. Only entries that have been compressed are in this list.
     */
    @NonNull
    private final Map<String, FileUseMapEntry<StoredEntry>> mEntries;

    /**
     * Entries added to the zip file, but that are not yet compressed. When compression is done,
     * these entries are eventually moved to {@link #mEntries}. mUncompressedEntries is a list
     * because entries need to be kept in the order by which they were added. It allows adding
     * multiple files with the same name and getting the right notifications on which files replaced
     * which.
     *
     * <p>Files are placed in this list in {@link #add(StoredEntry)} method. This method will
     * keep files here temporarily and move then to {@link #mEntries} when the data is
     * available.
     *
     * <p>Moving files out of this list to {@link #mEntries} is done by
     * {@link #processAllReadyEntries()}.
     */
    @NonNull
    private final List<StoredEntry> mUncompressedEntries;

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
     * The alignment rule.
     */
    @NonNull
    private final AlignmentRule mAlignmentRule;

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
     * Should all timestamps be zeroed when reading / writing the zip?
     */
    private boolean mNoTimestamps;

    /**
     * Compressor to use.
     */
    @NonNull
    private Compressor mCompressor;

    /**
     * Byte tracker to use.
     */
    @NonNull
    private final ByteTracker mTracker;

    /**
     * Use the zip entry's "extra field" field to cover empty space in the zip file?
     */
    private boolean mCoverEmptySpaceUsingExtraField;

    /**
     * Should files be automatically sorted when updating?
     */
    private boolean mAutoSortFiles;


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
        this(file, new ZFileOptions());
    }

    /**
     * Creates a new zip file. If the zip file does not exist, then no file is created at this
     * point and {@code ZFile} will contain an empty structure. However, an (empty) zip file will
     * be created if either {@link #update()} or {@link #close()} are used. If a zip file exists,
     * it will be parsed and read.
     *
     * @param file the zip file
     * @param options configuration options
     * @throws IOException some file exists but could not be read
     */
    public ZFile(@NonNull File file, @NonNull ZFileOptions options) throws IOException {
        mFile = file;
        mMap = new FileUseMap(
                0,
                options.getCoverEmptySpaceUsingExtraField()
                        ? MINIMUM_EXTRA_FIELD_SIZE
                        : 0);
        mDirty = false;
        mClosedControl = null;
        mAlignmentRule = options.getAlignmentRule();
        mExtensions = Lists.newArrayList();
        mToRun = Lists.newArrayList();
        mNoTimestamps = options.getNoTimestamps();
        mTracker = options.getTracker();
        mCompressor = options.getCompressor();
        mCoverEmptySpaceUsingExtraField = options.getCoverEmptySpaceUsingExtraField();
        mAutoSortFiles = options.getAutoSortFiles();

        /*
         * These two values will be overwritten by openReadOnly() below if the file exists.
         */
        mState = ZipFileState.CLOSED;
        mRaf = null;

        if (file.exists()) {
            openReadOnly();
        } else {
            mDirty = true;
        }

        mEntries = Maps.newHashMap();
        mUncompressedEntries = Lists.newArrayList();
        mExtraDirectoryOffset = 0;

        try {
            if (mState != ZipFileState.CLOSED) {
                long rafSize = mRaf.length();
                if (rafSize > Integer.MAX_VALUE) {
                    throw new IOException("File exceeds size limit of " + Integer.MAX_VALUE + ".");
                }

                mMap.extend(Ints.checkedCast(rafSize));
                readData();

                notify(ZFileExtension::open);
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
        Map<String, StoredEntry> entries = Maps.newHashMap();

        for (FileUseMapEntry<StoredEntry> mapEntry : mEntries.values()) {
            StoredEntry entry = mapEntry.getStore();
            assert entry != null;
            entries.put(entry.getCentralDirectoryHeader().getName(), entry);
        }

        /*
         * mUncompressed may override mEntriesReady as we may not have yet processed all
         * entries.
         */
        for (StoredEntry uncompressed : mUncompressedEntries) {
            entries.put(uncompressed.getCentralDirectoryHeader().getName(), uncompressed);
        }

        return Sets.newHashSet(entries.values());
    }

    /**
     * Obtains an entry at a given path in the zip.
     *
     * @param path the path
     * @return the entry at the path or {@code null} if none exists
     */
    @Nullable
    public StoredEntry get(@NonNull String path) {
        /*
         * The latest entries are the last ones in uncompressed and they may eventually override
         * files in mEntries.
         */
        for (StoredEntry stillUncompressed : Lists.reverse(mUncompressedEntries)) {
            if (stillUncompressed.getCentralDirectoryHeader().getName().equals(path)) {
                return stillUncompressed;
            }
        }

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

                /*
                 * If isExtraAlignmentBlock(entry.getLocalExtra()) is true, we know the entry
                 * has an extra field that is solely used for alignment. This means the
                 * actual entry could start at start + extra.length and leave space before.
                 *
                 * But, if we did this here, we would be modifying the zip file and that is
                 * weird because we're just opening it for reading.
                 *
                 * The downside is that we will never reuse that space. Maybe one day ZFile
                 * can be clever enough to remove the local extra when we start modifying the zip
                 * file.
                 */

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

        for (int endIdx = last.length - MIN_EOCD_SIZE; endIdx >= 0 && foundEocdSignature == -1;
                endIdx--) {
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
                ByteBuffer eocdBytes =
                        ByteBuffer.wrap(last, foundEocdSignature, last.length - foundEocdSignature);

                try {
                    eocd = new Eocd(eocdBytes);
                    eocdStart = Ints.checkedCast(mRaf.length() - lastToRead + foundEocdSignature);

                    /*
                     * Make sure the EOCD takes the whole file up to the end.
                     */
                    if (eocdStart + eocd.getEocdSize() != mRaf.length()) {
                        throw new IOException("EOCD starts at " + eocdStart + " and has "
                                + eocd.getEocdSize() + " bytes, but file ends at " + mRaf.length()
                                + ".");
                    }
                } catch (IOException e) {
                    if (errorFindingSignature != null) {
                        e.addSuppressed(errorFindingSignature);
                    }

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
            if (LittleEndianUtils.readUnsigned4Le(ByteBuffer.wrap(possibleZip64Locator)) ==
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

        CentralDirectory directory = CentralDirectory.makeFromData(ByteBuffer.wrap(directoryData),
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
            private long mCurr = start;

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
            notify(ext -> ext.removed(entry));
        }
    }

    /**
     * Updates the file writing new entries and removing deleted entries. This will force
     * reopening the file as read/write if the file wasn't open in read/write mode.
     * @throws IOException failed to update the file; this exception may have been thrown by
     * the compressor but only reported here
     */
    public void update() throws IOException {
        /*
         * Process all background stuff before calling in the extensions.
         */
        processAllReadyEntriesWithWait();

        notify(ZFileExtension::beforeUpdate);

        /*
         * Process all background stuff that may be leftover by the extensions.
         */
        processAllReadyEntriesWithWait();

        if (!mDirty) {
            return;
        }

        reopenRw();

        if (mAutoSortFiles) {
            sortZipContents();
        }

        /*
         * We're going to change the file so delete the central directory and the EOCD as they
         * will have to be rewritten.
         */
        deleteDirectoryAndEocd();
        mMap.truncate();

        /*
         * If we need to add virtual entries, register them here.
         */
        Set<FileUseMapEntry<StoredEntry>> addedVirtualEntries = new HashSet<>();

        /*
         * If we need to use the extra field to cover empty spaces, we do the processing here.
         */
        if (mCoverEmptySpaceUsingExtraField) {

            /* We will go over all files in the zip and check whether there is empty space before
             * them. If there is, then we will move the entry to the beginning of the empty space
             * (covering it) and extend the extra field with the size of the empty space.
             *
             * If the empty space is too large to use the extra field, we'll create a virtual
             * file.
             */
            int virtualFileCount = 0;
            for (FileUseMapEntry<StoredEntry> entry : new HashSet<>(mEntries.values())) {
                StoredEntry storedEntry = entry.getStore();
                assert storedEntry != null;

                FileUseMapEntry<?> before = mMap.before(entry);
                if (before == null || !before.isFree()) {
                    continue;
                }

                int localExtraSize =
                        storedEntry.getLocalExtra().length + Ints.checkedCast(before.getSize());
                if (localExtraSize > MAX_LOCAL_EXTRA_FIELD_CONTENTS_SIZE) {
                    /*
                     * We can't use the local extra field for this one. Create a virtual file.
                     */
                    String vfName = VIRTUAL_FILE_NAME_PREFIX + virtualFileCount
                            + VIRTUAL_FILE_NAME_SUFFIX;

                    StoredEntry virtualEntry = makeVirtual(vfName, before.getSize());
                    FileUseMapEntry<StoredEntry> virtualEntryInMap =
                            mMap.add(before.getStart(), before.getEnd(), virtualEntry);
                    addedVirtualEntries.add(virtualEntryInMap);

                    mEntries.put(vfName, virtualEntryInMap);
                } else {
                    long newStart = before.getStart();
                    long newSize = entry.getSize() + before.getSize();

                    String name = storedEntry.getCentralDirectoryHeader().getName();
                    mMap.remove(entry);
                    Verify.verify(entry == mEntries.remove(name));

                    storedEntry.setLocalExtra(
                            makeExtraAlignmentBlock(localExtraSize, chooseAlignment(storedEntry)));
                    mEntries.put(name, mMap.add(newStart, newStart + newSize, storedEntry));

                    /*
                     * Reset the offset to force the file to be rewritten.
                     */
                    storedEntry.getCentralDirectoryHeader().setOffset(-1);
                }
            }
        }

        /*
         * Write new files in the zip. We identify new files because they don't have an offset
         * in the zip where they are written although we already know, by their location in the
         * file map, where they will be written to.
         *
         * Before writing the files, we sort them in the order they are written in the file so that
         * writes are made in order on disk.
         * This is, however, unlikely to optimize anything relevant given the way the Operating
         * System does caching, but it certainly won't hurt :)
         */
        TreeMap<FileUseMapEntry<?>, StoredEntry> toWriteToStore =
                new TreeMap<>(FileUseMapEntry.COMPARE_BY_START);

        for (FileUseMapEntry<StoredEntry> entry : mEntries.values()) {
            StoredEntry entryStore = entry.getStore();
            assert entryStore != null;
            if (entryStore.getCentralDirectoryHeader().getOffset() == -1) {
                toWriteToStore.put(entry, entryStore);
            }
        }

        /*
         * Add all free entries to the set.
         */
        for(FileUseMapEntry<?> freeArea : mMap.getFreeAreas()) {
            toWriteToStore.put(freeArea, null);
        }

        /*
         * Write everything to file.
         */
        for (FileUseMapEntry<?> fileUseMapEntry : toWriteToStore.keySet()) {
            StoredEntry entry = toWriteToStore.get(fileUseMapEntry);
            if (entry == null) {
                int size = Ints.checkedCast(fileUseMapEntry.getSize());
                directWrite(fileUseMapEntry.getStart(), new byte[size]);
            } else {
                writeEntry(entry, fileUseMapEntry.getStart());
            }
        }

        /*
         * Remove all virtual entries.
         */
        for (FileUseMapEntry<StoredEntry> virtualEntryInMap : addedVirtualEntries) {
            mMap.remove(virtualEntryInMap);
            StoredEntry entry = virtualEntryInMap.getStore();
            assert entry != null;
            mEntries.remove(entry.getCentralDirectoryHeader().getName());
        }

        boolean hasCentralDirectory;
        int extensionBugDetector = MAXIMUM_EXTENSION_CYCLE_COUNT;
        do {
            computeCentralDirectory();
            computeEocd();

            hasCentralDirectory = (mDirectoryEntry != null);

            notify(ext -> {
                ext.entriesWritten();
                return null;
            });

            if ((--extensionBugDetector) == 0) {
                throw new IOException("Extensions keep resetting the central directory. This is "
                        + "probably a bug.");
            }
        } while (hasCentralDirectory && mDirectoryEntry == null);

        appendCentralDirectory();
        appendEocd();

        Verify.verifyNotNull(mRaf);
        mRaf.setLength(mMap.size());

        mDirty = false;

        notify(ext -> {
           ext.updated();
            return null;
        });
    }

    /**
     * Updates the file and closes it.
     */
    @Override
    public void close() throws IOException {
        // We need to make sure to release mRaf, otherwise we end up locking the file on
        // Windows. Use try-with-resources to handle exception suppressing.
        try (Closeable ignored = this::innerClose) {
            update();
        }

        notify(ext -> {
           ext.closed();
            return null;
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
         * Get the raw source data to write.
         */
        ProcessedAndRawByteSources source = entry.getSource();
        ByteSource rawContents = source.getRawByteSource();

        /*
         * Write the source data.
         */
        byte[] chunk = new byte[IO_BUFFER_SIZE];
        int r;
        long writeOffset = offset + headerData.length;
        InputStream is = rawContents.openStream();
        while ((r = is.read(chunk)) >= 0) {
            directWrite(writeOffset, chunk, 0, r);
            writeOffset += r;
        }

        is.close();

        /*
         * Set the entry's offset and create the entry source.
         */
        entry.replaceSourceFromZip(offset);
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

        /*
         * Make sure we truncate the map before computing the central directory's location since
         * the central directory is the last part of the file.
         */
        mMap.truncate();

        CentralDirectory newDirectory = CentralDirectory.makeFromEntries(newStored, this);
        byte[] newDirectoryBytes = newDirectory.toBytes();
        long directoryOffset = mMap.size() + mExtraDirectoryOffset;

        mMap.extend(directoryOffset + newDirectoryBytes.length);

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
        Preconditions.checkNotNull(newDirectory, "newDirectory != null");

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
        Preconditions.checkNotNull(cd, "cd == null");
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
        Preconditions.checkNotNull(eocd, "eocd == null");

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
        Preconditions.checkNotNull(eocd, "eocd == null");
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
            mClosedControl = new CachedFileContents<>(mFile);
        }

        mClosedControl.closed(null);
    }

    /**
     * If the zip file is closed, opens it in read-only mode. If it is already open, does nothing.
     * In general, it is not necessary to directly invoke this method. However, if directly
     * reading the zip file using, for example {@link #directRead(long, byte[])}, then this
     * method needs to be called.
     * @throws IOException failed to open the file
     */
    public void openReadOnly() throws IOException {
        if (mState != ZipFileState.CLOSED) {
            return;
        }

        mState = ZipFileState.OPEN_RO;
        mRaf = new RandomAccessFile(mFile, "r");
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
            notify(ZFileExtension::open);
        }
    }

    /**
     * Equivalent to call {@link #add(String, InputStream, boolean)} using
     * {@code true} as {@code mayCompress}.
     *
     * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes
     * and the name should not end in slash
     * @param stream the source for the file's data
     * @throws IOException failed to read the source data
     */
    public void add(@NonNull String name, @NonNull InputStream stream) throws IOException {
        add(name, stream, true);
    }

    /**
     * Creates a stored entry. This does not add the entry to the zip file, it just creates the
     * {@link StoredEntry} object.
     *
     * @param name the name of the entry
     * @param stream the input stream with the entry's data
     * @param mayCompress can the entry be compressed?
     * @return the created entry
     * @throws IOException failed to create the entry
     */
    @NonNull
    private StoredEntry makeStoredEntry(
            @NonNull String name,
            @NonNull InputStream stream,
            boolean mayCompress)
            throws IOException {
        CloseableByteSource source = mTracker.fromStream(stream);
        long crc32 = source.hash(Hashing.crc32()).padToLong();

        boolean encodeWithUtf8 = !EncodeUtils.canAsciiEncode(name);

        SettableFuture<CentralDirectoryHeaderCompressInfo> compressInfo =
                SettableFuture.create();
        CentralDirectoryHeader newFileData = new CentralDirectoryHeader(name,
                source.size(), compressInfo, GPFlags.make(encodeWithUtf8));
        newFileData.setCrc32(crc32);

        /*
         * Create the new entry and sets its data source. Offset should be set to -1 automatically
         * because this is a new file. With offset set to -1, StoredEntry does not try to verify the
         * local header. Since this is a new file, there is no local header and not checking it is
         * what we want to happen.
         */
        Verify.verify(newFileData.getOffset() == -1);
        return new StoredEntry(
                newFileData,
                this,
                createSources(mayCompress, source, compressInfo, newFileData));
    }

    /**
     * Creates the processed and raw sources for an entry.
     *
     * @param mayCompress can the entry be compressed?
     * @param source the entry's data (uncompressed)
     * @param compressInfo the compression info future that will be set when the raw entry is
     * created and the {@link CentralDirectoryHeaderCompressInfo} object can be created
     * @param newFileData the central directory header for the new file
     * @return the sources whose data may or may not be already defined
     * @throws IOException failed to create the raw sources
     */
    @NonNull
    private ProcessedAndRawByteSources createSources(
            boolean mayCompress,
            @NonNull CloseableByteSource source,
            @NonNull SettableFuture<CentralDirectoryHeaderCompressInfo> compressInfo,
            @NonNull CentralDirectoryHeader newFileData)
            throws IOException {
        if (mayCompress) {
            ListenableFuture<CompressionResult> result = mCompressor.compress(source);
            Futures.addCallback(result, new FutureCallback<CompressionResult>() {
                @Override
                public void onSuccess(CompressionResult result) {
                    compressInfo.set(new CentralDirectoryHeaderCompressInfo(newFileData,
                            result.getCompressionMethod(), result.getSize()));
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    compressInfo.setException(t);
                }
            });

            ListenableFuture<CloseableByteSource> compressedByteSourceFuture =
                    Futures.transform(result, CompressionResult::getSource);
            LazyDelegateByteSource compressedByteSource = new LazyDelegateByteSource(
                    compressedByteSourceFuture);
            return new ProcessedAndRawByteSources(source, compressedByteSource);
        } else {
            compressInfo.set(new CentralDirectoryHeaderCompressInfo(newFileData,
                    CompressionMethod.STORE, source.size()));
            return new ProcessedAndRawByteSources(source, source);
        }
    }

    /**
     * Adds a file to the archive.
     *
     * <p>Adding the file will not update the archive immediately. Updating will only happen
     * when the {@link #update()} method is invoked.
     *
     * <p>Adding a file with the same name as an existing file will replace that file in the
     * archive.
     *
     * @param name the file name (<em>i.e.</em>, path); paths should be defined using slashes
     * and the name should not end in slash
     * @param stream the source for the file's data
     * @param mayCompress can the file be compressed? This flag will be ignored if the alignment
     * rules force the file to be aligned, in which case the file will not be compressed.
     * @throws IOException failed to read the source data
     */
    public void add(@NonNull String name, @NonNull InputStream stream, boolean mayCompress)
            throws IOException {

        /*
         * Clean pending background work, if needed.
         */
        processAllReadyEntries();

        add(makeStoredEntry(name, stream, mayCompress));
    }

    /**
     * Adds a {@link StoredEntry} to the zip. The entry is not immediately added to
     * {@link #mEntries} because data may not yet be available. Instead, it is placed under
     * {@link #mUncompressedEntries} and later moved to {@link #processAllReadyEntries()} when
     * done.
     *
     * <p>This method invokes {@link #processAllReadyEntries()} to move the entry if it has already
     * been computed so, if there is no delay in compression, and no more files are in waiting
     * queue, then the entry is added to {@link #mEntries} immediately.
     *
     * @param newEntry the entry to add
     * @throws IOException failed to process this entry (or a previous one whose future only
     * completed now)
     */
    private void add(@NonNull final StoredEntry newEntry) throws IOException {
        mUncompressedEntries.add(newEntry);
        processAllReadyEntries();
    }

    /**
     * Moves all ready entries from {@link #mUncompressedEntries} to {@link #mEntries}. It will
     * stop as soon as entry whose future has not been completed is found.
     *
     * @throws IOException the exception reported in the future computation, if any, or failed
     * to add a file to the archive
     */
    private void processAllReadyEntries() throws IOException {
        /*
         * Many things can happen during addToEntries(). Because addToEntries() fires
         * notifications to extensions, other files can be added, removed, etc. Ee are *not*
         * guaranteed that new stuff does not get into mUncompressedEntries: add() will still work
         * and will add new entries in there.
         *
         * However -- important -- processReadyEntries() may be invoked during addToEntries()
         * because of the extension mechanism. This means that stuff *can* be removed from
         * mUncompressedEntries and moved to mEntries during addToEntries().
         */
        while (!mUncompressedEntries.isEmpty()) {
            StoredEntry next = mUncompressedEntries.get(0);
            CentralDirectoryHeader cdh = next.getCentralDirectoryHeader();
            Future<CentralDirectoryHeaderCompressInfo> compressionInfo = cdh.getCompressionInfo();
            if (!compressionInfo.isDone()) {
                /*
                 * First entry in queue is not yet complete. We can't do anything else.
                 */
                return;
            }

            mUncompressedEntries.remove(0);

            try {
                compressionInfo.get();
            } catch (InterruptedException e) {
                throw new IOException("Impossible I/O exception: get for already computed "
                        + "future throws InterruptedException", e);
            } catch (ExecutionException e) {
                throw new IOException("Failed to obtain compression information for entry", e);
            }

            addToEntries(next);
        }
    }

    /**
     * Waits until {@link #mUncompressedEntries} is empty.
     *
     * @throws IOException the exception reported in the future computation, if any, or failed
     * to add a file to the archive
     */
    private void processAllReadyEntriesWithWait() throws IOException {
        processAllReadyEntries();
        while (!mUncompressedEntries.isEmpty()) {
            /*
             * Wait for the first future to complete and then try again. Keep looping until we're
             * done.
             */
            StoredEntry first = mUncompressedEntries.get(0);
            CentralDirectoryHeader cdh = first.getCentralDirectoryHeader();
            cdh.getCompressionInfoWithWait();

            processAllReadyEntries();
        }
    }

    /**
     * Adds a new file to {@link #mEntries}. This is actually added to the zip and its space
     * allocated in the {@link #mMap}.
     *
     * @param newEntry the new entry to add
     * @throws IOException failed to add the file
     */
    private void addToEntries(@NonNull final StoredEntry newEntry) throws IOException {
        Preconditions.checkArgument(newEntry.getDataDescriptorType() ==
                DataDescriptorType.NO_DATA_DESCRIPTOR, "newEntry has data descriptor");

        /*
         * If there is a file with the same name in the archive, remove it. We remove it by
         * calling delete() on the entry (this is the public API to remove a file from the archive).
         * StoredEntry.delete() will call {@link ZFile#delete(StoredEntry, boolean)}  to perform
         * data structure cleanup.
         */
        FileUseMapEntry<StoredEntry> toReplace = mEntries.get(
                newEntry.getCentralDirectoryHeader().getName());
        final StoredEntry replaceStore;
        if (toReplace != null) {
            replaceStore = toReplace.getStore();
            assert replaceStore != null;
            replaceStore.delete(false);
        } else {
            replaceStore = null;
        }

        FileUseMapEntry<StoredEntry> fileUseMapEntry = positionInFile(newEntry);
        mEntries.put(newEntry.getCentralDirectoryHeader().getName(), fileUseMapEntry);

        mDirty = true;

        notify(ext -> ext.added(newEntry, replaceStore));
    }

    /**
     * Finds a location in the zip where this entry will be added to and create the map entry.
     * This method cannot be called if there is already a map entry for the given entry (if you
     * do that, then you're doing something wrong somewhere).
     *
     * <p>This may delete the central directory and EOCD (if it deletes one, it deletes the other)
     * if there is no space before the central directory. Otherwise, the file would be added
     * after the central directory. This would force a new central directory to be written
     * when updating the file and would create a hole in the zip. Me no like holes. Holes are evil.
     *
     * @param entry the entry to place in the zip
     * @return the position in the file where the entry should be placed
     */
    @NonNull
    private FileUseMapEntry<StoredEntry> positionInFile(@NonNull StoredEntry entry)
            throws IOException {
        deleteDirectoryAndEocd();
        long size = entry.getInFileSize();
        int localHeaderSize = entry.getLocalHeaderSize();
        int alignment = chooseAlignment(entry);

        long newOffset = mMap.locateFree(size, localHeaderSize, alignment);
        long newEnd = newOffset + entry.getInFileSize();
        if (newEnd > mMap.size()) {
            mMap.extend(newEnd);
        }

        return mMap.add(newOffset, newEnd, entry);
    }

    /**
     * Determines what is the alignment value of an entry.
     *
     * @param entry the entry
     * @return the alignment value, {@link AlignmentRule#NO_ALIGNMENT} if there is no alignment
     * required for the entry
     * @throws IOException failed to determine the alignment
     */
    private int chooseAlignment(@NonNull StoredEntry entry) throws IOException {
        CentralDirectoryHeader cdh = entry.getCentralDirectoryHeader();
        CentralDirectoryHeaderCompressInfo compressionInfo = cdh.getCompressionInfoWithWait();

        boolean isCompressed = compressionInfo.getMethod() != CompressionMethod.STORE;
        if (isCompressed) {
            return AlignmentRule.NO_ALIGNMENT;
        } else {
            return mAlignmentRule.alignment(cdh.getName());
        }
    }

    /**
     * Adds all files from another zip file, maintaining their compression. Files specified in
     * <em>src</em> that are already on this file will replace the ones in this file. However, if
     * their sizes and checksums are equal, they will be ignored.
     *
     * <p> This method will not perform any changes in itself, it will only update in-memory data
     * structures. To actually write the zip file, invoke either {@link #update()} or
     * {@link #close()}.
     *
     * @param src the source archive
     * @param ignoreFilter predicate that, if {@code true}, identifies files in <em>src</em> that
     * should be ignored by merging; merging will behave as if these files were not there
     * @throws IOException failed to read from <em>src</em> or write on the output
     */
    public void mergeFrom(@NonNull ZFile src, @NonNull Predicate<String> ignoreFilter)
            throws IOException {
        for (StoredEntry fromEntry : src.entries()) {
            if (ignoreFilter.test(fromEntry.getCentralDirectoryHeader().getName())) {
                continue;
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
                CentralDirectoryHeaderCompressInfo fromCompressInfo =
                        fromCdr.getCompressionInfoWithWait();
                CentralDirectoryHeader newFileData;
                try {
                    /*
                     * We make two changes in the central directory from the file to merge:
                     * we reset the offset to force the entry to be written and we reset the
                     * deferred CRC bit as we don't need the extra stuff after the file. It takes
                     * space and is totally useless.
                     */
                    newFileData = fromCdr.clone();
                    newFileData.setOffset(-1);
                    newFileData.resetDeferredCrc();
                } catch (CloneNotSupportedException e) {
                    throw new IOException("Failed to clone CDR.", e);
                }

                /*
                 * Read the data (read directly the compressed source if there is one).
                 */
                ProcessedAndRawByteSources fromSource = fromEntry.getSource();
                InputStream fromInput = fromSource.getRawByteSource().openStream();
                long sourceSize = fromSource.getRawByteSource().size();
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
                CloseableByteSource rawContents = mTracker.fromSource(fromSource.getRawByteSource());
                CloseableByteSource processedContents;
                if (fromCompressInfo.getMethod() == CompressionMethod.DEFLATE) {
                    //noinspection IOResourceOpenedButNotSafelyClosed
                    processedContents = new InflaterByteSource(rawContents);
                } else {
                    processedContents = rawContents;
                }

                ProcessedAndRawByteSources newSource = new ProcessedAndRawByteSources(
                        processedContents, rawContents);

                /*
                 * Add will replace any current entry with the same name.
                 */
                StoredEntry newEntry = new StoredEntry(newFileData, this, newSource);
                add(newEntry);
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
     * Wait for any background tasks to finish and report any errors. In general this method does
     * not need to be invoked directly as errors from background tasks are reported during
     * {@link #add(String, InputStream, boolean)}, {@link #update()} and {@link #close()}.
     * However, if required for some purposes, <em>e.g.</em>, ensuring all notifications have been
     * done to extensions, then this method may be called. It will wait for all background tasks
     * to complete.
     * @throws IOException some background work failed
     */
    public void finishAllBackgroundTasks() throws IOException {
        processAllReadyEntriesWithWait();
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

        FileUseMapEntry<StoredEntry> mapEntry = mEntries.get(
                entry.getCentralDirectoryHeader().getName());
        Verify.verify(entry == mapEntry.getStore());
        long currentDataOffset = mapEntry.getStart() + entry.getLocalHeaderSize();

        int expectedAlignment = chooseAlignment(entry);
        long misalignment = currentDataOffset % expectedAlignment;
        if (misalignment == 0) {
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
         * inflate and deflate).
         */
        CentralDirectoryHeaderCompressInfo compressInfo =
                entry.getCentralDirectoryHeader().getCompressionInfoWithWait();

        ProcessedAndRawByteSources source = entry.getSource();

        CentralDirectoryHeader clonedCdh;
        try {
            clonedCdh = entry.getCentralDirectoryHeader().clone();
        } catch (CloneNotSupportedException e) {
            Verify.verify(false);
            return false;
        }

        /*
         * We make two changes in the central directory when realigning:
         * we reset the offset to force the entry to be written and we reset the
         * deferred CRC bit as we don't need the extra stuff after the file. It takes
         * space and is totally useless and we may need the extra space to realign the entry...
         */
        clonedCdh.setOffset(-1);
        clonedCdh.resetDeferredCrc();

        CloseableByteSource rawContents = mTracker.fromSource(source.getRawByteSource());
        CloseableByteSource processedContents;

        if (compressInfo.getMethod() == CompressionMethod.DEFLATE) {
            //noinspection IOResourceOpenedButNotSafelyClosed
            processedContents = new InflaterByteSource(rawContents);
        } else {
            processedContents = rawContents;
        }

        ProcessedAndRawByteSources newSource = new ProcessedAndRawByteSources(processedContents,
                rawContents);

        /*
         * Add the new file. This will replace the existing one.
         */
        StoredEntry newEntry = new StoredEntry(clonedCdh, this, newSource);
        add(newEntry);
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
     * <p>
     * Equivalent to calling {@link #addAllRecursively(File, Function)} using a function that
     * always returns {@code true}
     *
     * @param file a file or directory; if it is a directory, all files and directories will be
     * added recursively
     * @throws IOException failed to some (or all ) of the files
     */
    public void addAllRecursively(@NonNull File file) throws IOException {
        addAllRecursively(file, f -> true);
    }

    /**
     * Adds all files and directories recursively.
     *
     * @param file a file or directory; if it is a directory, all files and directories will be
     * added recursively
     * @param mayCompress a function that decides whether files may be compressed
     * @throws IOException failed to some (or all ) of the files
     */
    public void addAllRecursively(@NonNull File file,
            @NonNull Function<? super File, Boolean> mayCompress) throws IOException {
        /*
         * The case of file.isFile() is different because if file.isFile() we will add it to the
         * zip in the root. However, if file.isDirectory() we won't add it and add its children.
         */
        if (file.isFile()) {
            boolean mayCompressFile = Verify.verifyNotNull(mayCompress.apply(file),
                    "mayCompress.apply() returned null");

            try (Closer closer = Closer.create()) {
                FileInputStream fileInput = closer.register(new FileInputStream(file));
                add(file.getName(), fileInput, mayCompressFile);
            }

            return;
        }

        for (File f : Files.fileTreeTraverser().preOrderTraversal(file).skip(1)) {
            String path = FileUtils.relativePath(f, file);
            path = FileUtils.toSystemIndependentPath(path);

            InputStream stream;
            try (Closer closer = Closer.create()) {
                boolean mayCompressFile;
                if (f.isDirectory()) {
                    stream = closer.register(new ByteArrayInputStream(new byte[0]));
                    mayCompressFile = false;
                } else {
                    stream = closer.register(new FileInputStream(f));
                    mayCompressFile = Verify.verifyNotNull(mayCompress.apply(f),
                            "mayCompress.apply() returned null");
                }

                add(path, stream, mayCompressFile);
            }
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
            deleteDirectoryAndEocd();
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

    /**
     * Obtains whether this {@code ZFile} is ignoring timestamps.
     *
     * @return are the timestamps being ignored?
     */
    public boolean areTimestampsIgnored() {
        return mNoTimestamps;
    }

    /**
     * Sorts all files in the zip. This will force all files to be loaded and will wait for all
     * background tasks to complete. Sorting files is never done implicitly and will operate in
     * memory only (maybe reading files from the zip disk into memory, if needed). It will leave
     * the zip in dirty state, requiring a call to {@link #update()} to force the entries to be
     * written to disk.
     *
     * @throws IOException failed to load or move a file in the zip
     */
    public void sortZipContents() throws IOException {
        reopenRw();

        processAllReadyEntriesWithWait();

        Verify.verify(mUncompressedEntries.isEmpty());

        SortedSet<StoredEntry> sortedEntries = Sets.newTreeSet(StoredEntry.COMPARE_BY_NAME);
        for (FileUseMapEntry<StoredEntry> fmEntry : mEntries.values()) {
            StoredEntry entry = fmEntry.getStore();
            Preconditions.checkNotNull(entry);
            sortedEntries.add(entry);
            entry.loadSourceIntoMemory();

            mMap.remove(fmEntry);
        }

        mEntries.clear();
        for (StoredEntry entry : sortedEntries) {
            String name = entry.getCentralDirectoryHeader().getName();
            FileUseMapEntry<StoredEntry> positioned = positionInFile(entry);
            mEntries.put(name, positioned);
        }

        mDirty = true;
    }

    /**
     * Obtains the filesystem path to the zip file.
     *
     * @return the file that may or may not exist (depending on whether something existed there
     * before the zip was created and on whether the zip has been updated or not)
     */
    @NonNull
    public File getFile() {
        return mFile;
    }

    /**
     * Creates the extra field block to fill in {@code blockSize} bytes.
     *
     * @param blockSize the block size to fill as an extra field
     * @param alignment the alignment that is being used for the file
     * @return the extra field block
     * @throws IOException failed to write the extra block
     */
    @NonNull
    private static byte[] makeExtraAlignmentBlock(int blockSize, int alignment) throws IOException {
        Preconditions.checkArgument(
                blockSize >= MINIMUM_EXTRA_FIELD_SIZE,
                "blockSize (" + blockSize + ") < MINIMUM_EXTRA_FIELD_SIZE");

        byte[] data = new byte[blockSize];
        ByteBuffer buffer = ByteBuffer.wrap(data);

        LittleEndianUtils.writeUnsigned2Le(buffer, ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID);
        LittleEndianUtils.writeUnsigned2Le(buffer, blockSize - 4);
        LittleEndianUtils.writeUnsigned2Le(buffer, alignment);

        /*
         * The rest is left filled with zeroes.
         */

        return data;
    }

    /**
     * Creates a virtual file.
     *
     * @param name the file name
     * @param totalSize the total size to be occupied by the file, including header
     * @return the created entry
     * @throws IOException failed to create the entry
     */
    private StoredEntry makeVirtual(@NonNull String name, long totalSize)
            throws IOException {
        Preconditions.checkArgument(totalSize > 0, "totalSize <= 0");

        boolean encodeWithUtf8 = !EncodeUtils.canAsciiEncode(name);
        GPFlags flags = GPFlags.make(encodeWithUtf8);
        byte[] encodedName = EncodeUtils.encode(name, flags);

        long contentsSize = totalSize - StoredEntry.FIXED_LOCAL_FILE_HEADER_SIZE
                - encodedName.length;
        if (contentsSize <= VIRTUAL_FILE_EXPLANATION.length()) {
            throw new IllegalArgumentException("Virtual file size '" + totalSize + "' is too "
                    + "small");
        }

        /*
         * We make use of the fact that under ASCII encoding, each character is one byte.
         */
        byte[] contents = Charsets.US_ASCII.encode(
                Strings.padEnd(
                    VIRTUAL_FILE_EXPLANATION,
                    Ints.checkedCast(contentsSize),
                    ' '))
                .array();
        Verify.verify(contents.length == contentsSize);

        return makeStoredEntry(name, new ByteArrayInputStream(contents), false);
    }
}
