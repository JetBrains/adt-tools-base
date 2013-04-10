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
import com.google.common.collect.Maps;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Represents a data file.
 *
 * It contains a link to its {@link java.io.File}, and the {@DataItem}s it generates.
 *
 */
abstract class DataFile<I extends DataItem> {

    static enum FileType {
        SINGLE, MULTI
    }

    private final FileType mType;
    private final File mFile;
    private final Map<String, I> mItems = Maps.newHashMap();

    /**
     * Creates a data file with a list of data items.
     *
     * The source file is set on the items with {@link DataItem#setSource(DataFile)}
     *
     * The type of the DataFile will by {@link FileType#MULTI}.
     *
     * @param file the File
     */
    DataFile(@NonNull File file, FileType fileType) {
        mType = fileType;
        mFile = file;
    }

    /**
     * This must be called from the constructor of the children classes.
     * @param item the item
     */
    protected final void init(@NonNull I item) {
        item.setSource(this);
        mItems.put(item.getKey(), item);
    }

    /**
     * This must be called from the constructor of the children classes.
     * @param items the items
     */
    protected void init(@NonNull List<I> items) {
        for (I item : items) {
            item.setSource(this);
            mItems.put(item.getKey(), item);
        }
    }

    @NonNull
    FileType getType() {
        return mType;
    }

    @NonNull
    File getFile() {
        return mFile;
    }

    I getItem() {
        assert mItems.size() == 1;
        return mItems.values().iterator().next();
    }

    @NonNull
    Collection<I> getItems() {
        return mItems.values();
    }

    @NonNull
    Map<String, I> getItemMap() {
        return mItems;
    }

    void addItems(Collection<I> items) {
        for (I item : items) {
            mItems.put(item.getKey(), item);
            item.setSource(this);
        }
    }

    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        // nothing
    }

    @Override
    public String toString() {
        return "DataFile{" +
                "mFile=" + mFile +
                '}';
    }
}
