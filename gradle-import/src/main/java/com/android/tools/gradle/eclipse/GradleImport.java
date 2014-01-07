/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.gradle.eclipse;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_SOURCES;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_NAME;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_NDK;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_SDK;
import static com.android.xml.AndroidManifest.NODE_INSTRUMENTATION;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.primitives.Bytes;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Importer which can generate Android Gradle projects.
 * <p>
 * It currently only supports importing ADT projects, so it will require
 * some tweaks to handle importing from other types of projects.
 * <p>
 * TODO:
 * <ul>
 *     <li>Migrate SDK folder from local.properties. If should make doubly sure that
 *         the repository you point to contains the app support library and other
 *         libraries that may be needed.</li>
 *     <li>Consider whether I can make this import mechanism work for Maven and plain
 *     sources as well?</li>
 *     <li>Make it optional whether we replace the directory structure with the Gradle one?</li>
 *     <li>Allow migrating a project in-place?</li>
 *     <li>If I have a workspace, check to see if there are problem markers and if
 *     so warn that the project may not be buildable</li>
 *     <li>Read SDK home out of local.properties and ask whether to use it or the Studio one
 *     (if they differ), and if the former, ensure it has all the gradle repositories we need</li>
 *     <li>Optional:  at the end of the import, migrate Eclipse settings too --
 *      such as code styles, compiler flags (especially those for the
 *      project), ask about enabling eclipse key bindings, etc?</li>
 *     <li>If replaceJars=false, insert *comments* in the source code for potential
 *     replacements such that users don't forget and consider switching in the future</li>
 *     <li>Figure out if we can reuse fragments from the default freemarker templates for
 *     the code generation part.</li>
 *     <li>Allow option to preserve module nesting hierarchy</li>
 *     <li>Make it possible to use this wizard to migrate an already exported Eclipse project?</li>
 *     <li>Consider making the export create an HTML file and open in browser?</li>
 * </ul>
 */
public class GradleImport {
    public static final String NL = SdkUtils.getLineSeparator();
    public static final int CURRENT_COMPILE_VERSION = 19;
    public static final String CURRENT_BUILD_TOOLS_VERSION = "19.0.1";
    public static final String ANDROID_GRADLE_PLUGIN =
            GRADLE_PLUGIN_NAME + GRADLE_PLUGIN_LATEST_VERSION;
    public static final String MAVEN_URL_PROPERTY = "android.mavenRepoUrl";
    private static final String WORKSPACE_PROPERTY = "android.eclipseWorkspace";

    static final String MAVEN_REPOSITORY;
    static {
        String repository = System.getProperty(MAVEN_URL_PROPERTY);
        if (repository == null) {
            repository = "mavenCentral()";
        } else {
            repository = "maven { url '" + repository + "' }";
        }
        MAVEN_REPOSITORY = repository;
    }

    public static final String ECLIPSE_DOT_CLASSPATH = ".classpath";
    public static final String ECLIPSE_DOT_PROJECT = ".project";
    public static final String IMPORT_SUMMARY_TXT = "import-summary.txt";

    /**
     * Whether we should place the repository definitions in the global build.gradle rather
     * than in each module
     */
    static final boolean DECLARE_GLOBAL_REPOSITORIES = true;

    private List<? extends ImportModule> mRootModules;
    private Set<ImportModule> mModules;
    private ImportSummary mSummary;
    private File mWorkspaceLocation;
    private File mGradleWrapperLocation;
    private File mSdkLocation;
    private File mNdkLocation;
    private SdkManager mSdkManager;
    private Set<String> mHandledJars = Sets.newHashSet();
    private Map<String,File> mWorkspaceProjects;

    /** Whether we should convert project names to lowercase module names */
    private boolean mGradleNameStyle = true;
    /** Whether we should try to replace jars with dependencies */
    private boolean mReplaceJars = true;
    /** Whether we should try to replace libs with dependencies */
    private boolean mReplaceLibs = true;

    private final List<String> mWarnings = Lists.newArrayList();
    private final List<String> mErrors = Lists.newArrayList();
    private Map<String, File> mPathMap = Maps.newTreeMap();

    public GradleImport() {
        String workspace = System.getProperty(WORKSPACE_PROPERTY);
        if (workspace != null) {
            mWorkspaceLocation = new File(workspace);
        }
    }

