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
import com.android.repository.Revision;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Map;

/**
 * Key to store the dexed items. This is Jack specific key, and in addition to
 * the properties found it {@link DexKey} it adds the map of additional parameters
 * that were used for running Jack.
 */
final class JackDexKey extends DexKey {
    private static final String ELEM_ADDITIONAL_PARAMETERS_ENTRY = "custom-entry";
    private static final String ELEM_ADDITIONAL_PARAMETERS = "custom-flags";
    private static final String ATTR_PARAMETER_KEY = "param-key";
    private static final String ATTR_PARAMETER_VALUE = "param-value";

    @NonNull
    private final ImmutableSortedMap<String, String> mAdditionalParameters;

    private JackDexKey(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @NonNull Map<String, String> additionalParameters) {
        super(sourceFile, buildToolsRevision, jumboMode, optimize);
        mAdditionalParameters = ImmutableSortedMap.copyOf(additionalParameters);;
    }

    static JackDexKey of(
            @NonNull File sourceFile,
            @NonNull Revision buildToolsRevision,
            boolean jumboMode,
            boolean optimize,
            @NonNull Map<String, String> additionalParameters) {
        return new JackDexKey(
                sourceFile, buildToolsRevision, jumboMode, optimize, additionalParameters);
    }

    static final PreProcessCache.KeyFactory<JackDexKey> FACTORY = (sourceFile, revision, attrMap) -> {
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

        Map<String, String> additionalParameters = Maps.newHashMap();
        if (attrMap.getLength() > 0) {
            Document document = attrMap.item(0).getOwnerDocument();

            NodeList matchingAdditionalParamsTags =
                    document.getElementsByTagName(ELEM_ADDITIONAL_PARAMETERS);
            if (matchingAdditionalParamsTags.getLength() > 0){
                Node elemAdditionalParams = matchingAdditionalParamsTags.item(0);

                for (int i = 0; i < elemAdditionalParams.getChildNodes().getLength(); i++) {
                    Node paramEntry = elemAdditionalParams.getChildNodes().item(i);

                    additionalParameters.put(
                            paramEntry.getAttributes().getNamedItem(ATTR_PARAMETER_VALUE).getNodeValue(),
                            paramEntry.getAttributes().getNamedItem(ATTR_PARAMETER_KEY).getNodeValue()
                    );
                }
            }
        }

        return JackDexKey.of(sourceFile, revision, jumboMode, optimize, additionalParameters);
    };

    @Override
    protected void writeFieldsToXml(@NonNull Node itemNode) {
        super.writeFieldsToXml(itemNode);

        Document document = itemNode.getOwnerDocument();
        Element params = document.createElement(ELEM_ADDITIONAL_PARAMETERS);
        for (String paramKey: mAdditionalParameters.keySet()) {
            String paramValue = mAdditionalParameters.get(paramKey);

            Element element = document.createElement(ELEM_ADDITIONAL_PARAMETERS_ENTRY);
            element.setAttribute(ATTR_PARAMETER_KEY, paramKey);
            element.setAttribute(ATTR_PARAMETER_VALUE, paramValue);

            params.appendChild(element);
        }
        document.appendChild(params);
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

        JackDexKey dxDexKey = (JackDexKey) o;

        return mAdditionalParameters.equals(dxDexKey.mAdditionalParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mAdditionalParameters);
    }
}
