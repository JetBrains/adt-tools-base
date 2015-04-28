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
import com.android.annotations.Nullable;
import com.google.common.io.Files;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * {@link MergeWriter} for preprocessing resources.
 */
public class PreprocessResourcesWriter extends MergeWriter<PreprocessDataItem> {

    public PreprocessResourcesWriter(@NonNull File rootFolder) {
        super(rootFolder);
    }

    @Override
    public void addItem(@NonNull final PreprocessDataItem item) throws ConsumerException {
        if (!item.isTouched()) {
            return;
        }

        getExecutor().execute(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                File destination = new File(getRootFolder(), item.getName());
                Files.createParentDirs(destination);
                Files.copy(item.getFileToUse(), destination);
                return null;
            }
        });
    }

    @Override
    public void removeItem(
            @NonNull PreprocessDataItem removedItem,
            @Nullable PreprocessDataItem replacedBy) throws ConsumerException {
        // If replacedBy is not null, addItem has already been called and the new file has
        // overwritten the old one.
        if (replacedBy == null) {
            File removedFile = new File(getRootFolder(), removedItem.getName());
            //noinspection ResultOfMethodCallIgnored
            removedFile.delete();
        }
    }

    @Override
    public boolean ignoreItemInMerge(PreprocessDataItem item) {
        return false;
    }
}