    /** Imports the given projects. Note that this just reads in the project state;
     * it does not actually write out a Gradle project. For that, you should call
     * {@link #exportProject(java.io.File, boolean)}.
     *
     * @param projectDirs the project directories to import
     * @throws IOException if something is wrong
     */
    public void importProjects(@NonNull List<File> projectDirs) throws IOException {
        mSummary = new ImportSummary(this);
        mProjectMap.clear();
        mHandledJars.clear();
        mWarnings.clear();
        mErrors.clear();
        mWorkspaceProjects = null;
        mRootModules = Collections.emptyList();
        mModules = Sets.newHashSet();

        for (File file : projectDirs) {
            if (file.isFile()) {
                assert !file.isDirectory();
                file = file.getParentFile();
            }

            guessWorkspace(file);

            if (isAdtProjectDir(file)) {
                guessSdk(file);
                guessNdk(file);

                try {
                    EclipseProject.getProject(this, file);
                } catch (ImportException e) {
                    // Already recorded
                    return;
                } catch (Exception e) {
                    reportError(null, file, e.toString(), false);
                    return;
                }
            } else {
                reportError(null, file, "Not a recognized project: " + file, false);
                return;
            }
        }

        // Find unique projects. (We can register projects under multiple paths
        // if the dir and the canonical dir differ, so pick unique values here)
        Set<EclipseProject> projects = Sets.newHashSet(mProjectMap.values());
        mRootModules = EclipseProject.performImport(this, projects);
        for (ImportModule module : mRootModules) {
            mModules.add(module);
            mModules.addAll(module.getAllDependencies());
        }
    }

    public static boolean isEclipseProjectDir(@Nullable File file) {
        return file != null && file.isDirectory()
                && new File(file, ECLIPSE_DOT_CLASSPATH).exists()
                && new File(file, ECLIPSE_DOT_PROJECT).exists();
    }

    public static boolean isAdtProjectDir(@Nullable File file) {
        return new File(file, ANDROID_MANIFEST_XML).exists() &&
                (isEclipseProjectDir(file) ||
                        (new File(file, FD_RES).exists() &&
                         new File(file, FD_SOURCES).exists()));
    }

    /** Sets location of gradle wrapper to copy into exported project, if known */
    @NonNull
    public GradleImport setGradleWrapperLocation(@NonNull File gradleWrapper) {
        mGradleWrapperLocation = gradleWrapper;
        return this;
    }

    /** Sets location of the SDK to use with the import, if known */
    @NonNull
    public GradleImport setSdkLocation(@Nullable File sdkLocation) {
        mSdkLocation = sdkLocation;
        return this;
    }

    /** Returns the location of the SDK to use with the import, if known */
    @Nullable
    public File getSdkLocation() {
        return mSdkLocation;
    }

    /** Sets SDK manager to use with the import, if known */
    @NonNull
    public GradleImport setSdkManager(@NonNull SdkManager sdkManager) {
        mSdkManager = sdkManager;
        mSdkLocation = new File(sdkManager.getLocation());
        return this;
    }

    @Nullable
    public SdkManager getSdkManager() {
        if (mSdkManager == null && mSdkLocation != null && mSdkLocation.exists()) {
            ILogger logger = new StdLogger(StdLogger.Level.INFO);
            mSdkManager = SdkManager.createManager(mSdkLocation.getPath(), logger);
        }

        return mSdkManager;
    }

    /** Sets location of the SDK to use with the import, if known */
    @NonNull
    public GradleImport setNdkLocation(@Nullable File ndkLocation) {
        mNdkLocation = ndkLocation;
        return this;
    }

    /** Gets location of the SDK to use with the import, if known */
    @Nullable
    public File getNdkLocation() {
        return mNdkLocation;
    }

    /** Sets location of Eclipse workspace, if known */
    public GradleImport setEclipseWorkspace(@NonNull File workspace) {
        mWorkspaceLocation = workspace;
        assert mWorkspaceLocation.exists() : workspace.getPath();
        mWorkspaceProjects = null;
        return this;
    }

    /** Gets location of Eclipse workspace, if known */
    @Nullable
    public File getEclipseWorkspace() {
        return mWorkspaceLocation;
    }

    /** Whether import should attempt to replace jars with dependencies */
    @NonNull
    public GradleImport setReplaceJars(boolean replaceJars) {
        mReplaceJars = replaceJars;
        return this;
    }

    /** Whether import should attempt to replace jars with dependencies */
    public boolean isReplaceJars() {
        return mReplaceJars;
    }

    /** Whether import should attempt to replace inlined library projects with dependencies */
    public boolean isReplaceLibs() {
        return mReplaceLibs;
    }

    /** Whether import should attempt to replace inlined library projects with dependencies */
    public GradleImport setReplaceLibs(boolean replaceLibs) {
        mReplaceLibs = replaceLibs;
        return this;
    }

    /** Whether import should lower-case module names from ADT project names */
    @NonNull
    public GradleImport setGradleNameStyle(boolean lowerCase) {
        mGradleNameStyle = lowerCase;
        return this;
    }

    /** Whether import should lower-case module names from ADT project names */
    public boolean isGradleNameStyle() {
        return mGradleNameStyle;
    }

    private void guessWorkspace(@NonNull File projectDir) {
        if (mWorkspaceLocation == null) {
            File dir = projectDir.getParentFile();
            while (dir != null) {
                if (isEclipseWorkspaceDir(dir)) {
                    setEclipseWorkspace(dir);
                    break;
                }
                dir = dir.getParentFile();
            }
        }
    }

