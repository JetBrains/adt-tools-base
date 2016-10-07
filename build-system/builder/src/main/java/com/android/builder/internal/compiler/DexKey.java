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
import com.android.repository.Revision;
import com.google.common.base.Objects;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;

/**
 * Key to store Item/StoredItem in maps.
 * The key contains the element that are used for the dex call:
 * - source file
 * - build tools revision
 * - jumbo mode
 * - optimization on/off
 */
class DexKey extends PreProcessCache.Key {

    protected static final String ATTR_JUMBO_MODE = "jumboMode";

    protected static final String ATTR_OPTIMIZE = "optimize";

    private final boolean mJumboMode;

    private final boolean mOptimize;

    protected DexKey(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize) {
        super(sourceFile, buildToolsRevision);
        mJumboMode = jumboMode;
        mOptimize = optimize;
    }

    protected void writeFieldsToXml(@NonNull Node itemNode) {
        Document document = itemNode.getOwnerDocument();

        Attr jumboMode = document.createAttribute(ATTR_JUMBO_MODE);
        jumboMode.setValue(Boolean.toString(this.mJumboMode));
        itemNode.getAttributes().setNamedItem(jumboMode);

        Attr optimize = document.createAttribute(ATTR_OPTIMIZE);
        optimize.setValue(Boolean.toString(this.mOptimize));
        itemNode.getAttributes().setNamedItem(optimize);
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
        return mJumboMode == dexKey.mJumboMode && mOptimize == dexKey.mOptimize;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mJumboMode, mOptimize);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("buildTools", getBuildToolsRevision())
                .add("sourceFile", getSourceFile())
                .add("mJumboMode", mJumboMode)
                .add("mOptimize", mOptimize)
                .toString();
    }
}
