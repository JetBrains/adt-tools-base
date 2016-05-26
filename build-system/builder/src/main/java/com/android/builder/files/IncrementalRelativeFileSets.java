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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.StoredEntryType;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for incremental relative file sets, immutable maps of relative files to status.
 */
public final class IncrementalRelativeFileSets {

    /**
     * Utility class: no visible constructor.
     */
    private IncrementalRelativeFileSets() {
        /*
         * Nothing to do.
         */
    }

    /**
     * Reads a directory and adds all files in the directory in a new incremental relative set.
     * The status of each file is set to {@link FileStatus#NEW}. This method is used to construct
     * an initial set of files and is, therefore, an incremental update from zero.
     *
     * @param directory the directory, must be an existing directory
     * @return the file set
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromDirectory(@NonNull File directory) {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");
        Set<RelativeFile> files = RelativeFiles.fromDirectory(directory);
        files = Sets.filter(files, Predicates.compose(Files.isFile(), RelativeFile.EXTRACT_FILE));
        Map<RelativeFile, FileStatus> map = Maps.asMap(files, Functions.constant(FileStatus.NEW));
        return ImmutableMap.copyOf(map);
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The
     * status of each file is set to {@link FileStatus#NEW}. This method is used to construct an
     * initial set of files and is, therefore, an incremental update from zero.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull File zip)
            throws IOException {
        return fromZip(zip, FileStatus.NEW);
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The
     * status of each file is set to {@code status}.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @param status the status to set the files to
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull File zip,
            FileStatus status) throws IOException {
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

        Set<RelativeFile> files = Sets.newHashSet();

        Closer closer = Closer.create();
        try {
            ZFile zipReader = closer.register(new ZFile(zip));
            for (StoredEntry entry : zipReader.entries()) {
                if (entry.getType() == StoredEntryType.FILE) {
                    File file = new File(zip, FileUtils.toSystemDependentPath(
                            entry.getCentralDirectoryHeader().getName()));
                    files.add(new RelativeFile(zip, file));
                }
            }
        } catch (Throwable t) {
            throw closer.rethrow(t, IOException.class);
        } finally {
            closer.close();
        }

        Map<RelativeFile, FileStatus> map = Maps.asMap(files, Functions.constant(status));
        return ImmutableMap.copyOf(map);
    }

    /**
     * Computes the incremental file set that results from comparing a zip file with a possibly
     * existing cached file. If the cached file does not exist, then the whole zip is reported
     * as {@link FileStatus#NEW}.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull File zip,
            @NonNull FileCacheByPath cache) throws IOException {
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

        File oldFile = cache.get(zip);
        if (oldFile == null) {
            return fromZip(zip, FileStatus.NEW);
        }

        Map<RelativeFile, FileStatus> result = Maps.newHashMap();

        Closer closer = Closer.create();
        try {
            ZFile newZipReader = closer.register(new ZFile(zip));
            ZFile oldZipReader = closer.register(new ZFile(oldFile));

            /*
             * Search for new and modified files.
             */
            for (StoredEntry entry : newZipReader.entries()) {
                String path = entry.getCentralDirectoryHeader().getName();
                if (entry.getType() == StoredEntryType.FILE) {
                    File file = new File(zip, FileUtils.toSystemDependentPath(path));
                    RelativeFile newRelative = new RelativeFile(zip, file);

                    StoredEntry oldEntry = oldZipReader.get(path);
                    if (oldEntry == null || oldEntry.getType() != StoredEntryType.FILE) {
                        result.put(newRelative, FileStatus.NEW);
                        continue;
                    }

                    if (oldEntry.getCentralDirectoryHeader().getCrc32()
                            != entry.getCentralDirectoryHeader().getCrc32()
                            || oldEntry.getCentralDirectoryHeader().getUncompressedSize()
                            != entry.getCentralDirectoryHeader().getUncompressedSize()) {
                        result.put(newRelative, FileStatus.CHANGED);
                    }

                    /*
                     * If we get here, then the file exists in both unmodified.
                     */
                }
            }

