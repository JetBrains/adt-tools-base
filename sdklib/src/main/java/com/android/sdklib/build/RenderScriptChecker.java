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

package com.android.sdklib.build;

import static com.android.SdkConstants.DOT_DEP;
import static com.android.SdkConstants.EXT_FS;
import static com.android.SdkConstants.EXT_RS;
import static com.android.SdkConstants.EXT_RSH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Checks whether Renderscript compilation is needed. This is entirely based
 * on using dependency files and manually looking up the list of current inputs, and old
 * outputs timestamp.
 *
 * TODO: add checks on input/output checksum to detect true changes.
 * TODO: (better) delete Ant and use Gradle.
 *
 * This should be only needed in Ant.
 */
public class RenderScriptChecker {

    @NonNull
    private final List<File> mSourceFolders;

    private List<File> mInputFiles;
    private Set<File> mOldOutputs;

    @NonNull
    private final File mBinFolder;

    private List<DependencyFile> mDependencyFiles;

    public RenderScriptChecker(
            @NonNull List<File> sourceFolders,
            @NonNull File binFolder) {
        mSourceFolders = sourceFolders;
        mBinFolder = binFolder;
    }

    public boolean mustCompile() throws IOException {
        // get the dependency data from all files under bin/rsDeps/
        File renderscriptDeps = new File(mBinFolder, RenderScriptProcessor.RS_DEPS);

        File[] depsFiles = null;

        if (renderscriptDeps.isDirectory()) {
            depsFiles = renderscriptDeps.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.endsWith(DOT_DEP);
                }
            });
        }

        if (depsFiles == null || depsFiles.length == 0) {
            // gather source files.
            SourceSearcher searcher = new SourceSearcher(mSourceFolders, EXT_RS, EXT_FS);
            FileGatherer fileGatherer = new FileGatherer();
            searcher.search(fileGatherer);
            mInputFiles = fileGatherer.getFiles();
            // only return true if there are input files
            return !mInputFiles.isEmpty();
        }

        mDependencyFiles = Lists.newArrayListWithCapacity(depsFiles.length);
        mOldOutputs = Sets.newHashSet();
        Set<File> oldInputs = Sets.newHashSet();
        for (File file : depsFiles) {
            DependencyFile depFile = new DependencyFile(file, mSourceFolders);
            depFile.parse();
            mDependencyFiles.add(depFile);
            // record old inputs
            mOldOutputs.addAll(depFile.getOutputFiles());
            // record old inputs
            oldInputs.addAll(depFile.getInputFiles());
        }

        // get the current files to compile.
        SourceSearcher searcher = new SourceSearcher(mSourceFolders, EXT_RS, EXT_FS, EXT_RSH);
        InputProcessor inputProcessor = new InputProcessor(oldInputs);
        searcher.search(inputProcessor);

        // at this point we have gathered the input files, so we can record them in case we have to
        // compile later.
        mInputFiles = inputProcessor.sourceFiles;

        if (inputProcessor.mustCompile) {
            System.out.println("INPUT PROCESSOR: COMPILE TRUE");
            return true;
        }

        // no new files? check if we have less input files.
        if (oldInputs.size() !=
                inputProcessor.sourceFiles.size() + inputProcessor.headerFiles.size()) {
            System.out.println("INPUT COUNT CHANGE: COMPILE TRUE");
            return true;
        }

        // since there's no change in the input, look for change in the output.
        for (File file : mOldOutputs) {
            if (!file.isFile()) {
                // deleted output file?
                System.out.println("DELETED OUTPUT: COMPILE TRUE");
                return true;
            }
        }

        // finally look at file changes.
        for (DependencyFile depFile : mDependencyFiles) {
            if (depFile.needCompilation()) {
                System.out.println("INPUT CHANGE: COMPILE TRUE");
                return true;
            }
        }

        return false;
    }

    @Nullable
    public List<File> getInputFiles() {
        return mInputFiles;
    }

    @Nullable
    public Set<File> getOldInputs() {
        return mOldOutputs;
    }

    public void cleanDependencies() {
        if (mDependencyFiles != null) {
            for (DependencyFile depFile : mDependencyFiles) {
                depFile.getFile().delete();
            }
        }
    }

    private static class InputProcessor implements SourceSearcher.SourceFileProcessor {

        @NonNull
        private final Set<File> mOldInputs;

        List<File> sourceFiles = Lists.newArrayList();
        List<File> headerFiles = Lists.newArrayList();
        boolean mustCompile = false;

        InputProcessor(@NonNull Set<File> oldInputs) {
            mOldInputs = oldInputs;
        }

        @Override
        public void processFile(@NonNull File sourceFile, @NonNull String extension)
                throws IOException {
            if (EXT_RSH.equals(extension)) {
                headerFiles.add(sourceFile);
            } else {
                sourceFiles.add(sourceFile);
            }

            // detect new inputs.
            if (!mOldInputs.contains(sourceFile)) {
                mustCompile = true;
            }
        }
    }
}
