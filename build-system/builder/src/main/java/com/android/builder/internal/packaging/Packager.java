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
import com.android.builder.internal.packaging.JavaResourceProcessor.IArchiveBuilder;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.builder.signing.SignedJarBuilder;
import com.android.builder.signing.SignedJarBuilder.IZipEntryFilter;
import com.android.ide.common.signing.CertificateInfo;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class making the final app package.
 * The inputs are:
 * - packaged resources (output of aapt)
 * - code file (ouput of dx)
 * - Java resources coming from the project, its libraries, and its jar files
 * - Native libraries from the project or its library.
 *
 */
public final class Packager implements IArchiveBuilder {

    /**
     * Filter to detect duplicate entries
     *
     */
    private final class DuplicateZipFilter implements IZipEntryFilter {
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
    private static final class NoJavaClassZipFilter implements IZipEntryFilter {
        @NonNull
        private final IZipEntryFilter parentFilter;

        private NoJavaClassZipFilter(@NonNull IZipEntryFilter parentFilter) {
            this.parentFilter = parentFilter;
        }


        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return parentFilter.checkEntry(archivePath) && !archivePath.endsWith(DOT_CLASS);
        }
    }

    /**
     * A filter to filter out unwanted ABIs.
     */
    private static final class NativeLibZipFilter implements IZipEntryFilter {
        @NonNull
        private final IZipEntryFilter parentFilter;
        @NonNull
        private final Set<String> acceptedAbis;
        private final boolean mJniDebugMode;

        private final Pattern mAbiPattern = Pattern.compile("lib/([^/]+)/[^/]+");
        private final Pattern mFilenamePattern = Pattern.compile(".*\\.so");

        private NativeLibZipFilter(
                @NonNull Set<String> acceptedAbis,
                @NonNull IZipEntryFilter parentFilter,
                boolean jniDebugMode) {
            this.acceptedAbis = acceptedAbis;
            this.parentFilter = parentFilter;
            this.mJniDebugMode = jniDebugMode;
        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            if (!parentFilter.checkEntry(archivePath)) {
                return false;
            }

            // extract abi from path and convert.
            Matcher m = mAbiPattern.matcher(archivePath);

            // if the ABI is accepted, check the 3rd segment
            if (m.matches() && (acceptedAbis.isEmpty() || acceptedAbis.contains(m.group(1)))) {
                // remove the beginning of the path (lib/<abi>/)
                String filename = archivePath.substring(5 + m.group(1).length());
                return mFilenamePattern.matcher(filename).matches() ||
                        (mJniDebugMode &&
                                (SdkConstants.FN_GDBSERVER.equals(filename) ||
                                        SdkConstants.FN_GDB_SETUP.equals(filename)));
            }

            return false;
        }
    }

    private SignedJarBuilder mBuilder = null;
    private final ILogger mLogger;
    private boolean mJniDebugMode = false;
    private boolean mIsSealed = false;

    private final DuplicateZipFilter mNoDuplicateFilter = new DuplicateZipFilter();
    private final NoJavaClassZipFilter mNoJavaClassZipFilter = new NoJavaClassZipFilter(mNoDuplicateFilter);
    private final HashMap<String, File> mAddedFiles = new HashMap<String, File>();

    /**
     * Creates a new instance.
     *
     * This creates a new builder that will create the specified output file, using the two
     * mandatory given input files.
     *
     * An optional debug keystore can be provided. If set, it is expected that the store password
     * is 'android' and the key alias and password are 'androiddebugkey' and 'android'.
     *
     * An optional {@link ILogger} can also be provided for verbose output. If null, there will
     * be no output.
     *
     * @param apkLocation the file to create
     * @param resLocation the file representing the packaged resource file.
     * @param certificateInfo the signing information used to sign the package. Optional the OS path to the debug keystore, if needed or null.
     * @param logger the logger.
     * @throws com.android.builder.packaging.PackagerException
     */
    public Packager(
            @NonNull String apkLocation,
            @NonNull String resLocation,
            CertificateInfo certificateInfo,
            @Nullable String createdBy,
            ILogger logger) throws PackagerException {

        try {
            File apkFile = new File(apkLocation);
            checkOutputFile(apkFile);

            File resFile = new File(resLocation);
            checkInputFile(resFile);

            mLogger = logger;

            mBuilder = new SignedJarBuilder(
                    new FileOutputStream(apkFile, false /* append */),
                    certificateInfo != null ? certificateInfo.getKey() : null,
                    certificateInfo != null ? certificateInfo.getCertificate() : null,
                    getLocalVersion(),
                    createdBy);

            mLogger.verbose("Packaging %s", apkFile.getName());

            // add the resources
            addZipFile(resFile);

        } catch (PackagerException e) {
            if (mBuilder != null) {
                mBuilder.cleanUp();
            }
            throw e;
        } catch (Exception e) {
            if (mBuilder != null) {
                mBuilder.cleanUp();
            }
            throw new PackagerException(e);
        }
    }

