/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Merges {@link DataSet}s and writes a resulting data folder.
 *
 * This is able to save its post work state and reload this for incremental update.
 */
abstract class DataMerger<I extends DataItem<F>, F extends DataFile<I>, S extends DataSet<I,F>> implements DataMap<I> {

    static final String FN_MERGER_XML = "merger.xml";
    private static final String NODE_MERGER = "merger";
    private static final String NODE_DATA_SET = "dataSet";

    /**
     * All the DataSets.
     */
    private final List<S> mDataSets = Lists.newArrayList();

    public DataMerger() { }

    protected abstract S createFromXml(Node node);

    /**
     * adds a new {@link DataSet} and overlays it on top of the existing DataSet.
     *
     * @param resourceSet the ResourceSet to add.
     */
    public void addDataSet(S resourceSet) {
        // TODO figure out if we allow partial overlay through a per-resource flag.
        mDataSets.add(resourceSet);
    }

    /**
     * Returns the list of ResourceSet objects.
     * @return the resource sets.
     */
    @VisibleForTesting
    List<S> getDataSets() {
        return mDataSets;
    }

    @VisibleForTesting
    void validateDataSets() throws DuplicateDataException {
        for (S resourceSet : mDataSets) {
            resourceSet.checkItems();
        }
    }

    /**
     * Returns the number of items.
     * @return the number of items.
     *
     * @see DataMap
     */
    @Override
    public int size() {
        // put all the resource keys in a set.
        Set<String> keys = Sets.newHashSet();

        for (S resourceSet : mDataSets) {
            ListMultimap<String, I> map = resourceSet.getDataMap();
            keys.addAll(map.keySet());
        }

        return keys.size();
    }

    /**
     * Returns a map of the data items.
     * @return a map of items.
     *
     * @see DataMap
     */
    @NonNull
    @Override
    public ListMultimap<String, I> getDataMap() {
        // put all the sets in a multimap. The result is that for each key,
        // there is a sorted list of items from all the layers, including removed ones.
        ListMultimap<String, I> fullItemMultimap = ArrayListMultimap.create();

        for (S resourceSet : mDataSets) {
            ListMultimap<String, I> map = resourceSet.getDataMap();
            for (Map.Entry<String, Collection<I>> entry : map.asMap().entrySet()) {
                fullItemMultimap.putAll(entry.getKey(), entry.getValue());
            }
        }

        return fullItemMultimap;
    }

    /**
     * Merges the data into a given consumer.
     *
     * @param consumer the consumer of the merge.
     * @param doCleanUp clean up the state to be able to do further incremental merges. If this
     *                  is a one-shot merge, this can be false to improve performance.
     * @throws java.io.IOException
     * @throws DuplicateDataException
     * @throws MergeConsumer.ConsumerException
     */
    public void mergeData(@NonNull MergeConsumer<I> consumer, boolean doCleanUp)
            throws IOException, DuplicateDataException, MergeConsumer.ConsumerException {

        consumer.start();

        try {
            // get all the items keys.
            Set<String> dataItemKeys = Sets.newHashSet();

            for (S dataSet : mDataSets) {
                // quick check on duplicates in the resource set.
                dataSet.checkItems();
                ListMultimap<String, I> map = dataSet.getDataMap();
                dataItemKeys.addAll(map.keySet());
            }

            // loop on all the data items.
            for (String dataItemKey : dataItemKeys) {
                // for each items, look in the data sets, starting from the end of the list.

                I previouslyWritten = null;
                I toWrite = null;

                /*
                 * We are looking for what to write/delete: the last non deleted item, and the
                 * previously written one.
                 */

                setLoop: for (int i = mDataSets.size() - 1 ; i >= 0 ; i--) {
                    S dataSet = mDataSets.get(i);

                    // look for the resource key in the set
                    ListMultimap<String, I> itemMap = dataSet.getDataMap();

                    List<I> items = itemMap.get(dataItemKey);
                    if (items.isEmpty()) {
                        continue;
                    }

                    // The list can contain at max 2 items. One touched and one deleted.
                    // More than one deleted means there was more than one which isn't possible
                    // More than one touched means there is more than one and this isn't possible.
                    for (int ii = items.size() - 1 ; ii >= 0 ; ii--) {
                        I item = items.get(ii);

                        if (item.isWritten()) {
                            assert previouslyWritten == null;
                            previouslyWritten = item;
                        }

                        if (toWrite == null && !item.isRemoved()) {
                            toWrite = item;
                        }

                        if (toWrite != null && previouslyWritten != null) {
                            break setLoop;
                        }
                    }
                }

                // done searching, we should at least have something.
                assert previouslyWritten != null || toWrite != null;

                // now need to handle, the type of each (single res file, multi res file), whether
                // they are the same object or not, whether the previously written object was deleted.

                if (toWrite == null) {
                    // nothing to write? delete only then.
                    assert previouslyWritten.isRemoved();

                    consumer.removeItem(previouslyWritten, null /*replacedBy*/);

                } else if (previouslyWritten == null || previouslyWritten == toWrite) {
                    // easy one: new or updated res
                    consumer.addItem(toWrite);
                } else {
                    // replacement of a resource by another.

                    // force write the new value
                    toWrite.setTouched();
                    consumer.addItem(toWrite);
                    // and remove the old one
                    consumer.removeItem(previouslyWritten, toWrite);
                }
            }
        } finally {
            consumer.end();
        }

        if (doCleanUp) {
            // reset all states. We can't just reset the toWrite and previouslyWritten objects
            // since overlayed items might have been touched as well.
            // Should also clean (remove) objects that are removed.
            postMergeCleanUp();
        }
    }

