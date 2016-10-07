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
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

/**
 * A {@link MergeWriter} for assets, using {@link AssetItem}.
 */
public class MergedAssetWriter extends MergeWriter<AssetItem> {

    public MergedAssetWriter(@NonNull File rootFolder) {
        super(rootFolder);
    }

    @Override
    public void addItem(@NonNull final AssetItem item) throws ConsumerException {
        // Only write it if the state is TOUCHED.
        if (item.isTouched()) {
            getExecutor().execute(() -> {
                AssetFile assetFile = Preconditions.checkNotNull(item.getSource());

                Path fromFile = assetFile.getFile().toPath();

                // the out file is computed from the item key since that includes the
                // relative folder.
                Path toFile = new File(getRootFolder(),
                        item.getKey().replace('/', File.separatorChar)).toPath();

                Files.createDirectories(toFile.getParent());

                if (item.shouldBeUnGzipped()) {
                    // When AAPT processed resources, it would uncompress gzipped files, as they will be
                    // compressed in the APK anyway. They are renamed in AssetItem#create(File, File)
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(
                            new BufferedInputStream(Files.newInputStream(fromFile)))) {
                        Files.copy(gzipInputStream, toFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.copy(fromFile, toFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            });
        }
    }

    @Override
    public void removeItem(@NonNull AssetItem removedItem, @Nullable AssetItem replacedBy)
            throws ConsumerException {
        if (replacedBy == null) {
            File removedFile = new File(getRootFolder(), removedItem.getName());
            removedFile.delete();
        }
    }

    @Override
    public boolean ignoreItemInMerge(AssetItem item) {
        // never ignore any item
        return false;
    }
}
