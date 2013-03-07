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

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.apache.tools.ant.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *  This class takes care of dependency tracking for all targets and prerequisites listed in
 *  a single dependency file. A dependency graph always has a dependency file associated with it
 *  for the duration of its lifetime
 */
public class DependencyGraph {

    private final static boolean DEBUG = false;

    private static enum DependencyStatus {
        NONE, NEW_FILE, UPDATED_FILE, MISSING_FILE, ERROR;
    }

    // Files that we know about from the dependency file
    private Set<File> mTargets = Collections.emptySet();
    private Set<File> mPrereqs = mTargets;
    private File mFirstPrereq = null;
    private boolean mMissingDepFile = false;
    private long mDepFileLastModified;
    private final List<InputPath> mNewInputs;

    public DependencyGraph(String dependencyFilePath, List<InputPath> newInputPaths) {
        mNewInputs = newInputPaths;
        parseDependencyFile(dependencyFilePath);
    }

    /**
     * Check all the dependencies to see if anything has changed.
     *
     * @param printStatus will print to {@link System#out} the dependencies status.
     * @return true if new prerequisites have appeared, target files are missing or if
     *         prerequisite files have been modified since the last target generation.
     */
    public boolean dependenciesHaveChanged(boolean printStatus) {
        // If no dependency file has been set up, then we'll just return true
        // if we have a dependency file, we'll check to see what's been changed
        if (mMissingDepFile) {
            System.out.println("No Dependency File Found");
            return true;
        }

        // check for missing output first
        if (missingTargetFile()) {
            if (printStatus) {
                System.out.println("Found Deleted Target File");
            }
            return true;
        }

        // get the time stamp of the oldest target.
        long oldestTarget = getOutputLastModified();

        // first look through the input folders and look for new files or modified files.
        DependencyStatus status = checkInputs(oldestTarget);

        // this can't find missing files. This is done later.
        switch (status) {
            case ERROR:
                throw new BuildException();
            case NEW_FILE:
                if (printStatus) {
                    System.out.println("Found new input file");
                }
                return true;
            case UPDATED_FILE:
                if (printStatus) {
                    System.out.println("Found modified input file");
                }
                return true;
        }

        // now do a full check on the remaining files.
        status = checkPrereqFiles(oldestTarget);
        // this can't find new input files. This is done above.
        switch (status) {
            case ERROR:
                throw new BuildException();
            case MISSING_FILE:
                if (printStatus) {
                    System.out.println("Found deleted input file");
                }
                return true;
            case UPDATED_FILE:
                if (printStatus) {
                    System.out.println("Found modified input file");
                }
                return true;
        }

        return false;
    }

    public Set<File> getTargets() {
        return Collections.unmodifiableSet(mTargets);
    }

    public File getFirstPrereq() {
        return mFirstPrereq;
    }

    /**
     * Parses the given dependency file and stores the file paths
     *
     * @param dependencyFilePath the dependency file
     */
    private void parseDependencyFile(String dependencyFilePath) {
        // first check if the dependency file is here.
        File depFile = new File(dependencyFilePath);
        if (depFile.isFile() == false) {
            mMissingDepFile = true;
            return;
        }

        // get the modification time of the dep file as we may need it later
        mDepFileLastModified = depFile.lastModified();

        // Read in our dependency file
        List<String> content = readFile(depFile);
        if (content == null) {
            System.err.println("ERROR: Couldn't read " + dependencyFilePath);
            return;
        }

        // The format is something like:
        // output1 output2 [...]: dep1 dep2 [...]
        // expect it's likely split on several lines. So let's move it back on a single line
        // first
        StringBuilder sb = new StringBuilder();
        for (String line : content) {
            line = line.trim();
            if (line.endsWith("\\")) {
                line = line.substring(0, line.length() - 1);
            }
            sb.append(line);
        }

        // split the left and right part
        String[] files = sb.toString().split(":");

        // get the target files:
        String[] targets = files[0].trim().split(" ");

        String[] prereqs = {};
        // Check to make sure our dependency file is okay
        if (files.length < 1) {
            System.err.println(
                    "Warning! Dependency file does not list any prerequisites after ':' ");
        } else {
            // and the prerequisite files:
            prereqs = files[1].trim().split(" ");
        }

        mTargets = new HashSet<File>(targets.length);
        for (String path : targets) {
            if (path.length() > 0) {
                mTargets.add(new File(path));
            }
        }

        mPrereqs = new HashSet<File>(prereqs.length);
        for (String path : prereqs) {
            if (path.length() > 0) {
                if (DEBUG) {
                    System.out.println("PREREQ: " + path);
                }
                File f = new File(path);
                if (mFirstPrereq == null) {
                    mFirstPrereq = f;
                }
                mPrereqs.add(f);
            }
        }
    }