    private void guessSdk(@NonNull File projectDir) {
        if (mSdkLocation == null) {
            mSdkLocation = getDirFromLocalProperties(projectDir, PROPERTY_SDK);

            if (mSdkLocation == null && mWorkspaceLocation != null) {
                mSdkLocation = getDirFromWorkspaceSetting(getAdtSettingsFile(),
                        "com.android.ide.eclipse.adt.sdk");
            }
        }
    }

    private void guessNdk(@NonNull File projectDir) {
        if (mNdkLocation == null) {
            mNdkLocation = getDirFromLocalProperties(projectDir, PROPERTY_NDK);

            if (mNdkLocation == null && mWorkspaceLocation != null) {
                mNdkLocation = getDirFromWorkspaceSetting(getNdkSettingsFile(), "ndkLocation");
            }
        }
    }

    @Nullable
    private static File getDirFromLocalProperties(@NonNull File projectDir,
            @NonNull String property) {
        File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);
        if (localProperties.exists()) {
            try {
                Properties properties = getProperties(localProperties);
                if (properties != null) {
                    String sdk = properties.getProperty(property);
                    if (sdk != null) {
                        File dir = new File(sdk);
                        if (dir.exists()) {
                            return dir;
                        } else {
                            dir = new File(sdk.replace('/', separatorChar));
                            if (dir.exists()) {
                                return dir;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // ignore properties
            }
        }

        return null;
    }

    private File getDirFromWorkspaceSetting(@NonNull File settings, @NonNull String property) {
        //noinspection VariableNotUsedInsideIf
        if (mWorkspaceLocation != null) {
            if (settings.exists()) {
                try {
                    Properties properties = getProperties(settings);
                    if (properties != null) {
                        String path = properties.getProperty(property);
                        File dir = new File(path);
                        if (dir.exists()) {
                            return dir;
                        } else {
                            dir = new File(path.replace('/', separatorChar));
                            if (dir.exists()) {
                                return dir;
                            }
                        }
                    }
                } catch (IOException e) {
                    // Ignore workspace data
                }
            }
        }

        return null;
    }

    public static boolean isEclipseWorkspaceDir(@NonNull File file) {
        return file.isDirectory() &&
                new File(file, ".metadata" + separator + "version.ini").exists();
    }

    @Nullable
    public File resolveWorkspacePath(@Nullable EclipseProject fromProject, @NonNull String path, boolean record) {
        if (path.isEmpty()) {
            return null;
        }

        // If file within project, must match on all prefixes
        for (Map.Entry<String,File> entry : mPathMap.entrySet()) {
            String workspacePath = entry.getKey();
            File file = entry.getValue();
            if (file != null && path.startsWith(workspacePath)) {
                if (path.equals(workspacePath)) {
                    return file;
                } else {
                    path = path.substring(workspacePath.length());
                    if (path.charAt(0) == '/' || path.charAt(0) == separatorChar) {
                        path = path.substring(1);
                    }
                    File resolved = new File(file, path.replace('/', separatorChar));
                    if (resolved.exists()) {
                        return resolved;
                    }
                }
            }
        }

        if (fromProject != null && mWorkspaceLocation == null) {
            guessWorkspace(fromProject.getDir());
        }

        if (mWorkspaceLocation != null) {
            // Is the file present directly in the workspace?
            char first = path.charAt(0);
            if (first != '/') {
                return null;
            }
            File f = new File(mWorkspaceLocation, path.substring(1).replace('/', separatorChar));
            if (f.exists()) {
                mPathMap.put(path, f);
                return f;
            }

            // Other files may be in other file systems, mapped by a .location link in the
            // workspace metadata
            if (mWorkspaceProjects == null) {
                mWorkspaceProjects = Maps.newHashMap();
                File projectDir = new File(mWorkspaceLocation, ".metadata" + separator + ".plugins"
                        + separator + "org.eclipse.core.resources" + separator + ".projects");
                File[] projects = projectDir.exists() ? projectDir.listFiles() : null;
                byte[] target = "URI//file:".getBytes(Charsets.US_ASCII);
                if (projects != null) {
                    for (File project : projects) {
                        File location = new File(project, ".location");
                        if (location.exists()) {
                            try {
                                byte[] bytes = Files.toByteArray(location);
                                int start = Bytes.indexOf(bytes, target);
                                if (start != -1) {
                                    int end = start + target.length;
                                    for (; end < bytes.length; end++) {
                                        if (bytes[end] == (byte)0) {
                                            break;
                                        }
                                    }
                                    try {
                                        int length = end - start;
                                        String s = new String(bytes, start, length, UTF_8);
                                        s = s.substring(5); // skip URI//
                                        File file = SdkUtils.urlToFile(s);
                                        if (file.exists()) {
                                            String name = project.getName();
                                            mWorkspaceProjects.put('/' + name, file);
                                            //noinspection ConstantConditions
                                        }
                                    } catch (Throwable t) {
                                        // Ignore binary data we can't read
                                    }
                                }
                            } catch (IOException e) {
                                reportWarning((ImportModule) null, location,
                                        "Can't read .location file");
                            }
                        }
                    }
                }
            }

            // Is it just a project root?
            File project = mWorkspaceProjects.get(path);
            if (project != null) {
                mPathMap.put(path, project);
                return project;
            }

            // If file within project, must match on all prefixes
            for (Map.Entry<String,File> entry : mWorkspaceProjects.entrySet()) {
                String workspacePath = entry.getKey();
                File file = entry.getValue();
                if (file != null && path.startsWith(workspacePath)) {
                    if (path.equals(workspacePath)) {
                        return file;
                    } else {
                        path = path.substring(workspacePath.length());
                        if (path.charAt(0) == '/' || path.charAt(0) == separatorChar) {
                            path = path.substring(1);
                        }
                        File resolved = new File(file, path.replace('/', separatorChar));
                        if (resolved.exists()) {
                            return resolved;
                        }
                    }
                }
            }

            // Record path as one we need to resolve
            if (record) {
                mPathMap.put(path, null);
            }
        } else if (record) {
            // Record path as one we need to resolve
            mPathMap.put(path, null);
        }

        return null;
    }

    public void exportProject(@NonNull File destDir, boolean allowNonEmpty) throws IOException {
        mSummary.setDestDir(destDir);
        createDestDir(destDir, allowNonEmpty);
        createProjectBuildGradle(new File(destDir, FN_BUILD_GRADLE));
        createSettingsGradle(new File(destDir, FN_SETTINGS_GRADLE));

        exportGradleWrapper(destDir);
        exportLocalProperties(destDir);

        for (ImportModule module : mRootModules) {
            exportModule(new File(destDir, module.getModuleName()), module);
        }

        mSummary.write(new File(destDir, IMPORT_SUMMARY_TXT));
    }

    private void exportGradleWrapper(@NonNull File destDir) throws IOException {
        if (mGradleWrapperLocation != null && mGradleWrapperLocation.exists()) {
            File gradlewDest = new File(destDir, "gradlew");
            copyDir(new File(mGradleWrapperLocation, "gradlew"), gradlewDest, null);
            boolean madeExecutable = gradlewDest.setExecutable(true);
            if (!madeExecutable) {
                reportWarning((ImportModule)null, gradlewDest,
                        "Could not make gradle wrapper script executable");
            }
            copyDir(new File(mGradleWrapperLocation, "gradlew.bat"), new File(destDir,
                    "gradlew.bat"), null);
            copyDir(new File(mGradleWrapperLocation, "gradle"), new File(destDir, "gradle"), null);
        }
    }

    // Write local.properties file
    private void exportLocalProperties(@NonNull File destDir) throws IOException {
        boolean needsNdk = needsNdk();
        if (mNdkLocation != null && needsNdk || mSdkLocation != null) {
            Properties properties = new Properties();
            if (mSdkLocation != null) {
                properties.put(PROPERTY_SDK, mSdkLocation.getPath());
            }
            if (mNdkLocation != null && needsNdk) {
                properties.put(PROPERTY_NDK, mNdkLocation.getPath());
            }

            FileOutputStream out = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                out = new FileOutputStream(new File(destDir, FN_LOCAL_PROPERTIES));
                properties.store(out,
                    "# This file must *NOT* be checked into Version Control Systems,\n" +
                    "# as it contains information specific to your local configuration.\n" +
                    "\n" +
                    "# Location of the SDK. This is only used by Gradle.\n");
            } finally {
                Closeables.close(out, true);
            }
        }
    }

    /** Returns true if this project appears to need the NDK */
    public boolean needsNdk() {
        for (ImportModule module : mModules) {
            if (module.isNdkProject()) {
                return true;
            }
        }

        return false;
    }

    private void exportModule(File destDir, ImportModule module) throws IOException {
        mkdirs(destDir);
        createModuleBuildGradle(new File(destDir, FN_BUILD_GRADLE), module);
        module.copyInto(destDir);
    }

    @SuppressWarnings("MethodMayBeStatic")
    /** Ensure that the given directory exists, and if it can't be created, report an I/O error */
    public void mkdirs(@NonNull File destDir) throws IOException {
        if (!destDir.exists()) {
            boolean ok = destDir.mkdirs();
            if (!ok) {
                reportError(null, destDir, "Could not make directory " + destDir);
            }
        }
    }

    private void createModuleBuildGradle(@NonNull File file, ImportModule module)
            throws IOException {
        StringBuilder sb = new StringBuilder(500);

        if (module.isApp() || module.isAndroidLibrary()) {
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (!DECLARE_GLOBAL_REPOSITORIES) {
                appendRepositories(sb, true);
            }

            if (module.isApp()) {
                sb.append("apply plugin: 'android'").append(NL);
            } else {
                assert module.isAndroidLibrary();
                sb.append("apply plugin: 'android-library'").append(NL);
            }
            sb.append(NL);
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (!DECLARE_GLOBAL_REPOSITORIES) {
                sb.append("repositories {").append(NL);
                sb.append("    ").append(MAVEN_REPOSITORY).append(NL);
                sb.append("}").append(NL);
                sb.append(NL);
            }
            sb.append("android {").append(NL);
            String compileSdkVersion = Integer.toString(module.getCompileSdkVersion());
            String minSdkVersion = Integer.toString(module.getMinSdkVersion());
            String targetSdkVersion = Integer.toString(module.getTargetSdkVersion());
            sb.append("    compileSdkVersion ").append(compileSdkVersion).append(NL);
            sb.append("    buildToolsVersion \"").append(getBuildToolsVersion()).append("\"")
                    .append(NL);
            sb.append(NL);
            sb.append("    defaultConfig {").append(NL);
            sb.append("        minSdkVersion ").append(minSdkVersion).append(NL);
            if (module.getTargetSdkVersion() > 1 && module.getCompileSdkVersion() > 3) {
                sb.append("        targetSdkVersion ").append(targetSdkVersion).append(NL);
            }

            String languageLevel = module.getLanguageLevel();
            if (!languageLevel.equals(EclipseProject.DEFAULT_LANGUAGE_LEVEL)) {
                sb.append("        compileOptions {").append(NL);
                String level = languageLevel.replace('.','_'); // 1.6 => 1_6
                sb.append("            sourceCompatibility JavaVersion.VERSION_").append(level)
                        .append(NL);
                sb.append("            targetCompatibility JavaVersion.VERSION_").append(level)
                        .append(NL);
                sb.append("        }").append(NL);
            }

            if (module.isNdkProject() && module.getNativeModuleName() != null) {
                sb.append(NL);
                sb.append("        ndk {").append(NL);
                sb.append("            moduleName \"").append(module.getNativeModuleName())
                        .append("\"").append(NL);
                sb.append("        }").append(NL);
            }

            if (module.getInstrumentationDir() != null) {
                sb.append(NL);
                File manifestFile = new File(module.getInstrumentationDir(), ANDROID_MANIFEST_XML);
                assert manifestFile.exists() : manifestFile;
                Document manifest = getXmlDocument(manifestFile, true);
                if (manifest != null && manifest.getDocumentElement() != null) {
                    String pkg = manifest.getDocumentElement().getAttribute(ATTR_PACKAGE);
                    if (pkg != null && !pkg.isEmpty()) {
                        sb.append("        testPackageName \"").append(pkg).append("\"")
                                .append(NL);
                    }
                    NodeList list = manifest.getElementsByTagName(NODE_INSTRUMENTATION);
                    if (list.getLength() > 0) {
                        Element tag = (Element) list.item(0);
                        String runner = tag.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (runner != null && !runner.isEmpty()) {
                            sb.append("        testInstrumentationRunner \"").append(runner)
                                    .append("\"").append(NL);
                        }
                        Attr attr = tag.getAttributeNodeNS(ANDROID_URI, "functionalTest");
                        if (attr != null) {
                            sb.append("        testFunctionalTest ").append(attr.getValue())
                                    .append(NL);
                        }
                        attr = tag.getAttributeNodeNS(ANDROID_URI, "handleProfiling");
                        if (attr != null) {
                            sb.append("        testHandlingProfiling ").append(attr.getValue())
                                    .append(NL);
                        }
                    }
                }
            }

            sb.append("    }").append(NL);
            sb.append(NL);

            List<File> localRules = module.getLocalProguardFiles();
            List<File> sdkRules = module.getSdkProguardFiles();
            if (!localRules.isEmpty() || !sdkRules.isEmpty()) {
                // User specified ProGuard rules; replicate exactly
                if (module.isAndroidLibrary()) {
                    sb.append("    release {").append(NL);
                    sb.append("        runProguard true").append(NL);
                    sb.append("        proguardFiles ");
                    sb.append(generateProguardFileList(localRules, sdkRules)).append(NL);
                    sb.append("    }").append(NL);
                } else {
                    sb.append("    buildTypes {").append(NL);
                    sb.append("        release {").append(NL);
                    sb.append("            runProguard true").append(NL);
                    sb.append("            proguardFiles ");
                    sb.append(generateProguardFileList(localRules, sdkRules)).append(NL);
                    sb.append("        }").append(NL);
                    sb.append("    }").append(NL);
                }

            } else {
                // User didn't specify ProGuard rules; put in defaults (but off)
                if (module.isAndroidLibrary()) {
                    sb.append("    release {").append(NL);
                    sb.append("        runProguard false").append(NL);
                    sb.append("        proguardFiles getDefaultProguardFile('proguard-"
                            + "android.txt'), 'proguard-rules.txt'").append(NL);
                    sb.append("    }").append(NL);
                } else {
                    sb.append("    buildTypes {").append(NL);
                    sb.append("        release {").append(NL);
                    sb.append("            runProguard false").append(NL);
                    sb.append("            proguardFiles getDefaultProguardFile('proguard-"
                            + "android.txt'), 'proguard-rules.txt'").append(NL);
                    sb.append("        }").append(NL);
                    sb.append("    }").append(NL);
                }
            }
            sb.append("}").append(NL);
            appendDependencies(sb, module);

        } else if (module.isJavaLibrary()) {
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (!DECLARE_GLOBAL_REPOSITORIES) {
                appendRepositories(sb, false);
            }

            sb.append("apply plugin: 'java'").append(NL);

            String languageLevel = module.getLanguageLevel();
            if (!languageLevel.equals(EclipseProject.DEFAULT_LANGUAGE_LEVEL)) {
                sb.append(NL);
                sb.append("sourceCompatibility = \"");
                sb.append(languageLevel);
                sb.append("\"").append(NL);
                sb.append("targetCompatibility = \"");
                sb.append(languageLevel);
                sb.append("\"").append(NL);
            }

            appendDependencies(sb, module);
        } else {
            assert false : module;
        }

        Files.write(sb.toString(), file, UTF_8);
    }

    String getBuildToolsVersion() {
        SdkManager sdkManager = getSdkManager();
        if (sdkManager != null) {
            final BuildToolInfo buildTool = sdkManager.getLatestBuildTool();
            if (buildTool != null) {
                return buildTool.getRevision().toString();
            }
        }

        return CURRENT_BUILD_TOOLS_VERSION;
    }

    private static String generateProguardFileList(List<File> localRules, List<File> sdkRules) {
        assert !localRules.isEmpty() || !sdkRules.isEmpty();
        StringBuilder sb = new StringBuilder();
        for (File rule : sdkRules) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("getDefaultProguardFile('");
            sb.append(escapeGroovyStringLiteral(rule.getName()));
            sb.append("')");
        }

        for (File rule : localRules) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("'");
            // Note: project config files are flattened into the module structure (see
            // ImportModule#copyInto handler)
            sb.append(escapeGroovyStringLiteral(rule.getName()));
            sb.append("'");
        }

        return sb.toString();
    }

