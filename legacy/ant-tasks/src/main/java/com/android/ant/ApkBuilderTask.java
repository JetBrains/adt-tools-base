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

import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkBuilder.FileEntry;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ApkBuilderTask extends SingleDependencyTask {

    private final static Pattern PATTERN_JAR_EXT = Pattern.compile("^.+\\.jar$",
            Pattern.CASE_INSENSITIVE);

    private String mOutFolder;
    private String mApkFilepath;
    private String mResourceFile;
    private boolean mVerbose = false;
    private boolean mDebugPackaging = false;
    private boolean mDebugSigning = false;
    private boolean mHasCode = true;

    private Path mDexPath;

    private final ArrayList<Path> mZipList = new ArrayList<Path>();
    private final ArrayList<Path> mSourceList = new ArrayList<Path>();
    private final ArrayList<Path> mJarfolderList = new ArrayList<Path>();
    private final ArrayList<Path> mJarfileList = new ArrayList<Path>();
    private final ArrayList<Path> mNativeList = new ArrayList<Path>();

    private static class SourceFolderInputPath extends InputPath {
        public SourceFolderInputPath(File file) {
            super(file);
        }

        @Override
        public boolean ignores(File file) {
            if (file.isDirectory()) {
                return !ApkBuilder.checkFolderForPackaging(file.getName());
            } else {
                return !ApkBuilder.checkFileForPackaging(file.getName());
            }
        }
    }

    /**
     * Sets the value of the "outfolder" attribute.
     * @param outFolder the value.
     */
    public void setOutfolder(Path outFolder) {
        mOutFolder = TaskHelper.checkSinglePath("outfolder", outFolder);
    }

    /**
     * Sets the full filepath to the apk to generate.
     * @param filepath
     */
    public void setApkfilepath(String filepath) {
        mApkFilepath = filepath;
    }

    /**
     * Sets the resourcefile attribute
     * @param resourceFile
     */
    public void setResourcefile(String resourceFile) {
        mResourceFile = resourceFile;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "debug" attribute.
     * @param debug the debug mode value.
     */
    public void setDebug(boolean debug) {
        System.out.println("WARNNG: Using deprecated 'debug' attribute in ApkBuilderTask." +
        "Use 'debugpackaging' and 'debugsigning' instead.");
        mDebugPackaging = debug;
        mDebugSigning = debug;
    }

    /**
     * Sets the value of the "debugpackaging" attribute.
     * @param debug the debug mode value.
     */
    public void setDebugpackaging(boolean debug) {
        mDebugPackaging = debug;
    }

    /**
     * Sets the value of the "debugsigning" attribute.
     * @param debug the debug mode value.
     */
    public void setDebugsigning(boolean debug) {
        mDebugSigning = debug;
    }

    /**
     * Sets the hascode attribute. Default is true.
     * If set to false, then <dex> and <sourcefolder> nodes are ignored and not processed.
     * @param hasCode the value of the attribute.
     */
    public void setHascode(boolean hasCode) {
        mHasCode   = hasCode;
    }

    /**
     * Returns an object representing a nested <var>zip</var> element.
     */
    public Object createZip() {
        Path path = new Path(getProject());
        mZipList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>dex</var> element.
     * This is similar to a nested <var>file</var> element, except when {@link #mHasCode}
     * is <code>false</code> in which case it's ignored.
     */
    public Object createDex() {
        if (mDexPath == null) {
            return mDexPath = new Path(getProject());
        } else {
            throw new BuildException("Only one <dex> inner element can be provided");
        }
    }

    /**
     * Returns an object representing a nested <var>sourcefolder</var> element.
     */
    public Object createSourcefolder() {
        Path path = new Path(getProject());
        mSourceList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>jarfolder</var> element.
     */
    public Object createJarfolder() {
        Path path = new Path(getProject());
        mJarfolderList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>jarfile</var> element.
     */
    public Object createJarfile() {
        Path path = new Path(getProject());
        mJarfileList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>nativefolder</var> element.
     */
    public Object createNativefolder() {
        Path path = new Path(getProject());
        mNativeList.add(path);
        return path;
    }

    @Override
    public void execute() throws BuildException {

        File outputFile;
        if (mApkFilepath != null) {
            outputFile = new File(mApkFilepath);
        } else {
            throw new BuildException("missing attribute 'apkFilepath'");
        }

        if (mResourceFile == null) {
            throw new BuildException("missing attribute 'resourcefile'");
        }

        if (mOutFolder == null) {
            throw new BuildException("missing attribute 'outfolder'");
        }

        // check dexPath is only one file.
        File dexFile = null;
        if (mHasCode) {
            String[] dexFiles = mDexPath.list();
            if (dexFiles.length != 1) {
                throw new BuildException(String.format(
                        "Expected one dex file but path value resolve to %d files.",
                        dexFiles.length));
            }
            dexFile = new File(dexFiles[0]);
        }

        try {
            // build list of input files/folders to compute dependencies
            // add the content of the zip files.
            List<InputPath> inputPaths = new ArrayList<InputPath>();

            // resource file
            InputPath resourceInputPath = new InputPath(new File(mOutFolder, mResourceFile));
            inputPaths.add(resourceInputPath);

            // dex file
            if (dexFile != null) {
                inputPaths.add(new InputPath(dexFile));
            }

            // zip input files
            List<File> zipFiles = new ArrayList<File>();
            for (Path pathList : mZipList) {
                for (String path : pathList.list()) {
                    File f =  new File(path);
                    zipFiles.add(f);
                    inputPaths.add(new InputPath(f));
                }
            }

            // now go through the list of source folders used to add non java files.
            List<File> sourceFolderList = new ArrayList<File>();
            if (mHasCode) {
                for (Path pathList : mSourceList) {
                    for (String path : pathList.list()) {
                        File f =  new File(path);
                        sourceFolderList.add(f);
                        // because this is a source folder but we only care about non
                        // java files.
                        inputPaths.add(new SourceFolderInputPath(f));
                    }
                }
            }

            // now go through the list of jar folders.
            List<File> jarFileList = new ArrayList<File>();
            for (Path pathList : mJarfolderList) {
                for (String path : pathList.list()) {
                    // it's ok if top level folders are missing
                    File folder = new File(path);
                    if (folder.isDirectory()) {
                        String[] filenames = folder.list(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return PATTERN_JAR_EXT.matcher(name).matches();
                            }
                        });

                        for (String filename : filenames) {
                            File f = new File(folder, filename);
                            jarFileList.add(f);
                            inputPaths.add(new InputPath(f));
                        }
                    }
                }
            }

            // now go through the list of jar files.
            for (Path pathList : mJarfileList) {
                for (String path : pathList.list()) {
                    File f = new File(path);
                    jarFileList.add(f);
                    inputPaths.add(new InputPath(f));
                }
            }

            // now the native lib folder.
            List<FileEntry> nativeFileList = new ArrayList<FileEntry>();
            for (Path pathList : mNativeList) {
                for (String path : pathList.list()) {
                    // it's ok if top level folders are missing
                    File folder = new File(path);
                    if (folder.isDirectory()) {
                        List<FileEntry> entries = ApkBuilder.getNativeFiles(folder,
                                mDebugPackaging);
                        // add the list to the list of native files and then create an input
                        // path for each file
                        nativeFileList.addAll(entries);

                        for (FileEntry entry : entries) {
                            inputPaths.add(new InputPath(entry.mFile));
                        }
                    }
                }
            }

            // Finally figure out the path to the dependency file.
            String depFile = outputFile.getAbsolutePath() + ".d";

            // check dependencies
            if (initDependencies(depFile, inputPaths) && dependenciesHaveChanged() == false) {
                System.out.println(
                        "No changes. No need to create apk.");
                return;
            }

            if (mDebugSigning) {
                System.out.println(String.format(
                        "Creating %s and signing it with a debug key...", outputFile.getName()));
            } else {
                System.out.println(String.format(
                        "Creating %s for release...", outputFile.getName()));
            }

            ApkBuilder apkBuilder = new ApkBuilder(
                    outputFile,
                    resourceInputPath.getFile(),
                    dexFile,
                    mDebugSigning ? ApkBuilder.getDebugKeystore() : null,
                    mVerbose ? System.out : null);
            apkBuilder.setDebugMode(mDebugPackaging);


            // add the content of the zip files.
            for (File f : zipFiles) {
                if (mVerbose) {
                    System.out.println("Zip Input: " + f.getAbsolutePath());
                }
                apkBuilder.addZipFile(f);
            }

            // now go through the list of file to directly add the to the list.
            for (File f : sourceFolderList) {
                if (mVerbose) {
                    System.out.println("Source Folder Input: " + f.getAbsolutePath());
                }
                apkBuilder.addSourceFolder(f);
            }

            // now go through the list of jar files.
            for (File f : jarFileList) {
                if (mVerbose) {
                    System.out.println("Jar Input: " + f.getAbsolutePath());
                }
                apkBuilder.addResourcesFromJar(f);
            }

            // and finally the native files
            apkBuilder.addNativeLibraries(nativeFileList);

            // close the archive
            apkBuilder.sealApk();

            // and generate the dependency file
            generateDependencyFile(depFile, inputPaths, outputFile.getAbsolutePath());
        } catch (DuplicateFileException e) {
            System.err.println(String.format(
                    "Found duplicate file for APK: %1$s\nOrigin 1: %2$s\nOrigin 2: %3$s",
                    e.getArchivePath(), e.getFile1(), e.getFile2()));
            throw new BuildException(e);
        } catch (ApkCreationException e) {
            throw new BuildException(e);
        } catch (SealedApkException e) {
            throw new BuildException(e);
        } catch (IllegalArgumentException e) {
            throw new BuildException(e);
        }
    }

    @Override
    protected String getExecTaskName() {
        return "apkbuilder";
    }
}
