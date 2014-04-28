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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.AndroidBuilder;
import com.android.builder.DexOptions;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
 * The API is fairly simple, just call {@link #preDexLibrary(java.io.File, java.io.File, com.android.builder.DexOptions, com.android.sdklib.BuildToolInfo, boolean, com.android.ide.common.internal.CommandLineRunner)}
 *
 * The call will be blocking until the pre-dexing happened, either through actual pre-dexing or
 * through copying the output of a previous pre-dex run.
 *
 * After a build a call to {@link #clear(java.io.File, com.android.utils.ILogger)} with a file
 * will allow saving the known pre-dexed libraries for future reuse.
 */
public class PreDexCache {

    private static final String NODE_ITEMS = "pre-dex-items";
    private static final String NODE_ITEM = "item";
    private static final String ATTR_JUMBO_MODE = "jumboMode";
    private static final String ATTR_REVISION = "revision";
    private static final String ATTR_JAR = "jar";
    private static final String ATTR_DEX = "dex";
    private static final String ATTR_SHA1 = "sha1";

    /**
     * Items representing jar/dex files that have been processed during a build.
     */
    @Immutable
    private static class Item {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final File mOutputFile;
        @NonNull
        private final CountDownLatch mLatch;

        Item(
                @NonNull File sourceFile,
                @NonNull File outputFile,
                @NonNull CountDownLatch latch) {
            mSourceFile = sourceFile;
            mOutputFile = outputFile;
            mLatch = latch;
        }

        @NonNull
        private File getSourceFile() {
            return mSourceFile;
        }

        @NonNull
        private File getOutputFile() {
            return mOutputFile;
        }

        @NonNull
        private CountDownLatch getLatch() {
            return mLatch;
        }
    }

    /**
     * Items representing jar/dex files that have been processed in a previous build, then were
     * stored in a cache file and then reloaded during the current build.
     */
    @Immutable
    private static class StoredItem {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final File mOutputFile;
        @NonNull
        private final HashCode mSourceHash;

        StoredItem(
                @NonNull File sourceFile,
                @NonNull File outputFile,
                @NonNull HashCode sourceHash) {
            mSourceFile = sourceFile;
            mOutputFile = outputFile;
            mSourceHash = sourceHash;
        }

        @NonNull
        private File getSourceFile() {
            return mSourceFile;
        }

        @NonNull
        private File getOutputFile() {
            return mOutputFile;
        }

        @NonNull
        private HashCode getSourceHash() {
            return mSourceHash;
        }
    }

    /**
     * Key to store Item/StoredItem in maps.
     * The key contains the element that are used for the dex call:
     * - source file
     * - build tools revision
     * - jumbo mode
     */
    @Immutable
    private static class Key {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final FullRevision mBuildToolsRevision;
        private final boolean mJumboMode;

        private static Key of(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision,
                boolean jumboMode) {
            return new Key(sourceFile, buildToolsRevision, jumboMode);
        }

        private Key(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision,
                boolean jumboMode) {
            mSourceFile = sourceFile;
            mBuildToolsRevision = buildToolsRevision;
            mJumboMode = jumboMode;
        }

        @NonNull
        private FullRevision getBuildToolsRevision() {
            return mBuildToolsRevision;
        }

        public boolean isJumboMode() {
            return mJumboMode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (mJumboMode != key.mJumboMode) {
                return false;
            }
            if (!mBuildToolsRevision.equals(key.mBuildToolsRevision)) {
                return false;
            }
            if (!mSourceFile.equals(key.mSourceFile)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mSourceFile, mBuildToolsRevision, mJumboMode);
        }
    }

    private static final PreDexCache sSingleton = new PreDexCache();

    public static PreDexCache getCache() {
        return sSingleton;
    }

    @GuardedBy("this")
    private boolean mLoaded = false;

    @GuardedBy("this")
    private final Map<Key, Item> mMap = Maps.newHashMap();
    @GuardedBy("this")
    private final Map<Key, StoredItem> mStoredItems = Maps.newHashMap();

    @GuardedBy("this")
    private int mMisses = 0;
    @GuardedBy("this")
    private int mHits = 0;

    /**
     * Loads the stored item. This can be called several times (per subproject), so only
     * the first call should do something.
     */
    public synchronized void load(@NonNull File itemStorage) {
        if (mLoaded) {
            return;
        }

        loadItems(itemStorage);

        mLoaded = true;
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
        Pair<Item, Boolean> pair = getItem(inputFile, outFile, buildToolInfo, dexOptions);

        // if this is a new item
        if (pair.getSecond()) {
            try {
                // haven't process this file yet so do it and record it.
                AndroidBuilder.preDexLibrary(inputFile, outFile, dexOptions, buildToolInfo,
                        verbose, commandLineRunner);

                synchronized (this) {
                    mMisses++;
                }
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
                synchronized (this) {
                    mHits++;
                }
            }
        }
    }

    @VisibleForTesting
    /*package*/ synchronized int getMisses() {
        return mMisses;
    }

    @VisibleForTesting
    /*package*/ synchronized int getHits() {
        return mHits;
    }

    /**
     * Returns a Pair of {@link Item}, and a boolean which indicates whether the item is new (true)
     * or if it already existed (false).
     *
     * @param inputFile the input file
     * @param outFile the output file
     * @param buildToolInfo the build tools info.
     * @return a pair of item, boolean
     * @throws IOException
     */
    private synchronized Pair<Item, Boolean> getItem(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull DexOptions dexOptions) throws IOException {

        Key itemKey = Key.of(inputFile, buildToolInfo.getRevision(), dexOptions.getJumboMode());

        // get the item
        Item item = mMap.get(itemKey);

        boolean newItem = false;

        if (item == null) {
            // check if we have a stored version.
            StoredItem storedItem = mStoredItems.get(itemKey);

            if (storedItem != null) {
                // check the sha1 is still valid, and the pre-dex file is still there.
                File dexFile = storedItem.getOutputFile();
                if (dexFile.isFile() &&
                        storedItem.getSourceHash().equals(Files.hash(inputFile, Hashing.sha1()))) {

                    // create an item where the outFile is the one stored since it
                    // represent the pre-dexed library already.
                    // Next time this lib needs to be pre-dexed, we'll use the item
                    // rather than the stored item, allowing us to not compute the sha1 again.
                    // Use a 0-count latch since there is nothing to do.
                    item = new Item(inputFile, dexFile, new CountDownLatch(0));
                }
            }

            // if we didn't find a valid stored item, create a new one.
            if (item == null) {
                item = new Item(inputFile, outFile, new CountDownLatch(1));
                newItem = true;
            }

            mMap.put(itemKey, item);
        }

        return Pair.of(item, newItem);
    }

    public synchronized void clear(@Nullable File itemStorage, @Nullable ILogger logger) throws IOException {
        if (!mMap.isEmpty()) {
            if (itemStorage != null) {
                saveItems(itemStorage);
            }

            if (logger != null) {
                logger.info("PREDEX CACHE HITS:   " + mHits);
                logger.info("PREDEX CACHE MISSES: " + mMisses);
            }
        }

        mMap.clear();
        mStoredItems.clear();
        mHits = 0;
        mMisses = 0;
    }

    private synchronized void loadItems(@NonNull File itemStorage) {
        if (!itemStorage.isFile()) {
            return;
        }

        BufferedInputStream stream = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            stream = new BufferedInputStream(new FileInputStream(itemStorage));
            InputSource is = new InputSource(stream);
            factory.setNamespaceAware(true);
            factory.setValidating(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);

            // get the root node
            Node rootNode = document.getDocumentElement();
            if (rootNode == null || !NODE_ITEMS.equals(rootNode.getLocalName())) {
                return;
            }

            NodeList nodes = rootNode.getChildNodes();

            for (int i = 0, n = nodes.getLength(); i < n; i++) {
                Node node = nodes.item(i);

                if (node.getNodeType() != Node.ELEMENT_NODE ||
                        !NODE_ITEM.equals(node.getLocalName())) {
                    continue;
                }

                NamedNodeMap attrMap = node.getAttributes();

                File sourceFile = new File(attrMap.getNamedItem(ATTR_JAR).getNodeValue());
                FullRevision revision = FullRevision.parseRevision(attrMap.getNamedItem(
                        ATTR_REVISION).getNodeValue());

                StoredItem item = new StoredItem(
                        sourceFile,
                        new File(attrMap.getNamedItem(ATTR_DEX).getNodeValue()),
                        HashCode.fromString(attrMap.getNamedItem(ATTR_SHA1).getNodeValue()));

                Key key = Key.of(sourceFile, revision,
                        Boolean.parseBoolean(attrMap.getNamedItem(ATTR_JUMBO_MODE).getNodeValue()));

                mStoredItems.put(key, item);
            }
        } catch (Exception ignored) {
            // if we fail to read parts or any of the file, all it'll do is fail to reuse an
            // already pre-dexed library, so that's not a super big deal.
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private synchronized void saveItems(@NonNull File itemStorage) throws IOException {
        // write "compact" blob
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;

        try {
            builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Node rootNode = document.createElement(NODE_ITEMS);
            document.appendChild(rootNode);

            Set<Key> keys = Sets.newHashSetWithExpectedSize(mMap.size() + mStoredItems.size());
            keys.addAll(mMap.keySet());
            keys.addAll(mStoredItems.keySet());

            for (Key key : keys) {
                Item item = mMap.get(key);

                if (item != null) {

                    Node itemNode = createItemNode(document,
                            item.getSourceFile(),
                            item.getOutputFile(),
                            key.getBuildToolsRevision(),
                            key.isJumboMode(),
                            Files.hash(item.getSourceFile(), Hashing.sha1()));
                    rootNode.appendChild(itemNode);

                } else {
                    StoredItem storedItem = mStoredItems.get(key);
                    // check that the source file still exists in order to avoid
                    // storing libraries that are gone.
                    if (storedItem != null &&
                            storedItem.getSourceFile().isFile() &&
                            storedItem.getOutputFile().isFile()) {
                        Node itemNode = createItemNode(document,
                                storedItem.getSourceFile(),
                                storedItem.getOutputFile(),
                                key.getBuildToolsRevision(),
                                key.isJumboMode(),
                                storedItem.getSourceHash());
                        rootNode.appendChild(itemNode);
                    }
                }
            }

            String content = XmlPrettyPrinter.prettyPrint(document, true);

            itemStorage.getParentFile().mkdirs();
            Files.write(content, itemStorage, Charsets.UTF_8);
        } catch (ParserConfigurationException e) {
        }
    }

    private static Node createItemNode(
            @NonNull Document document,
            @NonNull File sourceFile,
            @NonNull File outputFile,
            @NonNull FullRevision toolsRevision,
                     boolean jumboMode,
            @NonNull HashCode hashCode) {
        Node itemNode = document.createElement(NODE_ITEM);

        Attr attr = document.createAttribute(ATTR_JAR);
        attr.setValue(sourceFile.getPath());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_DEX);
        attr.setValue(outputFile.getPath());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_REVISION);
        attr.setValue(toolsRevision.toString());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_JUMBO_MODE);
        attr.setValue(Boolean.toString(jumboMode));
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_SHA1);
        attr.setValue(hashCode.toString());
        itemNode.getAttributes().setNamedItem(attr);

        return itemNode;
    }
}
