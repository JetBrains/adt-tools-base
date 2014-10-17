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
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 */
public abstract class PreProcessCache<T extends PreProcessCache.Key> {

    private static final String NODE_ITEMS = "items";
    private static final String ATTR_JAR = "jar";
    private static final String NODE_ITEM = "item";
    private static final String ATTR_DEX = "dex";
    private static final String ATTR_SHA1 = "sha1";
    private static final String ATTR_REVISION = "revision";

    protected interface BaseItem {
        @NonNull
        File getSourceFile();

        @NonNull
        File getOutputFile();

        @Nullable
        HashCode getSourceHash();
    }

    /**
     * Items representing jar/dex files that have been processed during a build.
     */
    @Immutable
    protected static class Item implements BaseItem {
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

        @Override
        @NonNull
        public File getSourceFile() {
            return mSourceFile;
        }

        @Override
        @NonNull
        public File getOutputFile() {
            return mOutputFile;
        }

        @Nullable
        @Override
        public HashCode getSourceHash() {
            return null;
        }

        @NonNull
        protected CountDownLatch getLatch() {
            return mLatch;
        }
    }

    /**
     * Items representing jar/dex files that have been processed in a previous build, then were
     * stored in a cache file and then reloaded during the current build.
     */
    @Immutable
    protected static class StoredItem implements BaseItem {
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

        @Override
        @NonNull
        public File getSourceFile() {
            return mSourceFile;
        }

        @Override
        @NonNull
        public File getOutputFile() {
            return mOutputFile;
        }

        @Override
        @NonNull
        public HashCode getSourceHash() {
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
    protected static class Key {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final FullRevision mBuildToolsRevision;

        public static Key of(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision) {
            return new Key(sourceFile, buildToolsRevision);
        }

        protected Key(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision) {
            mSourceFile = sourceFile;
            mBuildToolsRevision = buildToolsRevision;
        }

        @NonNull
        private FullRevision getBuildToolsRevision() {
            return mBuildToolsRevision;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }

            Key key = (Key) o;

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
            return Objects.hashCode(mSourceFile, mBuildToolsRevision);
        }
    }

    protected interface KeyFactory<T> {
        T of(@NonNull File sourceFile, @NonNull FullRevision revision, @NonNull NamedNodeMap attrMap);
    }

    @GuardedBy("this")
    private boolean mLoaded = false;

    @GuardedBy("this")
    private final Map<T, Item> mMap = Maps.newHashMap();
    @GuardedBy("this")
    private final Map<T, StoredItem> mStoredItems = Maps.newHashMap();

    @GuardedBy("this")
    private int mMisses = 0;
    @GuardedBy("this")
    private int mHits = 0;

    @NonNull
    protected abstract KeyFactory<T> getKeyFactory();

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
     * Returns a Pair of {@link Item}, and a boolean which indicates whether the item is new (true)
     * or if it already existed (false).
     *
     * @param inputFile the input file
     * @param outFile the output file
     * @return a pair of item, boolean
     * @throws IOException
     */
    protected synchronized Pair<Item, Boolean> getItem(
            @NonNull T itemKey,
            @NonNull File inputFile,
            @NonNull File outFile) throws IOException {

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

    public synchronized void clear(@Nullable File itemStorage, @Nullable ILogger logger) throws
            IOException {
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

        try {
            Document document = XmlUtils.parseUtfXmlFile(itemStorage, true);

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

                T key = getKeyFactory().of(sourceFile, revision, attrMap);

                mStoredItems.put(key, item);
            }
        } catch (Exception ignored) {
            // if we fail to read parts or any of the file, all it'll do is fail to reuse an
            // already pre-dexed library, so that's not a super big deal.
        }
    }

    protected synchronized void saveItems(@NonNull File itemStorage) throws IOException {
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

            Set<T> keys = Sets.newHashSetWithExpectedSize(mMap.size() + mStoredItems.size());
            keys.addAll(mMap.keySet());
            keys.addAll(mStoredItems.keySet());

            for (T key : keys) {
                Item item = mMap.get(key);

                if (item != null) {

                    Node itemNode = createItemNode(document,
                            key,
                            item);
                    rootNode.appendChild(itemNode);

                } else {
                    StoredItem storedItem = mStoredItems.get(key);
                    // check that the source file still exists in order to avoid
                    // storing libraries that are gone.
                    if (storedItem != null &&
                            storedItem.getSourceFile().isFile() &&
                            storedItem.getOutputFile().isFile()) {
                        Node itemNode = createItemNode(document,
                                key,
                                storedItem);
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

    protected Node createItemNode(
            @NonNull Document document,
            @NonNull T itemKey,
            @NonNull BaseItem item) throws IOException {
        Node itemNode = document.createElement(NODE_ITEM);

        Attr attr = document.createAttribute(ATTR_JAR);
        attr.setValue(item.getSourceFile().getPath());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_DEX);
        attr.setValue(item.getOutputFile().getPath());
        itemNode.getAttributes().setNamedItem(attr);

        attr = document.createAttribute(ATTR_REVISION);
        attr.setValue(itemKey.getBuildToolsRevision().toString());
        itemNode.getAttributes().setNamedItem(attr);

        HashCode hashCode = item.getSourceHash();
        if (hashCode == null) {
            hashCode = Files.hash(item.getSourceFile(), Hashing.sha1());
        }
        attr = document.createAttribute(ATTR_SHA1);
        attr.setValue(hashCode.toString());
        itemNode.getAttributes().setNamedItem(attr);

        return itemNode;
    }

    protected synchronized void incrementMisses() {
        mMisses++;
    }

    protected synchronized void incrementHits() {
        mHits++;
    }

    @VisibleForTesting
    /*package*/ synchronized int getMisses() {
        return mMisses;
    }

    @VisibleForTesting
    /*package*/ synchronized int getHits() {
        return mHits;
    }

}
