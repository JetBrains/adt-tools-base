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

package com.android.builder.internal.packaging;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.res2.FileStatus;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;

import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Keeps track of incremental, renamed dex files.
 *
 * <p>Dex files need to be renamed when packaged. When a dex file is incrementally modified (added,
 * modified, deleted), it is necessary to incrementally propagate that modification to the package.
 *
 * <p>This class keeps a map of dex files and their new names in the archive. When a dex file
 * is incrementally modified, this class computes what the incremental change to the archive needs
 * to be, with respect to the dex file.
 *
 * <p>For example, if an archive is empty and file {@code a.dex} is added, then the manager will
 * say {@code classes.dex} needs to be added and {@code classes.dex} refers to {@code a.dex}.
 *
 * <p>If, later, archive {@code b.dex} is added, then the manager will say {@code classes2.dex}
 * needs to be added and {@code classes2.dex} refers to {@code b.dex}.
 *
 * <p>Then, if {@code a.dex} is removed, the manager will say {@code classes.dex} needs to be
 * updated and {@code classes.dex} now refers to {@code b.dex}.
 */
class DexIncrementalRenameManager implements Closeable {

    /**
     * Name of state file.
     */
    private static final String STATE_FILE = "dex-renamer-state.txt";

    /**
     * Prefix for property that has the base name of the relative file.
     */
    private static final String BASE_KEY_PREFIX = "base.";

    /**
     * Prefix for property that has the name of the relative file.
     */
    private static final String FILE_KEY_PREFIX = "file.";

    /**
     * Prefix for property that has the name of the renamed file.
     */
    private static final String RENAMED_KEY_PREFIX = "renamed.";

    /**
     * Mapping between relative files and file names.
     */
    @NonNull
    private final BiMap<RelativeFile, String> mNameMap;

    /**
     * Temporary directory to use to store and retrieve state.
     */
    @NonNull
    private final File mIncrementalDir;

    /**
     * Is the manager closed?
     */
    private boolean mClosed;

    /**
     * Creates a new rename manager.
     *
     * @param incrementalDir an incremental directory to store state.
     * @throws IOException failed to read incremental state
     */
    DexIncrementalRenameManager(@NonNull File incrementalDir) throws IOException {
        Preconditions.checkArgument(incrementalDir.isDirectory(), "!incrementalDir.isDirectory()");

        mNameMap = HashBiMap.create();
        mIncrementalDir = incrementalDir;
        mClosed = false;

        readState();
    }

