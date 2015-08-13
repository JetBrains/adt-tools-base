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
import com.android.resources.Density;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link DataMerger} for preprocessing resources.
 */
public class PreprocessResourcesMerger extends DataMerger<PreprocessDataItem, PreprocessDataFile, PreprocessDataSet> {
    private static final String NODE_DENSITIES = "densities";
    private static final String NODE_DENSITY = "density";

    /** Keep track of the densities used, save it to XML. */
    private EnumSet<Density> mDensities = EnumSet.noneOf(Density.class);

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

    @NonNull
    @Override
    protected String getAdditionalDataTagName() {
        return NODE_DENSITIES;
    }

    @Override
    protected void loadAdditionalData(@NonNull Node densitiesNode, boolean incrementalState)
            throws MergingException {
        NodeList childNodes = densitiesNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeName().equals(NODE_DENSITY)) {
                mDensities.add(Density.getEnum(child.getTextContent()));
            }
        }
    }

    @Override
    protected void writeAdditionalData(Document document, Node rootNode) {
        Element densities = document.createElement(getAdditionalDataTagName());

        for (Density density : mDensities) {
            Element densityElement = document.createElement(NODE_DENSITY);
            densityElement.setTextContent(density.getResourceValue());
            densities.appendChild(densityElement);
        }

        rootNode.appendChild(densities);
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

    public Set<Density> getDensities() {
        return EnumSet.copyOf(mDensities);
    }

    public void setDensities(Collection<Density> densities) {
        mDensities = EnumSet.copyOf(densities);
    }
}
