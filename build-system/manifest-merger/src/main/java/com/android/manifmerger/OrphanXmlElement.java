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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.w3c.dom.Element;

/**
 * An xml element that does not belong to a {@link com.android.manifmerger.XmlDocument}
 */
public class OrphanXmlElement extends XmlNode {

    private static final PositionXmlParser.Position UNKNOWN_POSITION = new PositionXmlParser.Position() {
        @Nullable
        @Override
        public PositionXmlParser.Position getEnd() {
            return null;
        }

        @Override
        public void setEnd(@NonNull PositionXmlParser.Position end) {

        }

        @Override
        public int getLine() {
            return 0;
        }

        @Override
        public int getOffset() {
            return 0;
        }

        @Override
        public int getColumn() {
            return 0;
        }
    };


    @NonNull
    private final Element mXml;

    @NonNull
    private final ManifestModel.NodeTypes mType;

    public OrphanXmlElement(@NonNull Element xml) {

        mXml = Preconditions.checkNotNull(xml);
        mType = ManifestModel.NodeTypes.fromXmlSimpleName(mXml.getNodeName());
    }

    /**
     * Returns true if this xml element's {@link com.android.manifmerger.ManifestModel.NodeTypes} is
     * the passed one.
     */
    public boolean isA(ManifestModel.NodeTypes type) {
        return this.mType == type;
    }

    @NonNull
    @Override
    public Element getXml() {
        return mXml;
    }


    @Override
    public NodeKey getId() {
        return new NodeKey(Strings.isNullOrEmpty(getKey())
                ? getName().toString()
                : getName().toString() + "#" + getKey());
    }

    @Override
    public NodeName getName() {
        return XmlNode.unwrapName(mXml);
    }

    /**
     * Returns this xml element {@link com.android.manifmerger.ManifestModel.NodeTypes}
     */
    @NonNull
    public ManifestModel.NodeTypes getType() {
        return mType;
    }

    /**
     * Returns the unique key for this xml element within the xml file or null if there can be only
     * one element of this type.
     */
    @Nullable
    public String getKey() {
        return mType.getNodeKeyResolver().getKey(mXml);
    }

    @Override
    @NonNull
    public PositionXmlParser.Position getPosition() {
        return UNKNOWN_POSITION;
    }
}

