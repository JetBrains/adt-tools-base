/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Custom task to execute dx while handling dependencies.
 */
public class DexExecTask extends SingleDependencyTask {

    private String mExecutable;
    private String mOutput;
    private String mDexedLibs;
    private boolean mVerbose = false;
    private boolean mNoLocals = false;
    private boolean mForceJumbo = false;
    private boolean mDisableDexMerger = false;
    private List<Path> mPathInputs;
    private List<FileSet> mFileSetInputs;


    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "output" attribute.
     * @param output the value.
     */
    public void setOutput(Path output) {
        mOutput = TaskHelper.checkSinglePath("output", output);
    }

    public void setDexedLibs(Path dexedLibs) {
        mDexedLibs = TaskHelper.checkSinglePath("dexedLibs", dexedLibs);
    }

    /**
     * Sets the value of the "nolocals" attribute.
     * @param nolocals the value.
     */
    public void setNoLocals(boolean nolocals) {
        mNoLocals = nolocals;
    }

    public void setForceJumbo(boolean forceJumbo) {
        mForceJumbo = forceJumbo;
    }

    public void setDisableDexMerger(boolean disableMerger) {
        mDisableDexMerger  = disableMerger;
    }

    /**
     * Returns an object representing a nested <var>path</var> element.
     */
    public Object createPath() {
        if (mPathInputs == null) {
            mPathInputs = new ArrayList<Path>();
        }

        Path path = new Path(getProject());
        mPathInputs.add(path);

        return path;
    }

    /**
     * Returns an object representing a nested <var>path</var> element.
     */
    public Object createFileSet() {
        if (mFileSetInputs == null) {
            mFileSetInputs = new ArrayList<FileSet>();
        }

        FileSet fs = new FileSet();
        fs.setProject(getProject());
        mFileSetInputs.add(fs);

        return fs;
    }


    private void preDexLibraries(List<File> inputs) {
        if (mDisableDexMerger || inputs.size() == 1) {
            // only one input, no need to put a pre-dexed version, even if this path is
            // just a jar file (case for proguard'ed builds)
            return;
        }

        final int count = inputs.size();
        for (int i = 0 ; i < count; i++) {
            File input = inputs.get(i);
            if (input.isFile()) {
                // check if this libs needs to be pre-dexed
                String fileName = getDexFileName(input);
                File dexedLib = new File(mDexedLibs, fileName);
                String dexedLibPath = dexedLib.getAbsolutePath();

                if (dexedLib.isFile() == false ||
                        dexedLib.lastModified() < input.lastModified()) {

                    System.out.println(
                            String.format("Pre-Dexing %1$s -> %2$s",
                                    input.getAbsolutePath(), fileName));

                    if (dexedLib.isFile()) {
                        dexedLib.delete();
                    }

                    runDx(input, dexedLibPath, false /*showInput*/);
                } else {
                    System.out.println(
                            String.format("Using Pre-Dexed %1$s <- %2$s",
                                    fileName, input.getAbsolutePath()));
                }

                // replace the input with the pre-dex libs.
                inputs.set(i, dexedLib);
            }
        }
    }

    private String getDexFileName(File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path
        HashFunction hashFunction = Hashing.md5();
        HashCode hashCode = hashFunction.hashString(inputFile.getAbsolutePath());

        return name + "-" + hashCode.toString() + ".jar";
    }


    @Override
    public void execute() throws BuildException {

        // get all input paths
        List<File> paths = new ArrayList<File>();
        if (mPathInputs != null) {
            for (Path pathList : mPathInputs) {
                for (String path : pathList.list()) {
                    System.out.println("input: " + path);
                    paths.add(new File(path));
                }
            }
        }

        if (mFileSetInputs != null) {
            for (FileSet fs : mFileSetInputs) {
                Iterator<?> iter = fs.iterator();
                while (iter.hasNext()) {
                    FileResource fr = (FileResource) iter.next();
                    System.out.println("input: " + fr.getFile().toString());
                    paths.add(fr.getFile());
                }
            }
        }

        // pre dex libraries if needed
        preDexLibraries(paths);

        // figure out the path to the dependency file.
        String depFile = mOutput + ".d";

        // get InputPath with no extension restrictions
        List<InputPath> inputPaths = getInputPaths(paths, null /*extensionsToCheck*/,
                null /*factory*/);

        if (initDependencies(depFile, inputPaths) && dependenciesHaveChanged() == false) {
            System.out.println(
                    "No new compiled code. No need to convert bytecode to dalvik format.");
            return;
        }

        System.out.println(String.format(
                "Converting compiled files and external libraries into %1$s...", mOutput));

        runDx(paths, mOutput, mVerbose /*showInputs*/);

        // generate the dependency file.
        generateDependencyFile(depFile, inputPaths, mOutput);
    }

    private void runDx(File input, String output, boolean showInputs) {
        runDx(Collections.singleton(input), output, showInputs);
    }

    private void runDx(Collection<File> inputs, String output, boolean showInputs) {
        ExecTask task = new ExecTask();
        task.setProject(getProject());
        task.setOwningTarget(getOwningTarget());
        task.setExecutable(mExecutable);
        task.setTaskName(getExecTaskName());
        task.setFailonerror(true);

        task.createArg().setValue("--dex");

        if (mNoLocals) {
            task.createArg().setValue("--no-locals");
        }

        if (mVerbose) {
            task.createArg().setValue("--verbose");
        }

        if (mForceJumbo) {
            task.createArg().setValue("--force-jumbo");
        }

        task.createArg().setValue("--output");
        task.createArg().setValue(output);

        for (File input : inputs) {
            String absPath = input.getAbsolutePath();
            if (showInputs) {
                System.out.println("Input: " + absPath);
            }
            task.createArg().setValue(absPath);
        }

        // execute it.
        task.execute();
    }

    @Override
    protected String getExecTaskName() {
        return "dx";
    }
}
