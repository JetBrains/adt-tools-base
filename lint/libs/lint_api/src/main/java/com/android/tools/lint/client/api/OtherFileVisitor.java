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
package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FD_ASSETS;
import static com.android.tools.lint.detector.api.Detector.OtherFileScanner;

/**
 * Visitor for "other" files: files that aren't java sources,
 * XML sources, etc -- or which should have custom handling in some
 * other way.
 */
class OtherFileVisitor {
    @NonNull
    private final List<Detector> mDetectors;

    @NonNull
    private Map<Scope, List<File>> mFiles = new EnumMap<Scope, List<File>>(Scope.class);

    OtherFileVisitor(@NonNull List<Detector> detectors) {
        mDetectors = detectors;
    }

    /** Analyze other files in the given project */
    void scan(
            @NonNull LintDriver driver,
            @NonNull Project project,
            @Nullable Project main) {
        // Collect all project files
        File projectFolder = project.getDir();

        EnumSet<Scope> scopes = EnumSet.noneOf(Scope.class);
        for (Detector detector : mDetectors) {
            OtherFileScanner fileScanner = (OtherFileScanner) detector;
            EnumSet<Scope> applicable = fileScanner.getApplicableFiles();
            if (applicable.contains(Scope.OTHER)) {
                scopes = Scope.ALL;
                break;
            }
            scopes.addAll(applicable);
        }

        if (scopes.contains(Scope.RESOURCE_FILE)) {
            List<File> files = Lists.newArrayListWithExpectedSize(100);
            for (File res : project.getResourceFolders()) {
                collectFiles(files, res);
            }
            File assets = new File(projectFolder, FD_ASSETS);
            if (assets.exists()) {
                collectFiles(files, assets);
            }
            if (!files.isEmpty()) {
                mFiles.put(Scope.RESOURCE_FILE, files);
            }
        }

        if (scopes.contains(Scope.JAVA_FILE)) {
            List<File> files = Lists.newArrayListWithExpectedSize(100);
            for (File srcFolder : project.getJavaSourceFolders()) {
                collectFiles(files, srcFolder);
            }
            mFiles.put(Scope.JAVA_FILE, files);
        }

        if (scopes.contains(Scope.CLASS_FILE)) {
            List<File> files = Lists.newArrayListWithExpectedSize(100);
            for (File classFolder : project.getJavaClassFolders()) {
                collectFiles(files, classFolder);
            }
            mFiles.put(Scope.JAVA_FILE, files);
        }

        if (scopes.contains(Scope.MANIFEST)) {
            File manifestFile = project.getManifestFile();
            if (manifestFile != null) {
                mFiles.put(Scope.MANIFEST, Collections.<File>singletonList(manifestFile));
            }
        }

        for (Map.Entry<Scope, List<File>> entry : mFiles.entrySet()) {
            Scope scope = entry.getKey();
            List<File> files = entry.getValue();
            List<Detector> applicable = new ArrayList<Detector>(mDetectors.size());
            for (Detector detector : mDetectors) {
                OtherFileScanner fileScanner = (OtherFileScanner) detector;
                EnumSet<Scope> appliesTo = fileScanner.getApplicableFiles();
                if (appliesTo.contains(Scope.OTHER) || appliesTo.contains(scope)) {
                    applicable.add(detector);
                }
            }
            if (!applicable.isEmpty()) {
                for (File file : files) {
                    Context context = new Context(driver, project, main, file);
                    for (Detector detector : mDetectors) {
                        detector.beforeCheckFile(context);
                        detector.run(context);
                        detector.afterCheckFile(context);
                    }
                    if (driver.isCanceled()) {
                        return;
                    }
                }
            }
        }
    }

    private static void collectFiles(List<File> files, File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectFiles(files, child);
                }
            }
        } else {
            files.add(file);
        }
    }
}
