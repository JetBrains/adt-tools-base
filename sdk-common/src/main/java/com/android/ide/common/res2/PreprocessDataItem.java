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

import com.android.annotations.NonNull;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;

/**
 * {@link DataItem} for preprocessing resources.
 */
public class PreprocessDataItem extends DataItem<PreprocessDataFile> {

    static final String ATTR_FILE_TO_USE = "file-to-use";

    @NonNull
    private final File mFileToUse;

    PreprocessDataItem(@NonNull String name, @NonNull File fileToUse) {
        super(name);
        mFileToUse = fileToUse;
    }

    @NonNull
    public File getFileToUse() {
        return mFileToUse;
    }

    @Override
    Node getAdoptedNode(Document document) {
        Node itemNode = document.createElement("item");
        NodeUtils.addAttribute(document, itemNode, null, DataSet.ATTR_NAME, getName());
        NodeUtils.addAttribute(document, itemNode, null, ATTR_FILE_TO_USE, mFileToUse.getAbsolutePath());
        return itemNode;
    }
}