    private static void appendDependencies(@NonNull StringBuilder sb,
            @NonNull ImportModule module)
            throws IOException {
        if (!module.getDirectDependencies().isEmpty()
                || !module.getDependencies().isEmpty()
                || !module.getJarDependencies().isEmpty()
                || !module.getTestDependencies().isEmpty()
                || !module.getTestJarDependencies().isEmpty()) {
            sb.append(NL);
            sb.append("dependencies {").append(NL);
            for (ImportModule lib : module.getDirectDependencies()) {
                if (lib.isReplacedWithDependency()) {
                    continue;
                }
                sb.append("    compile project('").append(lib.getModuleReference()).append("')")
                        .append(NL);
            }
            for (GradleCoordinate dependency : module.getDependencies()) {
                sb.append("    compile '").append(dependency.toString()).append("'").append(NL);
            }
            for (File jar : module.getJarDependencies()) {
                String path = jar.getPath().replace(separatorChar, '/'); // Always / in gradle
                sb.append("    compile files('").append(escapeGroovyStringLiteral(path))
                        .append("')").append(NL);
            }
            for (GradleCoordinate dependency : module.getTestDependencies()) {
                sb.append("    instrumentTestCompile '").append(dependency.toString()).append("'")
                        .append(NL);
            }
            for (File jar : module.getTestJarDependencies()) {
                String path = jar.getPath().replace(separatorChar, '/');
                sb.append("    instrumentTestCompile files('")
                        .append(escapeGroovyStringLiteral(path)).append("')").append(NL);
            }
            sb.append("}").append(NL);
        }
    }

