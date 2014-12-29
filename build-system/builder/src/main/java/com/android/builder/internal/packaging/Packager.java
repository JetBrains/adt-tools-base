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

import static com.android.SdkConstants.FN_APK_CLASSES_N_DEX;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.JavaResourceProcessor.IArchiveBuilder;
import com.android.builder.model.PackagingOptions;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.builder.signing.SignedJarBuilder;
import com.android.builder.signing.SignedJarBuilder.IZipEntryFilter;
import com.android.ide.common.packaging.PackagingUtils;
import com.android.ide.common.signing.CertificateInfo;
import com.android.utils.ILogger;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
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

    private static final Pattern PATTERN_NATIVELIB_EXT = Pattern.compile("^.+\\.so$",
            Pattern.CASE_INSENSITIVE);

    /**
     * A No-op zip filter. It's used to detect conflicts.
     *
     */
    private final class NullZipFilter implements IZipEntryFilter {
        private File mInputFile;

        void reset(File inputFile) {
            mInputFile = inputFile;
        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            mLogger.verbose("=> %s", archivePath);

            File duplicate = checkFileForDuplicate(archivePath);
            if (duplicate != null) {
                throw new DuplicateFileException(archivePath, duplicate, mInputFile);
            } else {
                mAddedFiles.put(archivePath, mInputFile);
            }

            return true;
        }
    }

    /**
     * Custom {@link IZipEntryFilter} to filter out everything that is not a standard java
     * resources, and also record whether the zip file contains native libraries.
     * <p/>Used in {@link SignedJarBuilder#writeZip(java.io.InputStream, IZipEntryFilter)} when
     * we only want the java resources from external jars.
     */
    private final class JavaAndNativeResourceFilter implements IZipEntryFilter {
        private final List<String> mNativeLibs = new ArrayList<String>();
        private Set<String> mUsedPickFirsts = null;

        @Nullable
        private final PackagingOptions mPackagingOptions;

        @NonNull
        private final Set<String> mExcludes;
        @NonNull
        private final Set<String> mPickFirsts;

        private boolean mNativeLibsConflict = false;
        private File mInputFile;

        private JavaAndNativeResourceFilter(@Nullable PackagingOptions packagingOptions) {
            mPackagingOptions = packagingOptions;

            mExcludes = mPackagingOptions != null ? mPackagingOptions.getExcludes() :
                    Collections.<String>emptySet();
            mPickFirsts = mPackagingOptions != null ? mPackagingOptions.getPickFirsts() :
                    Collections.<String>emptySet();

        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            // split the path into segments.
            String[] segments = archivePath.split("/");

            // empty path? skip to next entry.
            if (segments.length == 0) {
                return false;
            }

            //noinspection VariableNotUsedInsideIf
            if (mPackagingOptions != null) {
                if (mExcludes.contains(archivePath)) {
                    return false;
                }

                if (mPickFirsts.contains(archivePath)) {
                    if (mUsedPickFirsts == null) {
                        mUsedPickFirsts = Sets.newHashSetWithExpectedSize(mPickFirsts.size());
                    }

                    if (mUsedPickFirsts.contains(archivePath)) {
                        return false;
                    } else {
                        mUsedPickFirsts.add(archivePath);
                    }
                }
            }

            // Check each folders to make sure they should be included.
            // Folders like CVS, .svn, etc.. should already have been excluded from the
            // jar file, but we need to exclude some other folder (like /META-INF) so
            // we check anyway.
            for (int i = 0 ; i < segments.length - 1; i++) {
                if (!PackagingUtils.checkFolderForPackaging(segments[i])) {
                    return false;
                }
            }

            // get the file name from the path
            String fileName = segments[segments.length-1];

            boolean check = PackagingUtils.checkFileForPackaging(fileName);

            // only do additional checks if the file passes the default checks.
            if (check) {
                mLogger.verbose("=> %s", archivePath);

                File duplicate = checkFileForDuplicate(archivePath);
                if (duplicate != null) {
                    throw new DuplicateFileException(archivePath, duplicate, mInputFile);
                } else {
                    mAddedFiles.put(archivePath, mInputFile);
                }

                if (archivePath.endsWith(".so")) {
                    mNativeLibs.add(archivePath);

                    // only .so located in lib/ will interfere with the installation
                    if (archivePath.startsWith(SdkConstants.FD_APK_NATIVE_LIBS + "/")) {
                        mNativeLibsConflict = true;
                    }
                } else if (archivePath.endsWith(".jnilib")) {
                    mNativeLibs.add(archivePath);
                }
            }

            return check;
        }

        List<String> getNativeLibs() {
            return mNativeLibs;
        }

        boolean getNativeLibsConflict() {
            return mNativeLibsConflict;
        }

        void reset(File inputFile) {
            mInputFile = inputFile;
            mNativeLibs.clear();
            mNativeLibsConflict = false;
        }
    }

    private SignedJarBuilder mBuilder = null;
    private final ILogger mLogger;
    private boolean mJniDebugMode = false;
    private boolean mIsSealed = false;

    private final NullZipFilter mNullFilter = new NullZipFilter();
    private final JavaAndNativeResourceFilter mFilter;
    private final HashMap<String, File> mAddedFiles = new HashMap<String, File>();

    /**
     * Status for the addition of a jar file resources into the APK.
     * This indicates possible issues with native library inside the jar file.
     */
    public interface JarStatus {
        /**
         * Returns the list of native libraries found in the jar file.
         */
        List<String> getNativeLibs();

        /**
         * Returns whether some of those libraries were located in the location that Android
         * expects its native libraries.
         */
        boolean hasNativeLibsConflicts();

    }

    /** Internal implementation of {@link JarStatus}. */
    private static final class JarStatusImpl implements JarStatus {
        public final List<String> mLibs;
        public final boolean mNativeLibsConflict;

        private JarStatusImpl(List<String> libs, boolean nativeLibsConflict) {
            mLibs = libs;
            mNativeLibsConflict = nativeLibsConflict;
        }

        @Override
        public List<String> getNativeLibs() {
            return mLibs;
        }

        @Override
        public boolean hasNativeLibsConflicts() {
            return mNativeLibsConflict;
        }
    }

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
            @Nullable PackagingOptions packagingOptions,
            ILogger logger) throws PackagerException {
        mFilter = new JavaAndNativeResourceFilter(packagingOptions);

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

    public void addDexFiles(@NonNull File mainDexFolder, @NonNull Collection<File> extraDexFiles)
            throws DuplicateFileException, SealedPackageException, PackagerException {

        File[] mainDexFiles = mainDexFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith(SdkConstants.DOT_DEX);
            }
        });

        if (mainDexFiles != null && mainDexFiles.length > 0) {
            // Never rename the dex files in the main dex folder, in case we are in legacy mode
            // we requires the main dex files to not be renamed.
            for (File dexFile : mainDexFiles) {
                addFile(dexFile, dexFile.getName());
            }

            // prepare the index for the next files.
            int dexIndex = mainDexFiles.length + 1;

            for (File dexFile : extraDexFiles) {
                addFile(dexFile, String.format(FN_APK_CLASSES_N_DEX, dexIndex++));
            }
        }
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
            doAddFile(file, archivePath);
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
            mNullFilter.reset(zipFile);

            // ask the builder to add the content of the file.
            fis = new FileInputStream(zipFile);
            mBuilder.writeZip(fis, mNullFilter);
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
     * Adds the resources from a jar file.
     * @param jarFile the jar File.
     * @return a {@link JarStatus} object indicating if native libraries where found in
     *         the jar file.
     * @throws PackagerException if an error occurred
     * @throws SealedPackageException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     */
    public JarStatus addResourcesFromJar(File jarFile) throws PackagerException,
            SealedPackageException, DuplicateFileException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        FileInputStream fis = null;
        try {
            mLogger.verbose("%s:", jarFile);

            // reset the filter with this input.
            mFilter.reset(jarFile);

            // ask the builder to add the content of the file, filtered to only let through
            // the java resources.
            fis = new FileInputStream(jarFile);
            mBuilder.writeZip(fis, mFilter);

            // check if native libraries were found in the external library. This should
            // constitutes an error or warning depending on if they are in lib/
            return new JarStatusImpl(mFilter.getNativeLibs(), mFilter.getNativeLibsConflict());
        } catch (DuplicateFileException e) {
            mBuilder.cleanUp();
            throw e;
        } catch (Exception e) {
            mBuilder.cleanUp();
            throw new PackagerException(e, "Failed to add %s", jarFile);
        } finally {
            try {
                Closeables.close(fis, true /* swallowIOException */);
            } catch (IOException e) {
                // ignore.
            }
        }
    }

    /**
     * Adds the native libraries from the top native folder.
     * The content of this folder must be the various ABI folders.
     *
     * This may or may not copy gdbserver into the apk based on whether the debug mode is set.
     *
     * @param nativeFolder the root folder containing the abi folders which contain the .so
     * @param abiFilters a list of abi filters to include. If null or empty, all abis are included.
     *
     * @throws PackagerException if an error occurred
     * @throws SealedPackageException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     *
     * @see #setJniDebugMode(boolean)
     */
    public void addNativeLibraries(@NonNull File nativeFolder, @Nullable Set<String> abiFilters)
            throws PackagerException, SealedPackageException, DuplicateFileException {
        if (mIsSealed) {
            throw new SealedPackageException("APK is already sealed");
        }

        if (!nativeFolder.isDirectory()) {
            // not a directory? check if it's a file or doesn't exist
            if (nativeFolder.exists()) {
                throw new PackagerException("%s is not a folder", nativeFolder);
            } else {
                throw new PackagerException("%s does not exist", nativeFolder);
            }
        }

        File[] abiList = nativeFolder.listFiles();

        mLogger.verbose("Native folder: %s", nativeFolder);

        if (abiList != null) {
            for (File abi : abiList) {
                if (abiFilters != null && !abiFilters.isEmpty() && !abiFilters.contains(abi.getName())) {
                    continue;
                }

                if (abi.isDirectory()) { // ignore files

                    File[] libs = abi.listFiles();
                    if (libs != null) {
                        for (File lib : libs) {
                            // only consider files that are .so or, if in debug mode, that
                            // are gdbserver executables
                            String libName = lib.getName();
                            if (lib.isFile() &&
                                    (PATTERN_NATIVELIB_EXT.matcher(lib.getName()).matches() ||
                                        (mJniDebugMode &&
                                            (SdkConstants.FN_GDBSERVER.equals(libName) ||
                                             SdkConstants.FN_GDB_SETUP.equals(libName))))) {

                                String path =
                                    SdkConstants.FD_APK_NATIVE_LIBS + "/" +
                                    abi.getName() + "/" + libName;

                                try {
                                    doAddFile(lib, path);
                                } catch (IOException e) {
                                    mBuilder.cleanUp();
                                    throw new PackagerException(e, "Failed to add %s", lib);
                                }
                            }
                        }
                    }
                }
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
        }
    }

    private void doAddFile(File file, String archivePath) throws DuplicateFileException,
            IOException {
        mLogger.verbose("%1$s => %2$s", file, archivePath);

        File duplicate = checkFileForDuplicate(archivePath);
        if (duplicate != null) {
            throw new DuplicateFileException(archivePath, duplicate, file);
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
    private void checkOutputFile(File file) throws PackagerException {
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
