/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import static com.android.SdkConstants.DOT_DEX;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_XML;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.manifmerger.ManifestMerger2.Invoker;
import static com.android.manifmerger.ManifestMerger2.SystemProperty;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.dependency.ManifestDependency;
import com.android.builder.dependency.SymbolFileProvider;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.internal.SymbolLoader;
import com.android.builder.internal.SymbolWriter;
import com.android.builder.internal.TestManifestGenerator;
import com.android.builder.internal.compiler.AidlProcessor;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.builder.internal.compiler.LeafFolderGatherer;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.internal.compiler.RenderScriptProcessor;
import com.android.builder.internal.compiler.SourceSearcher;
import com.android.builder.internal.incremental.DependencyData;
import com.android.builder.internal.packaging.JavaResourceProcessor;
import com.android.builder.internal.packaging.Packager;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ClassField;
import com.android.builder.model.PackagingOptions;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.builder.packaging.SigningException;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.signing.SignedJarBuilder;
import com.android.ide.common.internal.AaptCruncher;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.manifmerger.ICallback;
import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergerLog;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.PlaceholderHandler;
import com.android.manifmerger.XmlDocument;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.SdkUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the main builder class. It is given all the data to process the build (such as
 * {@link DefaultProductFlavor}s, {@link DefaultBuildType} and dependencies) and use them when doing specific
 * build steps.
 *
 * To use:
 * create a builder with {@link #AndroidBuilder(String, String, ILogger, boolean)}
 *
 * then build steps can be done with
 * {@link #mergeManifests(java.io.File, java.util.List, java.util.List, String, int, String, String, String, Integer, String, com.android.manifmerger.ManifestMerger2.MergeType, java.util.Map)}
 * {@link #processTestManifest2(String, String, String, String, String, Boolean, Boolean, java.io.File, java.util.List, java.io.File, java.io.File)}
 * {@link #processResources(java.io.File, java.io.File, java.io.File, java.util.List, String, String, String, String, String, com.android.builder.core.VariantConfiguration.Type, boolean, com.android.builder.model.AaptOptions, java.util.Collection, boolean, java.util.Collection)}
 * {@link #compileAllAidlFiles(java.util.List, java.io.File, java.io.File, java.util.List, com.android.builder.compiling.DependencyFileProcessor)}
 * {@link #convertByteCode(Iterable, Iterable, java.io.File, boolean, DexOptions, java.util.List, boolean)}
 * {@link #packageApk(String, java.io.File, java.util.Collection, String, java.util.Collection, java.util.Set, boolean, com.android.builder.model.SigningConfig, com.android.builder.model.PackagingOptions, String)}
 *
 * Java compilation is not handled but the builder provides the bootclasspath with
 * {@link #getBootClasspath()}.
 */
public class AndroidBuilder {

    private static final FullRevision MIN_BUILD_TOOLS_REV = new FullRevision(19, 1, 0);
    private static final FullRevision MIN_MULTIDEX_BUILD_TOOLS_REV = new FullRevision(21, 0, 0);

    private static final DependencyFileProcessor sNoOpDependencyFileProcessor = new DependencyFileProcessor() {
        @Override
        public DependencyData processFile(@NonNull File dependencyFile) {
            return null;
        }
    };

    @NonNull private final String mProjectId;
    @NonNull private final ILogger mLogger;
    @NonNull private final CommandLineRunner mCmdLineRunner;
    private final boolean mVerboseExec;

    @Nullable private String mCreatedBy;

    private SdkInfo mSdkInfo;
    private TargetInfo mTargetInfo;

    /**
     * Creates an AndroidBuilder.
     * <p/>
     * <var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param createdBy the createdBy String for the apk manifest.
     * @param logger the Logger
     * @param verboseExec whether external tools are launched in verbose mode
     */
    public AndroidBuilder(
            @NonNull String projectId,
            @Nullable String createdBy,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mProjectId = projectId;
        mCreatedBy = createdBy;
        mLogger = checkNotNull(logger);
        mVerboseExec = verboseExec;
        mCmdLineRunner = new CommandLineRunner(mLogger);
    }

    @VisibleForTesting
    AndroidBuilder(
            @NonNull String projectId,
            @NonNull CommandLineRunner cmdLineRunner,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mProjectId = projectId;
        mCmdLineRunner = checkNotNull(cmdLineRunner);
        mLogger = checkNotNull(logger);
        mVerboseExec = verboseExec;
    }

    /**
     * Sets the SdkInfo and the targetInfo on the builder. This is required to actually
     * build (some of the steps).
     *
     * @param sdkInfo the SdkInfo
     * @param targetInfo the TargetInfo
     *
     * @see com.android.builder.sdk.SdkLoader
     */
    public void setTargetInfo(@NonNull SdkInfo sdkInfo, @NonNull TargetInfo targetInfo) {
        mSdkInfo = sdkInfo;
        mTargetInfo = targetInfo;

        if (mTargetInfo.getBuildTools().getRevision().compareTo(MIN_BUILD_TOOLS_REV) < 0) {
            throw new IllegalArgumentException(String.format(
                    "The SDK Build Tools revision (%1$s) is too low for project '%2$s'. Minimum required is %3$s",
                    mTargetInfo.getBuildTools().getRevision(), mProjectId, MIN_BUILD_TOOLS_REV));
        }
    }

    /**
     * Returns the SdkInfo, if set.
     */
    @Nullable
    public SdkInfo getSdkInfo() {
        return mSdkInfo;
    }

    /**
     * Returns the TargetInfo, if set.
     */
    @Nullable
    public TargetInfo getTargetInfo() {
        return mTargetInfo;
    }

    @NonNull
    public ILogger getLogger() {
        return mLogger;
    }

    /**
     * Returns the compilation target, if set.
     */
    @Nullable
    public IAndroidTarget getTarget() {
        checkState(mTargetInfo != null,
                "Cannot call getTarget() before setTargetInfo() is called.");
        return mTargetInfo.getTarget();
    }

    /**
     * Returns whether the compilation target is a preview.
     */
    public boolean isPreviewTarget() {
        checkState(mTargetInfo != null,
                "Cannot call isTargetAPreview() before setTargetInfo() is called.");
        return mTargetInfo.getTarget().getVersion().isPreview();
    }

    public String getTargetCodename() {
        checkState(mTargetInfo != null,
                "Cannot call getTargetCodename() before setTargetInfo() is called.");
        return mTargetInfo.getTarget().getVersion().getCodename();
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     */
    @NonNull
    public List<File> getBootClasspath() {
        checkState(mTargetInfo != null,
                "Cannot call getBootClasspath() before setTargetInfo() is called.");

        List<File> classpath = Lists.newArrayList();

        IAndroidTarget target = mTargetInfo.getTarget();

        for (String p : target.getBootClasspath()) {
            classpath.add(new File(p));
        }

        // add optional libraries if any
        IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
        if (libs != null) {
            for (IAndroidTarget.IOptionalLibrary lib : libs) {
                classpath.add(new File(lib.getJarPath()));
            }
        }

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(mSdkInfo.getAnnotationsJar());
        }

        return classpath;
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     */
    @NonNull
    public List<String> getBootClasspathAsStrings() {
        checkState(mTargetInfo != null,
                "Cannot call getBootClasspath() before setTargetInfo() is called.");

        List<String> classpath = Lists.newArrayList();

        IAndroidTarget target = mTargetInfo.getTarget();

        classpath.addAll(target.getBootClasspath());

        // add optional libraries if any
        IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
        if (libs != null) {
            for (IAndroidTarget.IOptionalLibrary lib : libs) {
                classpath.add(lib.getJarPath());
            }
        }

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(mSdkInfo.getAnnotationsJar().getPath());
        }

        return classpath;
    }

    /**
     * Returns the jar file for the renderscript mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the jar file, or null.
     *
     * @see #setTargetInfo(com.android.builder.sdk.SdkInfo, com.android.builder.sdk.TargetInfo)
     */
    @Nullable
    public File getRenderScriptSupportJar() {
        if (mTargetInfo != null) {
            return RenderScriptProcessor.getSupportJar(
                    mTargetInfo.getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config.
     *
     * If the SDK was loaded, this may include the renderscript support jar.
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getCompileClasspath(@NonNull VariantConfiguration<?,?,?> variantConfiguration) {
        Set<File> compileClasspath = variantConfiguration.getCompileClasspath();

        if (variantConfiguration.getRenderscriptSupportMode()) {
            File renderScriptSupportJar = getRenderScriptSupportJar();

            Set<File> fullJars = Sets.newHashSetWithExpectedSize(compileClasspath.size() + 1);
            fullJars.addAll(compileClasspath);
            if (renderScriptSupportJar != null) {
                fullJars.add(renderScriptSupportJar);
            }
            compileClasspath = fullJars;
        }

        return compileClasspath;
    }

    /**
     * Returns the list of packaged jars for this config. If the config tests a library, this
     * will include the jars of the tested config
     *
     * If the SDK was loaded, this may include the renderscript support jar.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getPackagedJars(@NonNull VariantConfiguration<?,?,?> variantConfiguration) {
        Set<File> packagedJars = variantConfiguration.getPackagedJars();

        if (variantConfiguration.getRenderscriptSupportMode()) {
            File renderScriptSupportJar = getRenderScriptSupportJar();

            Set<File> fullJars = Sets.newHashSetWithExpectedSize(packagedJars.size() + 1);
            fullJars.addAll(packagedJars);
            if (renderScriptSupportJar != null) {
                fullJars.add(renderScriptSupportJar);
            }
            packagedJars = fullJars;
        }

        return packagedJars;
    }

    /**
     * Returns the native lib folder for the renderscript mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the folder, or null.
     *
     * @see #setTargetInfo(com.android.builder.sdk.SdkInfo, com.android.builder.sdk.TargetInfo)
     */
    @Nullable
    public File getSupportNativeLibFolder() {
        if (mTargetInfo != null) {
            return RenderScriptProcessor.getSupportNativeLibFolder(
                    mTargetInfo.getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    /**
     * Returns an {@link PngCruncher} using aapt underneath
     * @return an PngCruncher object
     */
    @NonNull
    public PngCruncher getAaptCruncher() {
        checkState(mTargetInfo != null,
                "Cannot call getAaptCruncher() before setTargetInfo() is called.");
        return new AaptCruncher(
                mTargetInfo.getBuildTools().getPath(BuildToolInfo.PathId.AAPT),
                mCmdLineRunner);
    }

    @NonNull
    public CommandLineRunner getCommandLineRunner() {
        return mCmdLineRunner;
    }

    @NonNull
    public static ClassField createClassField(@NonNull String type, @NonNull String name, @NonNull String value) {
        return new ClassFieldImpl(type, name, value);
    }

    /**
     * Invoke the Manifest Merger version 2.
     */
    public void mergeManifests(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<? extends ManifestDependency> libraries,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @NonNull String outManifestLocation,
            ManifestMerger2.MergeType mergeType,
            Map<String, String> placeHolders) {

        try {
            Invoker manifestMergerInvoker =
                    ManifestMerger2.newMerger(mainManifest, mLogger, mergeType)
                    .setPlaceHolderValues(placeHolders)
                    .addFlavorAndBuildTypeManifests(
                            manifestOverlays.toArray(new File[manifestOverlays.size()]))
                    .addLibraryManifests(collectLibraries(libraries));

            if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
            }

            setInjectableValues(manifestMergerInvoker,
                    packageOverride, versionCode, versionName,
                    minSdkVersion, targetSdkVersion, maxSdkVersion);

            MergingReport mergingReport = manifestMergerInvoker.merge();
            mLogger.info("Merging result:" + mergingReport.getResult());
            switch (mergingReport.getResult()) {
                case WARNING:
                    mergingReport.log(mLogger);
                    // fall through since these are just warnings.
                case SUCCESS:
                    XmlDocument xmlDocument = mergingReport.getMergedDocument().get();
                    try {
                        String annotatedDocument = mergingReport.getActions().blame(xmlDocument);
                        mLogger.verbose(annotatedDocument);
                    } catch (Exception e) {
                        mLogger.error(e, "cannot print resulting xml");
                    }
                    save(xmlDocument, new File(outManifestLocation));
                    mLogger.info("Merged manifest saved to " + outManifestLocation);
                    break;
                case ERROR:
                    mergingReport.log(mLogger);
                    throw new RuntimeException(mergingReport.getReportString());
                default:
                    throw new RuntimeException("Unhandled result type : "
                            + mergingReport.getResult());
            }
        } catch (ManifestMerger2.MergeFailureException e) {
            // TODO: unacceptable.
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the {@link com.android.manifmerger.ManifestMerger2.SystemProperty} that can be injected
     * in the manifest file.
     */
    private static void setInjectableValues(
            ManifestMerger2.Invoker<?> invoker,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion) {

        if (!Strings.isNullOrEmpty(packageOverride)) {
            invoker.setOverride(SystemProperty.PACKAGE, packageOverride);
        }
        if (versionCode > 0) {
            invoker.setOverride(SystemProperty.VERSION_CODE,
                    String.valueOf(versionCode));
        }
        if (!Strings.isNullOrEmpty(versionName)) {
            invoker.setOverride(SystemProperty.VERSION_NAME, versionName);
        }
        if (!Strings.isNullOrEmpty(minSdkVersion)) {
            invoker.setOverride(SystemProperty.MIN_SDK_VERSION, minSdkVersion);
        }
        if (!Strings.isNullOrEmpty(targetSdkVersion)) {
            invoker.setOverride(SystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
        }
        if (maxSdkVersion != null) {
            invoker.setOverride(SystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
        }
    }

    /**
     * Saves the {@link com.android.manifmerger.XmlDocument} to a file in UTF-8 encoding.
     * @param xmlDocument xml document to save.
     * @param out file to save to.
     */
    private void save(XmlDocument xmlDocument, File out) {
        try {
            Files.write(xmlDocument.prettyPrint(), out, Charsets.UTF_8);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Collect the list of libraries' manifest files.
     * @param libraries declared dependencies
     * @return a list of files and names for the libraries' manifest files.
     */
    private static ImmutableList<Pair<String, File>> collectLibraries(
            List<? extends ManifestDependency> libraries) {

        ImmutableList.Builder<Pair<String, File>> manifestFiles = ImmutableList.builder();
        if (libraries != null) {
            collectLibraries(libraries, manifestFiles);
        }
        return manifestFiles.build();
    }

    /**
     * recursively calculate the list of libraries to merge the manifests files from.
     * @param libraries the dependencies
     * @param manifestFiles list of files and names identifiers for the libraries' manifest files.
     */
    private static void collectLibraries(List<? extends ManifestDependency> libraries,
            ImmutableList.Builder<Pair<String, File>> manifestFiles) {

        for (ManifestDependency library : libraries) {
            manifestFiles.add(Pair.of(library.getName(), library.getManifest()));
            List<? extends ManifestDependency> manifestDependencies = library
                    .getManifestDependencies();
            if (!manifestDependencies.isEmpty()) {
                collectLibraries(manifestDependencies, manifestFiles);
            }
        }
    }

    /**
     * Merges all the manifests into a single manifest
     *
     * @param mainManifest The main manifest of the application.
     * @param manifestOverlays manifest overlays coming from flavors and build types
     * @param libraries the library dependency graph
     * @param packageOverride a package name override. Can be null.
     * @param versionCode a version code to inject in the manifest or -1 to do nothing.
     * @param versionName a version name to inject in the manifest or null to do nothing.
     * @param minSdkVersion a minSdkVersion to inject in the manifest or -1 to do nothing.
     * @param targetSdkVersion a targetSdkVersion to inject in the manifest or -1 to do nothing.
     * @param outManifestLocation the output location for the merged manifest
     *
     * @see VariantConfiguration#getMainManifest()
     * @see VariantConfiguration#getManifestOverlays()
     * @see VariantConfiguration#getDirectLibraries()
     * @see VariantConfiguration#getMergedFlavor()
     * @see DefaultProductFlavor#getVersionCode()
     * @see DefaultProductFlavor#getVersionName()
     * @see DefaultProductFlavor#getMinSdkVersion()
     * @see DefaultProductFlavor#getTargetSdkVersion()
     */
    public void processManifest(
            @NonNull  File mainManifest,
            @NonNull  List<File> manifestOverlays,
            @NonNull  List<? extends ManifestDependency> libraries,
                      String packageOverride,
                      int versionCode,
                      String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull  String outManifestLocation) {
        checkNotNull(mainManifest, "mainManifest cannot be null.");
        checkNotNull(manifestOverlays, "manifestOverlays cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifestLocation, "outManifestLocation cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call processManifest() before setTargetInfo() is called.");

        final IAndroidTarget target = mTargetInfo.getTarget();

        ICallback callback = new ICallback() {
            @Override
            public int queryCodenameApiLevel(@NonNull String codename) {
                if (codename.equals(target.getVersion().getCodename())) {
                    return target.getVersion().getApiLevel();
                }
                return ICallback.UNKNOWN_CODENAME;
            }
        };

        try {
            Map<String, String> attributeInjection = getAttributeInjectionMap(
                    versionCode, versionName, minSdkVersion, targetSdkVersion);

            if (manifestOverlays.isEmpty() && libraries.isEmpty()) {
                // if no manifest to merge, just copy to location, unless we have to inject
                // attributes
                if (attributeInjection.isEmpty() && packageOverride == null) {
                    SdkUtils.copyXmlWithSourceReference(mainManifest,
                            new File(outManifestLocation));
                } else {
                    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger),
                            callback);
                    doMerge(merger, new File(outManifestLocation), mainManifest,
                            attributeInjection, packageOverride);
                }
            } else {
                File outManifest = new File(outManifestLocation);

                // first merge the app manifest.
                if (!manifestOverlays.isEmpty()) {
                    File mainManifestOut = outManifest;

                    // if there is also libraries, put this in a temp file.
                    if (!libraries.isEmpty()) {
                        // TODO find better way of storing intermediary file?
                        mainManifestOut = File.createTempFile("manifestMerge", ".xml");
                        mainManifestOut.deleteOnExit();
                    }

                    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger),
                            callback);
                    doMerge(merger, mainManifestOut, mainManifest, manifestOverlays,
                            attributeInjection, packageOverride);

                    // now the main manifest is the newly merged one
                    mainManifest = mainManifestOut;
                    // and the attributes have been inject, no need to do it below
                    attributeInjection = null;
                }

                if (!libraries.isEmpty()) {
                    // recursively merge all manifests starting with the leaves and up toward the
                    // root (the app)
                    mergeLibraryManifests(mainManifest, libraries,
                            new File(outManifestLocation), attributeInjection, packageOverride,
                            callback);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and off
     * @param functionalTest whether or not the Instrumentation class should run as a functional test
     * @param libraries the library dependency graph
     * @param outManifest the output location for the merged manifest
     *
     * @see VariantConfiguration#getApplicationId()
     * @see VariantConfiguration#getTestedConfig()
     * @see VariantConfiguration#getMinSdkVersion()
     * @see VariantConfiguration#getTestedApplicationId()
     * @see VariantConfiguration#getInstrumentationRunner()
     * @see VariantConfiguration#getHandleProfiling()
     * @see VariantConfiguration#getFunctionalTest()
     * @see VariantConfiguration#getDirectLibraries()
     */
    public void processTestManifest(
            @NonNull  String testApplicationId,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull  String testedApplicationId,
            @NonNull  String instrumentationRunner,
            @NonNull  Boolean handleProfiling,
            @NonNull  Boolean functionalTest,
            @NonNull  List<? extends ManifestDependency> libraries,
            @NonNull  File outManifest) {
        checkNotNull(testApplicationId, "testApplicationId cannot be null.");
        checkNotNull(testedApplicationId, "testedApplicationId cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(handleProfiling, "handleProfiling cannot be null.");
        checkNotNull(functionalTest, "functionalTest cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifest, "outManifestLocation cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call processTestManifest() before setTargetInfo() is called.");

        final IAndroidTarget target = mTargetInfo.getTarget();

        ICallback callback = new ICallback() {
            @Override
            public int queryCodenameApiLevel(@NonNull String codename) {
                if (codename.equals(target.getVersion().getCodename())) {
                    return target.getVersion().getApiLevel();
                }
                return ICallback.UNKNOWN_CODENAME;
            }
        };

        if (!libraries.isEmpty()) {
            try {
                // create the test manifest, merge the libraries in it
                File generatedTestManifest = File.createTempFile("manifestMerge", ".xml");

                generateTestManifest(
                        testApplicationId,
                        minSdkVersion,
                        targetSdkVersion,
                        testedApplicationId,
                        instrumentationRunner,
                        handleProfiling,
                        functionalTest,
                        generatedTestManifest);

                mergeLibraryManifests(
                        generatedTestManifest,
                        libraries,
                        outManifest,
                        null, null,
                        callback);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            generateTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest,
                    outManifest);
        }
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and off
     * @param functionalTest whether or not the Instrumentation class should run as a functional test
     * @param testManifestFile optionally user provided AndroidManifest.xml for testing application
     * @param libraries the library dependency graph
     * @param outManifest the output location for the merged manifest
     *
     * @see VariantConfiguration#getApplicationId()
     * @see VariantConfiguration#getTestedConfig()
     * @see VariantConfiguration#getMinSdkVersion()
     * @see VariantConfiguration#getTestedApplicationId()
     * @see VariantConfiguration#getInstrumentationRunner()
     * @see VariantConfiguration#getHandleProfiling()
     * @see VariantConfiguration#getFunctionalTest()
     * @see VariantConfiguration#getDirectLibraries()
     */
    public void processTestManifest2(
            @NonNull  String testApplicationId,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull  String testedApplicationId,
            @NonNull  String instrumentationRunner,
            @NonNull  Boolean handleProfiling,
            @NonNull  Boolean functionalTest,
            @Nullable File testManifestFile,
            @NonNull  List<? extends ManifestDependency> libraries,
            @NonNull  File outManifest,
            @NonNull  File tmpDir) {
        checkNotNull(testApplicationId, "testApplicationId cannot be null.");
        checkNotNull(testedApplicationId, "testedApplicationId cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(handleProfiling, "handleProfiling cannot be null.");
        checkNotNull(functionalTest, "functionalTest cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifest, "outManifestLocation cannot be null.");

        try {
            tmpDir.mkdirs();
            File generatedTestManifest = libraries.isEmpty() && testManifestFile == null
                    ? outManifest : File.createTempFile("manifestMerger", ".xml", tmpDir);

            mLogger.verbose("Generating in %1$s", generatedTestManifest.getAbsolutePath());
            generateTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion.equals("-1") ? null : targetSdkVersion,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest,
                    generatedTestManifest);

            if (testManifestFile != null) {
                File mergedTestManifest = File.createTempFile("manifestMerger", ".xml", tmpDir);
                mLogger.verbose("Merging user supplied manifest in %1$s",
                        generatedTestManifest.getAbsolutePath());
                Invoker invoker = ManifestMerger2.newMerger(
                        testManifestFile, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .setOverride(SystemProperty.PACKAGE, testApplicationId)
                        .setPlaceHolderValue(PlaceholderHandler.INSTRUMENTATION_RUNNER,
                                instrumentationRunner)
                        .addLibraryManifests(generatedTestManifest);
                if (minSdkVersion != null) {
                    invoker.setOverride(SystemProperty.MIN_SDK_VERSION, minSdkVersion);
                }
                if (!targetSdkVersion.equals("-1")) {
                    invoker.setOverride(SystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
                }
                MergingReport mergingReport = invoker.merge();
                if (libraries.isEmpty()) {
                    handleMergingResult(mergingReport, outManifest);
                } else {
                    handleMergingResult(mergingReport, mergedTestManifest);
                    generatedTestManifest = mergedTestManifest;
                }
            }

            if (!libraries.isEmpty()) {
                MergingReport mergingReport = ManifestMerger2.newMerger(
                        generatedTestManifest, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                        .setOverride(SystemProperty.PACKAGE, testApplicationId)
                        .addLibraryManifests(collectLibraries(libraries))
                        .merge();

                handleMergingResult(mergingReport, outManifest);
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleMergingResult(@NonNull MergingReport mergingReport, @NonNull File outFile) {
        switch (mergingReport.getResult()) {
            case WARNING:
                mergingReport.log(mLogger);
                // fall through since these are just warnings.
            case SUCCESS:
                XmlDocument xmlDocument = mergingReport.getMergedDocument().get();
                try {
                    String annotatedDocument = mergingReport.getActions().blame(xmlDocument);
                    mLogger.verbose(annotatedDocument);
                } catch (Exception e) {
                    mLogger.error(e, "cannot print resulting xml");
                }
                save(xmlDocument, outFile);
                mLogger.info("Merged manifest saved to " + outFile);
                break;
            case ERROR:
                mergingReport.log(mLogger);
                throw new RuntimeException(mergingReport.getReportString());
            default:
                throw new RuntimeException("Unhandled result type : "
                        + mergingReport.getResult());
        }
    }

    private static void generateTestManifest(
            @NonNull String testApplicationId,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull String testedApplicationId,
            @NonNull String instrumentationRunner,
            @NonNull Boolean handleProfiling,
            @NonNull Boolean functionalTest,
            @NonNull File outManifestLocation) {
        TestManifestGenerator generator = new TestManifestGenerator(
                outManifestLocation,
                testApplicationId,
                minSdkVersion,
                targetSdkVersion,
                testedApplicationId,
                instrumentationRunner,
                handleProfiling,
                functionalTest);
        try {
            generator.generate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static Map<String, String> getAttributeInjectionMap(
                      int versionCode,
            @Nullable String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion) {

        Map<String, String> attributeInjection = Maps.newHashMap();

        if (versionCode != -1) {
            attributeInjection.put(
                    "/manifest|http://schemas.android.com/apk/res/android versionCode",
                    Integer.toString(versionCode));
        }

        if (versionName != null) {
            attributeInjection.put(
                    "/manifest|http://schemas.android.com/apk/res/android versionName",
                    versionName);
        }

        if (minSdkVersion != null) {
            attributeInjection.put(
                    "/manifest/uses-sdk|http://schemas.android.com/apk/res/android minSdkVersion",
                    minSdkVersion);
        }

        if (targetSdkVersion != null) {
            attributeInjection.put(
                    "/manifest/uses-sdk|http://schemas.android.com/apk/res/android targetSdkVersion",
                    targetSdkVersion);
        }
        return attributeInjection;
    }

    /**
     * Merges library manifests into a main manifest.
     * @param mainManifest the main manifest
     * @param directLibraries the libraries to merge
     * @param outManifest the output file
     * @throws IOException
     */
    private void mergeLibraryManifests(
            File mainManifest,
            Iterable<? extends ManifestDependency> directLibraries,
            File outManifest, Map<String, String> attributeInjection,
            String packageOverride,
            @NonNull ICallback callback)
            throws IOException {

        List<File> manifests = Lists.newArrayList();
        for (ManifestDependency library : directLibraries) {
            Collection<? extends ManifestDependency> subLibraries = library.getManifestDependencies();
            if (subLibraries.isEmpty()) {
                manifests.add(library.getManifest());
            } else {
                File mergeLibManifest = File.createTempFile("manifestMerge", ".xml");
                mergeLibManifest.deleteOnExit();

                // don't insert the attribute injection into libraries
                mergeLibraryManifests(
                        library.getManifest(), subLibraries, mergeLibManifest, null, null, callback);

                manifests.add(mergeLibManifest);
            }
        }

        ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger), callback);
        doMerge(merger, outManifest, mainManifest, manifests, attributeInjection, packageOverride);
    }

    private void doMerge(ManifestMerger merger, File output, File input,
                               Map<String, String> injectionMap, String packageOverride) {
        List<File> list = Collections.emptyList();
        doMerge(merger, output, input, list, injectionMap, packageOverride);
    }

    private void doMerge(ManifestMerger merger, File output, File input, List<File> subManifests,
                               Map<String, String> injectionMap, String packageOverride) {
        if (!merger.process(output, input,
                subManifests.toArray(new File[subManifests.size()]),
                injectionMap, packageOverride)) {
            throw new RuntimeException("Manifest merging failed. See console for more info.");
        }
    }

    /**
     * Process the resources and generate R.java and/or the packaged resources.
     *
     * @param manifestFile the location of the manifest file
     * @param resFolder the merged res folder
     * @param assetsDir the merged asset folder
     * @param libraries the flat list of libraries
     * @param packageForR Package override to generate the R class in a different package.
     * @param sourceOutputDir optional source folder to generate R.java
     * @param resPackageOutput optional filepath for packaged resources
     * @param proguardOutput optional filepath for proguard file to generate
     * @param type the type of the variant being built
     * @param debuggable whether the app is debuggable
     * @param options the {@link com.android.builder.model.AaptOptions}
     * @param resourceConfigs a list of resource config filters to pass to aapt.
     * @param enforceUniquePackageName if true method will fail if some libraries share the same
     *                                 package name
     * @param splits optional list of split dimensions values (like a density or an abi). This
     *               will be used by aapt to generate the corresponding pure split apks.
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void processResources(
            @NonNull  File manifestFile,
            @NonNull  File resFolder,
            @Nullable File assetsDir,
            @Nullable  List<? extends SymbolFileProvider> libraries,
            @Nullable String packageForR,
            @Nullable String sourceOutputDir,
            @Nullable String symbolOutputDir,
            @Nullable String resPackageOutput,
            @Nullable String proguardOutput,
                      VariantConfiguration.Type type,
                      boolean debuggable,
            @NonNull  AaptOptions options,
            @NonNull  Collection<String> resourceConfigs,
                      boolean enforceUniquePackageName,
            @Nullable Collection<String> splits)
            throws IOException, InterruptedException, LoggedErrorException {

        checkNotNull(manifestFile, "manifestFile cannot be null.");
        checkNotNull(resFolder, "resFolder cannot be null.");
        checkNotNull(options, "options cannot be null.");
        // if both output types are empty, then there's nothing to do and this is an error
        checkArgument(sourceOutputDir != null || resPackageOutput != null,
                "No output provided for aapt task");
        checkState(mTargetInfo != null,
                "Cannot call processResources() before setTargetInfo() is called.");
        if (symbolOutputDir != null || sourceOutputDir != null) {
            checkNotNull(libraries, "libraries cannot be null if symbolOutputDir or sourceOutputDir is non-null");
        }

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();
        IAndroidTarget target = mTargetInfo.getTarget();

        // launch aapt: create the command line
        ArrayList<String> command = Lists.newArrayList();

        String aapt = buildToolInfo.getPath(BuildToolInfo.PathId.AAPT);
        if (aapt == null || !new File(aapt).isFile()) {
            throw new IllegalStateException("aapt is missing");
        }

        command.add(aapt);
        command.add("package");

        if (mVerboseExec) {
            command.add("-v");
        }

        command.add("-f");
        command.add("--no-crunch");

        // inputs
        command.add("-I");
        command.add(target.getPath(IAndroidTarget.ANDROID_JAR));

        command.add("-M");
        command.add(manifestFile.getAbsolutePath());

        if (resFolder.isDirectory()) {
            command.add("-S");
            command.add(resFolder.getAbsolutePath());
        }

        if (assetsDir != null && assetsDir.isDirectory()) {
            command.add("-A");
            command.add(assetsDir.getAbsolutePath());
        }

        // outputs

        if (sourceOutputDir != null) {
            command.add("-m");
            command.add("-J");
            command.add(sourceOutputDir);
        }

        if (resPackageOutput != null) {
            command.add("-F");
            command.add(resPackageOutput);
        }

        if (proguardOutput != null) {
            command.add("-G");
            command.add(proguardOutput);
        }

        if (splits != null) {
            for (String split : splits) {

                command.add("--split");
                command.add(split);
            }
        }

        // options controlled by build variants

        if (debuggable) {
            command.add("--debug-mode");
        }


        if (type != VariantConfiguration.Type.TEST) {
            if (packageForR != null) {
                command.add("--custom-package");
                command.add(packageForR);
                mLogger.verbose("Custom package for R class: '%s'", packageForR);
            }
        }

        // library specific options
        if (type == VariantConfiguration.Type.LIBRARY) {
            command.add("--non-constant-id");
        }

        // AAPT options
        String ignoreAssets = options.getIgnoreAssets();
        if (ignoreAssets != null) {
            command.add("--ignore-assets");
            command.add(ignoreAssets);
        }

        if (options.getFailOnMissingConfigEntry()) {
            if (buildToolInfo.getRevision().getMajor() > 20) {
                command.add("--error-on-missing-config-entry");
            } else {
                throw new IllegalStateException("aaptOptions:failOnMissingConfigEntry cannot be used"
                        + " with SDK Build Tools revision earlier than 21.0.0");
            }
        }

        // never compress apks.
        command.add("-0");
        command.add("apk");

        // add custom no-compress extensions
        Collection<String> noCompressList = options.getNoCompress();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                command.add("-0");
                command.add(noCompress);
            }
        }

        if (!resourceConfigs.isEmpty()) {
            command.add("-c");

            Joiner joiner = Joiner.on(',');
            command.add(joiner.join(resourceConfigs));
        }

        if (symbolOutputDir != null &&
                (type == VariantConfiguration.Type.LIBRARY || !libraries.isEmpty())) {
            command.add("--output-text-symbols");
            command.add(symbolOutputDir);
        }

        mCmdLineRunner.runCmdLine(command, null);

        // now if the project has libraries, R needs to be created for each libraries,
        // but only if the current project is not a library.
        if (sourceOutputDir != null && type != VariantConfiguration.Type.LIBRARY && !libraries.isEmpty()) {
            SymbolLoader fullSymbolValues = null;

            // First pass processing the libraries, collecting them by packageName,
            // and ignoring the ones that have the same package name as the application
            // (since that R class was already created).
            String appPackageName = packageForR;
            if (appPackageName == null) {
                appPackageName = VariantConfiguration.getManifestPackage(manifestFile);
            }

            // list of all the symbol loaders per package names.
            Multimap<String, SymbolLoader> libMap = ArrayListMultimap.create();

            for (SymbolFileProvider lib : libraries) {
                String packageName = VariantConfiguration.getManifestPackage(lib.getManifest());
                if (appPackageName == null) {
                    continue;
                }

                if (appPackageName.equals(packageName)) {
                    if (enforceUniquePackageName) {
                        String msg = String.format(
                                "Error: A library uses the same package as this project: %s\n" +
                                        "You can temporarily disable this error with android.enforceUniquePackageName=false\n" +
                                        "However, this is temporary and will be enforced in 1.0",
                                packageName);
                        throw new RuntimeException(msg);
                    }

                    // ignore libraries that have the same package name as the app
                    continue;
                }

                File rFile = lib.getSymbolFile();
                // if the library has no resource, this file won't exist.
                if (rFile.isFile()) {


                    // load the full values if that's not already been done.
                    // Doing it lazily allow us to support the case where there's no
                    // resources anywhere.
                    if (fullSymbolValues == null) {
                        fullSymbolValues = new SymbolLoader(new File(symbolOutputDir, "R.txt"),
                                mLogger);
                        fullSymbolValues.load();
                    }

                    SymbolLoader libSymbols = new SymbolLoader(rFile, mLogger);
                    libSymbols.load();


                    // store these symbols by associating them with the package name.
                    libMap.put(packageName, libSymbols);
                }
            }

            // now loop on all the package name, merge all the symbols to write, and write them
            for (String packageName : libMap.keySet()) {
                Collection<SymbolLoader> symbols = libMap.get(packageName);

                if (enforceUniquePackageName && symbols.size() > 1) {
                    String msg = String.format(
                            "Error: more than one library with package name '%s'\n" +
                            "You can temporarily disable this error with android.enforceUniquePackageName=false\n" +
                            "However, this is temporary and will be enforced in 1.0", packageName);
                    throw new RuntimeException(msg);
                }

                SymbolWriter writer = new SymbolWriter(sourceOutputDir, packageName,
                        fullSymbolValues);
                for (SymbolLoader symbolLoader : symbols) {
                    writer.addSymbolsToWrite(symbolLoader);
                }
                writer.write();
            }
        }
    }

    public void generateApkData(@NonNull File apkFile,
                                @NonNull File outResFolder,
                                @NonNull String mainPkgName,
                                @NonNull String resName)
            throws InterruptedException, LoggedErrorException, IOException {

        // need to run aapt to get apk information
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        // launch aapt: create the command line
        ArrayList<String> command = Lists.newArrayList();

        String aapt = buildToolInfo.getPath(BuildToolInfo.PathId.AAPT);
        if (aapt == null || !new File(aapt).isFile()) {
            throw new IllegalStateException("aapt is missing");
        }

        command.add(aapt);
        command.add("dump");
        command.add("badging");
        command.add(apkFile.getPath());

        final List<String> aaptOutput = Lists.newArrayList();

        mCmdLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        Pattern p = Pattern.compile("^package: name='(.+)' versionCode='([0-9]*)' versionName='(.*)'$");

        String pkgName = null, versionCode = null, versionName = null;

        for (String line : aaptOutput) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pkgName = m.group(1);
                versionCode = m.group(2);
                versionName = m.group(3);
                break;
            }
        }

        if (pkgName == null) {
            throw new RuntimeException("Failed to find apk information with aapt");
        }

        if (!pkgName.equals(mainPkgName)) {
            throw new RuntimeException("The main and the micro apps do not have the same package name.");
        }

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<wearableApp package=\"%1$s\">\n" +
                "    <versionCode>%2$s</versionCode>\n" +
                "    <versionName>%3$s</versionName>\n" +
                "    <rawPathResId>%4$s</rawPathResId>\n" +
                "</wearableApp>", pkgName, versionCode, versionName, resName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        resXmlFile.mkdirs();

        Files.write(content,
                new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML),
                Charsets.UTF_8);
    }

    public void generateApkDataEntryInManifest(@NonNull File manifestFile)
            throws InterruptedException, LoggedErrorException, IOException {

        String content =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest package=\"\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application>\n" +
                        "        <meta-data android:name=\"" + ANDROID_WEAR + "\"\n" +
                "                   android:resource=\"@xml/" + ANDROID_WEAR_MICRO_APK + "\" />\n" +
                "    </application>\n" +
                "</manifest>\n";

        Files.write(content, manifestFile, Charsets.UTF_8);
    }

    /**
     * Compiles all the aidl files found in the given source folders.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param importFolders import folders
     * @param dependencyFileProcessor the dependencyFileProcessor to record the dependencies
     *                                of the compilation.
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void compileAllAidlFiles(@NonNull List<File> sourceFolders,
                                    @NonNull File sourceOutputDir,
                                    @Nullable File parcelableOutputDir,
                                    @NonNull List<File> importFolders,
                                    @Nullable DependencyFileProcessor dependencyFileProcessor)
            throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAllAidlFiles() before setTargetInfo() is called.");

        IAndroidTarget target = mTargetInfo.getTarget();
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String aidl = buildToolInfo.getPath(BuildToolInfo.PathId.AIDL);
        if (aidl == null || !new File(aidl).isFile()) {
            throw new IllegalStateException("aidl is missing");
        }

        List<File> fullImportList = Lists.newArrayListWithCapacity(
                sourceFolders.size() + importFolders.size());
        fullImportList.addAll(sourceFolders);
        fullImportList.addAll(importFolders);

        AidlProcessor processor = new AidlProcessor(
                aidl,
                target.getPath(IAndroidTarget.ANDROID_AIDL),
                fullImportList,
                sourceOutputDir,
                parcelableOutputDir,
                dependencyFileProcessor != null ?
                        dependencyFileProcessor : sNoOpDependencyFileProcessor,
                mCmdLineRunner);

        SourceSearcher searcher = new SourceSearcher(sourceFolders, "aidl");
        searcher.setUseExecutor(true);
        searcher.search(processor);
    }

    /**
     * Compiles the given aidl file.
     *
     * @param aidlFile the AIDL file to compile
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param importFolders all the import folders, including the source folders.
     * @param dependencyFileProcessor the dependencyFileProcessor to record the dependencies
     *                                of the compilation.
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void compileAidlFile(@NonNull File sourceFolder,
                                @NonNull File aidlFile,
                                @NonNull File sourceOutputDir,
                                @Nullable File parcelableOutputDir,
                                @NonNull List<File> importFolders,
                                @Nullable DependencyFileProcessor dependencyFileProcessor)
            throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(aidlFile, "aidlFile cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAidlFile() before setTargetInfo() is called.");

        IAndroidTarget target = mTargetInfo.getTarget();
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String aidl = buildToolInfo.getPath(BuildToolInfo.PathId.AIDL);
        if (aidl == null || !new File(aidl).isFile()) {
            throw new IllegalStateException("aidl is missing");
        }

        AidlProcessor processor = new AidlProcessor(
                aidl,
                target.getPath(IAndroidTarget.ANDROID_AIDL),
                importFolders,
                sourceOutputDir,
                parcelableOutputDir,
                dependencyFileProcessor != null ?
                        dependencyFileProcessor : sNoOpDependencyFileProcessor,
                mCmdLineRunner);

        processor.processFile(sourceFolder, aidlFile);
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     * Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     * Therefore whenever a renderscript file or header changes, all must be recompiled.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param importFolders all the import folders.
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param resOutputDir the output dir in which to generate the bitcode file
     * @param targetApi the target api
     * @param debugBuild whether the build is debug
     * @param optimLevel the optimization level
     * @param ndkMode
     * @param supportMode support mode flag to generate .so files.
     * @param abiFilters ABI filters in case of support mode
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void compileAllRenderscriptFiles(@NonNull List<File> sourceFolders,
                                            @NonNull List<File> importFolders,
                                            @NonNull File sourceOutputDir,
                                            @NonNull File resOutputDir,
                                            @NonNull File objOutputDir,
                                            @NonNull File libOutputDir,
                                            int targetApi,
                                            boolean debugBuild,
                                            int optimLevel,
                                            boolean ndkMode,
                                            boolean supportMode,
                                            @Nullable Set<String> abiFilters)
            throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAllRenderscriptFiles() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException("llvm-rs-cc is missing");
        }

        RenderScriptProcessor processor = new RenderScriptProcessor(
                sourceFolders,
                importFolders,
                sourceOutputDir,
                resOutputDir,
                objOutputDir,
                libOutputDir,
                buildToolInfo,
                targetApi,
                debugBuild,
                optimLevel,
                ndkMode,
                supportMode,
                abiFilters);
        processor.build(mCmdLineRunner);
    }

    /**
     * Computes and returns the leaf folders based on a given file extension.
     *
     * This looks through all the given root import folders, and recursively search for leaf
     * folders containing files matching the given extensions. All the leaf folders are gathered
     * and returned in the list.
     *
     * @param extension the extension to search for.
     * @param importFolders an array of list of root folders.
     * @return a list of leaf folder, never null.
     */
    @NonNull
    public List<File> getLeafFolders(@NonNull String extension, List<File>... importFolders) {
        List<File> results = Lists.newArrayList();

        if (importFolders != null) {
            for (List<File> folders : importFolders) {
                SourceSearcher searcher = new SourceSearcher(folders, extension);
                searcher.setUseExecutor(false);
                LeafFolderGatherer processor = new LeafFolderGatherer();
                try {
                    searcher.search(processor);
                } catch (InterruptedException e) {
                    // wont happen as we're not using the executor, and our processor
                    // doesn't throw those.
                } catch (IOException e) {
                    // wont happen as we're not using the executor, and our processor
                    // doesn't throw those.
                } catch (LoggedErrorException e) {
                    // wont happen as we're not using the executor, and our processor
                    // doesn't throw those.
                }

                results.addAll(processor.getFolders());
            }
        }

        return results;
    }

    /**
     * Converts the bytecode to Dalvik format
     * @param inputs the input files
     * @param preDexedLibraries the list of pre-dexed libraries
     * @param outDexFolder the location of the output folder
     * @param dexOptions dex options
     * @param additionalParameters list of additional parameters to give to dx
     * @param incremental true if it should attempt incremental dex if applicable
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void convertByteCode(
            @NonNull Iterable<File> inputs,
            @NonNull Iterable<File> preDexedLibraries,
            @NonNull File outDexFolder,
                     boolean multidex,
            @NonNull DexOptions dexOptions,
            @Nullable List<String> additionalParameters,
            boolean incremental) throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(inputs, "inputs cannot be null.");
        checkNotNull(preDexedLibraries, "preDexedLibraries cannot be null.");
        checkNotNull(outDexFolder, "outDexFolder cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");
        checkArgument(outDexFolder.isDirectory(), "outDexFolder must be a folder");
        checkState(mTargetInfo != null,
                "Cannot call convertByteCode() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        checkState(
                !multidex || buildToolInfo.getRevision().compareTo(MIN_MULTIDEX_BUILD_TOOLS_REV) >= 0,
                "Multi dex requires Build Tools " + MIN_MULTIDEX_BUILD_TOOLS_REV.toString() + " / Current: " + buildToolInfo.getRevision().toShortString());

        // launch dx: create the command line
        ArrayList<String> command = Lists.newArrayList();

        String dx = buildToolInfo.getPath(BuildToolInfo.PathId.DX);
        if (dx == null || !new File(dx).isFile()) {
            throw new IllegalStateException("dx is missing");
        }

        command.add(dx);

        if (dexOptions.getJavaMaxHeapSize() != null) {
            command.add("-JXmx" + dexOptions.getJavaMaxHeapSize());
        }

        command.add("--dex");

        if (mVerboseExec) {
            command.add("--verbose");
        }

        if (dexOptions.getJumboMode()) {
            command.add("--force-jumbo");
        }

        if (incremental) {
            command.add("--incremental");
            command.add("--no-strict");
        }

        if (multidex) {
            command.add("--multi-dex");
        }

        /**
         * This seems to trigger some error in dx, so disable for now.
        if (dexOptions.getThreadCount() > 1) {
            command.add("--num-threads=" + dexOptions.getThreadCount());
        }
         */

        if (additionalParameters != null) {
            for (String arg : additionalParameters) {
                command.add(arg);
            }
        }

        command.add("--output");
        command.add(outDexFolder.getAbsolutePath());

        // clean up input list
        List<String> inputList = Lists.newArrayList();
        for (File f : inputs) {
            if (f != null && f.exists()) {
                inputList.add(f.getAbsolutePath());
            }
        }

        if (!inputList.isEmpty()) {
            mLogger.verbose("Dex inputs: " + inputList);
            command.addAll(inputList);
        }

        // clean up and add library inputs.
        List<String> libraryList = Lists.newArrayList();
        for (File f : preDexedLibraries) {
            if (f != null && f.exists()) {
                libraryList.add(f.getAbsolutePath());
            }
        }

        if (!libraryList.isEmpty()) {
            mLogger.verbose("Dex pre-dexed inputs: " + libraryList);
            command.addAll(libraryList);
        }

        mCmdLineRunner.runCmdLine(command, null);
    }

    /**
     * Converts the bytecode to Dalvik format
     * @param inputFile the input file
     * @param outFile the output file or folder if multi-dex is enabled.
     * @param multiDex whether multidex is enabled.
     * @param dexOptions dex options
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void preDexLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
                     boolean multiDex,
            @NonNull DexOptions dexOptions)
            throws IOException, InterruptedException, LoggedErrorException {
        checkState(mTargetInfo != null,
                "Cannot call preDexLibrary() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        PreDexCache.getCache().preDexLibrary(inputFile, outFile, multiDex, dexOptions,
                buildToolInfo, mVerboseExec, mCmdLineRunner);
    }

    /**
     * Converts the bytecode to Dalvik format
     *
     * @param inputFile the input file
     * @param outFile the output file or folder if multi-dex is enabled.
     * @param multiDex whether multidex is enabled.
     * @param dexOptions the dex options
     * @param buildToolInfo the build tools info
     * @param verbose verbose flag
     * @param commandLineRunner the command line runner
     * @return the list of generated files.
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    @NonNull
    public static List<File> preDexLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
            boolean multiDex,
            @NonNull DexOptions dexOptions,
            @NonNull BuildToolInfo buildToolInfo,
                     boolean verbose,
            @NonNull CommandLineRunner commandLineRunner)
            throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(inputFile, "inputFile cannot be null.");
        checkNotNull(outFile, "outFile cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");

        // launch dx: create the command line
        ArrayList<String> command = Lists.newArrayList();

        String dx = buildToolInfo.getPath(BuildToolInfo.PathId.DX);
        if (dx == null || !new File(dx).isFile()) {
            throw new IllegalStateException("dx is missing");
        }

        command.add(dx);

        if (dexOptions.getJavaMaxHeapSize() != null) {
            command.add("-JXmx" + dexOptions.getJavaMaxHeapSize());
        }

        command.add("--dex");

        if (verbose) {
            command.add("--verbose");
        }

        if (dexOptions.getJumboMode()) {
            command.add("--force-jumbo");
        }

        if (multiDex) {
            command.add("--multi-dex");
        }

        command.add("--output");
        command.add(outFile.getAbsolutePath());

        command.add(inputFile.getAbsolutePath());

        commandLineRunner.runCmdLine(command, null);

        if (multiDex) {
            File[] files = outFile.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String name) {
                    return name.endsWith(DOT_DEX);
                }
            });

            if (files == null || files.length == 0) {
                throw new RuntimeException("No dex files created at " + outFile.getAbsolutePath());
            }

            return Lists.newArrayList(files);
        } else {
            return Collections.singletonList(outFile);
        }
    }

    /**
     * Converts the bytecode of a library to the jack format
     * @param inputFile the input file
     * @param outFile the location of the output classes.dex file
     * @param dexOptions dex options
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    public void convertLibraryToJack(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull DexOptions dexOptions)
            throws IOException, InterruptedException, LoggedErrorException {
        checkState(mTargetInfo != null,
                "Cannot call preJackLibrary() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        JackConversionCache.getCache().convertLibrary(inputFile, outFile, dexOptions, buildToolInfo,
                mVerboseExec, mCmdLineRunner);
    }

    public static List<File> convertLibraryToJack(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull DexOptions dexOptions,
            @NonNull BuildToolInfo buildToolInfo,
            boolean verbose,
            @NonNull CommandLineRunner commandLineRunner)
            throws IOException, InterruptedException, LoggedErrorException {
        checkNotNull(inputFile, "inputFile cannot be null.");
        checkNotNull(outFile, "outFile cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");

        // launch dx: create the command line
        ArrayList<String> command = Lists.newArrayList();

        String jill = buildToolInfo.getPath(BuildToolInfo.PathId.JILL);
        if (jill == null || !new File(jill).isFile()) {
            throw new IllegalStateException("jill.jar is missing");
        }

        command.add("java");
        if (dexOptions.getJavaMaxHeapSize() != null) {
            command.add("-JXmx" + dexOptions.getJavaMaxHeapSize());
        }
        command.add("-jar");
        command.add(jill);
        command.add("--container");
        command.add("zip");
        command.add("--output");
        command.add(outFile.getAbsolutePath());
        command.add(inputFile.getAbsolutePath());

        commandLineRunner.runCmdLine(command, null);

        return Collections.singletonList(outFile);
    }

    /**
     * Packages the apk.
     *
     * @param androidResPkgLocation the location of the packaged resource file
     * @param dexFolder the folder with the dex file.
     * @param dexedLibraries optional collection of additional dex files to put in the apk.
     * @param packagedJars the jars that are packaged (libraries + jar dependencies)
     * @param javaResourcesLocation the processed Java resource folder
     * @param jniLibsFolders the folders containing jni shared libraries
     * @param abiFilters optional ABI filter
     * @param jniDebugBuild whether the app should include jni debug data
     * @param signingConfig the signing configuration
     * @param packagingOptions the packaging options
     * @param outApkLocation location of the APK.
     * @throws DuplicateFileException
     * @throws FileNotFoundException if the store location was not found
     * @throws KeytoolException
     * @throws PackagerException
     * @throws SigningException when the key cannot be read from the keystore
     *
     * @see VariantConfiguration#getPackagedJars()
     */
    public void packageApk(
            @NonNull String androidResPkgLocation,
            @NonNull File dexFolder,
            @Nullable Collection<File> dexedLibraries,
            @NonNull Collection<File> packagedJars,
            @Nullable String javaResourcesLocation,
            @Nullable Collection<File> jniLibsFolders,
            @Nullable Set<String> abiFilters,
            boolean jniDebugBuild,
            @Nullable SigningConfig signingConfig,
            @Nullable PackagingOptions packagingOptions,
            @NonNull String outApkLocation)
            throws DuplicateFileException, FileNotFoundException,
            KeytoolException, PackagerException, SigningException {
        checkNotNull(androidResPkgLocation, "androidResPkgLocation cannot be null.");
        checkNotNull(dexFolder, "dexFolder cannot be null.");
        checkArgument(dexFolder.isDirectory(), "dexFolder is not a directory");
        checkNotNull(outApkLocation, "outApkLocation cannot be null.");

        CertificateInfo certificateInfo = null;
        if (signingConfig != null && signingConfig.isSigningReady()) {
            //noinspection ConstantConditions
            certificateInfo = KeystoreHelper.getCertificateInfo(signingConfig.getStoreType(),
                    signingConfig.getStoreFile(), signingConfig.getStorePassword(),
                    signingConfig.getKeyPassword(), signingConfig.getKeyAlias());
            if (certificateInfo == null) {
                throw new SigningException("Failed to read key from keystore");
            }
        }

        try {
            Packager packager = new Packager(
                    outApkLocation, androidResPkgLocation, dexFolder,
                    certificateInfo, mCreatedBy, packagingOptions, mLogger);

            if (dexedLibraries != null) {
                for (File dexedLibrary : dexedLibraries) {
                    packager.addDexFile(dexedLibrary);
                }
            }

            packager.setJniDebugMode(jniDebugBuild);

            // figure out conflicts!
            JavaResourceProcessor resProcessor = new JavaResourceProcessor(packager);

            if (javaResourcesLocation != null) {
                resProcessor.addSourceFolder(javaResourcesLocation);
            }

            // add the resources from the jar files.
            Set<String> hashs = Sets.newHashSet();

            for (File jar : packagedJars) {
                // TODO remove once we can properly add a library as a dependency of its test.
                String hash = getFileHash(jar);
                if (hash == null) {
                    throw new PackagerException("Unable to compute hash of " + jar.getAbsolutePath());
                }
                if (hashs.contains(hash)) {
                    continue;
                }

                hashs.add(hash);

                packager.addResourcesFromJar(jar);
            }

            // also add resources from library projects and jars
            if (jniLibsFolders != null) {
                for (File jniFolder : jniLibsFolders) {
                    if (jniFolder.isDirectory()) {
                        packager.addNativeLibraries(jniFolder, abiFilters);
                    }
                }
            }

            packager.sealApk();
        } catch (SealedPackageException e) {
            // shouldn't happen since we control the package from start to end.
            throw new RuntimeException(e);
        }
    }

    /**
     * Signs a single jar file using the passed {@link SigningConfig}.
     * @param in the jar file to sign.
     * @param signingConfig the signing configuration
     * @param out the file path for the signed jar.
     * @throws IOException
     * @throws KeytoolException
     * @throws SigningException
     * @throws NoSuchAlgorithmException
     * @throws SignedJarBuilder.IZipEntryFilter.ZipAbortException
     * @throws com.android.builder.signing.SigningException
     */
    public void signApk(File in, SigningConfig signingConfig, File out)
            throws IOException, KeytoolException, SigningException, NoSuchAlgorithmException,
            SignedJarBuilder.IZipEntryFilter.ZipAbortException,
            com.android.builder.signing.SigningException {

        CertificateInfo certificateInfo = null;
        if (signingConfig != null && signingConfig.isSigningReady()) {
            certificateInfo = KeystoreHelper.getCertificateInfo(signingConfig.getStoreType(),
                    signingConfig.getStoreFile(), signingConfig.getStorePassword(),
                    signingConfig.getKeyPassword(), signingConfig.getKeyAlias());
            if (certificateInfo == null) {
                throw new SigningException("Failed to read key from keystore");
            }
        }

        SignedJarBuilder signedJarBuilder = new SignedJarBuilder(
                new FileOutputStream(out),
                certificateInfo != null ? certificateInfo.getKey() : null,
                certificateInfo != null ? certificateInfo.getCertificate() : null,
                Packager.getLocalVersion(), mCreatedBy);


        signedJarBuilder.writeZip(new FileInputStream(in), null);
        signedJarBuilder.close();

    }

    /**
     * Returns the hash of a file.
     * @param file the file to hash
     * @return the hash or null if an error happened
     */
    @Nullable
    private static String getFileHash(@NonNull File file) {
        try {
            HashCode hashCode = Files.hash(file, Hashing.sha1());
            return hashCode.toString();
        } catch (IOException ignored) {

        }

        return null;
    }

}