    private static String escapeGroovyStringLiteral(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 5);
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\'') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void appendRepositories(@NonNull StringBuilder sb, boolean needAndroidPlugin) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (!DECLARE_GLOBAL_REPOSITORIES) {
            sb.append("buildscript {").append(NL);
            sb.append("    repositories {").append(NL);
            sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
            sb.append("    }").append(NL);
            if (needAndroidPlugin) {
                sb.append("    dependencies {").append(NL);
                sb.append("        classpath '" + ANDROID_GRADLE_PLUGIN + "'").append(NL);
                sb.append("    }").append(NL);
            }
            sb.append("}").append(NL);
        }
    }

    private static void createProjectBuildGradle(@NonNull File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "// Top-level build file where you can add configuration options common to all sub-projects/modules.");

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (DECLARE_GLOBAL_REPOSITORIES) {
            sb.append(NL);
            sb.append("buildscript {").append(NL);
            sb.append("    repositories {").append(NL);
            sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
            sb.append("    }").append(NL);
            sb.append("    dependencies {").append(NL);
            sb.append("        classpath '" + ANDROID_GRADLE_PLUGIN + "'").append(NL);
            sb.append("    }").append(NL);
            sb.append("}").append(NL);
            sb.append(NL);
            sb.append("allprojects {").append(NL);
            sb.append("    repositories {").append(NL);
            sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
            sb.append("    }").append(NL);
            sb.append("}");
        }
        sb.append(NL);
        Files.write(sb.toString(), file, UTF_8);
    }

    private void createSettingsGradle(@NonNull File file) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (ImportModule module : mRootModules) {
            sb.append("include '");
            sb.append(module.getModuleReference());
            sb.append("'");
            sb.append(NL);
        }

        Files.write(sb.toString(), file, UTF_8);
    }

    private void createDestDir(@NonNull File destDir, boolean allowNonEmpty) throws IOException {
        if (destDir.exists()) {
          if (!allowNonEmpty) {
              File[] files = destDir.listFiles();
              if (files != null && files.length > 0) {
                  throw new IOException("Destination directory " + destDir + " should be empty");
              }
          }
        } else {
            mkdirs(destDir);
        }
    }

    @NonNull
    public List<String> getWarnings() {
        return mWarnings;
    }

    @NonNull
    public List<String> getErrors() {
        return mErrors;
    }

    private static class ImportException extends RuntimeException {
        private String mMessage;

        private ImportException(@NonNull String message) {
            mMessage = message;
        }

        @Override
        public String getMessage() {
            return mMessage;
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void reportError(
            @Nullable EclipseProject project,
            @Nullable File file,
            @NonNull String message) {
        reportError(project, file, message, true);
    }

    public void reportError(
            @Nullable EclipseProject project,
            @Nullable File file,
            @NonNull String message,
            boolean abort) {
        String text = formatMessage(project != null ? project.getName() : null, file, message);
        mErrors.add(text);
        if (abort) {
            throw new ImportException(text);
        }
    }

    public void reportWarning(
            @Nullable ImportModule module,
            @Nullable File file,
            @NonNull String message)  {
        String moduleName = module != null ? module.getOriginalName() : null;
        mWarnings.add(formatMessage(moduleName, file, message));
    }

    public void reportWarning(
            @Nullable EclipseProject project,
            @Nullable File file,
            @NonNull String message)  {
        String moduleName = project != null ? project.getName() : null;
        mWarnings.add(formatMessage(moduleName, file, message));
    }

    private static String formatMessage(
            @Nullable String project,
            @Nullable File file,
            @NonNull String message) {
        StringBuilder sb = new StringBuilder();
        if (project != null) {
            sb.append("Project ").append(project).append(":");
        }
        if (file != null) {
            sb.append(file.getPath());
            sb.append(":\n");
        }

        sb.append(message);

        return sb.toString();
    }

    @Nullable
    File resolvePathVariable(@Nullable EclipseProject fromProject, @NonNull String name, boolean record) throws IOException {
        File file = mPathMap.get(name);
        if (file != null) {
            return file;
        }

        if (fromProject != null && mWorkspaceLocation == null) {
            guessWorkspace(fromProject.getDir());
        }

        String value = null;
        Properties properties = getJdtSettingsProperties(false);
        if (properties != null) {
            value = properties.getProperty("org.eclipse.jdt.core.classpathVariable." + name);
        }
        if (value == null) {
            properties = getPathSettingsProperties(false);
            if (properties != null) {
                value = properties.getProperty("pathvariable." + name);
            }
        }

        if (value == null) {
            if (record) {
                mPathMap.put(name, null);
            }
            return null;
        }

        file = new File(value.replace('/', separatorChar));

        return file;
    }

    @Nullable
    private Properties getJdtSettingsProperties(boolean mustExist) throws IOException {
        File settings = getJdtSettingsFile();
        if (!settings.exists()) {
            if (mustExist) {
                reportError(null, settings, "Settings file does not exist");
            }
            return null;
        }

        return getProperties(settings);
    }

    private File getRuntimeSettingsDir() {
        return new File(getWorkspaceLocation(),
                ".metadata" + separator +
                ".plugins" + separator +
                "org.eclipse.core.runtime" + separator +
                ".settings");
    }

    private File getJdtSettingsFile() {
        return new File(getRuntimeSettingsDir(), "org.eclipse.jdt.core.prefs");
    }

    private File getPathSettingsFile() {
        return new File(getRuntimeSettingsDir(), "org.eclipse.core.resources.prefs");
    }

    private File getNdkSettingsFile() {
        return new File(getRuntimeSettingsDir(), "com.android.ide.eclipse.ndk.prefs");
    }

    private File getAdtSettingsFile() {
        return new File(getRuntimeSettingsDir(), "com.android.ide.eclipse.adt.prefs");
    }

    @Nullable
    private Properties getPathSettingsProperties(boolean mustExist) throws IOException {
        File settings = getPathSettingsFile();
        if (!settings.exists()) {
            if (mustExist) {
                reportError(null, settings, "Settings file does not exist");
            }
            return null;
        }

        return getProperties(settings);
    }

    private File getWorkspaceLocation() {
        return mWorkspaceLocation;
    }

    Document getXmlDocument(File file, boolean namespaceAware) throws IOException {
        String xml = Files.toString(file, UTF_8);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        InputSource is = new InputSource(new StringReader(xml));
        factory.setNamespaceAware(namespaceAware);
        factory.setValidating(false);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (Exception e) {
            reportError(null, file, "Invalid XML file: " + file.getPath() + ":\n"
                    + e.getMessage());
            return null;
        }
    }

    static Properties getProperties(File file) throws IOException {
        Properties properties = new Properties();
        FileReader reader = new FileReader(file);
        properties.load(reader);
        Closeables.close(reader, true);
        return properties;
    }

    private Map<File, EclipseProject> mProjectMap = Maps.newHashMap();

    Map<File, EclipseProject> getProjectMap() {
        return mProjectMap;
    }

    public ImportSummary getSummary() {
        return mSummary;
    }

    void registerProject(@NonNull EclipseProject project) {
        // Register not just this directory but the canonical versions too, since library
        // references in project.properties can be relative and can be made canonical;
        // we want to make sure that a project known by any of these versions of the paths
        // are treated as the same
        mProjectMap.put(project.getDir(), project);
        mProjectMap.put(project.getDir().getAbsoluteFile(), project);
        mProjectMap.put(project.getCanonicalDir(), project);
    }

    int getModuleCount() {
        int moduleCount = 0;
        for (ImportModule module : mModules) {
            if (!module.isReplacedWithDependency()) {
                moduleCount++;
            }
        }
        return moduleCount;
    }

    /** Returns a path map for workspace paths */
    public Map<String, File> getPathMap() {
        return mPathMap;
    }

    /** Interface used by the {@link #copyDir(java.io.File, java.io.File, CopyHandler)} handler */
    public interface CopyHandler {
        /**
         * Optionally handle the given file; returns true if the file has been
         * handled
         */
        boolean handle(@NonNull File source, @NonNull File dest) throws IOException;
    }

    /**
     * Handles copying the given source into the given destination, whether the source
     * is a file or directory. An optional handler can be used to perform special handling,
     * such as skipping files or changing the destination.
     */
    public void copyDir(@NonNull File source, @NonNull File dest, @Nullable CopyHandler handler)
            throws IOException {
        if (handler != null && handler.handle(source, dest)) {
            return;
        }
        if (source.isDirectory()) {
            if (isIgnoredFile(source)) {
                // Skip version control files when generating the migrated project;
                // it will only have fragments of the project, and in some cases moved
                // around, so don't pick up partial VCS state
                return;
            }

            mkdirs(dest);
            File[] files = source.listFiles();
            if (files != null) {
                for (File child : files) {
                    copyDir(child, new File(dest, child.getName()), handler);
                }
            }
        } else {
            Files.copy(source, dest);
        }
    }

    /**
     * Returns true if the given file should be ignored (note: this may not return
     * true for files inside ignored folders, so to determine if a given file should
     * really be ignored you should check all ancestors as well, or only call this as
     * part of a recursive directory traversal)
     */
    static boolean isIgnoredFile(File file) {
        String name = file.getName();
        return name.equals(".svn") || name.equals(".git") || name.equals(".hg")
                || name.equals(".DS_Store") || name.endsWith("~") && name.length() > 1;
    }

    /** Computes the relative path for the given file inside another directory */
    @Nullable
    public static File computeRelativePath(@NonNull File canonicalBase, @NonNull File file)
            throws IOException {
        File canonical = file.getCanonicalFile();
        String canonicalPath = canonical.getPath();
        if (canonicalPath.startsWith(canonicalBase.getPath())) {
            int length = canonicalBase.getPath().length();
            if (canonicalPath.length() == length) {
                return new File(".");
            } else if (canonicalPath.charAt(length) == separatorChar) {
                return new File(canonicalPath.substring(length + 1));
            } else {
                return new File(canonicalPath.substring(length));
            }
        }

        return null;
    }

    void markJarHandled(@NonNull File file) {
        mHandledJars.add(file.getName());
    }

    boolean isJarHandled(@NonNull File file) {
        return mHandledJars.contains(file.getName());
    }

    private boolean haveLocalRepository(String vendor) {
        SdkManager sdkManager = getSdkManager();
        if (sdkManager != null) {
            LocalSdk localSdk = sdkManager.getLocalSdk();
            LocalPkgInfo[] infos = localSdk.getPkgsInfos(PkgType.PKG_EXTRAS);
            for (LocalPkgInfo info : infos) {
                IPkgDesc d = info.getDesc();
                if (d.hasVendorId() && vendor.equals(d.getVendorId()) &&
                        d.hasPath() && "m2repository".equals(d.getPath())) {
                      return true;
                }
            }
        }

        if (mSdkLocation != null) {
            File repository = new File(mSdkLocation,
                    FD_EXTRAS + separator + vendor + separator + "m2repository");
            return repository.exists();
        }

        return false;
    }

    public boolean needSupportRepository() {
        return haveArtifact("com.android.support");
    }

    public boolean needGoogleRepository() {
        return haveArtifact("com.google.android.gms");
    }

    private boolean haveArtifact(String groupId) {
        for (ImportModule module : mRootModules) {
            for (GradleCoordinate dependency : module.getDependencies()) {
                if (groupId.equals(dependency.getGroupId())) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isMissingSupportRepository() {
        return !haveLocalRepository("android");
    }

    public boolean isMissingGoogleRepository() {
        return !haveLocalRepository("google");
    }
}