            for (StoredEntry entry : oldZipReader.entries()) {
                String path = entry.getCentralDirectoryHeader().getName();
                File file = new File(zip, FileUtils.toSystemDependentPath(path));
                RelativeFile oldRelative = new RelativeFile(zip, file);

                StoredEntry newEntry = newZipReader.get(path);
                if (newEntry == null || newEntry.getType() != StoredEntryType.FILE) {
                    /*
                     * File does not exist in new. It has been deleted.
                     */
                    result.put(oldRelative, FileStatus.REMOVED);
                }

            }
        } catch (Throwable t) {
            throw closer.rethrow(t, IOException.class);
        } finally {
            closer.close();
        }

        return ImmutableMap.copyOf(result);
    }

    /**
     * Computes the incremental relative file set that is the union of all provided sets. If a
     * relative file exists in more than one set, one of the files will exist in the union set,
     * but which one is not defined.
     *
     * @param sets the sets to union
     * @return the result of the union
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> union(
            @NonNull Iterable<ImmutableMap<RelativeFile, FileStatus>> sets) {
        Map<RelativeFile, FileStatus> map = Maps.newHashMap();
        for (ImmutableMap<RelativeFile, FileStatus> set : sets) {
            map.putAll(set);
        }

        return ImmutableMap.copyOf(map);
    }

    /**
     * Counts how many different base directories are there in a relative file set. This method will
     * look at the base of every {@link RelativeFile} and count how many distinct bases are that
     * that are directories.
     *
     * @param set the file set
     * @return the number of distinct base directories
     */
    public static int getBaseDirectoryCount(@NonNull ImmutableMap<RelativeFile, FileStatus> set) {
        return Sets.newHashSet(
                Iterables.filter(
                        Iterables.transform(
                                set.keySet(),
                                RelativeFile.EXTRACT_BASE),
                        Files.isDirectory()))
                .size();
    }

    /**
     * Reads files and builds an incremental relative file set. Each individual file in
     * {@code files} may be a file or directory. If it is a directory, then all files in the
     * directory are added as if {@link #fromDirectory(File)} had been invoked; if it is a file,
     * then it is assumed to be a zip file and all files in the zip are added as if
     * {@link #fromZip(File)} had been invoked.
     *
     * <p>The status of each file is set to {@link FileStatus#NEW}. This method is used to construct
     * an initial set of files and is, therefore, an incremental update from zero.
     *
     * @param files the files and directories
     * @return the file set
     * @throws IOException failed to read the files
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZipsAndDirectories(
            @NonNull Iterable<File> files) throws IOException {

        Set<ImmutableMap<RelativeFile, FileStatus>> sets = Sets.newHashSet();
        for (File f : files) {
            if (f.isFile()) {
                sets.add(fromZip(f));
            } else {
                sets.add(fromDirectory(f));
            }
        }

        return union(sets);
    }

    /**
     * Builds an incremental relative file set from a set of modified files. If the modified
     * file is in a base directory, then it is placed as a relative file in the resulting data set.
     * If the modified file is itself a base file, then it is treated as a zip file.
     *
     * <p>If there are new zip files, then all files in the zip are added to the result data set.
     * If there are deleted or updated zips, then the relative incremental changes are added to the
     * data set. To allow detecting incremental changes in zips, the provided cache is used.
     *
     * @param baseFiles the files; all entries must exist and be either directories or zip files
     * @param updates the files updated in the directories or base zip files updated
     * @param cache the file cache where to find old versions of zip files
     * @return the data
     * @throws IOException failed to read a zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> makeFromBaseFiles(
            @NonNull Collection<File> baseFiles,
            @NonNull Map<File, FileStatus> updates,
            @NonNull FileCacheByPath cache)
            throws IOException {
        for (File f : baseFiles) {
            Preconditions.checkArgument(f.exists(), "!f.exists()");
        }

        Map<RelativeFile, FileStatus> relativeUpdates = Maps.newHashMap();
        for (Map.Entry<File, FileStatus> fileUpdate : updates.entrySet()) {
            File file = fileUpdate.getKey();
            FileStatus status = fileUpdate.getValue();

            if (baseFiles.contains(file)) {
                /*
                 * If the file exists in the set of base files, assume it is a zip file.
                 */
                switch (status) {
                    case CHANGED:
                        relativeUpdates.putAll(IncrementalRelativeFileSets.fromZip(file, cache));
                        break;
                    case NEW:
                        relativeUpdates.putAll(
                                IncrementalRelativeFileSets.fromZip(file, FileStatus.NEW));
                        break;
                    case REMOVED:
                        relativeUpdates.putAll(
                                IncrementalRelativeFileSets.fromZip(file, FileStatus.REMOVED));
                        break;
                }
            } else {
                /*
                 * If the file does not exist in the set of base files, assume it is a file in a
                 * directory. If we don't find the base directory, ignore it.
                 */
                File possibleBaseDirectory = file.getParentFile();
                while (possibleBaseDirectory != null) {
                    if (baseFiles.contains(possibleBaseDirectory)) {
                        relativeUpdates.put(new RelativeFile(possibleBaseDirectory, file), status);
                        break;
                    }

                    possibleBaseDirectory = possibleBaseDirectory.getParentFile();
                }
            }
        }

        return ImmutableMap.copyOf(relativeUpdates);
    }
}
