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

package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.annotations.PackageFile;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.internal.utils.CachedFileContents;
import com.android.builder.internal.utils.IOExceptionWrapper;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import org.gradle.api.Task;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.tooling.BuildException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Abstract task to package an Android artifact.
 */
public abstract class PackageAndroidArtifact extends IncrementalTask implements FileSupplier {

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    // ----- PUBLIC TASK API -----

    @InputFile
    public File getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    // ----- PRIVATE TASK API -----

    @InputFiles
    @Optional
    public Collection<File> getJavaResourceFiles() {
        return javaResourceFiles;
    }
    @InputFiles
    @Optional
    public Collection<File> getJniFolders() {
        return jniFolders;
    }

    private File resourceFile;

    private Set<File> dexFolders;

    private File assets;

    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    public void setDexFolders(Set<File> dexFolders) {
        this.dexFolders = dexFolders;
    }

    @InputDirectory
    public File getAssets() {
        return assets;
    }

    public void setAssets(File assets) {
        this.assets = assets;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    private Set<File> javaResourceFiles;
    private Set<File> jniFolders;

    @PackageFile
    private File outputFile;

    private Set<String> abiFilters;

    private boolean debugBuild;
    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private ApiVersion minSdkVersion;

    protected InstantRunBuildContext instantRunContext;

    protected File instantRunSupportDir;

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";

    /**
     * Zip caches to allow incremental updates.
     */
    protected FileCacheByPath cacheByPath;

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Input
    public boolean getDebugBuild() {
        return debugBuild;
    }

    public void setDebugBuild(boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    @Nested
    @Optional
    public CoreSigningConfig getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(CoreSigningConfig signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Nested
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    public void setPackagingOptions(PackagingOptions packagingOptions) {
        this.packagingOptions = packagingOptions;
    }

    @Input
    public int getMinSdkVersion() {
        return this.minSdkVersion.getApiLevel();
    }

    public void setMinSdkVersion(ApiVersion version) {
        this.minSdkVersion = version;
    }

    protected DexPackagingPolicy dexPackagingPolicy;

    @Input
    String getDexPackagingPolicy() {
        return dexPackagingPolicy.toString();
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        /*
         * Clear the cache to make sure we have do not do an incremental build.
         */
        cacheByPath.clear();

        /*
         * Also clear the intermediate build directory. We don't know if anything is in there and
         * since this is a full build, we don't want to get any interference from previous state.
         */
        FileUtils.deleteDirectoryContents(getIncrementalFolder());

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        getOutputFile().delete();

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(getJavaResourceFiles());
        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getAssets()));
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources=
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        doTask(
                updatedDex,
                updatedJavaResources,
                updatedAssets,
                updatedAndroidResources,
                updatedJniResources);

        /*
         * Update the known files.
         */
        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());
        saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
        saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
        saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
    }

    /**
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has
     * occurred.
     *
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private void doTask(
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        Collection<File> instantRunDexBaseFiles;
        switch(dexPackagingPolicy) {
            case INSTANT_RUN_SHARDS_IN_SINGLE_APK:
                /*
                 * If we're doing instant run, then we don't want to treat all dex archives
                 * as dex archives for packaging. We will package some of the dex files as
                 * resources.
                 *
                 * All dex files in directories whose name contains InstantRunSlicer.MAIN_SLICE_NAME
                 * are kept in the apk as dex files. All other dex files are placed as
                 * resources as defined by makeInstantRunResourcesFromDex.
                 */
                ;
                instantRunDexBaseFiles = getDexFolders()
                        .stream()
                        .filter(input -> input.getName().contains(InstantRunSlicer.MAIN_SLICE_NAME))
                        .collect(Collectors.toSet());
                Iterable<File> nonInstantRunDexBaseFiles = getDexFolders()
                        .stream()
                        .filter(f -> !instantRunDexBaseFiles.contains(f))
                        .collect(Collectors.toSet());

                ImmutableMap<RelativeFile, FileStatus> newInstantRunResources =
                        makeInstantRunResourcesFromDex(nonInstantRunDexBaseFiles);

                @SuppressWarnings("unchecked")
                ImmutableMap<RelativeFile, FileStatus> updatedChangedResources =
                        IncrementalRelativeFileSets.union(
                                Sets.newHashSet(changedJavaResources, newInstantRunResources));
                changedJavaResources = updatedChangedResources;