    /**
     * Check all the input files and folders to see if there have been new
     * files added to them or if any of the existing files have been modified.
     *
     * This looks at the input paths, not at the list of known prereq. Therefore this
     * will not find missing files. It will however remove processed files from the
     * prereq file list so that we can process those in a 2nd step.
     *
     * This should be followed by a call to {@link #checkPrereqFiles(long)} which
     * will process the remaining files in the prereq list.
     *
     * If a change is found, this will return immediately with either
     * {@link DependencyStatus#NEW_FILE} or {@link DependencyStatus#UPDATED_FILE}.
     *
     * @param oldestTarget the timestamp of the oldest output file to compare against.
     *
     * @return the status of the file in the watched folders.
     *
     */
    private DependencyStatus checkInputs(long oldestTarget) {
        if (mNewInputs != null) {
            for (InputPath input : mNewInputs) {
                File file = input.getFile();
                if (file.isDirectory()) {
                    DependencyStatus status = checkInputFolder(file, input, oldestTarget);
                    if (status != DependencyStatus.NONE) {
                        return status;
                    }
                } else if (file.isFile()) {
                    DependencyStatus status = checkInputFile(file, input, oldestTarget);
                    if (status != DependencyStatus.NONE) {
                        return status;
                    }
                }
            }
        }

        // If we make it all the way through our directories we're good.
        return DependencyStatus.NONE;
    }

    /**
     * Check all the files in the tree under root and check to see if the files are
     * listed under the dependencies, or if they have been modified. Recurses into subdirs.
     *
     * @param folder the folder to search through.
     * @param inputFolder the root level inputFolder
     * @param oldestTarget the time stamp of the oldest output file to compare against.
     *
     * @return the status of the file in the folder.
     */
    private DependencyStatus checkInputFolder(File folder, InputPath inputFolder,
            long oldestTarget) {
        if (inputFolder.ignores(folder)) {
            return DependencyStatus.NONE;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            System.err.println("ERROR " + folder.toString() + " is not a dir or can't be read");
            return DependencyStatus.ERROR;
        }
        // Loop through files in this folder
        for (File file : files) {
            // If this is a directory, recurse into it
            if (file.isDirectory()) {
                DependencyStatus status = checkInputFolder(file, inputFolder, oldestTarget);
                if (status != DependencyStatus.NONE) {
                    return status;
                }
            } else if (file.isFile()) {
                DependencyStatus status = checkInputFile(file, inputFolder, oldestTarget);
                if (status != DependencyStatus.NONE) {
                    return status;
                }
            }
        }
        // If we got to here then we didn't find anything interesting
        return DependencyStatus.NONE;
    }

    private DependencyStatus checkInputFile(File file, InputPath inputFolder,
            long oldestTarget) {
        if (inputFolder.ignores(file)) {
            return DependencyStatus.NONE;
        }

        // if it's a file, remove it from the list of prereqs.
        // This way if files in this folder don't trigger a build we'll have less
        // files to go through manually
        if (mPrereqs.remove(file) == false) {
            // turns out this is a new file!

            if (DEBUG) {
                System.out.println("NEW FILE: " + file.getAbsolutePath());
            }
            return DependencyStatus.NEW_FILE;
        } else {
            // check the time stamp on this file if it's a file we care about based what the
            // input folder decides.
            if (inputFolder.checksForModification(file)) {
                if (file.lastModified() > oldestTarget) {
                    if (DEBUG) {
                        System.out.println("UPDATED FILE: " + file.getAbsolutePath());
                    }
                    return DependencyStatus.UPDATED_FILE;
                }
            }
        }

        return DependencyStatus.NONE;
    }

