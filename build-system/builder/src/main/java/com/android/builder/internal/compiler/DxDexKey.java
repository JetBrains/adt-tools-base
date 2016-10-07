/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.util.List;

/**
 * Key to store the dexed items. This is dx tool specific key, and in addition to
 * the properties found it {@link DexKey} it adds the list of additional parameters
 * that were used for the dx tool, and flag if the entry was created with with multidex
 * option enabled.
 */
final class DxDexKey extends DexKey {

    private static final char ADDITIONAL_PARAMETERS_SEPARATOR = ',';
    private static final String ATTR_ADDITIONAL_PARAMETERS = "custom-flags";

    private static final String ATTR_IS_MULTIDEX = "is-multidex";

    @NonNull
    private final ImmutableSortedSet<String> mAdditionalParameters;

    @Nullable
    private Boolean mIsMultiDex;

    private DxDexKey(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @NonNull Iterable<String> additionalParameters,
            @Nullable Boolean isMultiDex) {
        super(sourceFile, buildToolsRevision, jumboMode, optimize);
        mAdditionalParameters = ImmutableSortedSet.copyOf(additionalParameters);;
        mIsMultiDex = isMultiDex;
    }

    static DxDexKey of(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @NonNull Iterable<String> additionalParameters,
            @Nullable Boolean isMultiDex) {
        return new DxDexKey(
                sourceFile,
                buildToolsRevision,
                jumboMode,
                optimize,
                additionalParameters,
                isMultiDex);
    }

    static final PreProcessCache.KeyFactory<DxDexKey> FACTORY = (sourceFile, revision, attrMap) -> {
        boolean jumboMode =
                Boolean.parseBoolean(attrMap.getNamedItem(ATTR_JUMBO_MODE).getNodeValue());

        boolean optimize;
        Node optimizeAttribute = attrMap.getNamedItem(ATTR_OPTIMIZE);

        //noinspection SimplifiableIfStatement
        if (optimizeAttribute != null) {
            optimize = Boolean.parseBoolean(optimizeAttribute.getNodeValue());
        } else {
            // Old code didn't set this attribute and always used optimizations.
            optimize = true;
        }

        List<String> additionalParameters = ImmutableList.of();
        Node additionalParametersAttribute = attrMap.getNamedItem(ATTR_ADDITIONAL_PARAMETERS);
        if (additionalParametersAttribute != null) {
            additionalParameters =
                    Splitter.on(ADDITIONAL_PARAMETERS_SEPARATOR)
                            .omitEmptyStrings()
                            .splitToList(additionalParametersAttribute.getNodeValue());
        }

        Boolean isMultiDex = null;
        Node multiDexAttr = attrMap.getNamedItem(ATTR_IS_MULTIDEX);
        if (multiDexAttr != null) {
            isMultiDex = Boolean.parseBoolean(multiDexAttr.getNodeValue());
        }

        return DxDexKey.of(
                sourceFile,
                revision,
                jumboMode,
                optimize,
                additionalParameters,
                isMultiDex);
    };

    @Override
    protected void writeFieldsToXml(@NonNull Node itemNode) {
        super.writeFieldsToXml(itemNode);

        Document document = itemNode.getOwnerDocument();
        if (!mAdditionalParameters.isEmpty()) {
            Attr additionalParameters = document.createAttribute(ATTR_ADDITIONAL_PARAMETERS);
            additionalParameters.setValue(
                    Joiner.on(ADDITIONAL_PARAMETERS_SEPARATOR).join(mAdditionalParameters));
            itemNode.getAttributes().setNamedItem(additionalParameters);
        }

        if (mIsMultiDex != null) {
            Attr multiDexAttr = document.createAttribute(ATTR_IS_MULTIDEX);
            multiDexAttr.setValue(Boolean.toString(mIsMultiDex));
            itemNode.getAttributes().setNamedItem(multiDexAttr);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DxDexKey dxDexKey = (DxDexKey) o;

        return mAdditionalParameters.equals(dxDexKey.mAdditionalParameters)
                && Objects.equal(mIsMultiDex, dxDexKey.mIsMultiDex);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mAdditionalParameters, mIsMultiDex);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("dexKey", super.toString())
                .add("mAdditionalParameters", mAdditionalParameters)
                .add("mIsMultiDex", mIsMultiDex)
                .toString();
    }
}
