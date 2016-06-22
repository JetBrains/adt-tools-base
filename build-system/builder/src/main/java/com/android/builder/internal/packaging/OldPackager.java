/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.FN_APK_CLASSES_DEX;
import static com.android.SdkConstants.FN_APK_CLASSES_N_DEX;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.files.FileModificationType;
import com.android.builder.files.RelativeFile;
import com.android.builder.packaging.ApkCreator;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.builder.signing.SignedJarApkCreatorFactory;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * This is the old implementation of the {@link IncrementalPackager} class maintained to allow
 * using the old packaging code in case the new one is not usable in some scenario that was not
 * tested. This class will eventually be removed in a future version (along with all other old
 * packaging code).
 *
 * <p>Previous documentation:
 *
 * <p>Class making the final app package.
 * The inputs are:
 * - packaged resources (output of aapt)
 * - code file (output of dx)
 * - Java resources coming from the project, its libraries, and its jar files
 * - Native libraries from the project or its library.
 *
 * @deprecated the {@link IncrementalPackager} should be used from now one that allows both full
 * and incremental packaging.
 */
@Deprecated
public final class OldPackager implements Closeable {

    /**
     * Filter to detect duplicate entries
     *
     */
    private final class DuplicateZipFilter implements ZipEntryFilter {
        private File mInputFile;

        void reset(File inputFile) {
            mInputFile = inputFile;
        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            mLogger.verbose("=> %s", archivePath);

            File duplicate = checkFileForDuplicate(archivePath);
            if (duplicate != null) {
                // we have a duplicate but it might be the same source file, in this case,
                // we just ignore the duplicate, and of course, we don't add it again.
                File potentialDuplicate = new File(mInputFile, archivePath);
                if (!duplicate.getAbsolutePath().equals(potentialDuplicate.getAbsolutePath())) {
                    throw new DuplicateFileException(archivePath, duplicate, mInputFile);
                }
                return false;
            } else {
                mAddedFiles.put(archivePath, mInputFile);
            }

            return true;
        }
    }

    /**
     * A filter to filter out binary files like .class
     */
    private static final class NoJavaClassZipFilter implements ZipEntryFilter {
        @NonNull
        private final ZipEntryFilter parentFilter;

