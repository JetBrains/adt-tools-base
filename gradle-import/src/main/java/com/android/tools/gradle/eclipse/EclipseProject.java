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

import static com.android.SdkConstants.ANDROID_LIBRARY;
import static com.android.SdkConstants.ANDROID_LIBRARY_REFERENCE_FORMAT;
import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_PROJECT_PROPERTIES;
import static com.android.SdkConstants.GEN_FOLDER;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.SdkConstants.PROGUARD_CONFIG;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_SDK;
import static com.android.tools.gradle.eclipse.GradleImport.CURRENT_COMPILE_VERSION;
import static com.android.tools.gradle.eclipse.GradleImport.ECLIPSE_DOT_CLASSPATH;
import static com.android.tools.gradle.eclipse.GradleImport.ECLIPSE_DOT_PROJECT;
import static com.android.xml.AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION;
import static com.android.xml.AndroidManifest.ATTRIBUTE_TARGET_SDK_VERSION;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Provides information about an Eclipse project */
class EclipseProject implements Comparable<EclipseProject> {
    static final String DEFAULT_LANGUAGE_LEVEL = "1.6";

    private final GradleImport mImporter;
    private final File mDir;
    private final File mCanonicalDir;
    private boolean mLibrary;
    private boolean mAndroidProject;
    private int mMinSdkVersion;
    private int mTargetSdkVersion;
    private Document mClassPathDoc;
    private Document mProjectDoc;
    private Document mManifestDoc;
    private Properties mProjectProperties;
    private AndroidVersion mVersion;
    private String mName;
    private String mLanguageLevel;
    private List<EclipseProject> mDirectLibraries;
    private List<File> mSourcePaths;
    private List<File> mJarPaths;
    private File mOutputDir;
    private String mPackage;
    private List<File> mLocalProguardFiles;
    private List<File> mSdkProguardFiles;
    private List<EclipseProject> mAllLibraries;
    private EclipseImportModule mModule;

    private EclipseProject(
            @NonNull GradleImport importer,
            @NonNull File dir) throws IOException {
        mImporter = importer;
        mDir = dir;
        mCanonicalDir = dir.getCanonicalFile();

        // Ensure that  the library references (which are canonicalized) find this project
        // if included from multiple locations
        mImporter.registerProject(this);

        File file = getClassPathFile();
        mClassPathDoc = mImporter.getXmlDocument(file, false);

        initProjectName();
        initAndroidProject();
        initLanguageLevel();

        if (isAndroidProject()) {
            Properties properties = getProjectProperties();
            initProguard(properties);
            initVersion(properties);
            initLibraries(properties);
            initLibrary(properties);
            initPackage();
            initMinSdkVersion();
        } else {
            mDirectLibraries = new ArrayList<EclipseProject>(4);
        }

        initClassPathEntries();
    }

    @NonNull
    public static EclipseProject getProject(@NonNull GradleImport importer, @NonNull File dir)
            throws IOException {
        Map<File,EclipseProject> mProjectMap = importer.getProjectMap();
        EclipseProject project = mProjectMap.get(dir);

        if (project == null) {
            project = createProject(importer, dir);
            // The project should register itself in the map; we don't have to do that here.
            // (The code used to do that here, but it turns out project creation can recursively
            // visit library references as part of initialization, so have the projects register
            // themselves prior to initialization instead)
            assert mProjectMap.get(dir) != null;
        }

        return project;
    }

    @NonNull
    private static EclipseProject createProject(@NonNull GradleImport importer, @NonNull File dir)
            throws IOException {
        // Read the .classpath, .project, project.properties and local.properties files (if there)
        return new EclipseProject(importer, dir);
    }

    private void initVersion(Properties properties) {
        String target = properties.getProperty("target"); //$NON-NLS-1$
        if (target != null) {
            mVersion = AndroidTargetHash.getPlatformVersion(target);
        }
    }

    private void initLibraries(Properties properties) throws IOException {
        mDirectLibraries = new ArrayList<EclipseProject>(4);

        for (int i = 1; i < 1000; i++) {
            String key = String.format(ANDROID_LIBRARY_REFERENCE_FORMAT, i);
            String library = properties.getProperty(key);
            if (library == null || library.isEmpty()) {
                // No holes in the numbering sequence is allowed
                break;
            }

            File libraryDir = new File(mDir, library).getCanonicalFile();

            EclipseProject libraryPrj = getProject(mImporter, libraryDir);
            mDirectLibraries.add(libraryPrj);
        }
    }

