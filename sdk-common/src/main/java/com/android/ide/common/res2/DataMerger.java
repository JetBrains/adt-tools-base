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
import com.android.ide.common.internal.WaitableExecutor;
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
import java.util.concurrent.ExecutionException;

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

    /**
     * Post write processing.
     *
     * This can be used for delayed file writing.
     *
     * @param rootFolder the root of the output.
     *
     * @throws IOException
     */
    protected abstract void postWriteDataFolder(File rootFolder) throws IOException;

    /**
     * Removes an data Item.
     *
     * This method can optionally receive the item that replaces the removed item in case
     * processing can be optimized. the replacement item has already been written.
     *
     * @param rootFolder
     * @param item
     * @param replacedBy
     */
    protected abstract void removeItem(File rootFolder, I item, I replacedBy);

    /**
     * Writes a given DataItem to a given root folder.
     *
     * @param rootFolder the root res folder
     * @param item the resource to add.
     * @param executor an executor
     * @throws java.io.IOException
     */
    protected abstract void writeItem(@NonNull final File rootFolder,
                                      @NonNull final I item,
                                      @NonNull WaitableExecutor executor) throws IOException;


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
     * Writes the result of the merge to a destination data folder.
     *
     * @param rootFolder the folder to write the resources in.
     * @throws java.io.IOException
     * @throws DuplicateDataException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void writeDataFolder(@NonNull File rootFolder)
            throws IOException, DuplicateDataException, ExecutionException, InterruptedException {

        WaitableExecutor executor = new WaitableExecutor();

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

                removeItem(rootFolder, previouslyWritten, null /*replacedBy*/);

            } else if (previouslyWritten == null || previouslyWritten == toWrite) {
                // easy one: new or updated res

                writeItem(rootFolder, toWrite, executor);
            } else {
                // replacement of a resource by another.

                // first force the writing of the new one.
                toWrite.setTouched();

                // write the new value
                writeItem(rootFolder, toWrite, executor);

                removeItem(rootFolder, previouslyWritten, toWrite);
            }
        }

        postWriteDataFolder(rootFolder);

        executor.waitForTasks();
    }

    /**
     * Writes a single blog file to store all that the DataMerger knows about.
     *
     * @param blobRootFolder the root folder where blobs are store.
     * @throws IOException
     *
     * @see #loadFromBlob(File)
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
     * @param blobRootFolder the folder containing the blob.
     * @return true if the blob was loaded.
     * @throws IOException
     *
     * @see #writeBlobTo(File)
     */
    public boolean loadFromBlob(File blobRootFolder) throws IOException {
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

            setItemsToWritten();

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
     * Sets all existing items to have their state be WRITTEN.
     *
     * This only sets the last item to be written.
     *
     * @see DataItem#isWritten()
     */
    private void setItemsToWritten() {
        ListMultimap<String, I> itemMap = ArrayListMultimap.create();

        for (S dataSet : mDataSets) {
            ListMultimap<String, I> map = dataSet.getDataMap();
            for (Map.Entry<String, Collection<I>> entry : map.asMap().entrySet()) {
                itemMap.putAll(entry.getKey(), entry.getValue());
            }
        }

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
            if (file != null) {
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