    /**
     * Writes a single blob file to store all that the DataMerger knows about.
     *
     * @param blobRootFolder the root folder where blobs are store.
     * @throws IOException
     *
     * @see #loadFromBlob(File, boolean)
     */
    public void writeBlobTo(File blobRootFolder) throws IOException {
        // write "compact" blob
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;

        try {
            builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Node rootNode = document.createElement(NODE_MERGER);
            document.appendChild(rootNode);

            for (S dataSet : mDataSets) {
                Node dataSetNode = document.createElement(NODE_DATA_SET);
                rootNode.appendChild(dataSetNode);

                dataSet.appendToXml(dataSetNode, document);
            }

            String content = XmlPrettyPrinter.prettyPrint(document);

            createDir(blobRootFolder);
            Files.write(content, new File(blobRootFolder, FN_MERGER_XML), Charsets.UTF_8);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    /**
     * Loads the merger state from a blob file.
     *
     * This can be loaded into two different ways that differ only by the state on the
     * {@link DataItem} objects.
     *
     * If <var>incrementalState</var> is <code>true</code> then the items that are on disk are
     * marked as written ({@link DataItem#isWritten()} returning <code>true</code>.
     * This is to be used by {@link MergeWriter} to update a merged res folder.
     *
     * If <code>false</code>, the items are marked as touched, and this can be used to feed a new
     * {@link ResourceRepository} object.
     *
     * @param blobRootFolder the folder containing the blob.
     * @param incrementalState whether to load into an incremental state or a new state.
     * @return true if the blob was loaded.
     * @throws IOException
     *
     * @see #writeBlobTo(File)
     */
    public boolean loadFromBlob(File blobRootFolder, boolean incrementalState) throws IOException {
        File file = new File(blobRootFolder, FN_MERGER_XML);
        if (!file.isFile()) {
            return false;
        }

        BufferedInputStream stream = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            stream = new BufferedInputStream(new FileInputStream(file));
            InputSource is = new InputSource(stream);
            factory.setNamespaceAware(true);
            factory.setValidating(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);

            // get the root node
            Node rootNode = document.getDocumentElement();
            if (rootNode == null || !NODE_MERGER.equals(rootNode.getLocalName())) {
                return false;
            }

            NodeList nodes = rootNode.getChildNodes();

            for (int i = 0, n = nodes.getLength(); i < n; i++) {
                Node node = nodes.item(i);

                if (node.getNodeType() != Node.ELEMENT_NODE ||
                        !NODE_DATA_SET.equals(node.getLocalName())) {
                    continue;
                }

                S dataSet = createFromXml(node);
                if (dataSet != null) {
                    mDataSets.add(dataSet);
                }
            }

            if (incrementalState) {
                setPostBlobLoadStateToWritten();
            } else {
                setPostBlobLoadStateToTouched();
            }

            return true;
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
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

    /**
     * Sets the post blob load state to WRITTEN.
     *
     * After a load from the blob file, all items have their state set to nothing.
     * If the load mode is set to incrementalState then we want the items that are in the current
     * merge result to have their state be WRITTEN.
     *
     * This will allow further updates with {@link #mergeData(MergeConsumer, boolean)} to ignore the
     * state at load time and only apply the new changes.
     *
     * @see #loadFromBlob(java.io.File, boolean)
     * @see DataItem#isWritten()
     */
    private void setPostBlobLoadStateToWritten() {
        ListMultimap<String, I> itemMap = ArrayListMultimap.create();

        // put all the sets into list per keys. The order is important as the lower sets are
        // overridden by the higher sets.
        for (S dataSet : mDataSets) {
            ListMultimap<String, I> map = dataSet.getDataMap();
            for (Map.Entry<String, Collection<I>> entry : map.asMap().entrySet()) {
                itemMap.putAll(entry.getKey(), entry.getValue());
            }
        }

        // the items that represent the current state is the last item in the list for each key.
        for (String key : itemMap.keySet()) {
            List<I> itemList = itemMap.get(key);
            itemList.get(itemList.size() - 1).resetStatusToWritten();
        }
    }

    /**
     * Sets the post blob load state to TOUCHED.
     *
     * After a load from the blob file, all items have their state set to nothing.
     * If the load mode is not set to incrementalState then we want the items that are in the
     * current merge result to have their state be TOUCHED.
     *
     * This will allow the first use of {@link #mergeData(MergeConsumer, boolean)} to add these
     * to the consumer as if they were new items.
     *
     * @see #loadFromBlob(java.io.File, boolean)
     * @see DataItem#isTouched()
     */
    private void setPostBlobLoadStateToTouched() {
        ListMultimap<String, I> itemMap = ArrayListMultimap.create();

        // put all the sets into list per keys. The order is important as the lower sets are
        // overridden by the higher sets.
        for (S dataSet : mDataSets) {
            ListMultimap<String, I> map = dataSet.getDataMap();
            for (Map.Entry<String, Collection<I>> entry : map.asMap().entrySet()) {
                itemMap.putAll(entry.getKey(), entry.getValue());
            }
        }

        // the items that represent the current state is the last item in the list for each key.
        for (String key : itemMap.keySet()) {
            List<I> itemList = itemMap.get(key);
            itemList.get(itemList.size() - 1).resetStatusToTouched();
        }
    }

    /**
     * Post merge clean up.
     *
     * - Remove the removed items.
     * - Clear the state of all the items (this allow newly overridden items to lose their
     *   WRITTEN state)
     * - Set the items that are part of the new merge to be WRITTEN to allow the next merge to
     *   be incremental.
     */
    private void postMergeCleanUp() {
        ListMultimap<String, I> itemMap = ArrayListMultimap.create();

        // remove all removed items, and copy the rest in the full map while resetting their state.
        for (S dataSet : mDataSets) {
            ListMultimap<String, I> map = dataSet.getDataMap();

            List<String> keys = Lists.newArrayList(map.keySet());
            for (String key : keys) {
                List<I> list = map.get(key);
                for (int i = 0 ; i < list.size() ;) {
                    I item = list.get(i);
                    if (item.isRemoved()) {
                        list.remove(i);
                    } else {
                        //noinspection unchecked
                        itemMap.put(key, (I) item.resetStatus());
                        i++;
                    }
                }
            }
        }

        // for the last items (the one that have been written into the consumer), set their
        // state to WRITTEN
        for (String key : itemMap.keySet()) {
            List<I> itemList = itemMap.get(key);
            itemList.get(itemList.size() - 1).resetStatusToWritten();
        }
    }

    /**
     * Checks that a loaded merger can be updated with a given list of DataSet.
     *
     * For now this means the sets haven't changed.
     *
     * @param dataSets the resource sets.
     * @return true if the update can be performed. false if a full merge should be done.
     */
    public boolean checkValidUpdate(List<S> dataSets) {
        if (dataSets.size() != mDataSets.size()) {
            return false;
        }

        for (int i = 0, n = dataSets.size(); i < n; i++) {
            S localSet = mDataSets.get(i);
            S newSet = dataSets.get(i);

            List<File> localSourceFiles = localSet.getSourceFiles();
            List<File> newSourceFiles = newSet.getSourceFiles();

            // compare the config name and source files sizes.
            if (!newSet.getConfigName().equals(localSet.getConfigName()) ||
                    localSourceFiles.size() != newSourceFiles.size()) {
                return false;
            }

            // compare the source files. The order is not important so it should be normalized
            // before it's compared.
            // make copies to sort.
            localSourceFiles = Lists.newArrayList(localSourceFiles);
            Collections.sort(localSourceFiles);
            newSourceFiles = Lists.newArrayList(newSourceFiles);
            Collections.sort(newSourceFiles);

            if (!localSourceFiles.equals(newSourceFiles)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a DataSet that contains a given file.
     *
     * "contains" means that the DataSet has a source file/folder that is the root folder
     * of this file. The folder and/or file doesn't have to exist.
     *
     * @param file the file to check
     * @return a pair containing the ResourceSet and its source file that contains the file.
     */
    public Pair<S, File> getDataSetContaining(File file) {
        for (S dataSet : mDataSets) {
            File sourceFile = dataSet.findMatchingSourceFile(file);
            if (sourceFile != null) {
                return Pair.of(dataSet, sourceFile);
            }
        }

        return null;
    }

    protected synchronized void createDir(File folder) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Failed to create directory: " + folder);
        }
    }


    @Override
    public String toString() {
        return Arrays.toString(mDataSets.toArray());
    }
}