    /**
     * Reads previously saved incremental state.
     *
     * @throws IOException failed to read state; not thrown if no state exists
     */
    private void readState() throws IOException {
        File stateFile = new File(mIncrementalDir, STATE_FILE);
        if (!stateFile.isFile()) {
            return;
        }

        Properties props = new Properties();
        Closer closer = Closer.create();
        try {
            props.load(closer.register(new FileReader(stateFile)));
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        for (int i = 0; ; i++) {
            String baseKey = BASE_KEY_PREFIX + i;
            String fileKey = FILE_KEY_PREFIX + i;
            String renamedKey = RENAMED_KEY_PREFIX + i;

            String base = props.getProperty(baseKey);
            String file = props.getProperty(fileKey);
            String rename = props.getProperty(renamedKey);

            if (base == null || file == null || rename == null) {
                break;
            }

            RelativeFile rf = new RelativeFile(new File(base), new File(file));
            mNameMap.put(rf, rename);
        }
    }

    /**
     * Writes incremental state.
     *
     * @throws IOException failed to write state
     */
    private void writeState() throws IOException {
        File stateFile = new File(mIncrementalDir, STATE_FILE);

        Properties props = new Properties();
        int currIdx = 0;
        for (BiMap.Entry<RelativeFile, String> entry : mNameMap.entrySet()) {
            props.put(BASE_KEY_PREFIX + currIdx, entry.getKey().getBase().getPath());
            props.put(FILE_KEY_PREFIX + currIdx, entry.getKey().getFile().getPath());
            props.put(RENAMED_KEY_PREFIX + currIdx, entry.getValue());
            currIdx++;
        }

        Closer closer = Closer.create();
        try {
            props.store(closer.register(new FileWriter(stateFile)), null);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    /**
     * Updates the state of the manager with file changes.
     *
     * @param files the files that have changed
     * @return the changed in the packaged files
     * @throws IOException failed to process the changes
     */
    @NonNull
    Set<PackagedFileUpdate> update(@NonNull ImmutableMap<RelativeFile, FileStatus> files)
            throws IOException {
        /*
         * This describes the algorithm to update the files. This algorithm:
         * - (1) Generates the minimal number of PackagedFileUpdates
         * - (2) Ensures that the data that results from making the updates does not contain any
         * gaps in the dex sequences as defined by DexFileNameSupplier.
         * - (3) If at least one of the input files is "classes.dex", that input file will be
         * mapped to "classes.dex".
         *
         * To explain the algorithm, we describe all steps and follow 3 different scenarios, whose
         * initial conditions are:
         * == Scenario S1 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex, FileC -> classes3.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         * == Scenario S2 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes3.dex, FileC -> classes3.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         * == Scenario S3 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *
         *
         * 1. We start by getting all names in the order defined by the DexFileNameSupplier and
         * put all names that are in the map in "nameList".
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex, FileC -> classes3.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex, FileC -> classes3.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         * == Scenario S3 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex ]
         *
         *
         * 2. For every deleted file in the set, we remove it from the name map and keep its
         * name in "deletedNames". Put the file/name map in "deletedFiles".
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileC -> classes3.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes.dex, classes2.dex ]
         *     - deletedFiles = { classes.dex -> FileA, classes2 -> FileB }
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes2.dex ]
         *     - deletedFiles = { classes2 -> FileB }
         * == Scenario S3 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *
         *
         * 3. For every added file in the set, we add it to newFiles. If any of the new files is
         * named "classes.dex" is it added to the beginning of newFiles and the addingClassesDex
         * is set to true.
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileC -> classes3.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes.dex, classes2.dex ]
         *     - deletedFiles = { classes.dex -> FileA, classes2 -> FileB }
         *     - newFiles = [ FileD ]
         *     - addingClassesDex = false
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes2.dex ]
         *     - deletedFiles = { classes2 -> FileB }
         *     - newFiles = [ FileD, FileE]
         *     - addingClassesDex = false
         * == Scenario S3 ==
         *     - mNameMap = { FileA -> classes.dex, FileB -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = [ classes.dex ]
         *     - addingClassesDex = true
         *
         *
         * 4.If addingClassesDex is true, mNameMap contains a mapping for classes.dex and the file
         * it is mapped from is not classes.dex, remove it from the mapping and add it to
         * newFiles. Also, add "classes.dex" to "deletedNames".
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileC -> classes3.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes.dex, classes2.dex ]
         *     - deletedFiles = { classes.dex -> FileA, classes2 -> FileB }
         *     - newFiles = [ FileD ]
         *     - addingClassesDex = false
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes2.dex ]
         *     - deletedFiles = { classes2 -> FileB }
         *     - newFiles = []
         *     - addingClassesDex = false
         * == Scenario S3 ==
         *     - mNameMap = { FileB -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex ]
         *     - deletedNames = [ classes.dex ]
         *     - deletedFiles = {}
         *     - newFiles = [ classes.dex, FileA ]
         *     - addingClassesDex = true
         *
         *
         * 5. For every added file in the set, we add it to the name map using names from
         * "deletedNames", if possible. If a name is used from "deletedNames", we remove it from
         * "deletedNames" and add it to "updatedNames". If no name is available in "deletedNames",
         * we fetch a new name and add it to "addedNames". If we need to fetch new names, we also
         * add them to "nameList". If we remove entries from "deletedNames", we also remove it
         * from "deletedFiles".
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileC -> classes3.dex, FileD -> classes.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes2.dex ]
         *     - deletedFiles = { classes2 -> FileB }
         *     - newFiles = [ FileD ]
         *     - addingClassesDex = false
         *     - updatedNames = { classes.dex }
         *     - addedNames = {}
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex, FileD -> classes2.dex,
          *                     FileE -> classes4.dex }
         *     - files = { FileB: removed, FileC: updated, FileD -> new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex, classes4.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = []
         *     - addingClassesDex = false
         *     - updatedNames = { classes2.dex }
         *     - addedNames = { classes4.dex }
         * == Scenario S3 ==
         *     - mNameMap = { FileB -> classes2.dex, classes.dex -> classes.dex,
         *          FileA -> classes3.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = [ classes.dex, FileA ]
         *     - addingClassesDex = true
         *     - updatedNames = { classes.dex }
         *     - addedNames = { classes3.dex }
         *
         *
         * 6. For every updated file in the set, we search for it in the name map
         * and add it to "updatedNames".
         *
         * == Scenario 1 ==
         *     - mNameMap = { FileC -> classes3.dex, FileD -> classes.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = [ classes2.dex ]
         *     - deletedFiles = { classes2 -> FileB }
         *     - newFiles = [ FileD ]
         *     - addingClassesDex = false
         *     - updatedNames = { classes.dex, classes3.dex }
         *     - addedNames = {}
         * == Scenario 2 ==
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex, FileD -> classes2.dex,
          *                     FileE -> classes4.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex, classes4.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = []
         *     - addingClassesDex = false
         *     - updatedNames = { classes2.dex, classes3.dex }
         *     - addedNames = { classes4.dex }
         * == Scenario S3 ==
         *     - mNameMap = { FileB -> classes2.dex, classes.dex -> classes.dex,
         *          FileA -> classes3.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = [ classes.dex, FileA ]
         *     - addingClassesDex = true
         *     - updatedNames = { classes.dex }
         *     - addedNames = { classes3.dex }
         *
         *
         * 7. Do one of the following:
         * 7.1. If "deletedNames" is empty, we end step 5.
         * 7.2. If the last item of "deletedNames" matches the last name in "nameList", we move it
         * to "finalDeletedNames". We also remove the last name in "nameList". Restart step 5.
         * 7.3. Do the following:
         * - Move the last entry in "nameList" to "finallyDeletedNames" and copy the corresponding
         * entry from mNameMap to deletedFiles.
         * - Rename the name of the file in "mNameMap" corresponding to the moved item of
         * "nameList" to the first position of "deletedNames".
         * - Move the name in the first position of "deletedNames" to "updatedNames".
         * - If the last item from "nameList" that was removed existed in "updatedNames", remove it
         * from "updatedNames".
         * - Restart step 7.
         *
         * == Scenario 1 ==
         *     (after executing 7.3 and then 7.1):
         *     - mNameMap = { FileC -> classes2.dex, FileD -> classes.dex }
         *     - files = { FileA: removed, FileB: removed, FileC: updated, FileD: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex ]
         *     - deletedNames = []
         *     - deletedFiles = { classes2 -> FileB, classes3.dex -> FileC }
         *     - newFiles = [ FileD ]
         *     - addingClassesDex = false
         *     - updatedNames = { classes.dex, classes2.dex }
         *     - addedNames = {}
         *     - finallyDeletedNames = { classes3.dex }
         * == Scenario 2 ==
         *     (after executing 7.1):
         *     - mNameMap = { FileA -> classes.dex, FileC -> classes3.dex, FileD -> classes2.dex,
         *                     FileE -> classes4.dex }
         *     - files = { FileB: removed, FileC: updated, FileD: new, FileE: new }
         *     - nameList = [ classes.dex, classes2.dex, classes3.dex, classes4.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = []
         *     - addingClassesDex = false
         *     - updatedNames = { classes2.dex, classes3.dex }
         *     - addedNames = { classes4.dex }
         *     - finallyDeletedNames = {}
         * == Scenario S3 ==
         *     (after executing 7.1):
         *     - mNameMap = { FileB -> classes2.dex, classes.dex -> classes.dex,
         *          FileA -> classes2.dex }
         *     - files = { classes.dex: new, FileB: updated }
         *     - nameList = [ classes.dex, classes2.dex ]
         *     - deletedNames = []
         *     - deletedFiles = {}
         *     - newFiles = [ classes.dex, FileA ]
         *     - addingClassesDex = true
         *     - updatedNames = { classes.dex }
         *     - addedNames = { classes3.dex }
         *
         * 8. Build the final list with the changes defined in "addedNames", "updatedNames" and
         * "finallyDeletedNames".
         */

        /*
         * Step 1.
         */
        Deque<String> nameList = Lists.newLinkedList();
        DexFileNameSupplier nameSupplier = new DexFileNameSupplier();
        for (int i = 0; i < mNameMap.size(); i++) {
            String nextName = nameSupplier.get();
            nameList.add(nextName);
            Verify.verify(mNameMap.containsValue(nextName), "mNameMap does not contain '"
                    + nextName + "', but has a total of " + mNameMap.size()
                    + " entries {mNameMap = " + mNameMap + "}");
        }

        /*
         * Step 2.
         *
         * Make sure that classes.dex, if it was removed, is the first in the deletedNames.
         */
        Deque<String> deletedNames = Lists.newLinkedList();
        Map<String, RelativeFile> deletedFiles = Maps.newHashMap();
        for (RelativeFile deletedRf :
                Maps.filterValues(files, Predicates.equalTo(FileStatus.REMOVED)).keySet()) {
            String deletedName = mNameMap.get(deletedRf);
            if (deletedName == null) {
                throw new IOException("Incremental update refers to relative file '" + deletedRf
                        + "' as deleted, but this file is not known.");
            }

            if (deletedName.equals(SdkConstants.FN_APK_CLASSES_DEX)) {
                deletedNames.addFirst(deletedName);
            } else {
                deletedNames.add(deletedName);
            }

            deletedFiles.put(deletedName, deletedRf);
            mNameMap.remove(deletedRf);
        }

        /*
         * Step 3.
         */
        AtomicBoolean addingClassesDex = new AtomicBoolean(false);
        Deque<RelativeFile> addedFiles = Lists.newLinkedList(
                Maps.filterValues(files, Predicates.equalTo(FileStatus.NEW)).keySet().stream()
                        .peek(rf -> {
                            if (getOsIndependentFileName(rf).equals(
                                    SdkConstants.FN_APK_CLASSES_DEX)) {
                                addingClassesDex.set(true);

                            }
                        })
                        .sorted(new DexNameComparator())
                        .collect(Collectors.toList()));

        /*
         * Step 4.
         */
        if (addingClassesDex.get()) {
            RelativeFile mappingToClassesDex =
                    mNameMap.inverse().get(SdkConstants.FN_APK_CLASSES_DEX);
            if (mappingToClassesDex != null) {
                if (!getOsIndependentFileName(mappingToClassesDex).equals(
                        SdkConstants.FN_APK_CLASSES_DEX)) {
                    /*
                     * If we get here is because we're adding a file named "classes.dex" and the
                     * current file that maps to "classes.dex" is not named "classes.dex". We
                     * prefer having "classes.dex" mapping to "classes.dex".
                     */
                    mNameMap.remove(mappingToClassesDex);
                    addedFiles.add(mappingToClassesDex);
                    deletedNames.add(SdkConstants.FN_APK_CLASSES_DEX);
                }
            }
        }

        /*
         * Step 5.
         */
        Set<String> addedNames = Sets.newHashSet();
        Set<String> updatedNames = Sets.newHashSet();
        Iterator<String> deletedNamesIterator = deletedNames.iterator();
        for (RelativeFile addedRf : addedFiles) {
            if (deletedNamesIterator.hasNext()) {
                String toUse = deletedNamesIterator.next();
                deletedNamesIterator.remove();
                deletedFiles.remove(toUse);
                updatedNames.add(toUse);
                mNameMap.put(addedRf, toUse);
            } else {
                String addedName = nameSupplier.get();
                addedNames.add(addedName);
                nameList.add(addedName);
                mNameMap.put(addedRf, addedName);
            }
        }

        /*
         * Step 6.
         */
        for (RelativeFile updatedRf :
                Maps.filterValues(files, Predicates.equalTo(FileStatus.CHANGED)).keySet()) {
            String updatedName = mNameMap.get(updatedRf);
            if (updatedName == null) {
                throw new IOException("Incremental update refers to relative file '" + updatedRf
                        + "' as updated, but this file is not known.");
            }

            updatedNames.add(updatedName);
        }

        /*
         * Step 7.
         */
        Set<String> finallyDeletedNames = Sets.newHashSet();
        while (true) {
            /*
             * Step 7.1.
             */
            if (deletedNames.isEmpty()) {
                break;
            }

            /*
             * Step 7.2.
             */
            if (deletedNames.getLast().equals(nameList.getLast())) {
                nameList.removeLast();
                finallyDeletedNames.add(deletedNames.removeLast());
                continue;
            }

            /*
             * Step 7.3.
             */
            String lastInNames = nameList.removeLast();
            String firstInDeleted = deletedNames.remove();

            finallyDeletedNames.add(lastInNames);
            updatedNames.remove(lastInNames);
            updatedNames.add(firstInDeleted);

            RelativeFile file = mNameMap.inverse().get(lastInNames);
            Verify.verifyNotNull(file, "file == null");
            mNameMap.put(file, firstInDeleted);
            deletedFiles.put(lastInNames, file);
        }

        /*
         * Step 8.
         */
        Set<PackagedFileUpdate> updates = Sets.newHashSet();
        for (String addedName : addedNames) {
            RelativeFile file = Verify.verifyNotNull(mNameMap.inverse().get(addedName));
            updates.add(new PackagedFileUpdate(file, addedName, FileStatus.NEW));
        }

        for (String updatedName : updatedNames) {
            RelativeFile file = Verify.verifyNotNull(mNameMap.inverse().get(updatedName));
            updates.add(new PackagedFileUpdate(file, updatedName, FileStatus.CHANGED));

        }

        for (String deletedName : finallyDeletedNames) {
            RelativeFile file = Verify.verifyNotNull(deletedFiles.get(deletedName));
            updates.add(new PackagedFileUpdate(file, deletedName, FileStatus.REMOVED));
        }

        /*
         * Phew! We're done! Yey!
         */
        return updates;
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }

        mClosed = true;
        writeState();
    }

    /**
     * Obtains the file name in the OS-independent relative path in a relative file.
     *
     * @param file the file, <i>e.g.</i>, {@code foo/bar}
     * @return the file name, <i>e.g.</i>, {@code bar}
     */
    @NonNull
    private static String getOsIndependentFileName(@NonNull RelativeFile file) {
        String[] pathSplit = file.getOsIndependentRelativePath().split("/");
        return pathSplit[pathSplit.length - 1];
    }

    /**
     * Comparator that compares dex file names placing classes.dex always in front.
     */
    private static class DexNameComparator implements Comparator<RelativeFile> {

        @Override
        public int compare(RelativeFile f1, RelativeFile f2) {
            String s1 = f1.getOsIndependentRelativePath();
            String s2 = f2.getOsIndependentRelativePath();

            if (s1.equals(SdkConstants.FN_APK_CLASSES_DEX)) {
                return -1;
            } else if (s2.equals(SdkConstants.FN_APK_CLASSES_DEX)) {
                return 1;
            } else {
                return s1.compareTo(s2);
            }
        }
    }

}
