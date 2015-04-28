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

package com.android.ide.common.res2;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Splitter;
import com.google.common.collect.SetMultimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.List;

/**
 * {@link DataSet} used to combine the "merged" resources directory with resources generated for
 * preprocessing.
 */
public class PreprocessDataSet extends DataSet<PreprocessDataItem, PreprocessDataFile> {
    public enum ResourcesDirectory {
        MERGED,
        GENERATED
    }

    private ResourcesDirectory mResourcesDirectory;
    private SetMultimap<File, File> mGeneratedFiles;
    private File generatedResDirectory;
    private File mergedResDirectory;

    /**
     * Creates a DataSet with a given configName. The name is used to identify the set across sessions.
     *
     * @param configName the name of the config this set is associated with.
     */
    public PreprocessDataSet(String configName, ResourcesDirectory resourcesDirectory) {
        super(configName + ":" + resourcesDirectory.name());
        mResourcesDirectory = resourcesDirectory;
    }

    public void setGeneratedFiles(
            SetMultimap<File, File> generatedFiles) {
        mGeneratedFiles = generatedFiles;
    }

    @Override
    protected DataSet<PreprocessDataItem, PreprocessDataFile> createSet(String name) {
        List<String> parts = Splitter.on(":").splitToList(name);
        checkArgument(parts.size() == 2, "Invalid data set name.");
        return new PreprocessDataSet(
                parts.get(0),
                Enum.valueOf(ResourcesDirectory.class, parts.get(1)));
    }

    @Override
    protected PreprocessDataFile createFileAndItems(@NonNull File file, @NonNull Node fileNode) {
        Node name = fileNode.getAttributes().getNamedItem(DataSet.ATTR_NAME);
        if (name != null) {
            PreprocessDataFile dataFile = new PreprocessDataFile(file, DataFile.FileType.SINGLE);
            PreprocessDataItem dataItem = new PreprocessDataItem(((Attr) name).getValue(), file);
            dataFile.addItem(dataItem);
            return dataFile;
        } else {
            PreprocessDataFile dataFile = new PreprocessDataFile(file, DataFile.FileType.SINGLE);
            NodeList childNodes = fileNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node itemNode = childNodes.item(i);
                name = itemNode.getAttributes().getNamedItem(DataSet.ATTR_NAME);
                Attr path = (Attr) itemNode.getAttributes()
                        .getNamedItem(PreprocessDataItem.ATTR_FILE_TO_USE);
                File fileToUse = new File(path.getValue());
                PreprocessDataItem dataItem =
                        new PreprocessDataItem(((Attr) name).getValue(), fileToUse);
                dataFile.addItem(dataItem);
            }
            return dataFile;
        }
    }

    @Nullable
    @Override
    protected PreprocessDataFile createFileAndItems(File sourceFolder, File file, ILogger logger)
            throws MergingException {
        if (mResourcesDirectory == ResourcesDirectory.MERGED) {
            String name = FileUtils.relativePath(file, getMergedResDirectory());
            PreprocessDataFile dataFile = new PreprocessDataFile(file, DataFile.FileType.SINGLE);
            PreprocessDataItem dataItem = new PreprocessDataItem(name, file);
            dataFile.addItem(dataItem);
            return dataFile;
        } else {
            PreprocessDataFile dataFile = new PreprocessDataFile(file, DataFile.FileType.MULTI);
            for (File generatedFile : mGeneratedFiles.get(file)) {
                String name = FileUtils.relativePath(generatedFile, getGeneratedResDirectory());
                dataFile.addItem(new PreprocessDataItem(name, generatedFile));
            }
            return dataFile;
        }
    }

    @Override
    protected void readSourceFolder(File sourceFolder, ILogger logger) throws MergingException {
        throw new UnsupportedOperationException(
                "Use per-file methods on PreprocessDataSet instead.");
    }

    public File getGeneratedResDirectory() {
        return generatedResDirectory;
    }

    public void setGeneratedResDirectory(File generatedResDirectory) {
        this.generatedResDirectory = generatedResDirectory;
    }

    public File getMergedResDirectory() {
        return mergedResDirectory;
    }

    public void setMergedResDirectory(File mergedResDirectory) {
        this.mergedResDirectory = mergedResDirectory;
    }

    public ResourcesDirectory getResourcesDirectory() {
        return mResourcesDirectory;
    }
}
