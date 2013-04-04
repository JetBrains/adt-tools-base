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

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.internal.AaptRunner;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Implementation of {@link DataMerger} for {@link ResourceSet}, {@link ResourceItem}, and
 * {@link ResourceFile}.
 */
public class ResourceMerger extends DataMerger<ResourceItem, ResourceFile, ResourceSet> {

    private static final String FN_VALUES_XML = "values.xml";

    private AaptRunner mAaptRunner;

    /**
     * map of XML values files to write after parsing all the files. the key is the qualifier.
     */
    private ListMultimap<String, ResourceItem> mValuesResMap;
    /**
     * Set of qualifier that had a previously written resource now gone.
     * This is to keep a list of values files that must be written out even with no
     * touched or updated resources, in case one or more resources were removed.
     */
    private Set<String> mQualifierWithDeletedValues;

    /**
     * Writes the result of the merge to a destination resource folder.
     *
     * The output is an Android style resource folder than can be fed to aapt.
     *
     * @param rootFolder the folder to write the resources in.
     * @param aaptRunner an aapt runner.
     * @throws java.io.IOException
     * @throws DuplicateDataException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    public void writeDataFolder(@NonNull File rootFolder, @Nullable AaptRunner aaptRunner)
            throws IOException, DuplicateDataException, ExecutionException, InterruptedException {

        // init some field used during the write.
        mAaptRunner = aaptRunner;
        mValuesResMap = ArrayListMultimap.create();
        mQualifierWithDeletedValues = Sets.newHashSet();

        try {
            super.writeDataFolder(rootFolder);
        } finally {
            mAaptRunner = null;
            mValuesResMap = null;
            mQualifierWithDeletedValues = null;
        }
    }

    /**
     * Writes a given ResourceItem to a given root res folder.
     *
     * If the ResourceItem is to be written in a "Values" folder, then it is added to a map instead.
     *
     * @param rootFolder the root res folder
     * @param item the resource to add.
     * @param executor an executor
     * @throws java.io.IOException
     */
    @Override
    protected void writeItem(@NonNull final File rootFolder,
                             @NonNull final ResourceItem item,
                             @NonNull WaitableExecutor executor) throws IOException {
        ResourceFile.FileType type = item.getSource().getType();

        if (type == ResourceFile.FileType.MULTI) {
            // this is a resource for the values files

            // just add the node to write to the map based on the qualifier.
            // We'll figure out later if the files needs to be written or (not)

            String qualifier = item.getSource().getQualifiers();
            if (qualifier == null) {
                qualifier = "";
            }

            mValuesResMap.put(qualifier, item);
        } else {
            // This is a single value file.
            // Only write it if the state is TOUCHED.
            if (item.isTouched()) {
                executor.execute(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        ResourceFile resourceFile = item.getSource();
                        File file = resourceFile.getFile();

                        String filename = file.getName();
                        String folderName = item.getType().getName();
                        String qualifiers = resourceFile.getQualifiers();
                        if (qualifiers != null && qualifiers.length() > 0) {
                            folderName = folderName + RES_QUALIFIER_SEP + qualifiers;
                        }

                        File typeFolder = new File(rootFolder, folderName);
                        createDir(typeFolder);

                        File outFile = new File(typeFolder, filename);

                        if (mAaptRunner != null && filename.endsWith(DOT_9PNG)) {
                            // run aapt in single crunch mode on the original file to write the
                            // destination file.
                            mAaptRunner.crunchPng(file, outFile);
                        } else {
                            Files.copy(file, outFile);
                        }
                        return null;
                    }
                });
            }
        }
    }

    @Override
    protected void removeItem(File rootFolder, ResourceItem removedItem, ResourceItem replacedBy) {
        ResourceFile.FileType removedType = removedItem.getSource().getType();
        ResourceFile.FileType replacedType = replacedBy != null ?
                replacedBy.getSource().getType() : null;

        if (removedType == replacedType) {
            // if the type is multi, then we make sure to flag the qualifier as deleted.
            if (removedType == ResourceFile.FileType.MULTI) {
                mQualifierWithDeletedValues.add(
                        removedItem.getSource().getQualifiers());
            } else {
                // both are single type resources, so we actually don't delete the previous
                // file as the new one will replace it instead.
            }
        } else if (removedType == ResourceFile.FileType.SINGLE) {
            // removed type is single.
            // The case of both single type is above, so here either, there is no replacement
            // or the replacement is multi. We always need to remove the old file.
            // if replacedType is non-null, then it was values, if not,
            removeOutFile(rootFolder, removedItem.getSource());
        } else {
            // removed type is multi.
            // whether the new type is single or doesn't exist, we always need to mark the qualifier
            // for rewrite.
            mQualifierWithDeletedValues.add(removedItem.getSource().getQualifiers());
        }
    }

    /**
     * Removes a file that already exists in the out res folder. This has to be a non value file.
     *
     * @param outFolder the out res folder
     * @param resourceFile the source file that created the file to remove.
     * @return true if success.
     */
    private static boolean removeOutFile(File outFolder, ResourceFile resourceFile) {
        if (resourceFile.getType() == ResourceFile.FileType.MULTI) {
            throw new IllegalArgumentException("SourceFile cannot be a FileType.MULTI");
        }

        File file = resourceFile.getFile();
        String fileName = file.getName();
        String folderName = file.getParentFile().getName();

        return removeOutFile(outFolder, folderName, fileName);
    }

    /**
     * Removes a file from a folder based on a sub folder name and a filename
     *
     * @param outFolder the root folder to remove the file from
     * @param folderName the sub folder name
     * @param fileName the file name.
     * @return true if success.
     */
    private static boolean removeOutFile(File outFolder, String folderName, String fileName) {
        File valuesFolder = new File(outFolder, folderName);
        File outFile = new File(valuesFolder, fileName);
        return outFile.delete();
    }


    @Override
    protected void postWriteDataFolder(File rootFolder) throws IOException {

        // now write the values files.
        for (String key : mValuesResMap.keySet()) {
            // the key is the qualifier.

            // check if we have to write the file due to deleted values.
            // also remove it from that list anyway (to detect empty qualifiers later).
            boolean mustWriteFile = mQualifierWithDeletedValues.remove(key);

            // get the list of items to write
            Collection<ResourceItem> items = mValuesResMap.get(key);

            // now check if we really have to write it
            if (!mustWriteFile) {
                for (ResourceItem item : items) {
                    if (item.isTouched()) {
                        mustWriteFile = true;
                        break;
                    }
                }
            }

            if (mustWriteFile) {
                String folderName = key.length() > 0 ?
                        ResourceFolderType.VALUES.getName() + RES_QUALIFIER_SEP + key :
                        ResourceFolderType.VALUES.getName();

                File valuesFolder = new File(rootFolder, folderName);
                createDir(valuesFolder);
                File outFile = new File(valuesFolder, FN_VALUES_XML);

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setValidating(false);
                factory.setIgnoringComments(true);
                DocumentBuilder builder;

                try {
                    builder = factory.newDocumentBuilder();
                    Document document = builder.newDocument();

                    Node rootNode = document.createElement(TAG_RESOURCES);
                    document.appendChild(rootNode);

                    for (ResourceItem item : items) {
                        Node adoptedNode = NodeUtils.adoptNode(document, item.getValue());
                        rootNode.appendChild(adoptedNode);
                    }

                    String content = XmlPrettyPrinter.prettyPrint(document);

                    Files.write(content, outFile, Charsets.UTF_8);
                } catch (ParserConfigurationException e) {
                    throw new IOException(e);
                }
            }
        }

        // now remove empty values files.
        for (String key : mQualifierWithDeletedValues) {
            String folderName = key != null && key.length() > 0 ?
                    ResourceFolderType.VALUES.getName() + RES_QUALIFIER_SEP + key :
                    ResourceFolderType.VALUES.getName();

            removeOutFile(rootFolder, folderName, FN_VALUES_XML);
        }
    }

    @Override
    protected ResourceSet createFromXml(Node node) {
        ResourceSet set = new ResourceSet("");
        return (ResourceSet) set.createFromXml(node);
    }


    /**
     * Call {@link #writeDataFolder(java.io.File, com.android.builder.AaptRunner)} instead.
     *
     * @param rootFolder the folder to write the resources in.
     * @throws java.io.IOException
     * @throws DuplicateDataException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    @Override
    public void writeDataFolder(@NonNull File rootFolder)
            throws IOException, DuplicateDataException, ExecutionException, InterruptedException {

        throw new UnsupportedOperationException(
                "Call writeDataFolder(File, AaptRunner) instead");
    }
}
