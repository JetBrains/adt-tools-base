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

import com.android.annotations.NonNull;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.Pair;
import com.google.common.io.Files;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;

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
 * The API is fairly simple, just call {@link #preDexLibrary(java.io.File, java.io.File, com.android.builder.core.DexOptions, com.android.sdklib.BuildToolInfo, boolean, com.android.ide.common.internal.CommandLineRunner)}
 *
 * The call will be blocking until the pre-dexing happened, either through actual pre-dexing or
 * through copying the output of a previous pre-dex run.
 *
 * After a build a call to {@link #clear(java.io.File, com.android.utils.ILogger)} with a file
 * will allow saving the known pre-dexed libraries for future reuse.
 */
public class PreDexCache extends PreProcessCache<DexKey> {

    private static final String ATTR_JUMBO_MODE = "jumboMode";


    private static final PreDexCache sSingleton = new PreDexCache();

    public static PreDexCache getCache() {
        return sSingleton;
    }

    @Override
    @NonNull
    protected KeyFactory<DexKey> getKeyFactory() {
        return new KeyFactory<DexKey>() {
            @Override
            public DexKey of(@NonNull File sourceFile, @NonNull FullRevision revision,
                    @NonNull NamedNodeMap attrMap) {

                return DexKey.of(sourceFile, revision,
                        Boolean.parseBoolean(attrMap.getNamedItem(ATTR_JUMBO_MODE).getNodeValue()));
            }
        };
    }

    /**
     * Pre-dex a given library to a given output with a specific version of the build-tools.
     * @param inputFile the jar to pre-dex
     * @param outFile the output file.
     * @param dexOptions the dex options to run pre-dex
     * @param buildToolInfo the build tools info
     * @param verbose verbose flag
     * @param commandLineRunner the command line runner.
     * @throws IOException
     * @throws LoggedErrorException
     * @throws InterruptedException
     */
    public void preDexLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull DexOptions dexOptions,
            @NonNull BuildToolInfo buildToolInfo,
            boolean verbose,
            @NonNull CommandLineRunner commandLineRunner)
            throws IOException, LoggedErrorException, InterruptedException {

        DexKey itemKey = DexKey.of(inputFile, buildToolInfo.getRevision(), dexOptions.getJumboMode());

        Pair<Item, Boolean> pair = getItem(itemKey, inputFile, outFile);

        // if this is a new item
        if (pair.getSecond()) {
            try {
                // haven't process this file yet so do it and record it.
                AndroidBuilder.preDexLibrary(inputFile, outFile, dexOptions, buildToolInfo,
                        verbose, commandLineRunner);

                incrementMisses();
            } catch (IOException exception) {
                // in case of error, delete (now obsolete) output file
                outFile.delete();
                // and rethrow the error
                throw exception;
            } catch (LoggedErrorException exception) {
                // in case of error, delete (now obsolete) output file
                outFile.delete();
                // and rethrow the error
                throw exception;
            } catch (InterruptedException exception) {
                // in case of error, delete (now obsolete) output file
                outFile.delete();
                // and rethrow the error
                throw exception;
            } finally {
                // enable other threads to use the output of this pre-dex.
                // if something was thrown they'll handle the missing output file.
                pair.getFirst().getLatch().countDown();
            }
        } else {
            // wait until the file is pre-dexed by the first thread.
            pair.getFirst().getLatch().await();

            // check that the generated file actually exists
            File fromFile = pair.getFirst().getOutputFile();

            if (fromFile.isFile()) {
                // file already pre-dex, just copy the output.
                Files.copy(pair.getFirst().getOutputFile(), outFile);
                incrementHits();
            }
        }
    }

    @Override
    protected Node createItemNode(
            @NonNull Document document,
            @NonNull DexKey itemKey,
            @NonNull BaseItem item) throws IOException {
        Node itemNode = super.createItemNode(document, itemKey, item);

        Attr attr = document.createAttribute(ATTR_JUMBO_MODE);
        attr.setValue(Boolean.toString(itemKey.isJumboMode()));
        itemNode.getAttributes().setNamedItem(attr);

        return itemNode;
    }
}
