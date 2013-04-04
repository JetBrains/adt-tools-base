/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.io.Files;

import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Implementation of {@link DataMerger} for {@link AssetSet}, {@link AssetItem}, and
 * {@link AssetFile}.
 */
public class AssetMerger extends DataMerger<AssetItem, AssetFile, AssetSet> {

    @Override
    protected void removeItem(File rootFolder, AssetItem item, AssetItem replacedBy) {
        // only remove the file if there is no replacement.
        if (replacedBy == null) {
            File removedFile = new File(rootFolder, item.getName());
            removedFile.delete();
        }
    }

    @Override
    protected void writeItem(@NonNull final File rootFolder, @NonNull final AssetItem item,
                             @NonNull WaitableExecutor executor) throws IOException {
        // Only write it if the state is TOUCHED.
        if (item.isTouched()) {
            executor.execute(new Callable() {
                @Override
                public Object call() throws Exception {
                    AssetFile assetFile = item.getSource();

                    File fromFile = assetFile.getFile();

                    // the out file is computed from the item key since that includes the
                    // relative folder.
                    File toFile = new File(rootFolder,
                            item.getKey().replace('/', File.separatorChar));

                    // make sure the folders are created
                    toFile.getParentFile().mkdirs();

                    Files.copy(fromFile, toFile);

                    return null;
                }
            });
        }
    }

    @Override
    protected AssetSet createFromXml(Node node) {
        AssetSet set = new AssetSet("");
        return (AssetSet) set.createFromXml(node);
    }

    @Override
    protected void postWriteDataFolder(File rootFolder) throws IOException {
        // nothing to do.
    }
}