    public void addDexFiles(@NonNull Set<File> dexFolders)
            throws DuplicateFileException, SealedPackageException, PackagerException {
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
            throws PackagerException, SealedPackageException, DuplicateFileException {
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
     * Sets the JNI debug mode. In debug mode, when native libraries are present, the packaging
     * will also include one or more copies of gdbserver in the final APK file.
     *
     * These are used for debugging native code, to ensure that gdbserver is accessible to the
     * application.
     *
     * There will be one version of gdbserver for each ABI supported by the application.
     *
     * the gbdserver files are placed in the libs/abi/ folders automatically by the NDK.
     *
     * @param jniDebugMode the jni-debug mode flag.
     */
    public void setJniDebugMode(boolean jniDebugMode) {
        mJniDebugMode = jniDebugMode;
    }

    /**
     * Adds a file to the APK at a given path
     * @param file the file to add
     * @param archivePath the path of the file inside the APK archive.
     * @throws PackagerException if an error occurred
     * @throws com.android.builder.packaging.SealedPackageException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     */
    @Override
    public void addFile(File file, String archivePath) throws PackagerException,
            SealedPackageException, DuplicateFileException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        try {
            doAddFile(file, archivePath, null);
        } catch (DuplicateFileException e) {
            mBuilder.cleanUp();
            throw e;
        } catch (Exception e) {
            mBuilder.cleanUp();
            throw new PackagerException(e, "Failed to add %s", file);
        }
    }

    /**
     * Adds the content from a zip file.
     * All file keep the same path inside the archive.
     * @param zipFile the zip File.
     * @throws PackagerException if an error occurred
     * @throws SealedPackageException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     */
    void addZipFile(File zipFile) throws PackagerException, SealedPackageException,
            DuplicateFileException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        FileInputStream fis = null;
        try {
            mLogger.verbose("%s:", zipFile);

            // reset the filter with this input.
            mNoDuplicateFilter.reset(zipFile);

            // ask the builder to add the content of the file.
            fis = new FileInputStream(zipFile);
            mBuilder.writeZip(fis, mNoDuplicateFilter, null /* ZipEntryExtractor */);
        } catch (DuplicateFileException e) {
            mBuilder.cleanUp();
            throw e;
        } catch (Exception e) {
            mBuilder.cleanUp();
            throw new PackagerException(e, "Failed to add %s", zipFile);
        } finally {
            try {
                Closeables.close(fis, true /* swallowIOException */);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Adds all resources from a merged folder or jar file. There cannot be any duplicates and all
     * files present must be added unless it is a "binary" file like a .class or .dex (jack
     * produces the classes.dex in the same location as the obfuscated resources).
     * @param jarFileOrDirectory a jar file or directory reference.
     * @throws PackagerException could not add an entry to the package.
     * @throws DuplicateFileException if an entry with the same name was already present in the
     * package being built while adding the jarFileOrDirectory content.
     */
    public void addResources(@NonNull File jarFileOrDirectory)
            throws PackagerException, DuplicateFileException, SealedPackageException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        mNoDuplicateFilter.reset(jarFileOrDirectory);
        InputStream fis = null;
        try {
            if (jarFileOrDirectory.isDirectory()) {
                addResourcesFromDirectory(jarFileOrDirectory, "");
            } else {
                fis = new BufferedInputStream(new FileInputStream(jarFileOrDirectory));
                mBuilder.writeZip(fis, mNoJavaClassZipFilter, null /* ZipEntryExtractor */);
            }
        } catch (DuplicateFileException e) {
            mBuilder.cleanUp();
            throw e;
        } catch (Exception e) {
            mBuilder.cleanUp();
            throw new PackagerException(e, "Failed to add %s", jarFileOrDirectory);
        } finally {
            try {
                if (fis != null) {
                    Closeables.close(fis, true /* swallowIOException */);
                }
            } catch (IOException e) {
                // ignore.
            }
        }
    }

    private void addResourcesFromDirectory(@NonNull File directory, String path)
            throws IOException, IZipEntryFilter.ZipAbortException {
        File[] directoryFiles = directory.listFiles();
        if (directoryFiles == null) {
            return;
        }
        for (File file : directoryFiles) {
            String entryName = path.isEmpty() ? file.getName() : path + "/" + file.getName();
            if (file.isDirectory()) {
                addResourcesFromDirectory(file, entryName);
            } else {
                doAddFile(file, entryName, null);
            }
        }
    }

    /**
     * Adds the native libraries from a directory or jar file.
     *
     * The content must be the various ABI folders.
     *
     * This may or may not copy gdbserver into the apk based on whether the debug mode is set.
     *
     * @param jarFileOrDirectory a jar file or directory reference.
     * @param abiFilters a list of abi filters to include. If empty, all abis are included.
     *
     * @throws PackagerException if an error occurred
     * @throws SealedPackageException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     *
     * @see #setJniDebugMode(boolean)
     */
    public void addNativeLibraries(
            @NonNull File jarFileOrDirectory,
            @NonNull Set<String> abiFilters)
            throws PackagerException, SealedPackageException, DuplicateFileException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        mLogger.verbose("Native Libraries input: %s", jarFileOrDirectory);

        NativeLibZipFilter filter = new NativeLibZipFilter(
                abiFilters, mNoDuplicateFilter, mJniDebugMode);
        mNoDuplicateFilter.reset(jarFileOrDirectory);

        InputStream fis = null;
        try {
            if (jarFileOrDirectory.isDirectory()) {
                addNativeLibrariesFromDirectory(jarFileOrDirectory, "", filter);
            } else {
                fis = new BufferedInputStream(new FileInputStream(jarFileOrDirectory));
                mBuilder.writeZip(fis, filter, null /* ZipEntryExtractor */);
            }
        } catch (DuplicateFileException e) {
            mBuilder.cleanUp();
            throw e;
        } catch (Exception e) {
            mBuilder.cleanUp();
            throw new PackagerException(e, "Failed to add %s", jarFileOrDirectory);
        } finally {
            try {
                if (fis != null) {
                    Closeables.close(fis, true /* swallowIOException */);
                }
            } catch (IOException e) {
                // ignore.
            }
        }
    }

    private void addNativeLibrariesFromDirectory(
            @NonNull File directory,
            @NonNull String path,
            @NonNull NativeLibZipFilter zipFilter)
            throws IOException, IZipEntryFilter.ZipAbortException {
        File[] directoryFiles = directory.listFiles();
        if (directoryFiles == null) {
            return;
        }
        for (File file : directoryFiles) {
            String entryName = path.isEmpty() ? file.getName() : path + "/" + file.getName();
            if (file.isDirectory()) {
                addNativeLibrariesFromDirectory(file, entryName, zipFilter);
            } else {
                doAddFile(file, entryName, zipFilter);
            }
        }
    }

    /**
     * Seals the APK, and signs it if necessary.
     *
     * @throws PackagerException if an error occurred
     * @throws SealedPackageException if the APK is already sealed.
     */
    public void sealApk() throws PackagerException, SealedPackageException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        // close and sign the application package.
        try {
            mBuilder.close();
            mIsSealed = true;
        } catch (Exception e) {
            throw new PackagerException(e, "Failed to seal APK");
        } finally {
            mBuilder.cleanUp();
            mAddedFiles.clear();
        }
    }

    private void doAddFile(
            @NonNull File file,
            @NonNull String archivePath,
            @Nullable IZipEntryFilter filter) throws IZipEntryFilter.ZipAbortException,
            IOException {
        if (filter == null) {
            filter = mNoJavaClassZipFilter;
        }

        if (!filter.checkEntry(archivePath)) {
            return;
        }

        mAddedFiles.put(archivePath, file);
        mBuilder.writeFile(file, archivePath);
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
     * - that the file exists (if <var>throwIfDoesntExist</var> is <code>false</code>) and can
     *    be read.
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
        Class clazz = Packager.class;
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

}
