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

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Dexing as a transform.
 *
 * This consumes all the available {@link ContentType#CLASSES} streams and create a dex file
 * (or more in the case of multi-dex)
 *
 * This handles pre-dexing as well. If there are more than one stream, then only streams with
 * changed files will be re-dexed before a single merge phase is done at the end.
 * If there is a single input, then there's only a single dx phase.
 */
public class DexTransform extends Transform {

    @NonNull
    private final DexOptions dexOptions;

    private final boolean debugMode;
    private final boolean multiDex;

    @NonNull
    private final File intermediateFolder;
    @Nullable
    private final File mainDexListFile;

    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final ILogger logger;

    public DexTransform(
            @NonNull DexOptions dexOptions,
            boolean debugMode,
            boolean multiDex,
            @Nullable File mainDexListFile,
            @NonNull File intermediateFolder,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull Logger logger) {
        this.dexOptions = dexOptions;
        this.debugMode = debugMode;
        this.multiDex = multiDex;
        this.mainDexListFile = mainDexListFile;
        this.intermediateFolder = intermediateFolder;
        this.androidBuilder = androidBuilder;
        this.logger = new LoggerWrapper(logger);
    }

    @NonNull
    @Override
    public String getName() {
        return "dex";
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
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        if (mainDexListFile != null) {
            return ImmutableList.of(mainDexListFile);
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        // we use the intermediate folder only if
        // - there's per-scope dexing
        // - there's no native multi-dex
        if (dexOptions.getPreDexLibraries() && !(multiDex && mainDexListFile == null)) {
            return ImmutableList.of(intermediateFolder);
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);

            params.put("debugMode", debugMode);
            params.put("predex", dexOptions.getPreDexLibraries());
            params.put("incremental", dexOptions.getIncremental());
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("multidex", multiDex);
            params.put("multidex-legacy",  multiDex && mainDexListFile != null);

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
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws TransformException, IOException, InterruptedException {
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // Gather a full list of all inputs.
        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> directoryInputs = Lists.newArrayList();
        for (TransformInput input : inputs) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        try {
            // if only one scope or no per-scope dexing, just do a single pass that
            // runs dx on everything.
            if ((jarInputs.size() + directoryInputs.size()) == 1 || !dexOptions.getPreDexLibraries()) {
                File outputDir = outputProvider.getContentLocation("main",
                        getOutputTypes(), getScopes(),
                        Format.DIRECTORY);
                FileUtils.mkdirs(outputDir);

                // first delete the output folder where the final dex file(s) will be.
                FileUtils.emptyFolder(outputDir);

                // gather the inputs. This mode is always non incremental, so just
                // gather the top level folders/jars
                final List<File> inputFiles = Lists.newArrayList();
                for (JarInput jarInput : jarInputs) {
                    inputFiles.add(jarInput.getFile());
                }

                for (DirectoryInput directoryInput : directoryInputs) {
                    inputFiles.add(directoryInput.getFile());
                }

                androidBuilder.convertByteCode(
                        inputFiles,
                        outputDir,
                        multiDex,
                        mainDexListFile,
                        dexOptions,
                        null,
                        false,
                        true,
                        new LoggedProcessOutputHandler(logger));
            } else {
                // Figure out if we need to do a dx merge.
                // The ony case we don't need it is in native multi-dex mode when doing debug
                // builds. This saves build time at the expense of too many dex files which is fine.
                // FIXME dx cannot receive dex files to merge inside a folder. They have to be in a jar. Need to fix in dx.
                boolean needMerge = !multiDex || mainDexListFile != null;// || !debugMode;

                // where we write the pre-dex depends on whether we do the merge after.
                // If needMerge changed from one build to another, we'll be in non incremental
                // mode, so we don't have to deal with changing folder in incremental mode.
                File perStreamDexFolder = null;
                if (needMerge) {
                    perStreamDexFolder = intermediateFolder;

                    if (!isIncremental) {
                        FileUtils.deleteFolder(perStreamDexFolder);
                    }
                } else if (!isIncremental) {
                    // in this mode there's no merge and we dex it all separately into different
                    // output location so we have to delete everything.
                    outputProvider.deleteAll();
                }

                // dex all the different streams separately, then merge later (maybe)
                // hash to detect duplicate jars (due to isse with library and tests)
                final Set<String> hashs = Sets.newHashSet();
                // input files to output file map
                final Map<File, File> inputFiles = Maps.newHashMap();
                // stuff to delete. Might be folders.
                final List<File> deletedFiles = Lists.newArrayList();

                // first gather the different inputs to be dexed separately.
                for (DirectoryInput directoryInput : directoryInputs) {
                    File rootFolder = directoryInput.getFile();
                    // The incremental mode only detect file level changes.
                    // It does not handle removed root folders. However the transform
                    // task will add the TransformInput right after it's removed so that it
                    // can be detected by the transform.
                    if (!rootFolder.exists()) {
                        // if the root folder is gone we need to remove the previous
                        // output
                        File preDexedFile = getPreDexFile(outputProvider, needMerge, perStreamDexFolder,
                                directoryInput);
                        if (preDexedFile.exists()) {
                            deletedFiles.add(preDexedFile);
                        }
                    } else if (!isIncremental || !directoryInput.getChangedFiles().isEmpty()) {
                        // add the folder for re-dexing only if we're not in incremental
                        // mode or if it contains changed files.
                        File preDexFile = getPreDexFile(outputProvider, needMerge, perStreamDexFolder,
                                directoryInput);
                        inputFiles.put(rootFolder, preDexFile);
                    }
                }

                for (JarInput jarInput : jarInputs) {
                    switch (jarInput.getStatus()) {
                        case NOTCHANGED:
                            if (isIncremental) {
                                break;
                            }
                            // intended fall-through
                        case CHANGED:
                        case ADDED: {
                            File preDexFile = getPreDexFile(outputProvider, needMerge, perStreamDexFolder,
                                    jarInput);
                            inputFiles.put(jarInput.getFile(), preDexFile);
                            break;
                        }
                        case REMOVED: {
                            File preDexedFile = getPreDexFile(outputProvider, needMerge, perStreamDexFolder,
                                    jarInput);
                            if (preDexedFile.exists()) {
                                deletedFiles.add(preDexedFile);
                            }
                            break;
                        }
                    }
                }

                WaitableExecutor<Void> executor = new WaitableExecutor<Void>();
                ProcessOutputHandler outputHandler = new LoggedProcessOutputHandler(logger);

                for (Map.Entry<File, File> entry : inputFiles.entrySet()) {
                    Callable<Void> action = new PreDexTask(
                            entry.getKey(),
                            entry.getValue(),
                            hashs,
                            outputHandler);
                    executor.execute(action);
                }

                for (final File file : deletedFiles) {
                    executor.execute(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            FileUtils.deleteFolder(file);
                            return null;
                        }
                    });
                }

                executor.waitForTasksWithQuickFail(false);

                if (needMerge) {
                    File outputDir = outputProvider.getContentLocation("main",
                            TransformManager.CONTENT_DEX, getScopes(),
                            Format.DIRECTORY);
                    FileUtils.mkdirs(outputDir);

                    // first delete the output folder where the final dex file(s) will be.
                    FileUtils.emptyFolder(outputDir);
                    mkdirs(outputDir);

                    // find the inputs of the dex merge.
                    // they are the content of the intermediate folder.
                    List<File> outputs = null;
                    if (!multiDex) {
                        // content of the folder is jar files.
                        File[] files = intermediateFolder.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File file, String name) {
                                return name.endsWith(SdkConstants.DOT_JAR);
                            }
                        });
                        if (files != null) {
                            outputs = Arrays.asList(files);
                        }
                    } else {
                        File[] directories = intermediateFolder.listFiles(new FileFilter() {
                            @Override
                            public boolean accept(File file) {
                                return file.isDirectory();
                            }
                        });
                        if (directories != null) {
                            outputs = Arrays.asList(directories);
                        }
                    }

                    if (outputs == null) {
                        throw new RuntimeException("No dex files to merge!");
                    }

                    androidBuilder.convertByteCode(
                            outputs,
                            outputDir,
                            multiDex,
                            mainDexListFile,
                            dexOptions,
                            null,
                            false,
                            true,
                            new LoggedProcessOutputHandler(logger));
                }
            }
        } catch (LoggedErrorException e) {
            throw new TransformException(e);
        } catch (ProcessException e) {
            throw new TransformException(e);
        }
    }

    private final class PreDexTask implements Callable<Void> {
        @NonNull
        private final File from;
        @NonNull
        private final File to;
        @NonNull
        private final Set<String> hashs;
        @NonNull
        private final ProcessOutputHandler mOutputHandler;

        private PreDexTask(
                @NonNull File from,
                @NonNull File to,
                @NonNull Set<String> hashs,
                @NonNull ProcessOutputHandler outputHandler) {
            this.from = from;
            this.to = to;
            this.hashs = hashs;
            this.mOutputHandler = outputHandler;
        }

        @Override
        public Void call() throws Exception {
            // TODO remove once we can properly add a library as a dependency of its test.
            String hash = getFileHash(from);

            synchronized (hashs) {
                if (hashs.contains(hash)) {
                    return null;
                }

                hashs.add(hash);
            }

            if (to.isDirectory()) {
                FileUtils.emptyFolder(to);
            } else if (to.isFile()) {
                FileUtils.delete(to);
            } else {
                if (multiDex) {
                    mkdirs(to);
                } else {
                    mkdirs(to.getParentFile());
                }
            }

            androidBuilder.preDexLibrary(
                    from, to, multiDex, dexOptions, mOutputHandler);

            return null;
        }
    }

    /**
     * Returns the hash of a file.
     *
     * If the file is a folder, it's a hash of its path. If the file is a file, then
     * it's a hash of the file itself.
     *
     * @param file the file to hash
     */
    @NonNull
    private static String getFileHash(@NonNull File file) throws IOException {
        HashCode hashCode;
        HashFunction hashFunction = Hashing.sha1();
        if (file.isDirectory()) {
            hashCode = hashFunction.hashString(file.getPath(), Charsets.UTF_16LE);
        } else {
            hashCode = Files.hash(file, hashFunction);
        }

        return hashCode.toString();
    }


    @NonNull
    private File getPreDexFile(
            @NonNull TransformOutputProvider output,
            boolean needMerge,
            @Nullable File outFolder,
            @NonNull QualifiedContent qualifiedContent) {
        if (needMerge) {
            checkNotNull(outFolder);
            return new File(outFolder, getFilename(qualifiedContent.getFile()));
        } else {
            return getOutputLocation(output, qualifiedContent, qualifiedContent.getFile());
        }
    }

    @NonNull
    private File getOutputLocation(
            @NonNull TransformOutputProvider output,
            @NonNull QualifiedContent qualifiedContent,
            @NonNull File file) {
        File contentLocation = output.getContentLocation(getFilename(file),
                TransformManager.CONTENT_DEX, qualifiedContent.getScopes(),
                multiDex ? Format.DIRECTORY : Format.JAR);
        if (multiDex) {
            FileUtils.mkdirs(contentLocation);
        } else {
            FileUtils.mkdirs(contentLocation.getParentFile());
        }
        return contentLocation;
    }

    @NonNull
    private String getFilename(@NonNull File inputFile) {
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

        return name + "_" + hashCode.toString() + suffix;
    }
}
