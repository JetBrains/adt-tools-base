/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.JavaResourcesProvider;
import com.android.builder.model.PackagingOptions;
import com.android.builder.signing.SignedJarBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import groovy.lang.Closure;

/**
 * Extracts java resources from packaged jars into a single output directory that can be used
 * during obfuscation and packaging.
 *
 * This task is the default {@link JavaResourcesProvider} to provided merged java resources to
 * the final variant packaging step. However, if the variant obfuscation is turned on, some of
 * these resources packages might need to be adapted to match the obfuscated code. In such
 * a scenario, the {@link JavaResourcesProvider} will become the task responsible for obfuscation.
 *
 * We should make this task incremental at some point.
 */
@ParallelizableTask
public class MergeJavaResourcesTask extends DefaultAndroidTask implements JavaResourcesProvider {

    @InputDirectory
    public List<File> inputDirs;

    @OutputDirectory
    public File outputDir;

    @Nested
    @Optional
    public PackagingOptions packagingOptions;

    private FileFilter packagingOptionsFilter;

    @TaskAction
    void extractJavaResources() {

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("Cannot create output directory " + outputDir);
        }

        Closure<FileVisitDetails> fileVisitor = new Closure<FileVisitDetails>(this) {
            @SuppressWarnings("unused")
            void doCall(FileVisitDetails fileVisitDetails) {
                File f = fileVisitDetails.getFile();
                if (f.isDirectory()) return;
                try {
                    packagingOptionsFilter.apply(fileVisitDetails.getPath())
                            .execute(fileVisitDetails, outputDir);
                } catch(IOException e) {
                    throw new RuntimeException(
                            "Error while processing " + fileVisitDetails.getName(), e);
                }
            }
        };

        // visit each library jar file, to extract appropriate resources into the output directory.
        for (File inputDir : inputDirs) {
            File[] expandedFolders = inputDir.listFiles();
            if (expandedFolders != null) {
                for (final File expandedFolder : expandedFolders) {
                    getProject().fileTree(expandedFolder).visit(fileVisitor);
                }
            }
        }
    }

    /**
     * Define all possible actions for the {@link MergeJavaResourcesTask.FileFilter}
     */
    private enum Action {
        /**
         * Ignore the archive entry, it should not be merged into the resulting archive/folder.
         */
        IGNORE {
            @Override
            void execute(FileVisitDetails fileVisitDetails, File outputDir) throws IOException {
                // do nothing.
            }

        },
        /**
         * Copy the archive entry from its current location into the output location.
         */
        COPY {
            @Override
            void execute(FileVisitDetails fileVisitDetails, File outputDir) throws IOException {
                fileVisitDetails.copyTo(fileVisitDetails.getRelativePath().getFile(outputDir));
            }
        },
        /**
         * Merges the archive entry with a possibly already existing entry in the output location.
         */
        MERGE {
            @Override
            void execute(FileVisitDetails fileVisitDetails, File outputDir) throws IOException {
                // merge the two files...
                File outputFile = new File(outputDir,
                        fileVisitDetails.getRelativePath().getPathString());
                if (outputFile.exists()) {
                    OutputStream os = null;
                    try {
                        // open in append mode
                        os = new BufferedOutputStream(new FileOutputStream(outputFile, true));
                        // and merge at the end.
                        fileVisitDetails.copyTo(os);
                    } finally {
                        os.close();
                    }
                } else {
                    Action.COPY.execute(fileVisitDetails, outputDir);
                }
            }
        };

        /**
         * Defines the {@link Action} implementation.
         * @param fileVisitDetails the file to implement the action on.
         * @param outputDir the output location for the action, if the action has an output.
         * @throws IOException if the action cannot be implemented.
         */
        abstract void execute(FileVisitDetails fileVisitDetails, File outputDir) throws IOException;

    }

    /**
     * Defines a file filter contract which will use {@link PackagingOptions} to decide which
     * {@link com.android.build.gradle.internal.tasks.MergeJavaResourcesTask.Action} should be
     * taken on the file.
     */
    private static final class FileFilter implements SignedJarBuilder.IZipEntryFilter {
        @Nullable
        private final PackagingOptions packagingOptions;
        @NonNull
        private final Set<String> excludes;
        @NonNull
        private final Set<String> pickFirsts;
        private Set<String> usedPickFirsts = null;

        public FileFilter(@Nullable PackagingOptions packagingOptions) {
            this.packagingOptions = packagingOptions;
            excludes = this.packagingOptions != null ? this.packagingOptions.getExcludes() :
                    Collections.<String>emptySet();
            pickFirsts = this.packagingOptions != null ? this.packagingOptions.getPickFirsts() :
                    Collections.<String>emptySet();
        }

        /**
         * Implementation of the {@link SignedJarBuilder.IZipEntryFilter} contract which only
         * cares about copying or ignoring files since merging is handled differently.
         * @param archivePath the archive file path of the entry
         * @return true if the archive entry satisfies the filter, false otherwise.
         * @throws ZipAbortException
         */
        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return apply(archivePath) != Action.IGNORE;
        }

        /**
         * apply the filtering logic on an abstract archive entry denoted by its path and provide
         * an action to be implemented for the entry.
         * @param archivePath the archive entry path in the archive.
         * @return the action to implement.
         */
        @NonNull
        public Action apply(@NonNull String archivePath) {
            //noinspection VariableNotUsedInsideIf
            if (packagingOptions != null) {
                if (excludes.contains(archivePath)) {
                    return Action.IGNORE;
                }

                if (pickFirsts.contains(archivePath)) {
                    if (usedPickFirsts == null) {
                        usedPickFirsts = Sets.newHashSetWithExpectedSize(pickFirsts.size());
                    }

                    if (usedPickFirsts.contains(archivePath)) {
                        return Action.IGNORE;
                    } else {
                        usedPickFirsts.add(archivePath);
                    }
                }

                if (packagingOptions.getMerges().contains(archivePath)) {
                    return Action.MERGE;
                }
            }
            return Action.COPY;
        }
    }

    @NonNull
    @Override
    public ImmutableList<JavaResourcesLocation> getJavaResourcesLocations() {
        return ImmutableList.of(new JavaResourcesLocation(Type.FOLDER, outputDir));
    }

    public static class Config implements TaskConfigAction<MergeJavaResourcesTask> {

        private final VariantScope scope;

        public Config(VariantScope variantScope) {
            this.scope = variantScope;
        }

        @Override
        public String getName() {
            return scope.getTaskName("merge", "JavaResources");
        }

        @Override
        public Class<MergeJavaResourcesTask> getType() {
            return MergeJavaResourcesTask.class;
        }

        @Override
        public void execute(MergeJavaResourcesTask mergeJavaResourcesTask) {
            mergeJavaResourcesTask.inputDirs =
                    ImmutableList.of(scope.getPackagedJarsJavaResDestinationDir(),
                            scope.getSourceFoldersJavaResDestinationDir());
            mergeJavaResourcesTask.outputDir = scope.getJavaResourcesDestinationDir();

            PackagingOptions packagingOptions =
                    scope.getGlobalScope().getExtension().getPackagingOptions();
            mergeJavaResourcesTask.packagingOptionsFilter = new FileFilter(packagingOptions);
            mergeJavaResourcesTask.packagingOptions = packagingOptions;
            scope.setPackagingOptionsFilter(mergeJavaResourcesTask.packagingOptionsFilter);
        }
    }
}