        private NoJavaClassZipFilter(@NonNull ZipEntryFilter parentFilter) {
            this.parentFilter = parentFilter;
        }


        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return parentFilter.checkEntry(archivePath) && !archivePath.endsWith(DOT_CLASS);
        }
    }

    /**
     * APK creator. {@code null} if not open.
     */
    @Nullable
    private ApkCreator mApkCreator;

    private final ILogger mLogger;

    private final DuplicateZipFilter mNoDuplicateFilter = new DuplicateZipFilter();
    private final NoJavaClassZipFilter mNoJavaClassZipFilter = new NoJavaClassZipFilter(
            mNoDuplicateFilter);
    private final HashMap<String, File> mAddedFiles = new HashMap<String, File>();

    /**
     * Creates a new instance.
     *
     * <p>This creates a new builder that will create the specified output file.
     *
     * @param creationData APK creation data
     * @param resLocation location of the zip with the resources, if any
     * @param logger the logger
     * @throws PackagerException failed to create the initial APK
     * @throws IOException failed to create the APK
     */
    public OldPackager(@NonNull ApkCreatorFactory.CreationData creationData,
            @Nullable String resLocation,
            @NonNull ILogger logger) throws PackagerException, IOException {
        checkOutputFile(creationData.getApkPath());

        Closer closer = Closer.create();
        try {
            checkOutputFile(creationData.getApkPath());

            File resFile = null;
            if (resLocation != null) {
                resFile = new File(resLocation);
                checkInputFile(resFile);
            }

            mLogger = logger;

            ApkCreatorFactory factory = new SignedJarApkCreatorFactory();

            mApkCreator = factory.make(creationData);

            mLogger.verbose("Packaging %s", creationData.getApkPath().getName());

            // add the resources
            if (resFile != null) {
                addZipFile(resFile);
            }

        } catch (Throwable e) {
            closer.register(mApkCreator);
            mApkCreator = null;
            throw closer.rethrow(e, PackagerException.class);
        } finally {
            closer.close();
        }
    }

    public void addDexFiles(@NonNull Set<File> dexFolders) throws PackagerException, IOException {
        Preconditions.checkNotNull(mApkCreator, "mApkCreator == null");

        // If there is a single folder that's either no multi-dex or pre-21 multidex (where
        // dx has merged them all into 2+ dex files).
        // IF there are 2+ folders then we are directly adding the pre-dexing output.
        if (dexFolders.size() == 1 ) {
            File[] dexFiles = Iterables.getOnlyElement(dexFolders).listFiles(
                    new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String name) {
                            return name.endsWith(SdkConstants.DOT_DEX);
                        }
                    });

            if (dexFiles != null) {
                for (File dexFile : dexFiles) {
                    addFile(dexFile, dexFile.getName());
                }
            }
        } else {
            // in 21+ mode we can simply include all the dex files, and rename them as we
            // go so that their indices are contiguous.
            int dexIndex = 1;
            for (File folderEntry : dexFolders) {
                dexIndex = addContentOfDexFolder(folderEntry, dexIndex);
            }
        }
    }

    private int addContentOfDexFolder(@NonNull File dexFolder, int dexIndex)
            throws PackagerException, IOException {
        File[] dexFiles = dexFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith(SdkConstants.DOT_DEX);
            }
        });

        if (dexFiles != null) {
            for (File dexFile : dexFiles) {
                addFile(dexFile,
                        dexIndex == 1 ?
                                FN_APK_CLASSES_DEX :
                                String.format(FN_APK_CLASSES_N_DEX, dexIndex));
                dexIndex++;
            }
        }

        return dexIndex;
    }

    /**
     * Adds a file to the APK at a given path.
     *
     * @param file the file to add
     * @param archivePath the path of the file inside the APK archive.
     * @throws PackagerException if an error occurred
     * @throws IOException if an error occurred
     */
    public void addFile(File file, String archivePath) throws PackagerException, IOException {
        Preconditions.checkState(mApkCreator != null, "mApkCreator == null");

        doAddFile(file, archivePath);
    }

    /**
     * Adds the content from a zip file.
     * All file keep the same path inside the archive.
     *
     * @param zipFile the zip File.
     * @throws PackagerException if an error occurred
     * @throws IOException if an error occurred
     */
    void addZipFile(File zipFile) throws PackagerException, IOException {
        Preconditions.checkState(mApkCreator != null, "mApkCreator == null");

        mLogger.verbose("%s:", zipFile);

        // reset the filter with this input.
        mNoDuplicateFilter.reset(zipFile);

        /*
         * Predicate does not allow exceptions so we mask the ZipAbortException inside a
         * RuntimeException.
         */
        try {
            // ask the builder to add the content of the file.
            mApkCreator.writeZip(zipFile, null, new Predicate<String>() {
                @Override
                public boolean apply(String input) {
                    try {
                        return !mNoDuplicateFilter.checkEntry(input);
                    } catch (ZipAbortException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ZipAbortException) {
                throw (ZipAbortException) e.getCause();
            }

            throw e;
        }
    }

    /**
     * Incrementally updates a resource in the packaging. The resource can be added or removed,
     * depending on the change made to the file.
     *
     * @param file the file to update
     * @param modificationType the type of file modification
     * @throws PackagerException failed to update the package
     */
    public void updateResource(@NonNull RelativeFile file,
            @NonNull FileModificationType modificationType) throws PackagerException {
        if (modificationType == FileModificationType.NEW
                || modificationType == FileModificationType.CHANGED) {
            doAddFile(file.getFile(), file.getOsIndependentRelativePath());
        } else {
            throw new UnsupportedOperationException("Cannot remove a file from archive.");
        }
    }

    /**
     * Incrementally updates resources in the packaging. The resources can be added or removed,
     * depending on the changes made to the file. Updating an archive file as modified will update
     * the entries, but will not remove archive entries tht are no longer in the archive.
     *
     * @param file the archive file (zip)
     * @param modificationType the type of file modification
     * @param isIgnored the filter to apply to the contents of the archive; the filter is applied
     * before processing: filtered out files are exactly the same as inexistent files; the filter
     * applies to the path stored in the zip
     * @throws PackagerException failed to update the package
     */
    public void updateResourceArchive(@NonNull File file,
            @NonNull FileModificationType modificationType,
            @NonNull final Predicate<String> isIgnored) throws PackagerException {
        Preconditions.checkNotNull(mApkCreator, "mApkCreator == null");

        if (modificationType == FileModificationType.NEW
                || modificationType == FileModificationType.CHANGED) {
            try {
                Closer closer = Closer.create();
                try {
                    /*
                     * Note that ZipAbortException has to be masked because it is not allowed in
                     * the Predicate interface.
                     */
                    Predicate<String> newIsIgnored = new Predicate<String>() {
                        @Override
                        public boolean apply(String input) {
                            try {
                                if (!mNoJavaClassZipFilter.checkEntry(input)) {
                                    return true;
                                }
                            } catch (ZipAbortException e) {
                                throw new RuntimeException(e);
                            }

                            return isIgnored.apply(input);
                        }
                    };

                    mApkCreator.writeZip(file, null, newIsIgnored);
                } catch (Throwable t) {
                    throw closer.rethrow(t, ZipAbortException.class);
                } finally {
                    closer.close();
                }
            } catch (IOException e) {
                throw new PackagerException(e);
            }
        }
    }

    private void doAddFile(
            @NonNull File file,
            @NonNull String archivePath) throws PackagerException {
        Preconditions.checkNotNull(mApkCreator, "mApkCreator == null");

        mAddedFiles.put(archivePath, file);

        try {
            mApkCreator.writeFile(file, archivePath);
        } catch (IOException e) {
            throw new PackagerException(e);
        }
    }

    /**
     * Checks if the given path in the APK archive has not already been used and if it has been,
     * then returns a {@link File} object for the source of the duplicate
     * @param archivePath the archive path to test.
     * @return A File object of either a file at the same location or an archive that contains a
     * file that was put at the same location.
     */
    private File checkFileForDuplicate(String archivePath) {
        return mAddedFiles.get(archivePath);
    }

    /**
     * Checks an output {@link File} object.
     * This checks the following:
     * - the file is not an existing directory.
     * - if the file exists, that it can be modified.
     * - if it doesn't exists, that a new file can be created.
     * @param file the File to check
     * @throws PackagerException If the check fails
     */
    private static void checkOutputFile(File file) throws PackagerException {
        if (file.isDirectory()) {
            throw new PackagerException("%s is a directory!", file);
        }

        if (file.exists()) { // will be a file in this case.
            if (!file.canWrite()) {
                throw new PackagerException("Cannot write %s", file);
            }
        } else {
            try {
                if (!file.createNewFile()) {
                    throw new PackagerException("Failed to create %s", file);
                }

                /*
                 * We succeeded at creating the file. Now, delete it because a zero-byte file is
                 * not a valid APK and some ApkCreator implementations (e.g., the ZFile one)
                 * complain if open on top of an invalid zip file.
                 */
                if (!file.delete()) {
                    throw new PackagerException("Failed to delete newly created %s", file);
                }
            } catch (IOException e) {
                throw new PackagerException(
                        "Failed to create '%1$ss': %2$s", file, e.getMessage());
            }
        }
    }

    /**
     * Checks an input {@link File} object.
     * This checks the following:
     * - the file is not an existing directory.
     * - that the file exists and can be read.
     * @param file the File to check
     * @throws FileNotFoundException if the file is not here.
     * @throws PackagerException If the file is a folder or a file that cannot be read.
     */
    private static void checkInputFile(File file) throws FileNotFoundException, PackagerException {
        if (file.isDirectory()) {
            throw new PackagerException("%s is a directory!", file);
        }

        if (file.exists()) {
            if (!file.canRead()) {
                throw new PackagerException("Cannot read %s", file);
            }
        } else {
            throw new FileNotFoundException(String.format("%s does not exist", file));
        }
    }

    public static String getLocalVersion() {
        Class clazz = IncrementalPackager.class;
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            // Class not from JAR, unlikely
            return null;
        }
        try {
            String manifestPath = classPath.substring(0, classPath.lastIndexOf('!') + 1) +
                    "/META-INF/MANIFEST.MF";

            URLConnection jarConnection = new URL(manifestPath).openConnection();
            jarConnection.setUseCaches(false);
            InputStream jarInputStream = jarConnection.getInputStream();
            Attributes attr = new Manifest(jarInputStream).getMainAttributes();
            jarInputStream.close();
            return attr.getValue("Builder-Version");
        } catch (MalformedURLException ignored) {
        } catch (IOException ignored) {
        }

        return null;
    }

    @Override
    public void close() throws IOException {
        if (mApkCreator == null) {
            return;
        }

        ApkCreator builder = mApkCreator;
        mApkCreator = null;
        builder.close();
    }
}
