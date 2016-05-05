/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.annotations.concurrency.Immutable;
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
 * Key to store Item/StoredItem in maps.
 * The key contains the element that are used for the dex call:
 * - source file
 * - build tools revision
 * - jumbo mode
 * - optimization on/off
 * - additional parameters/flags
 */
@Immutable
final class DexKey extends PreProcessCache.Key {

    private static final char ADDITIONAL_PARAMETERS_SEPARATOR = ',';

    private static final String ATTR_JUMBO_MODE = "jumboMode";

    private static final String ATTR_OPTIMIZE = "optimize";

    private static final String ATTR_IS_MULTIDEX = "is-multidex";

    private static final String ATTR_ADDITIONAL_PARAMETERS = "custom-flags";

    static final PreProcessCache.KeyFactory<DexKey> FACTORY = (sourceFile, revision, attrMap) -> {
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

        Boolean isMultiDex = null;
        Node formatAttribute = attrMap.getNamedItem(ATTR_IS_MULTIDEX);
        if (formatAttribute != null) {
            isMultiDex = Boolean.parseBoolean(formatAttribute.getNodeValue());
        }

        List<String> additionalParameters = ImmutableList.of();
        Node additionalParametersAttribute = attrMap.getNamedItem(ATTR_ADDITIONAL_PARAMETERS);
        if (additionalParametersAttribute != null) {
            additionalParameters =
                    Splitter.on(ADDITIONAL_PARAMETERS_SEPARATOR)
                            .omitEmptyStrings()
                            .splitToList(additionalParametersAttribute.getNodeValue());
        }

        return DexKey.of(sourceFile, revision, jumboMode, optimize, isMultiDex, additionalParameters);
    };

    private final boolean mJumboMode;

    private final boolean mOptimize;

    @Nullable
    private final Boolean mIsMultiDex;

    @NonNull
    private final ImmutableSortedSet<String> mAdditionalParameters;

    static DexKey of(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @Nullable Boolean isMultiDex,
            @NonNull Iterable<String> additionalParameters) {
        return new DexKey(
                sourceFile,
                buildToolsRevision,
                jumboMode,
                optimize,
                isMultiDex,
                additionalParameters);
    }

    private DexKey(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @Nullable Boolean isMultiDex,
            @NonNull Iterable<String> additionalParameters) {
        super(sourceFile, buildToolsRevision);
        mJumboMode = jumboMode;
        mOptimize = optimize;
        mAdditionalParameters = ImmutableSortedSet.copyOf(additionalParameters);
        mIsMultiDex = isMultiDex;
    }

    void writeFieldsToXml(@NonNull Node itemNode) {
        Document document = itemNode.getOwnerDocument();

        Attr jumboMode = document.createAttribute(ATTR_JUMBO_MODE);
        jumboMode.setValue(Boolean.toString(this.mJumboMode));
        itemNode.getAttributes().setNamedItem(jumboMode);

        Attr optimize = document.createAttribute(ATTR_OPTIMIZE);
        optimize.setValue(Boolean.toString(this.mOptimize));
        itemNode.getAttributes().setNamedItem(optimize);

        if (mIsMultiDex != null) {
            Attr isMultiDex = document.createAttribute(ATTR_IS_MULTIDEX);
            isMultiDex.setValue(Boolean.toString(this.mIsMultiDex));
            itemNode.getAttributes().setNamedItem(isMultiDex);
        }

        if (!mAdditionalParameters.isEmpty()) {
            Attr additionalParameters = document.createAttribute(ATTR_ADDITIONAL_PARAMETERS);
            additionalParameters.setValue(
                    Joiner.on(ADDITIONAL_PARAMETERS_SEPARATOR).join(mAdditionalParameters));
            itemNode.getAttributes().setNamedItem(additionalParameters);
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

        DexKey dexKey = (DexKey) o;
        return mJumboMode == dexKey.mJumboMode
                && mOptimize == dexKey.mOptimize
                && Objects.equal(mIsMultiDex, dexKey.mIsMultiDex)
                && Objects.equal(mAdditionalParameters, dexKey.mAdditionalParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                super.hashCode(), mJumboMode, mOptimize, mIsMultiDex, mAdditionalParameters);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("buildTools", getBuildToolsRevision())
                .add("sourceFile", getSourceFile())
                .add("mJumboMode", mJumboMode)
                .add("mOptimize", mOptimize)
                .add("mIsMultiDex", mIsMultiDex)
                .add("mAdditionalParameters", mAdditionalParameters)
                .toString();
    }
}
