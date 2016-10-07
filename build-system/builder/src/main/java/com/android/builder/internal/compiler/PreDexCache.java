/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.io.Files;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Pre Dexing cache.
 *
 * Since we cannot yet have a single task for each library that needs to be pre-dexed (because
 * there is no task-level parallelization), this class allows reusing the output of the pre-dexing
 * of a library in a project to write the output of the pre-dexing of the same library in
 * a different project.
 *
 * Because different project could use different build-tools, both the library to pre-dex and the
 * version of the build tools are used as keys in the cache.
 *
 * The API is fairly simple, just call {@link #preDexLibrary(AndroidBuilder, File, File, boolean, DexOptions, boolean, ProcessOutputHandler)}
 *
 * The call will be blocking until the pre-dexing happened, either through actual pre-dexing or
 * through copying the output of a previous pre-dex run.
 *
 * After a build a call to {@link #clear(java.io.File, com.android.utils.ILogger)} with a file
 * will allow saving the known pre-dexed libraries for future reuse.
 */
public class PreDexCache extends PreProcessCache<DxDexKey> {

    private static final PreDexCache sSingleton = new PreDexCache();

    public static PreDexCache getCache() {
        return sSingleton;
    }

    @Override
    @NonNull
    protected KeyFactory<DxDexKey> getKeyFactory() {
        return DxDexKey.FACTORY;
    }

    /**
     * Pre-dex a given library to a given output with a specific version of the build-tools.
     *
     * @param builder {@link AndroidBuilder} instance used to dex the library
     * @param inputFile the jar to pre-dex
     * @param outFile the output file or folder (if multi-dex is enabled), must exist
     * @param multiDex whether multi-dex is enabled
     * @param dexOptions the dex options to run pre-dex
     * @param optimize whether to run dx with optimizations turned on
     * @throws IOException
     * @throws ProcessException
     * @throws InterruptedException
     */
    public void preDexLibrary(
            @NonNull AndroidBuilder builder,
            @NonNull File inputFile,
            @NonNull File outFile,
            boolean multiDex,
            @NonNull DexOptions dexOptions,
            boolean optimize,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, ProcessException, InterruptedException {
        checkState(!multiDex || outFile.isDirectory());
        checkState(builder.getTargetInfo() != null);

        // Forcing optimize to true as it has no effect due to b.android.com/82031.
        optimize = true;

        DxDexKey itemKey = DxDexKey.of(
                inputFile,
                builder.getTargetInfo().getBuildTools().getRevision(),
                dexOptions.getJumboMode(),
                optimize,
                dexOptions.getAdditionalParameters(),
                multiDex);

        ILogger logger = builder.getLogger();
        logger.info("preDexLibrary : %s", itemKey);
        Pair<Item, Boolean> pair = getItem(logger, itemKey);
        Item item = pair.getFirst();
        logger.info("Item from cache %s", item.toString());

        // if this is an already cached item.
        if (!pair.getSecond()) {
            if (waitForPredexFile(item, outFile, multiDex)) {
                return;
            }
            // if we fall through here, that means the cached item is not there any more, force
            // the regeneration.
            logger.info("Forced regeneration : %s", itemKey);
            pair = regenerateItem(logger, itemKey);
            item = pair.getFirst();
        }

        // if this is a new item, or we need to regenerate it.
        if (pair.getSecond()) {
            try {
                // haven't process this file yet so do it and record it.
                List<File> files = builder.preDexLibraryNoCache(
                        inputFile,
                        outFile,
                        multiDex,
                        dexOptions,
                        optimize,
                        processOutputHandler);

                item.getOutputFiles().clear();
                item.getOutputFiles().addAll(files);

                incrementMisses();
            } catch (ProcessException exception) {
                // in case of error, delete (now obsolete) output file
                FileUtils.deleteIfExists(outFile);
                // and rethrow the error
                throw exception;
            } finally {
                // enable other threads to use the output of this pre-dex.
                // if something was thrown they'll handle the missing output file.
                item.getLatch().countDown();
            }
        } else {
            // at this point, another thread should have re-generated it for us.
            if (!waitForPredexFile(item, outFile, multiDex)) {
                throw new RuntimeException(String.format("Cannot obtain or regenerate item %s",
                        item.getSourceFile()));
            }
        }
    }

    private boolean waitForPredexFile(Item item, File outFile, boolean multiDex)
            throws InterruptedException, IOException {
        // wait until the file is pre-dexed by the first thread.
        item.getLatch().await();

        // check that the generated file actually exists
        if (item.areOutputFilesPresent()) {
            if (multiDex) {
                // output should be a folder
                for (File sourceFile : item.getOutputFiles()) {
                    File destFile = new File(outFile, sourceFile.getName());
                    checkSame(sourceFile, destFile);
                    Files.copy(sourceFile, destFile);
                }
            } else {
                // file already pre-dex, just copy the output.
                if (item.getOutputFiles().isEmpty()) {
                    throw new RuntimeException(item.toString());
                }
                checkSame(item.getOutputFiles().get(0), outFile);
                Files.copy(item.getOutputFiles().get(0), outFile);
            }
            incrementHits();
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    protected Node createItemNode(
            @NonNull Document document,
            @NonNull DxDexKey itemKey,
            @NonNull BaseItem item) throws IOException {
        Node itemNode = super.createItemNode(document, itemKey, item);

        if (itemNode != null) {
            itemKey.writeFieldsToXml(itemNode);
        }

        return itemNode;
    }

    private static void checkSame(@NonNull File source, @NonNull File dest) {
        if (source.equals(dest)) {
            Logger.getAnonymousLogger().info(
                String.format("%s l:%d ts:%d", source, source.length(), source.lastModified()));
        }
    }
}
