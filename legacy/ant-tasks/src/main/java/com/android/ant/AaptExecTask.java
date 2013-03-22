/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ant;

import com.android.SdkConstants;
import com.android.sdklib.internal.build.SymbolLoader;
import com.android.sdklib.internal.build.SymbolWriter;
import com.android.xml.AndroidXPathFactory;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Task to execute aapt.
 *
 * <p>It does not follow the exec task format, instead it has its own parameters, which maps
 * directly to aapt.</p>
 * <p>It is able to run aapt several times if library setup requires generating several
 * R.java files.
 * <p>The following map shows how to use the task for each supported aapt command line
 * parameter.</p>
 *
 * <table border="1">
 * <tr><td><b>Aapt Option</b></td><td><b>Ant Name</b></td><td><b>Type</b></td></tr>
 * <tr><td>path to aapt</td><td>executable</td><td>attribute (Path)</td>
 * <tr><td>command</td><td>command</td><td>attribute (String)</td>
 * <tr><td>-v</td><td>verbose</td><td>attribute (boolean)</td></tr>
 * <tr><td>-f</td><td>force</td><td>attribute (boolean)</td></tr>
 * <tr><td>-M AndroidManifest.xml</td><td>manifest</td><td>attribute (Path)</td></tr>
 * <tr><td>-I base-package</td><td>androidjar</td><td>attribute (Path)</td></tr>
 * <tr><td>-A asset-source-dir</td><td>assets</td><td>attribute (Path</td></tr>
 * <tr><td>-S resource-sources</td><td>&lt;res path=""&gt;</td><td>nested element(s)<br>with attribute (Path)</td></tr>
 * <tr><td>-0 extension</td><td>&lt;nocompress extension=""&gt;<br>&lt;nocompress&gt;</td><td>nested element(s)<br>with attribute (String)</td></tr>
 * <tr><td>-F apk-file</td><td>apkfolder<br>outfolder<br>apkbasename<br>basename</td><td>attribute (Path)<br>attribute (Path) deprecated<br>attribute (String)<br>attribute (String) deprecated</td></tr>
 * <tr><td>-J R-file-dir</td><td>rfolder</td><td>attribute (Path)<br>-m always enabled</td></tr>
 * <tr><td>--rename-manifest-package package-name</td><td>manifestpackage</td><td>attribute (String)</td></tr>
 * <tr><td></td><td></td><td></td></tr>
 * </table>
 */
public final class AaptExecTask extends SingleDependencyTask {

    /**
     * Class representing a &lt;nocompress&gt; node in the main task XML.
     * This let the developers prevent compression of some files in assets/ and res/raw/
     * by extension.
     * If the extension is null, this will disable compression for all  files in assets/ and
     * res/raw/
     */
    public final static class NoCompress {
        String mExtension;

        /**
         * Sets the value of the "extension" attribute.
         * @param extention the extension.
         */
        public void setExtension(String extention) {
            mExtension = extention;
        }
    }

    private String mExecutable;
    private String mCommand;
    private boolean mForce = true; // true due to legacy reasons
    private boolean mDebug = false;
    private boolean mVerbose = false;
    private boolean mUseCrunchCache = false;
    private int mVersionCode = 0;
    private String mVersionName;
    private String mManifestFile;
    private String mManifestPackage;
    private String mOriginalManifestPackage;
    private ArrayList<Path> mResources;
    private String mAssets;
    private String mAndroidJar;
    private String mApkFolder;
    private String mApkName;
    private String mResourceFilter;
    private String mRFolder;
    private final ArrayList<NoCompress> mNoCompressList = new ArrayList<NoCompress>();
    private String mLibraryResFolderPathRefid;
    private String mLibraryPackagesRefid;
    private String mLibraryRFileRefid;
    private boolean mNonConstantId;
    private String mIgnoreAssets;
    private String mBinFolder;
    private String mProguardFile;

    /**
     * Input path that ignores the same folders/files that aapt does.
     */
    private static class ResFolderInputPath extends InputPath {
        public ResFolderInputPath(File file, Set<String> extensionsToCheck) {
            super(file, extensionsToCheck);
        }

        @Override
        public boolean ignores(File file) {
            String name = file.getName();
            char firstChar = name.charAt(0);

            if (firstChar == '.' || (firstChar == '_' && file.isDirectory()) ||
                    name.charAt(name.length()-1) == '~') {
                return true;
            }

            if ("CVS".equals(name) ||
                    "thumbs.db".equalsIgnoreCase(name) ||
                    "picasa.ini".equalsIgnoreCase(name)) {
                return true;
            }

            String ext = getExtension(name);
            if ("scc".equalsIgnoreCase(ext)) {
                return true;
            }

            return false;
        }
    }

    private final static InputPathFactory sPathFactory = new InputPathFactory() {

        @Override
        public InputPath createPath(File file, Set<String> extensionsToCheck) {
            return new ResFolderInputPath(file, extensionsToCheck);
        }
    };

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /**
     * Sets the value of the "command" attribute.
     * @param command the value.
     */
    public void setCommand(String command) {
        mCommand = command;
    }

    /**
     * Sets the value of the "force" attribute.
     * @param force the value.
     */
    public void setForce(boolean force) {
        mForce = force;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "usecrunchcache" attribute
     * @param usecrunch whether to use the crunch cache.
     */
    public void setNoCrunch(boolean usecrunch) {
        mUseCrunchCache = usecrunch;
    }

    public void setNonConstantId(boolean nonConstantId) {
        mNonConstantId = nonConstantId;
    }

    public void setIgnoreAssets(String ignoreAssets) {
        mIgnoreAssets = ignoreAssets;
    }

    public void setVersioncode(String versionCode) {
        if (versionCode.length() > 0) {
            try {
                mVersionCode = Integer.decode(versionCode);
            } catch (NumberFormatException e) {
                System.out.println(String.format(
                        "WARNING: Ignoring invalid version code value '%s'.", versionCode));
            }
        }
    }

    /**
     * Sets the value of the "versionName" attribute
     * @param versionName the value
     */
    public void setVersionname(String versionName) {
        mVersionName = versionName;
    }

    public void setDebug(boolean value) {
        mDebug = value;
    }

    /**
     * Sets the value of the "manifest" attribute.
     * @param manifest the value.
     */
    public void setManifest(Path manifest) {
        mManifestFile = TaskHelper.checkSinglePath("manifest", manifest);
    }

    /**
     * Sets a custom manifest package ID to be used during packaging.<p>
     * The manifest will be rewritten so that its package ID becomes the value given here.
     * Relative class names in the manifest (e.g. ".Foo") will be rewritten to absolute names based
     * on the existing package name, meaning that no code changes need to be made.
     *
     * @param packageName The package ID the APK should have.
     */
    public void setManifestpackage(String packageName) {
        if (packageName != null && packageName.length() != 0) {
            mManifestPackage = packageName;
        }
    }

    /**
     * Sets the original package name found in the manifest. This is the package name where
     * the R class is created.
     *
     * This is merely a shortcut in case the package is known when calling the aapt task. If not
     * provided (and needed) this task will recompute it.
     * @param packageName the package name declared in the manifest.
     */
    public void setOriginalManifestPackage(String packageName) {
        mOriginalManifestPackage = packageName;
    }

    /**
     * Sets the value of the "resources" attribute.
     * @param resources the value.
     *
     * @deprecated Use nested element(s) <res path="value" />
     */
    @Deprecated
    public void setResources(Path resources) {
        System.out.println("WARNNG: Using deprecated 'resources' attribute in AaptExecLoopTask." +
                "Use nested element(s) <res path=\"value\" /> instead.");
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        mResources.add(new Path(getProject(), resources.toString()));
    }

    /**
     * Sets the value of the "assets" attribute.
     * @param assets the value.
     */
    public void setAssets(Path assets) {
        mAssets = TaskHelper.checkSinglePath("assets", assets);
    }

    /**
     * Sets the value of the "androidjar" attribute.
     * @param androidJar the value.
     */
    public void setAndroidjar(Path androidJar) {
        mAndroidJar = TaskHelper.checkSinglePath("androidjar", androidJar);
    }

    /**
     * Sets the value of the "outfolder" attribute.
     * @param outFolder the value.
     * @deprecated use {@link #setApkfolder(Path)}
     */
    @Deprecated
    public void setOutfolder(Path outFolder) {
        System.out.println("WARNNG: Using deprecated 'outfolder' attribute in AaptExecLoopTask." +
                "Use 'apkfolder' (path) instead.");
        mApkFolder = TaskHelper.checkSinglePath("outfolder", outFolder);
    }

    /**
     * Sets the value of the "apkfolder" attribute.
     * @param apkFolder the value.
     */
    public void setApkfolder(Path apkFolder) {
        mApkFolder = TaskHelper.checkSinglePath("apkfolder", apkFolder);
    }

    /**
     * Sets the value of the resourcefilename attribute
     * @param apkName the value
     */
    public void setResourcefilename(String apkName) {
        mApkName = apkName;
    }

    /**
     * Sets the value of the "rfolder" attribute.
     * @param rFolder the value.
     */
    public void setRfolder(Path rFolder) {
        mRFolder = TaskHelper.checkSinglePath("rfolder", rFolder);
    }

    public void setresourcefilter(String filter) {
        if (filter != null && filter.length() > 0) {
            mResourceFilter = filter;
        }
    }

    /**
     * Set the property name of the property that contains the list of res folder for
     * Library Projects. This sets the name and not the value itself to handle the case where
     * it doesn't exist.
     * @param libraryResFolderPathRefid
     */
    public void setLibraryResFolderPathRefid(String libraryResFolderPathRefid) {
        mLibraryResFolderPathRefid = libraryResFolderPathRefid;
    }

    public void setLibraryPackagesRefid(String libraryPackagesRefid) {
        mLibraryPackagesRefid = libraryPackagesRefid;
    }

    public void setLibraryRFileRefid(String libraryRFileRefid) {
        mLibraryRFileRefid = libraryRFileRefid;
    }

    public void setBinFolder(Path binFolder) {
        mBinFolder = TaskHelper.checkSinglePath("binFolder", binFolder);
    }

    public void setProguardFile(Path proguardFile) {
        mProguardFile = TaskHelper.checkSinglePath("proguardFile", proguardFile);
    }

    /**
     * Returns an object representing a nested <var>nocompress</var> element.
     */
    public Object createNocompress() {
        NoCompress nc = new NoCompress();
        mNoCompressList.add(nc);
        return nc;
    }

    /**
     * Returns an object representing a nested <var>res</var> element.
     */
    public Object createRes() {
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        Path path = new Path(getProject());
        mResources.add(path);

        return path;
    }

    @Override
    protected String getExecTaskName() {
        return "aapt";
    }

    /*
     * (non-Javadoc)
     *
     * Executes the loop. Based on the values inside project.properties, this will
     * create alternate temporary ap_ files.
     *
     * @see org.apache.tools.ant.Task#execute()
     */
    @SuppressWarnings("deprecation")
    @Override
    public void execute() throws BuildException {
        if (mLibraryResFolderPathRefid == null) {
            throw new BuildException("Missing attribute libraryResFolderPathRefid");
        }
        if (mLibraryPackagesRefid == null) {
            throw new BuildException("Missing attribute libraryPackagesRefid");
        }
        if (mLibraryRFileRefid == null) {
            throw new BuildException("Missing attribute libraryRFileRefid");
        }

        Project taskProject = getProject();

        String libPkgProp = null;
        Path libRFileProp = null;

        // if the parameters indicate generation of the R class, check if
        // more R classes need to be created for libraries, only if this project itself
        // is not a library
        if (mNonConstantId == false && mRFolder != null && new File(mRFolder).isDirectory()) {
            libPkgProp = taskProject.getProperty(mLibraryPackagesRefid);
            Object rFilePath = taskProject.getReference(mLibraryRFileRefid);

            // if one is null, both should be
            if ((libPkgProp == null || rFilePath == null) &&
                    rFilePath != libPkgProp) {
                throw new BuildException(String.format(
                        "Both %1$s and %2$s should resolve to valid values.",
                        mLibraryPackagesRefid, mLibraryRFileRefid));
            }

            if (rFilePath instanceof Path) {
                libRFileProp = (Path) rFilePath;
            } else if (rFilePath != null) {
                throw new BuildException("attribute libraryRFileRefid must reference a Path object.");
            }
        }

        final boolean generateRClass = mRFolder != null && new File(mRFolder).isDirectory();

        // Get whether we have libraries
        Object libResRef = taskProject.getReference(mLibraryResFolderPathRefid);

        // Set up our input paths that matter for dependency checks
        ArrayList<File> paths = new ArrayList<File>();

        // the project res folder is an input path of course
        for (Path pathList : mResources) {
            for (String path : pathList.list()) {
                paths.add(new File(path));
            }
        }

        // and if libraries exist, their res folders folders too.
        if (libResRef instanceof Path) {
            for (String path : ((Path)libResRef).list()) {
                paths.add(new File(path));
            }
        }

        // Now we figure out what we need to do
        if (generateRClass) {
            // in this case we only want to run aapt if an XML file was touched, or if any
            // file is added/removed
            List<InputPath> inputPaths = getInputPaths(paths, Collections.singleton("xml"),
                    sPathFactory);

            // let's not forget the manifest as an input path (with no extension restrictions).
            if (mManifestFile != null) {
                inputPaths.add(new InputPath(new File(mManifestFile)));
            }

            // Check to see if our dependencies have changed. If not, then skip
            if (initDependencies(mRFolder + File.separator + "R.java.d", inputPaths)
                              && dependenciesHaveChanged() == false) {
                System.out.println("No changed resources. R.java and Manifest.java untouched.");
                return;
            } else {
                System.out.println("Generating resource IDs...");
            }
        } else {
            // in this case we want to run aapt if any file was updated/removed/added in any of the
            // input paths
            List<InputPath> inputPaths = getInputPaths(paths, null /*extensionsToCheck*/,
                    sPathFactory);

            // let's not forget the manifest as an input path.
            if (mManifestFile != null) {
                inputPaths.add(new InputPath(new File(mManifestFile)));
            }

            // If we're here to generate a .ap_ file we need to use assets as an input path as well.
            if (mAssets != null) {
                File assetsDir = new File(mAssets);
                if (assetsDir.isDirectory()) {
                    inputPaths.add(new InputPath(assetsDir));
                }
            }

            // Find our dependency file. It should have the same name as our target .ap_ but
            // with a .d extension
            String dependencyFilePath = mApkFolder + File.separator + mApkName;
            dependencyFilePath += ".d";

            // Check to see if our dependencies have changed
            if (initDependencies(dependencyFilePath, inputPaths)
                            && dependenciesHaveChanged() == false) {
                System.out.println("No changed resources or assets. " + mApkName
                                    + " remains untouched");
                return;
            }
            if (mResourceFilter == null) {
                System.out.println("Creating full resource package...");
            } else {
                System.out.println(String.format(
                        "Creating resource package with filter: (%1$s)...",
                        mResourceFilter));
            }
        }

        // create a task for the default apk.
        ExecTask task = new ExecTask();
        task.setExecutable(mExecutable);
        task.setFailonerror(true);

        task.setTaskName(getExecTaskName());

        // aapt command. Only "package" is supported at this time really.
        task.createArg().setValue(mCommand);

        // No crunch flag
        if (mUseCrunchCache) {
            task.createArg().setValue("--no-crunch");
        }

        if (mNonConstantId) {
            task.createArg().setValue("--non-constant-id");
        }

        // force flag
        if (mForce) {
            task.createArg().setValue("-f");
        }

        // verbose flag
        if (mVerbose) {
            task.createArg().setValue("-v");
        }

        if (mDebug) {
            task.createArg().setValue("--debug-mode");
        }

        if (generateRClass) {
            task.createArg().setValue("-m");
        }

        // filters if needed
        if (mResourceFilter != null && mResourceFilter.length() > 0) {
            task.createArg().setValue("-c");
            task.createArg().setValue(mResourceFilter);
        }

        // no compress flag
        // first look to see if there's a NoCompress object with no specified extension
        boolean compressNothing = false;
        for (NoCompress nc : mNoCompressList) {
            if (nc.mExtension == null) {
                task.createArg().setValue("-0");
                task.createArg().setValue("");
                compressNothing = true;
                break;
            }
        }

        if (compressNothing == false) {
            for (NoCompress nc : mNoCompressList) {
                task.createArg().setValue("-0");
                task.createArg().setValue(nc.mExtension);
            }
        }

        // if this is a library or there are library dependencies
        if (mNonConstantId || (libPkgProp != null && libPkgProp.length() > 0)) {
            if (mBinFolder == null) {
                throw new BuildException(
                        "Missing attribute binFolder when compiling libraries or projects with libraries.");
            }
            task.createArg().setValue("--output-text-symbols");
            task.createArg().setValue(mBinFolder);
        }

        // if the project contains libraries, force auto-add-overlay
        if (libResRef != null) {
            task.createArg().setValue("--auto-add-overlay");
        }

        if (mVersionCode != 0) {
            task.createArg().setValue("--version-code");
            task.createArg().setValue(Integer.toString(mVersionCode));
        }

        if (mVersionName != null && mVersionName.length() > 0) {
            task.createArg().setValue("--version-name");
            task.createArg().setValue(mVersionName);
        }

        // manifest location
        if (mManifestFile != null && mManifestFile.length() > 0) {
            task.createArg().setValue("-M");
            task.createArg().setValue(mManifestFile);
        }

        // Rename manifest package
        if (mManifestPackage != null) {
            task.createArg().setValue("--rename-manifest-package");
            task.createArg().setValue(mManifestPackage);
        }

        // resources locations.
        if (mResources.size() > 0) {
            for (Path pathList : mResources) {
                for (String path : pathList.list()) {
                    // This may not exists, and aapt doesn't like it, so we check first.
                    File res = new File(path);
                    if (res.isDirectory()) {
                        task.createArg().setValue("-S");
                        task.createArg().setValue(path);
                    }
                }
            }
        }

        // add other resources coming from library project
        if (libResRef instanceof Path) {
            for (String path : ((Path)libResRef).list()) {
                // This may not exists, and aapt doesn't like it, so we check first.
                File res = new File(path);
                if (res.isDirectory()) {
                    task.createArg().setValue("-S");
                    task.createArg().setValue(path);
                }
            }
        }

        // assets location. This may not exists, and aapt doesn't like it, so we check first.
        if (mAssets != null && new File(mAssets).isDirectory()) {
            task.createArg().setValue("-A");
            task.createArg().setValue(mAssets);
        }

        // android.jar
        if (mAndroidJar != null) {
            task.createArg().setValue("-I");
            task.createArg().setValue(mAndroidJar);
        }

        // apk file. This is based on the apkFolder, apkBaseName, and the configName (if applicable)
        String filename = null;
        if (mApkName != null) {
            filename = mApkName;
        }

        if (filename != null) {
            File file = new File(mApkFolder, filename);
            task.createArg().setValue("-F");
            task.createArg().setValue(file.getAbsolutePath());
        }

        // R class generation
        if (generateRClass) {
            task.createArg().setValue("-J");
            task.createArg().setValue(mRFolder);
        }

        // ignore assets flag
        if (mIgnoreAssets != null && mIgnoreAssets.length() > 0) {
            task.createArg().setValue("--ignore-assets");
            task.createArg().setValue(mIgnoreAssets);
        }

        // Use dependency generation
        task.createArg().setValue("--generate-dependencies");

        // use the proguard file
        if (mProguardFile != null && mProguardFile.length() > 0) {
            task.createArg().setValue("-G");
            task.createArg().setValue(mProguardFile);
        }

        // final setup of the task
        task.setProject(taskProject);
        task.setOwningTarget(getOwningTarget());

        // execute it.
        task.execute();

        // now if the project has libraries, R needs to be created for each libraries
        // but only if the project is not a library.
        try {
            if (!mNonConstantId && libPkgProp != null && !libPkgProp.isEmpty()) {
                File rFile = new File(mBinFolder, SdkConstants.FN_RESOURCE_TEXT);
                if (rFile.isFile()) {
                    // Load the full symbols from the full R.txt file.
                    SymbolLoader fullSymbolValues = new SymbolLoader(rFile);
                    fullSymbolValues.load();

                    // we have two props which contains list of items. Both items represent
                    // 2 data of a single property.
                    // Don't want to use guava's splitter because it doesn't provide a list of the
                    // result. but we know the list starts with a ; so strip it.
                    if (libPkgProp.startsWith(";")) {
                        libPkgProp = libPkgProp.substring(1).trim();
                    }
                    String[] packages = libPkgProp.split(";");
                    String[] rFiles = libRFileProp.list();

                    if (packages.length != rFiles.length) {
                        throw new BuildException(String.format(
                                "%1$s and %2$s must contain the same number of items.",
                                mLibraryPackagesRefid, mLibraryRFileRefid));
                    }

                    if (mOriginalManifestPackage == null) {
                        mOriginalManifestPackage = getPackageName(mManifestFile);
                    }

                    Multimap<String, SymbolLoader> libMap = ArrayListMultimap.create();

                    // First pass processing the libraries, collecting them by packageName,
                    // and ignoring the ones that have the same package name as the application
                    // (since that R class was already created).
                    for (int i = 0 ; i < packages.length ; i++) {
                        String libPackage = packages[i];

                        // skip libraries that have the same package name as the application.
                        if (mOriginalManifestPackage.equals(libPackage)) {
                            continue;
                        }

                        File rText = new File(rFiles[i]);
                        if (rText.isFile()) {
                            // load the lib symbols
                            SymbolLoader libSymbols = new SymbolLoader(rText);
                            libSymbols.load();

                            // store these symbols by associating them with the package name.
                            libMap.put(libPackage, libSymbols);
                        }
                    }

                    // now loop on all the package names, merge all the symbols to write,
                    // and write them
                    for (String packageName : libMap.keySet()) {
                        Collection<SymbolLoader> symbols = libMap.get(packageName);

                        SymbolWriter writer = new SymbolWriter(mRFolder, packageName,
                                fullSymbolValues);
                        for (SymbolLoader symbolLoader : symbols) {
                            writer.addSymbolsToWrite(symbolLoader);
                        }
                        writer.write();
                    }
                }
            }
        } catch (Exception e) {
            // HACK alert.
            // in order for this step to happen again when this part fails, we delete
            // the dependency file.
            File f = new File(mRFolder, "R.java.d");
            f.delete();

            throw (e instanceof BuildException) ? (BuildException)e : new BuildException(e);
        }
    }

    private String getPackageName(String manifest) {
        XPath xpath = AndroidXPathFactory.newXPath();

        try {
            String s = xpath.evaluate("/manifest/@package",
                    new InputSource(new FileInputStream(manifest)));
            return s;
        } catch (XPathExpressionException e) {
            throw new BuildException(e);
        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        }
    }
}
