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

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.PreprocessDataSet.ResourcesDirectory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.List;

/**
 * {@link DataMerger} for preprocessing resources.
 */
public class PreprocessResourcesMerger extends DataMerger<PreprocessDataItem, PreprocessDataFile, PreprocessDataSet> {
    private PreprocessDataSet mGeneratedDataSet;
    private PreprocessDataSet mMergedDataSet;

    @Override
    protected PreprocessDataSet createFromXml(Node node) throws MergingException {
        // An instance of the right type, to dispatch to the correct factory method.
        PreprocessDataSet typedHelper = new PreprocessDataSet("", ResourcesDirectory.GENERATED);
        return (PreprocessDataSet) typedHelper.createFromXml(node);
    }

    @Override
    protected boolean requiresMerge(@NonNull String dataItemKey) {
        return false;
    }

    @Override
    protected void mergeItems(@NonNull String dataItemKey, @NonNull List<PreprocessDataItem> items,
            @NonNull MergeConsumer<PreprocessDataItem> consumer) throws MergingException {
        throw new IllegalStateException("PreprocessMerger doesn't merge file contents.");
    }

    @Override
    protected void writeMergedItems(Document document, Node rootNode) {

    }

    @Override
    public void addDataSet(PreprocessDataSet resourceSet) {
        switch (resourceSet.getResourcesDirectory()) {
            case GENERATED:
                checkState(mGeneratedDataSet == null);
                mGeneratedDataSet = resourceSet;
                break;
            case MERGED:
                checkState(mMergedDataSet == null);
                mMergedDataSet = resourceSet;
                break;
            default:
                throw new RuntimeException("Unknown data set type.");
        }

        super.addDataSet(resourceSet);
    }

    public PreprocessDataSet getGeneratedDataSet() {
        return mGeneratedDataSet;
    }

    public PreprocessDataSet getMergedDataSet() {
        return mMergedDataSet;
    }
}
