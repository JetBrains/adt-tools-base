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
import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_NAME;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalExtraPkgInfo;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.primitives.Bytes;

import org.w3c.dom.Document;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
 *     <li>Handle workspace paths in dependencies, resolve to actual paths</li>
 *     <li>Consider whether I can make this import mechanism work for Maven and plain
 *     sources as well?</li>
 *     <li>Make it optional whether we replace the directory structure with the Gradle one?</li>
 *     <li>If I have a workspace, check to see if there are problem markers and if
 *     so warn that the project may not be buildable</li>
 *     <li>Do I migrate VCS folders (.git, .svn., etc?)</li>
 *     <li>Read SDK home out of local.properties and ask whether to use it or the Studio one
 *     (if they differ), and if the former, ensure it has all the gradle repositories we need</li>
 *     <li>Optional:  at the end of the import, migrate Eclipse settings too --
 *      such as code styles, compiler flags (especially those for the
 *      project), ask about enabling eclipse key bindings, etc?</li>
 *     <li>If replaceJars=false, insert *comments* in the source code for potential
 *     replacements such that users don't forget and consider switching in the future</li>
 *     <li>Allow migrating a project in-place?</li>
 *     <li>Figure out if we can reuse fragments from the default freemarker templates for
 *     the code generation part.</li>
 *     <li>Move instrumentation tests; analyze instrumentation ADT test project, pull out package
 *      info etc and put in Gradle file, then move to instrumentation tests folder</li>
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

    static final String MAVEN_REPOSITORY = "mavenCentral()";

    public static final String ECLIPSE_DOT_CLASSPATH = ".classpath";
    public static final String ECLIPSE_DOT_PROJECT = ".project";
    public static final String IMPORT_SUMMARY_TXT = "import-summary.txt";

    private List<? extends ImportModule> mModules;
    private ImportSummary mSummary;
    private File mWorkspaceLocation;
    private File mGradleWrapperLocation;
    private File mSdkLocation;
    private SdkManager mSdkManager;
    private int mModuleCount;
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

    public GradleImport() {
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
        mModuleCount = 0;
        mProjectMap.clear();
        mHandledJars.clear();
        mWarnings.clear();
        mErrors.clear();
        mWorkspaceProjects = null;

        for (File file : projectDirs) {
            if (file.isFile()) {
                assert !file.isDirectory();
                file = file.getParentFile();
            }

            if (isAdtProjectDir(file)) {
                EclipseProject.getProject(this, file);
            } else {
                reportError(null, file, "Not a recognized project: " + file);
            }

            guessWorkspace(file);
        }

        // Find unique projects. (We can register projects under multiple paths
        // if the dir and the canonical dir differ, so pick unique values here)
        Set<EclipseProject> projects = Sets.newHashSet(mProjectMap.values());
        mModules = EclipseProject.performImport(this, projects);
    }

    public static boolean isEclipseProjectDir(@Nullable File file) {
        return file != null && file.isDirectory()
                && new File(file, ECLIPSE_DOT_CLASSPATH).exists()
                && new File(file, ECLIPSE_DOT_PROJECT).exists();
    }

    public static boolean isAdtProjectDir(@Nullable File file) {
        return isEclipseProjectDir(file)
                && new File(file, ANDROID_MANIFEST_XML).exists();
    }

    /** Sets location of gradle wrapper to copy into exported project, if known */
    @NonNull
    public GradleImport setGradleWrapperLocation(@NonNull File gradleWrapper) {
        mGradleWrapperLocation = gradleWrapper;
        return this;
    }

    /** Sets location of the SDK to use with the import, if known */
    @NonNull
    public GradleImport setSdkLocation(@NonNull File sdkLocation) {
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

    /** Sets location of Eclipse workspace, if known */
    public GradleImport setEclipseWorkspace(@NonNull File workspace) {
        mWorkspaceLocation = workspace;
        assert mWorkspaceLocation.exists() : workspace.getPath();
        return this;
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

    /**
     * Do we need to know the Eclipse workspace location in order to work out path variables,
     * locations of dependent libraries etc?
     * <p>
     * To find workspace choose File->Switch Workspace->Other... and it will display the
     * workspace path.
     */
    public boolean needEclipseWorkspace() {
        // Already know it?
        //noinspection VariableNotUsedInsideIf
        if (mWorkspaceLocation != null) {
            return false;
        }

        for (EclipseProject project : mProjectMap.values()) {
            if (project.needWorkspaceLocation()) {
                return true;
            }
        }

        return false;
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

    private static boolean isEclipseWorkspaceDir(@NonNull File file) {
        return file.isDirectory() &&
                new File(file, ".metadata" + separator + "version.ini").exists();
    }

    @Nullable
    public File resolveWorkspacePath(@NonNull String path) {
        if (path.isEmpty()) {
            return null;
        }

        if (mWorkspaceLocation != null) {
            // Is the file present directly in the workspace?
            char first = path.charAt(0);
            if (first != '/' || first != separatorChar) {
                return null;
            }
            File f = new File(mWorkspaceLocation, path.substring(1).replace('/', separatorChar));
            if (f.exists()) {
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
                                            if (separatorChar != '/') {
                                                mWorkspaceProjects.put(separatorChar + name, file);
                                            }
                                        }
                                    } catch (Throwable t) {
                                        // Ignore binary data we can't read
                                        t.printStackTrace();
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

            // Clean up path to ensure there is no / at the end etc
            path = new File(path).getPath();

            // Is it just a project root?
            File project = mWorkspaceProjects.get(path);
            if (project != null) {
                return project;
            }

            // If file within project, must match on all prefixes
            for (File file : mWorkspaceProjects.values()) {
                File resolved = new File(file, path);
                if (resolved.exists()) {
                    return resolved;
                }
            }
        }

        return null;
    }

    public void exportProject(@NonNull File destDir, boolean allowNonEmpty) throws IOException {
        mSummary.setDestDir(destDir);
        createDestDir(destDir, allowNonEmpty);
        createProjectBuildGradle(new File(destDir, FN_BUILD_GRADLE));
        createSettingsGradle(new File(destDir, FN_SETTINGS_GRADLE));

        exportGradleWrapper(destDir);

        for (ImportModule module : mModules) {
            exportModule(new File(destDir, module.getModuleName()), module);
        }

        mSummary.write(new File(destDir, IMPORT_SUMMARY_TXT));
    }

    private void exportGradleWrapper(File destDir) throws IOException {
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
            appendRepositories(sb, true);

            if (module.isApp()) {
                sb.append("apply plugin: 'android'").append(NL);
            } else {
                assert module.isAndroidLibrary();
                sb.append("apply plugin: 'android-library'").append(NL);
            }
            sb.append("").append(NL);
            sb.append("repositories {").append(NL);
            sb.append("    ").append(MAVEN_REPOSITORY).append(NL);
            sb.append("}").append(NL);
            sb.append("").append(NL);
            sb.append("android {").append(NL);
            String compileSdkVersion = Integer.toString(module.getCompileSdkVersion());
            String minSdkVersion = Integer.toString(module.getMinSdkVersion());
            String targetSdkVersion = Integer.toString(module.getTargetSdkVersion());
            sb.append("    compileSdkVersion ").append(compileSdkVersion).append(NL);
            sb.append("    buildToolsVersion \"").append(getBuildToolsVersion()).append("\"")
                    .append(NL);
            sb.append("").append(NL);
            sb.append("    defaultConfig {").append(NL);
            sb.append("        minSdkVersion ").append(minSdkVersion).append(NL);
            if (module.getTargetSdkVersion() > 1) {
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
            appendRepositories(sb, false);

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

    private String getBuildToolsVersion() {
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
            sb.append(rule.getName());
            sb.append("')");
        }

        for (File rule : localRules) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("'");
            // Note: project config files are flattened into the module structure (see
            // ImportModule#copyInto handler)
            sb.append(rule.getName());
            sb.append("'");
        }

        return sb.toString();
    }

    private static void appendDependencies(@NonNull StringBuilder sb,
            @NonNull ImportModule module)
            throws IOException {
        if (!module.getDirectDependencies().isEmpty()
                || !module.getDependencies().isEmpty()
                || !module.getJarDependencies().isEmpty()) {
            sb.append(NL);
            sb.append("dependencies {").append(NL);
            for (ImportModule lib : module.getDirectDependencies()) {
                sb.append("    compile project('").append(lib.getModuleReference()).append("')")
                        .append(NL);
            }
            for (GradleCoordinate dependency : module.getDependencies()) {
                sb.append("    compile '").append(dependency.toString()).append("'").append(NL);
            }
            for (File jar : module.getJarDependencies()) {
                String path = jar.getPath().replace(separatorChar, '/'); // Always / in gradle
                sb.append("    compile files('").append(path).append("')").append(NL);
            }
            sb.append("}");
        }
    }

    private static void appendRepositories(@NonNull StringBuilder sb, boolean needAndroidPlugin) {
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

    private static void createProjectBuildGradle(@NonNull File file) throws IOException {
        Files.write("// Top-level build file where you can add configuration options " +
                "common to all sub-projects/modules." + NL, file,
                UTF_8);
    }

    private void createSettingsGradle(@NonNull File file) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (ImportModule module : mModules) {
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

    @SuppressWarnings("MethodMayBeStatic")
    public void reportError(
            @Nullable EclipseProject project,
            @Nullable File file,
            @NonNull String message) throws IOException {
        String text = formatMessage(project != null ? project.getName() : null, file, message);
        mErrors.add(text);
        throw new IOException(text);
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

    public String resolvePathVariable(String name) throws IOException {
        Properties properties = getJdkSettingsProperties(true);
        assert properties != null; // because mustExist=true, otherwise throws error
        String value = properties.getProperty("org.eclipse.jdt.core.classpathVariable." + name);
        if (value == null) {
            File settings = getSettingsFile();
            reportError(null, settings, "Didn't find path variable " + name + " definition in " +
                    settings);
            return null;
        }

        return value;
    }

    @Nullable
    private Properties getJdkSettingsProperties(boolean mustExist) throws IOException {
        File settings = getSettingsFile();
        if (!settings.exists()) {
            if (mustExist) {
                reportError(null, settings, "Settings file does not exist");
            }
            return null;
        }

        return getProperties(settings);
    }

    private File getSettingsFile() {
        return new File(getWorkspaceLocation(),
                ".plugins" + separator +
                "org.eclipse.core.runtime" + separator +
                ".settings" + separator +
                "org.eclipse.jdt.core.prefs");
    }

    private File getWorkspaceLocation() {
        return mWorkspaceLocation;
    }

    static Document getXmlDocument(File file, boolean namespaceAware) throws IOException {
        String xml = Files.toString(file, UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, namespaceAware);
        if (document == null) {
            throw new IOException("Invalid XML file: " + file.getPath());
        }
        return document;
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

    void registerModule(@NonNull ImportModule module) {
        if (module.isReplacedWithDependency()) {
            mModuleCount++;
        }
    }

    int getModuleCount() {
        return mModuleCount;
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
            String name = source.getName();
            if (name.equals(".git") || name.equals(".svn")) {
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
                if (info instanceof LocalExtraPkgInfo) {
                    LocalExtraPkgInfo ei = (LocalExtraPkgInfo) info;
                    if (vendor.equals(ei.getVendorId()) &&
                            "m2repository".equals(ei.getExtraPath())) {
                        return true;
                    }
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

    boolean needSupportRepository() {
        return haveArtifact("com.android.support");
    }

    boolean needGoogleRepository() {
        return haveArtifact("com.google.android.gms");
    }

    private boolean haveArtifact(String groupId) {
        for (ImportModule module : mModules) {
            for (GradleCoordinate dependency : module.getDependencies()) {
                if (groupId.equals(dependency.getGroupId())) {
                    return true;
                }
            }
        }

        return false;
    }

    boolean isMissingSupportRepository() {
        return !haveLocalRepository("android");
    }

    boolean isMissingGoogleRepository() {
        return !haveLocalRepository("google");
    }
}