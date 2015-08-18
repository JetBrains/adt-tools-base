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

package com.android.build.gradle.internal.transforms;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformInput.FileStatus;
import com.android.build.transform.api.TransformOutput;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * PreDexing as a transform
 */
public class PreDexTransform implements Transform {

    @NonNull
    private final Set<Scope> preDexedScopes;
    @NonNull
    private final DexOptions dexOptions;
    private final boolean multiDex;
    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final Logger logger;

    public PreDexTransform(
            @NonNull Set<Scope> preDexedScopes,
            @NonNull DexOptions dexOptions,
            boolean multiDex,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull Logger logger) {
        this.preDexedScopes = preDexedScopes;
        this.dexOptions = dexOptions;
        this.multiDex = multiDex;
        this.androidBuilder = androidBuilder;
        this.logger = logger;
    }

    @NonNull
    @Override
    public String getName() {
        return "predex";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return preDexedScopes;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.COMBINED;
    }

    @NonNull
    @Override
    public Format getOutputFormat() {
        return Format.SINGLE_FOLDER;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);

            params.put("incremental", dexOptions.getIncremental());
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("multidex", multiDex);

            TargetInfo targetInfo = androidBuilder.getTargetInfo();
            Preconditions.checkState(targetInfo != null,
                    "androidBuilder.targetInfo required for task '%s'.", getName());
            BuildToolInfo buildTools = targetInfo.getBuildTools();
            params.put("build-tools", buildTools.getRevision().toString());

            return params;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(
            @NonNull Map<TransformInput, TransformOutput> inputOutputs,
            @NonNull List<TransformInput> referencedInputs,
            boolean isIncremental) throws TransformException {
        // all the output will be the same since the transform type is COMBINED.
        TransformOutput transformOutput = Iterables.getFirst(inputOutputs.values(), null);
        checkNotNull(transformOutput, "Found no output in transform with Type=COMBINED");
        File outFolder = transformOutput.getOutFile();

        try {
            final Set<String> hashs = Sets.newHashSet();
            final List<File> inputFiles = Lists.newArrayList();

            Set<TransformInput> transformInputs = inputOutputs.keySet();

            if (!isIncremental) {
                // first delete the output folder
                FileUtils.emptyFolder(outFolder);

                // we need to search for all the files.
                for (TransformInput input : transformInputs) {
                    for (File file : input.getFiles()) {
                        if (file.isFile()) {
                            inputFiles.add(file);
                        } else if (file.isDirectory()) {
                            inputFiles.addAll(
                                    Files.fileTreeTraverser().postOrderTraversal(file).filter(
                                            new Predicate<File>() {
                                                @Override
                                                public boolean apply(File file) {
                                                    return file.getPath().endsWith(DOT_CLASS);
                                                }
                                            }).toList());
                        }
                    }
                }
            } else {
                for (TransformInput input : transformInputs) {
                    for (Entry<File, FileStatus> entry : input.getChangedFiles().entrySet()) {
                        File file = entry.getKey();
                        switch (entry.getValue()) {
                            case ADDED:
                            case CHANGED:
                                inputFiles.add(file);
                                break;
                            case REMOVED:
                                File preDexedFile = getDexFileName(outFolder, file);

                                try {
                                    FileUtils.deleteFolder(preDexedFile);
                                } catch (IOException e) {
                                    logger.info("Could not delete {}\n{}",
                                            preDexedFile, Throwables.getStackTraceAsString(e));
                                }
                                break;
                        }
                    }
                }
            }

            WaitableExecutor<Void> executor = new WaitableExecutor<Void>();
            ProcessOutputHandler outputHandler = new LoggedProcessOutputHandler(new LoggerWrapper(logger));

            for (final File file : inputFiles) {
                Callable<Void> action = new PreDexTask(outFolder, file, hashs, outputHandler);
                executor.execute(action);
            }

            executor.waitForTasksWithQuickFail(false);
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    private final class PreDexTask implements Callable<Void> {
        private final File outFolder;
        private final File fileToProcess;
        private final Set<String> hashs;
        private final ProcessOutputHandler mOutputHandler;

        private PreDexTask(
                File outFolder,
                File file,
                Set<String> hashs,
                ProcessOutputHandler outputHandler) {
            this.outFolder = outFolder;
            this.fileToProcess = file;
            this.hashs = hashs;
            this.mOutputHandler = outputHandler;
        }

        @Override
        public Void call() throws Exception {
            // TODO remove once we can properly add a library as a dependency of its test.
            String hash = getFileHash(fileToProcess);

            synchronized (hashs) {
                if (hashs.contains(hash)) {
                    return null;
                }

                hashs.add(hash);
            }

            File preDexedFile = getDexFileName(outFolder, fileToProcess);

            if (multiDex) {
                mkdirs(preDexedFile);
            }

            androidBuilder.preDexLibrary(
                    fileToProcess, preDexedFile, multiDex, dexOptions, mOutputHandler);

            return null;
        }
    }

    /**
     * Returns the hash of a file.
     * @param file the file to hash
     */
    private static String getFileHash(@NonNull File file) throws IOException {
        HashCode hashCode = Files.hash(file, Hashing.sha1());
        return hashCode.toString();
    }

    /**
     * Returns a unique File for the pre-dexed library, even
     * if there are 2 libraries with the same file names (but different
     * paths)
     *
     * If multidex is enabled the return File is actually a folder.
     *
     * @param outFolder the output folder.
     * @param inputFile the library.
     */
    @NonNull
    private File getDexFileName(@NonNull File outFolder, @NonNull File inputFile) {
        String name = Files.getNameWithoutExtension(inputFile.getName());

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE);

        // If multidex is enabled, this name will be used for a folder and classes*.dex files will
        // inside of it.
        String suffix = multiDex ? "" : SdkConstants.DOT_JAR;

        if (name.equals("classes") && inputFile.getAbsolutePath().contains("exploded-aar")) {
            // This naming scheme is coming from DependencyManager#computeArtifactPath.
            File versionDir = inputFile.getParentFile().getParentFile();
            File artifactDir = versionDir.getParentFile();
            File groupDir = artifactDir.getParentFile();

            name = Joiner.on('-').join(
                    groupDir.getName(), artifactDir.getName(), versionDir.getName());
        }

        return new File(outFolder, name + "_" + hashCode.toString() + suffix);
    }
}