    /**
     * Check all the prereq files we know about to make sure they're still there, or that they
     * haven't been modified since the last build.
     *
     * @param oldestTarget the time stamp of the oldest output file to compare against.
     *
     * @return the status of the files
     */
    private DependencyStatus checkPrereqFiles(long oldestTarget) {
        // TODO: Optimize for the case of a specific file as inputPath.
        //       We should have a map of filepath to inputpath to quickly search through them?

        // Loop through our prereq files and make sure they still exist
        for (File prereq : mPrereqs) {
            if (prereq.exists() == false) {
                if (DEBUG) {
                    System.out.println("MISSING FILE: " + prereq.getAbsolutePath());
                }
                return DependencyStatus.MISSING_FILE;
            }

            // check the time stamp on this file if it's a file we care about.
            // To know if we care about the file we have to find the matching input.
            if (mNewInputs != null) {
                String filePath = prereq.getAbsolutePath();
                for (InputPath input : mNewInputs) {
                    File inputFile = input.getFile();
                    // if the input path is a directory, check if the prereq file is in it,
                    // otherwise check if the prereq file match exactly the input path.
                    if (inputFile.isDirectory()) {
                        if (filePath.startsWith(inputFile.getAbsolutePath())) {
                            // ok file is inside a directory type input folder.
                            // check if we need to check this type of file, and if yes, check it.
                            if (input.checksForModification(prereq)) {
                                if (prereq.lastModified() > oldestTarget) {
                                    if (DEBUG) {
                                        System.out.println(
                                                "UPDATED FILE: " + prereq.getAbsolutePath());
                                    }
                                    return DependencyStatus.UPDATED_FILE;
                                }
                            }
                        }
                    } else {
                        // this is a file input path, we must check if the match is exact.
                        if (prereq.equals(inputFile)) {
                            if (input.checksForModification(prereq)) {
                                if (prereq.lastModified() > oldestTarget) {
                                    if (DEBUG) {
                                        System.out.println(
                                                "UPDATED FILE: " + prereq.getAbsolutePath());
                                    }
                                    return DependencyStatus.UPDATED_FILE;
                                }
                            }
                        }
                    }
                }
            } else {
                // no input? we consider all files.
                if (prereq.lastModified() > oldestTarget) {
                    if (DEBUG) {
                        System.out.println("UPDATED FILE: " + prereq.getAbsolutePath());
                    }
                    return DependencyStatus.UPDATED_FILE;
                }
            }
        }

        // If we get this far, then all our prereq are okay
        return DependencyStatus.NONE;
    }

    /**
     * Check all the target files we know about to make sure they're still there
     * @return true if any of the target files are missing.
     */
    private boolean missingTargetFile() {
        // Loop through our target files and make sure they still exist
        for (File target : mTargets) {
            if (target.exists() == false) {
                return true;
            }
        }
        // If we get this far, then all our targets are okay
        return false;
    }

    /**
     * Returns the earliest modification time stamp from all the output targets. If there
     * are no known target, the dependency file time stamp is returned.
     */
    private long getOutputLastModified() {
        // Find the oldest target
        long oldestTarget = Long.MAX_VALUE;
        // if there's no output, then compare to the time of the dependency file.
        if (mTargets.size() == 0) {
            oldestTarget = mDepFileLastModified;
        } else {
            for (File target : mTargets) {
                if (target.lastModified() < oldestTarget) {
                    oldestTarget = target.lastModified();
                }
            }
        }

        return oldestTarget;
    }

    /**
     * Reads and returns the content of a text file.
     * @param file the file to read
     * @return null if the file could not be read
     */
    private static List<String> readFile(File file) {
        try {
            return Files.readLines(file, Charsets.UTF_8);
        } catch (IOException e) {
            // return null below
        }

        return null;
    }
}
