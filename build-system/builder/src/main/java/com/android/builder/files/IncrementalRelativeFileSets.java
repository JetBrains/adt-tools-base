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
     * status of each file is set to the provided status. This method is used to construct an
     * initial set of files and is, therefore an incremental update from zero.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull File zip)
            throws IOException {
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

        Map<RelativeFile, FileStatus> map = Maps.asMap(files, Functions.constant(FileStatus.NEW));
        return ImmutableMap.copyOf(map);
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
            @NonNull Set<ImmutableMap<RelativeFile, FileStatus>> sets) {
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
}