    private void initLibrary(Properties properties) throws IOException {
        // This initialization must run after we've initialized the set of library
        // projects so we know whether or not we're including/merging manifests
        assert mDirectLibraries != null;
        String value = properties.getProperty(ANDROID_LIBRARY);
        mLibrary = VALUE_TRUE.equals(value);

        if (!mLibrary) {
            boolean mergeManifests = VALUE_TRUE.equals(properties.getProperty(
                    "manifestmerger.enabled")); //$NON-NLS-1$
            if (!mergeManifests) {
                // See if we (transitively) depend on libraries, and if any of them are
                // android library projects with non-empty manifests
                for (EclipseProject library : getAllLibraries()) {
                    if (library.isAndroidProject() && library.isLibrary() &&
                            library.getManifestFile().exists() &&
                            library.getManifestDoc().getDocumentElement() != null &&
                            XmlUtils.hasElementChildren(library.getManifestDoc().
                                    getDocumentElement())) {
                        mImporter.getSummary().reportManifestsMayDiffer();
                        break;
                    }
                }
            }
        }
    }

    private void initPackage() throws IOException {
        mPackage = getManifestDoc().getDocumentElement().getAttribute(ATTR_PACKAGE);
    }

    private void initMinSdkVersion() throws IOException {
        NodeList usesSdks = getManifestDoc().getDocumentElement().getElementsByTagName(
                NODE_USES_SDK);
        if (usesSdks.getLength() > 0) {
            Element usesSdk = (Element) usesSdks.item(0);
            mMinSdkVersion = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, 1);
            mTargetSdkVersion = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION,
                    mMinSdkVersion);
        } else {
            mMinSdkVersion = -1;
            mTargetSdkVersion = -1;
        }
    }

    private void initProjectName() throws IOException {
        Document document = getProjectDocument();
        if (document == null) {
            return;
        }
        NodeList names = document.getElementsByTagName("name");

        for (int i = 0; i < names.getLength(); i++) {
            Node element = names.item(i);
            mName = getStringValue((Element) element);
            //noinspection VariableNotUsedInsideIf
            if (mName != null) {
                break;
            }
        }

        if (mName == null) {
            mName = mDir.getName();
        }
    }

    private static int getApiVersion(Element usesSdk, String attribute, int defaultApiLevel) {
        String valueString = null;
        if (usesSdk.hasAttributeNS(ANDROID_URI, attribute)) {
            valueString = usesSdk.getAttributeNS(ANDROID_URI, attribute);
        }

        if (valueString != null) {
            int apiLevel = -1;
            try {
                apiLevel = Integer.valueOf(valueString);
            } catch (NumberFormatException e) {
                // TODO: Handle code names?
            }

            return apiLevel;
        }

        return defaultApiLevel;
    }

    private void initClassPathEntries() throws IOException {
        assert mSourcePaths == null && mJarPaths == null;
        mSourcePaths = Lists.newArrayList();
        mJarPaths = Lists.newArrayList();
        Document document = getClassPathDocument();
        NodeList entries = document.getElementsByTagName("classpathentry");
        for (int i = 0; i < entries.getLength(); i++) {
            Node entry = entries.item(i);
            assert entry.getNodeType() == Node.ELEMENT_NODE;
            Element element = (Element) entry;
            String kind = element.getAttribute("kind");
            String path = element.getAttribute("path");
            int index = path.indexOf('/');
            if (kind.equals("var") && index > 0) {
                String var = path.substring(0, index);
                if (mImporter.getEclipseWorkspace() != null) {
                    try {
                        String prefix = mImporter.resolvePathVariable(var);
                        File resolved = new File(prefix, path.replace('/', separatorChar));
                        if (resolved.exists()) {
                            mSourcePaths.add(resolved);
                            continue;
                        }
                    } catch (IOException ioe) {
                        // Don't abort; instead record missing path in resolveWorkspacePath
                        // call below
                    }
                }
                File resolved = mImporter.resolveWorkspacePath(path);
                if (resolved != null) {
                    mSourcePaths.add(resolved);
                } else {
                    mImporter.reportError(this, getClassPathFile(),
                            "Could not resolve path variable " + var);
                }
            } else if (kind.equals("src") && !path.isEmpty()) {
                if (!path.equals(GEN_FOLDER)) { // ignore special generated source folder
                    String relative = path.replace('/', separatorChar);
                    File file = new File(relative);
                    if (file.isAbsolute() || path.startsWith("/")) {
                        File resolved = resolveWorkspacePath(path);
                        if (resolved != null) {
                            if (GradleImport.isEclipseProjectDir(resolved)) {
                                // It's pointing to another project. Just add a dependency.
                                EclipseProject lib = getProject(mImporter, resolved);
                                if (!mDirectLibraries.contains(lib)) {
                                    mDirectLibraries.add(lib);
                                    mAllLibraries = null; // force refresh if already consulted
                                }
                                continue;
                            } else {
                                // It's some other source directory: just include as a source path
                                mSourcePaths.add(resolved);
                                continue;
                            }
                        }
                        mImporter.reportWarning(this, getClassPathFile(),
                                "Could not resolve source path " + path + " in project "
                                        + getName() + ": ignored. The project may not "
                                        + "compile if the given source path provided "
                                        + "source code.");
                    } else {
                        mSourcePaths.add(file);
                    }
                }
            } else if (kind.equals("lib") && !path.isEmpty()) {
                // Java library dependency. In Android projects we don't need these since
                // we pick up the information from the project.properties file for library
                // dependencies and the libs/ folder for jar files.
                if (!isAndroidProject()) {
                    String relative = path.replace('/', separatorChar);
                    File file = new File(relative);
                    if (file.isAbsolute() || path.startsWith("/")) {
                        File resolved = resolveWorkspacePath(path);
                        if (resolved != null) {
                            mJarPaths.add(resolved);
                        } else {
                            mImporter.reportWarning(this, getClassPathFile(),
                                "Absolute path in the path entry: If outside project, may not "
                                        + "work correctly: " + path);
                        }
                    } else {
                        mJarPaths.add(file);
                    }
                }
            } else if (kind.equals("output") && !path.isEmpty()) {
                String relative = path.replace('/', separatorChar);
                File file = new File(relative);
                if (!file.isAbsolute()) {
                    mOutputDir = file;
                }
            }
            // else: ignore kind="con"
        }

        // Automatically add in libraries in libs
        File[] libs = new File(mDir, LIBS_FOLDER).listFiles();
        if (libs != null) {
            for (File lib : libs) {
                File relative = new File(LIBS_FOLDER, lib.getName());
                if (!(mJarPaths.contains(relative) || mJarPaths.contains(lib))) {
                    // Skip jars that are the result of a library project dependency
                    boolean isLibraryJar = false;
                    for (EclipseProject project : getAllLibraries()) {
                        if (!project.isAndroidProject()) {
                            continue;
                        }
                        String pkg = project.getPackage();
                        if (pkg != null) {
                            String jarName = pkg.replace('.', '-') + DOT_JAR;
                            if (jarName.equals(lib.getName())) {
                                isLibraryJar = true;
                                break;
                            }
                        }
                    }
                    if (!isLibraryJar) {
                        mJarPaths.add(relative);
                    }
                }
            }
        }
    }

    @Nullable
    private File resolveWorkspacePath(@NonNull String path) {
        if (path.isEmpty()) {
            return null;
        }
        String relative = path.replace('/', separatorChar);
        File file = new File(relative);
        if (file.isAbsolute() || path.startsWith("/")) {
            if (file.exists()) {
                return file;
            }

            // It might be a workspace reference
            if (path.charAt(0) == '/') { // Workspace roots use '/', even on Windows
                // Try to resolve it using the workspace name
                File f = mImporter.resolveWorkspacePath(path);
                if (f != null && f.exists()) {
                    return f;
                }

                if (path.indexOf('/', 1) == -1 && path.indexOf('\\', 1) == -1) {
                    String name = path.substring(1);
                    // If we can't resolve workspace paths, try looking relative
                    // to the current project; dependent projects are often there
                    File parent = mDir.getParentFile();
                    if (parent != null) {
                        File sibling = new File(parent, name);
                        if (sibling.exists()) {
                            return sibling;
                        }
                    }

                    // Libraries are also often children
                    File child = new File(mDir, name);
                    if (child.exists()) {
                        return child;
                    }
                }
            }
        } else {
            File f = new File(mDir, relative);
            if (f.exists()) {
                return f;
            }
        }

        return null;
    }

    private void initAndroidProject() throws IOException {
        Document document = getProjectDocument();
        if (document == null) {
            return;
        }
        NodeList natures = document.getElementsByTagName("nature");
        for (int i = 0; i < natures.getLength(); i++) {
            Node nature = natures.item(i);
            String value = getStringValue((Element) nature);
            if ("com.android.ide.eclipse.adt.AndroidNature".equals(value)) {
                mAndroidProject = true;
            }
        }
    }

    private void initLanguageLevel() throws IOException {
        if (mLanguageLevel == null) {
            mLanguageLevel = DEFAULT_LANGUAGE_LEVEL; // default
            File file = new File(mDir, ".settings" + separator + "org.eclipse.jdt.core.prefs");
            if (file.exists()) {
                Properties properties = GradleImport.getProperties(file);
                if (properties != null) {
                    String source =
                            properties.getProperty("org.eclipse.jdt.core.compiler.source");
                    if (source != null) {
                        mLanguageLevel = source;
                    }
                }
            }
        }
    }

    private void initProguard(Properties properties) {
        mLocalProguardFiles = Lists.newArrayList();
        mSdkProguardFiles = Lists.newArrayList();

        String proguardConfig = properties.getProperty(PROGUARD_CONFIG);
        if (proguardConfig != null && !proguardConfig.isEmpty()) {
            // Be tolerant with respect to file and path separators just like
            // Ant is. Allow "/" in the property file to mean whatever the file
            // separator character is:
            if (File.separatorChar != '/' && proguardConfig.indexOf('/') != -1) {
                proguardConfig = proguardConfig.replace('/', File.separatorChar);
            }

            Iterable<String> paths = LintUtils.splitPath(proguardConfig);
            for (String path : paths) {
                // TODO: Support *arbitrary* files?
                if (path.startsWith(SDK_PROPERTY_REF)) {
                    mSdkProguardFiles.add(new File(path.substring(SDK_PROPERTY_REF.length())
                            .replace('/', separatorChar)));
                } else if (path.startsWith(HOME_PROPERTY_REF)) {
                    // TODO: How do we deal with home files?
                    //path = System.getProperty(HOME_PROPERTY) +
                    //        path.substring(HOME_PROPERTY_REF.length());
                    // For now just warn
                    mImporter.reportWarning(this, getProjectPropertiesFile(),
                            "Could not migrate Proguard config path " + path);
                } else {
                    File proguardConfigFile = new File(path.replace('/', separatorChar));
                    if (!proguardConfigFile.isAbsolute()) {
                        proguardConfigFile = new File(mDir, proguardConfigFile.getPath());
                    }
                    if (proguardConfigFile.isFile()) {
                        mLocalProguardFiles.add(proguardConfigFile);
                    }
                }
            }
        }
    }

    @NonNull
    public File getDir() {
        return mDir;
    }

    @NonNull
    public File getCanonicalDir() {
        return mCanonicalDir;
    }

    public boolean isLibrary() {
        return mLibrary;
    }

    private static final String HOME_PROPERTY = "user.home";                    //$NON-NLS-1$
    private static final String HOME_PROPERTY_REF = "${" + HOME_PROPERTY + '}'; //$NON-NLS-1$
    private static final String SDK_PROPERTY_REF = "${" + PROPERTY_SDK + '}';   //$NON-NLS-1$

    @NonNull
    public List<File> getLocalProguardFiles() {
        assert isAndroidProject();
        return mLocalProguardFiles;
    }

    @NonNull
    public List<File> getSdkProguardFiles() {
        assert isAndroidProject();
        return mSdkProguardFiles;
    }

    @NonNull
    public File getResourceDir() {
        assert isAndroidProject();
        return new File(mDir, FD_RES);
    }

    @NonNull
    public File getAssetsDir() {
        assert isAndroidProject();
        return new File(mDir, FD_ASSETS);
    }

    @NonNull
    public Document getClassPathDocument()  {
        return mClassPathDoc;
    }

    @NonNull
    private File getClassPathFile() {
        return new File(mDir, ECLIPSE_DOT_CLASSPATH);
    }

    @NonNull
    public Document getManifestDoc() throws IOException {
        assert isAndroidProject();
        if (mManifestDoc == null) {
            File file = getManifestFile();
            mManifestDoc = mImporter.getXmlDocument(file, true);
        }

        return mManifestDoc;
    }

    @NonNull
    File getManifestFile() {
        assert isAndroidProject();
        return new File(mDir, ANDROID_MANIFEST_XML);
    }

    @Nullable
    public Properties getProjectProperties() throws IOException {
        if (mProjectProperties == null) {
            assert isAndroidProject();
            File file = getProjectPropertiesFile();
            if (file.exists()) {
                mProjectProperties = GradleImport.getProperties(file);
            } else {
                mImporter.reportError(this, mDir,
                        "No project.project file found in " + mDir.getPath());
                return null;
            }
        }

        return mProjectProperties;
    }

    private File getProjectPropertiesFile() {
        return new File(mDir, FN_PROJECT_PROPERTIES);
    }

    @Nullable
    public Document getProjectDocument() throws IOException {
        if (mProjectDoc == null) {
            File file = new File(mDir, ECLIPSE_DOT_PROJECT);
            if (file.exists()) {
                mProjectDoc = mImporter.getXmlDocument(file, false);
            } else {
                mImporter.reportError(this, mDir,
                        "No Eclipse .project file found in " + mDir.getPath());
                return null;
            }
        }

        return mProjectDoc;
    }

    public boolean isAndroidProject() {
        return mAndroidProject;
    }

    @Nullable
    public static String getStringValue(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int j = 0; j < children.getLength(); j++) {
            Node child = children.item(j);
            if (child.getNodeType() == Node.TEXT_NODE) {
                return child.getNodeValue().trim();
            }

        }

        return null;
    }

    @Nullable
    public String getPackage() {
        assert isAndroidProject();
        return mPackage;
    }

    @NonNull
    public List<File> getSourcePaths() {
        return mSourcePaths;
    }

    @NonNull
    public List<File> getJarPaths() {
        return mJarPaths;
    }

    @Nullable
    public File getOutputDir() {
        return mOutputDir;
    }

    /** Returns "1.6", "1.7", etc */
    @NonNull
    public String getLanguageLevel()  {
        return mLanguageLevel;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public int getMinSdkVersion() {
        assert isAndroidProject();
        return mMinSdkVersion;
    }

    public int getTargetSdkVersion() {
        assert isAndroidProject();
        return mTargetSdkVersion;
    }

    public int getCompileSdkVersion() {
        assert isAndroidProject();
        return mVersion != null ? mVersion.getApiLevel() : CURRENT_COMPILE_VERSION;
    }

    @NonNull
    public List<EclipseProject> getDirectLibraries() {
        return mDirectLibraries;
    }

    @NonNull
    public List<EclipseProject> getAllLibraries() {
        if (mAllLibraries == null) {
            if (mDirectLibraries.isEmpty()) {
                return mDirectLibraries;
            }

            List<EclipseProject> all = new ArrayList<EclipseProject>();
            Set<EclipseProject> seen = Sets.newHashSet();
            Set<EclipseProject> path = Sets.newHashSet();
            seen.add(this);
            path.add(this);
            addLibraryProjects(all, seen, path);
            mAllLibraries = all;
        }

        return mAllLibraries;
    }

    private void addLibraryProjects(@NonNull Collection<EclipseProject> collection,
            @NonNull Set<EclipseProject> seen, @NonNull Set<EclipseProject> path) {
        for (EclipseProject library : mDirectLibraries) {
            if (seen.contains(library)) {
                if (path.contains(library)) {
                    mImporter.reportWarning(library, library.getDir(),
                            "Internal error: cyclic library dependency for " +
                                    library);
                }
                continue;
            }
            collection.add(library);
            seen.add(library);
            path.add(library);
            // Recurse
            library.addLibraryProjects(collection, seen, path);
            path.remove(library);
        }
    }

    @Override
    public int compareTo(@NonNull EclipseProject other) {
        return mDir.compareTo(other.mDir);
    }

    @Override
    public String toString() {
        return mDir.getPath();
    }

    /**
     * Creates a list of modules from the given set of projects. The returned list
     * is in dependency order.
     */
    public static List<? extends ImportModule> performImport(
            @NonNull GradleImport importer,
            @NonNull Collection<EclipseProject> projects) {
        List<EclipseImportModule> modules = Lists.newArrayList();
        List<EclipseImportModule> replacedByDependencies = Lists.newArrayList();

        for (EclipseProject project : projects) {
            EclipseImportModule module = new EclipseImportModule(importer, project);
            module.initialize();
            if (module.isReplacedWithDependency()) {
                replacedByDependencies.add(module);
            } else {
                modules.add(module);
            }
        }

        // Some libraries may be replaced by just a dependency (for example,
        // instead of copying in a whole copy of ActionBarSherlock, just
        // replace by the corresponding dependency.
        for (EclipseImportModule replaced : replacedByDependencies) {
            assert replaced.getReplaceWithDependencies() != null;
            EclipseProject project = replaced.getProject();
            for (EclipseImportModule module : modules) {
                if (module.getProject().getAllLibraries().contains(project)) {
                    module.addDependencies(replaced.getReplaceWithDependencies());
                }
            }
        }

        // Strip out .jar files from the libs/ folder if already implied by
        // library dependencies
        for (EclipseImportModule module : modules) {
            module.removeJarDependencies();
        }

        // Sort by dependency order
        Collections.sort(modules);

        return modules;
    }

    @Nullable
    public EclipseImportModule getModule() {
        return mModule;
    }

    public void setModule(@Nullable EclipseImportModule module) {
        mModule = module;
    }
}