                changedDex = ImmutableMap.copyOf(
                        Maps.filterKeys(
                                changedDex,
                                Predicates.compose(
                                        Predicates.in(instantRunDexBaseFiles),
                                        RelativeFile.EXTRACT_BASE
                                )));

                break;
            case INSTANT_RUN_MULTI_APK:
                instantRunDexBaseFiles = getDexFolders()
                        .stream()
                        .filter(input -> input.getName().contains(InstantRunSlicer.MAIN_SLICE_NAME))
                        .collect(Collectors.toSet());
                changedDex = ImmutableMap.copyOf(
                        Maps.filterKeys(
                                changedDex,
                                Predicates.compose(
                                        Predicates.in(instantRunDexBaseFiles),
                                        RelativeFile.EXTRACT_BASE
                                )));

            case STANDARD:
                break;
            default:
                throw new RuntimeException(
                        "Unhandled DexPackagingPolicy : " + getDexPackagingPolicy());
        }

        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;

        try {
            if (signingConfig != null && signingConfig.isSigningReady()) {
                CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                        signingConfig.getStoreType(),
                        Preconditions.checkNotNull(signingConfig.getStoreFile()),
                        Preconditions.checkNotNull(signingConfig.getStorePassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyAlias()));
                key = certificateInfo.getKey();
                certificate = certificateInfo.getCertificate();
                v1SigningEnabled = signingConfig.isV1SigningEnabled();
                v2SigningEnabled = signingConfig.isV2SigningEnabled();
            } else {
                key = null;
                certificate = null;
                v1SigningEnabled = false;
                v2SigningEnabled = false;
            }

            ApkCreatorFactory.CreationData creationData =
                    new ApkCreatorFactory.CreationData(
                            getOutputFile(),
                            key,
                            certificate,
                            v1SigningEnabled,
                            v2SigningEnabled,
                            null,   // BuiltBy
                            getBuilder().getCreatedBy(),
                            getMinSdkVersion());

            try (IncrementalPackager packager = createPackager(creationData)) {
                packager.updateDex(changedDex);
                packager.updateJavaResources(changedJavaResources);
                packager.updateAssets(changedAssets);
                packager.updateAndroidResources(changedAndroidResources);
                packager.updateNativeLibraries(changedNLibs);
            }
        } catch (PackagerException | KeytoolException e) {
            throw new RuntimeException(e);
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
            changedDex.keySet().stream(),
            Stream.concat(
                    changedJavaResources.keySet().stream(),
                    Stream.concat(
                            changedAndroidResources.keySet().stream(),
                            changedNLibs.keySet().stream())))
            .map(RelativeFile::getBase)
            .filter(File::isFile)
            .distinct()
            .forEach((File f) -> {
                try {
                    cacheByPath.add(f);
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            });

        // Mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overridden if the apk is signed/aligned.
        try {
            instantRunContext.addChangedFile(InstantRunBuildContext.FileType.MAIN,
                    getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    @NonNull
    private IncrementalPackager createPackager(ApkCreatorFactory.CreationData creationData)
            throws PackagerException, IOException {
        return new IncrementalPackager(
                creationData,
                getIncrementalFolder(),
                ApkCreatorFactories.fromProjectProperties(getProject(), getDebugBuild()),
                getAbiFilters(),
                getJniDebugBuild());
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        Preconditions.checkNotNull(changedInputs, "changedInputs == null");

        super.doIncrementalTaskAction(changedInputs);

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());

        ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.DEX,
                        getDexFolders(),
                        cacheByPath);

        ImmutableMap<RelativeFile, FileStatus> changedJavaResources =
                getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.JAVA_RESOURCE,
                        getJavaResourceFiles(),
                        cacheByPath);

        ImmutableMap<RelativeFile, FileStatus> changedAssets =
                getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.ASSET,
                        Collections.singleton(getAssets()),
                        cacheByPath);

        ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.ANDROID_RESOURCE,
                        androidResources,
                        cacheByPath);

        ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.NATIVE_RESOURCE,
                        getJniFolders(),
                        cacheByPath);


        doTask(
                changedDexFiles,
                changedJavaResources,
                changedAssets,
                changedAndroidResources,
                changedNLibs);

        /*
         * Removed cached versions of deleted zip files because we no longer need to compute diffs.
         */
        changedInputs.keySet().stream()
                .filter(f -> !f.exists())
                .forEach(f -> {
                    try {
                        cacheByPath.remove(f);
                    } catch (IOException e) {
                        throw new IOExceptionWrapper(e);
                    }
                });

        /*
         * Update the save data keep files.
         */
        ImmutableMap<RelativeFile, FileStatus> allDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJavaResourceFiles());
        ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> allJniResources=
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        saveData.setInputSet(allDex.keySet(), InputSet.DEX);
        saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
    }

    /**
     * Obtains all changed inputs of a given input set. Given a set of files mapped to their
     * changed status, this method returns a list of changes computed as follows:
     *
     * <ol>
     *     <li>Changed inputs are split into deleted and non-deleted inputs. This separation is
     *     needed because deleted inputs may no longer be mappable to any {@link InputSet} just
     *     by looking at the file path, without using {@link KnownFilesSaveData}.
     *     <li>Deleted inputs are filtered through {@link KnownFilesSaveData} to get only those
     *     whose input set matches {@code inputSet}.
     *     <li>Non-deleted inputs are processed through
     *     {@link IncrementalRelativeFileSets#makeFromBaseFiles(Collection, Map, FileCacheByPath)}
     *     to obtain the incremental file changes.
     *     <li>The results of processed deleted and non-deleted are merged and returned.
     * </ol>
     *
     * @param changedInputs all changed inputs
     * @param saveData the save data with all input sets from last run
     * @param inputSet the input set to filter
     * @param baseFiles the base files of the input set
     * @param cacheByPath where to cache files
     * @return the status of all relative files in the input set
     */
    @NonNull
    private ImmutableMap<RelativeFile, FileStatus> getChangedInputs(
            @NonNull Map<File, FileStatus> changedInputs,
            @NonNull KnownFilesSaveData saveData,
            @NonNull InputSet inputSet,
            @NonNull Collection<File> baseFiles,
            @NonNull FileCacheByPath cacheByPath)
            throws IOException {

        /*
         * Figure out changes to deleted files.
         */
        Set<File> deletedFiles =
                Maps.filterValues(changedInputs, Predicates.equalTo(FileStatus.REMOVED)).keySet();
        Set<RelativeFile> deletedRelativeFiles = saveData.find(deletedFiles, inputSet);

        /*
         * Figure out changes to non-deleted files.
         */
        Map<File, FileStatus> nonDeletedFiles =
                Maps.filterValues(
                        changedInputs,
                        Predicates.not(Predicates.equalTo(FileStatus.REMOVED)));
        Map<RelativeFile, FileStatus> nonDeletedRelativeFiles =
                IncrementalRelativeFileSets.makeFromBaseFiles(
                        baseFiles,
                        nonDeletedFiles,
                        cacheByPath);

        /*
         * Merge everything.
         */
        return new ImmutableMap.Builder<RelativeFile, FileStatus>()
                .putAll(Maps.asMap(deletedRelativeFiles, Functions.constant(FileStatus.REMOVED)))
                .putAll(nonDeletedRelativeFiles)
                .build();
    }

    /**
     * Creates the new instant run resources from the dex files. This method is not
     * incremental. It will ignore updates and look at all dex files and always rebuild the
     * instant run resources.
     *
     * <p>The instant run resources are resources that package dex files.
     *
     * @param dexBaseFiles the base files to dex
     * @return the instant run resources
     * @throws IOException failed to create the instant run resources
     */
    @NonNull
    private ImmutableMap<RelativeFile, FileStatus> makeInstantRunResourcesFromDex(
            @NonNull Iterable<File> dexBaseFiles) throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "instant-run.zip");
        boolean existedBefore = tmpZipFile.exists();

        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));
        // no need to compress a zip, the APK itself gets compressed.
        zipFile.setLevel(0);

        try {
            for (File dexFolder : dexBaseFiles) {
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                    if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                        // There are several pieces of code in the runtime library that depend
                        // on this exact pattern, so it should not be changed without thorough
                        // testing (it's basically part of the contract).
                        String entryName =
                                file.getParentFile().getName() + "-" + file.getName();
                        zipFile.putNextEntry(new ZipEntry(entryName));
                        try {
                            Files.copy(file, zipFile);
                        } finally {
                            zipFile.closeEntry();
                        }
                    }
                }
            }
        } finally {
            zipFile.close();
        }

        RelativeFile resourcesFile = new RelativeFile(instantRunSupportDir, tmpZipFile);
        return ImmutableMap.of(resourcesFile, existedBefore? FileStatus.CHANGED : FileStatus.NEW);
    }

    // ----- FileSupplierTask -----

    @Override
    public File get() {
        return getOutputFile();
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    /**
     * Class that keeps track of which files are known in incremental builds. Gradle tells us
     * which files were modified, but doesn't tell us which inputs the files come from so when a
     * file is marked as deleted, we don't know which input set it was deleted from. This class
     * maintains the list of files and their source locations and can be saved to the intermediate
     * directory.
     *
     * <p>File data is loaded on creation and saved on close.
     *
     * <p><i>Implementation note:</i> the actual data is saved in a property file with the
     * file name mapped to the name of the {@link InputSet} enum defining its input set.
     */
    private static class KnownFilesSaveData {

        /**
         * Name of the file with the save data.
         */
        private static final String SAVE_DATA_FILE_NAME = "file-input-save-data.txt";

        /**
         * Property with the number of files in the property file.
         */
        private static final String COUNT_PROPERTY = "count";

        /**
         * Suffix for property with the base file.
         */
        private static final String BASE_SUFFIX = ".base";

        /**
         * Suffix for property with the file.
         */
        private static final String FILE_SUFFIX = ".file";

        /**
         * Suffix for property with the input set.
         */
        private static final String INPUT_SET_SUFFIX = ".set";

        /**
         * Cache with all known cached files.
         */
        private static final Map<File, CachedFileContents<KnownFilesSaveData>> mCache =
                Maps.newHashMap();

        /**
         * File contents cache.
         */
        @NonNull
        private final CachedFileContents<KnownFilesSaveData> mFileContentsCache;

        /**
         * Maps all files in the last build to their input set.
         */
        @NonNull
        private final Map<RelativeFile, InputSet> mFiles;

        /**
         * Has the data been modified?
         */
        private boolean mDirty;

        /**
         * Creates a new file save data and reads it one exists. To create new instances, the
         * factory method {@link #make(File)} should be used.
         *
         * @param cache the cache used
         * @throws IOException failed to read the file (not thrown if the file does not exist)
         */
        private KnownFilesSaveData(@NonNull CachedFileContents<KnownFilesSaveData> cache)
                throws IOException {
            mFileContentsCache = cache;
            mFiles = Maps.newHashMap();
            if (cache.getFile().isFile()) {
                readCurrentData();
            }

            mDirty = false;
        }

        /**
         * Creates a new {@link KnownFilesSaveData}, or obtains one from cache if there already
         * exists a cached entry.
         *
         * @param intermediateDir the intermediate directory where the cache is stored
         * @return the save data
         * @throws IOException save data file exists but there was an error reading it (not thrown
         * if the file does not exist)
         */
        @NonNull
        private static synchronized KnownFilesSaveData make(@NonNull File intermediateDir)
                throws IOException {
            File saveFile = computeSaveFile(intermediateDir);
            CachedFileContents<KnownFilesSaveData> cached = mCache.get(saveFile);
            if (cached == null) {
                cached = new CachedFileContents<>(saveFile);
                mCache.put(saveFile, cached);
            }

            KnownFilesSaveData saveData = cached.getCache();
            if (saveData == null) {
                saveData = new KnownFilesSaveData(cached);
                cached.closed(saveData);
            }

            return saveData;
        }

        /**
         * Computes what is the save file for the provided intermediate directory.
         *
         * @param intermediateDir the intermediate directory
         * @return the file
         */
        private static File computeSaveFile(@NonNull File intermediateDir) {
            return new File(intermediateDir, SAVE_DATA_FILE_NAME);
        }

        /**
         * Reads the save file data into the in-memory data structures.
         *
         * @throws IOException failed to read the file
         */
        private void readCurrentData() throws IOException {
            Closer closer = Closer.create();

            File saveFile = mFileContentsCache.getFile();

            Properties properties = new Properties();
            try {
                Reader saveDataReader = closer.register(new FileReader(saveFile));
                properties.load(saveDataReader);
            } catch (Throwable t) {
                throw closer.rethrow(t);
            } finally {
                closer.close();
            }

            String fileCountText = null;
            int fileCount;
            try {
                fileCountText = properties.getProperty(COUNT_PROPERTY);
                if (fileCountText == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + COUNT_PROPERTY + "' has no value).");
                }

                fileCount = Integer.parseInt(fileCountText);
                if (fileCount < 0) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + COUNT_PROPERTY + "' has value " + fileCount + ").");
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                        + "property '" + COUNT_PROPERTY + "' has value '" + fileCountText + "').",
                        e);
            }

            for (int i = 0; i < fileCount; i++) {
                String baseName = properties.getProperty(i + BASE_SUFFIX);
                if (baseName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + BASE_SUFFIX + "' has no value).");
                }

                String fileName = properties.getProperty(i + FILE_SUFFIX);
                if (fileName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + FILE_SUFFIX + "' has no value).");
                }

                String inputSetName = properties.getProperty(i + INPUT_SET_SUFFIX);
                if (inputSetName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + INPUT_SET_SUFFIX + "' has no value).");
                }

                InputSet is;
                try {
                    is = InputSet.valueOf(InputSet.class, inputSetName);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + INPUT_SET_SUFFIX + "' has invalid value '"
                            + inputSetName + "').");
                }

                mFiles.put(new RelativeFile(new File(baseName), new File(fileName)), is);
            }
        }

        /**
         * Saves current in-memory data structures to file.
         *
         * @throws IOException failed to save the data
         */
        private void saveCurrentData() throws IOException {
            if (!mDirty) {
                return;
            }

            Closer closer = Closer.create();

            Properties properties = new Properties();
            properties.put(COUNT_PROPERTY, Integer.toString(mFiles.size()));
            int idx = 0;
            for (Map.Entry<RelativeFile, InputSet> e : mFiles.entrySet()) {
                RelativeFile rf = e.getKey();

                String basePath = Verify.verifyNotNull(rf.getBase().getPath());
                Verify.verify(!basePath.isEmpty());

                String filePath = Verify.verifyNotNull(rf.getFile().getPath());
                Verify.verify(!filePath.isEmpty());

                properties.put(idx + BASE_SUFFIX, basePath);
                properties.put(idx + FILE_SUFFIX, filePath);
                properties.put(idx + INPUT_SET_SUFFIX, e.getValue().name());

                idx++;
            }

            try {
                Writer saveDataWriter = closer.register(new FileWriter(
                        mFileContentsCache.getFile()));
                properties.store(saveDataWriter, "Internal package file, do not edit.");
                mFileContentsCache.closed(this);
            } catch (Throwable t) {
                throw closer.rethrow(t);
            } finally {
                closer.close();
            }
        }

        /**
         * Obtains all relative files stored in the save data that have the provided input set and
         * whose files are included in the provided set of files. This method allows retrieving
         * the original relative files from the files, while filtering for the desired input set.
         *
         * @param files the files to filter
         * @param inputSet the input set to filter
         * @return all saved relative files that have the given input set and whose files exist
         * in the provided set
         */
        @NonNull
        private ImmutableSet<RelativeFile> find(@NonNull Set<File> files,
                @NonNull InputSet inputSet) {
            Set<RelativeFile> found = Sets.newHashSet();
            for (RelativeFile rf :
                    Maps.filterValues(mFiles, Predicates.equalTo(inputSet)).keySet()) {
                if (files.contains(rf.getFile())) {
                    found.add(rf);
                }
            }

            return ImmutableSet.copyOf(found);
        }

        /**
         * Obtains a predicate that checks if a file is in an input set.
         *
         * @param inputSet the input set
         * @return the predicate
         */
        @NonNull
        private Function<File, RelativeFile> inInputSet(@NonNull InputSet inputSet) {
            Map<File, RelativeFile> inverseFiltered = mFiles.entrySet().stream()
                    .filter(e -> e.getValue() == inputSet)
                    .map(Map.Entry::getKey)
                    .collect(
                            HashMap::new,
                            (m, rf) -> m.put(rf.getFile(), rf),
                            Map::putAll);

            return inverseFiltered::get;
        }

        /**
         * Sets all files in an input set, replacing whatever existed previously.
         *
         * @param files the files
         * @param set the input set
         */
        private void setInputSet(@NonNull Collection<RelativeFile> files, @NonNull InputSet set) {
            for (Iterator<Map.Entry<RelativeFile, InputSet>> it = mFiles.entrySet().iterator();
                    it.hasNext(); ) {
                Map.Entry<RelativeFile, InputSet> next = it.next();
                if (next.getValue() == set && !files.contains(next.getKey())) {
                    it.remove();
                    mDirty = true;
                }
            }

            files.forEach(f -> {
                if (!mFiles.containsKey(f)) {
                    mFiles.put(f, set);
                    mDirty = true;
                }
            });
        }
    }

    /**
     * Input sets for files for save data (see {@link KnownFilesSaveData}).
     */
    private enum InputSet {
        /**
         * File belongs to the dex file set.
         */
        DEX,

        /**
         * File belongs to the java resources file set.
         */
        JAVA_RESOURCE,

        /**
         * File belongs to the native resources file set.
         */
        NATIVE_RESOURCE,

        /**
         * File belongs to the android resources file set.
         */
        ANDROID_RESOURCE,

        /**
         * File belongs to the assets file set.
         */
        ASSET
    }

    // ----- ConfigAction -----

    public abstract static class ConfigAction<T extends  PackageAndroidArtifact> implements TaskConfigAction<T> {

        protected final PackagingScope packagingScope;
        protected final DexPackagingPolicy dexPackagingPolicy;

        public ConfigAction(
                @NonNull PackagingScope packagingScope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            this.packagingScope = packagingScope;
            dexPackagingPolicy = patchingPolicy == null
                    ? DexPackagingPolicy.STANDARD
                    : patchingPolicy.getDexPatchingPolicy();
        }

        @Override
        public void execute(@NonNull final T packageAndroidArtifact) {
            packageAndroidArtifact.setAndroidBuilder(packagingScope.getAndroidBuilder());
            packageAndroidArtifact.setVariantName(packagingScope.getFullVariantName());
            packageAndroidArtifact.setMinSdkVersion(packagingScope.getMinSdkVersion());
            packageAndroidArtifact.instantRunContext =
                    packagingScope.getInstantRunBuildContext();
            packageAndroidArtifact.dexPackagingPolicy = dexPackagingPolicy;
            packageAndroidArtifact.instantRunSupportDir =
                    packagingScope.getInstantRunSupportDir();
            packageAndroidArtifact.setIncrementalFolder(
                    packagingScope.getIncrementalDir(packageAndroidArtifact.getName()));

            File cacheByPathDir = new File(packageAndroidArtifact.getIncrementalFolder(),
                    ZIP_DIFF_CACHE_DIR);
            FileUtils.mkdirs(cacheByPathDir);
            packageAndroidArtifact.cacheByPath = new FileCacheByPath(cacheByPathDir);

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "resourceFile", packagingScope::getFinalResourcesFile);

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "dexFolders", packagingScope::getDexFolders);

            ConventionMappingHelper.map(
                    packageAndroidArtifact,
                    "javaResourceFiles",
                    packagingScope::getJavaResources);

            packageAndroidArtifact.setAssets(packagingScope.getAssetsDir());

            ConventionMappingHelper.map(packageAndroidArtifact, "jniFolders", () -> {
                if (packagingScope.getSplitHandlingPolicy() ==
                        SplitHandlingPolicy.PRE_21_POLICY) {
                    return packagingScope.getJniFolders();
                }

                Set<String> filters =
                        AbiSplitOptions.getAbiFilters(packagingScope.getAbiFilters());

                return filters.isEmpty()
                        ? packagingScope.getJniFolders()
                        : Collections.emptySet();
            });

            ConventionMappingHelper.map(packageAndroidArtifact, "abiFilters", () -> {
                String filter = packagingScope.getMainOutputFile().getFilter(
                        com.android.build.OutputFile.ABI);
                if (filter != null) {
                    return ImmutableSet.of(filter);
                }
                Set<String> supportedAbis = packagingScope.getSupportedAbis();
                // TODO: nullability
                if (supportedAbis != null) {
                    return supportedAbis;
                }

                return ImmutableSet.of();
            });

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "jniDebugBuild", packagingScope::isJniDebuggable);

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "debugBuild", packagingScope::isDebuggable);

            packageAndroidArtifact.setSigningConfig(packagingScope.getSigningConfig());

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "packagingOptions", packagingScope::getPackagingOptions);
        }
    }

}
