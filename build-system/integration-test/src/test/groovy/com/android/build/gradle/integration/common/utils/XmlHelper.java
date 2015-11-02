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

package com.android.build.gradle.integration.common.utils;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class to parse XML files for assertions.
 */
public class XmlHelper {

    public static Node findChildWithTagAndAttrs(Node node, String childElementTag,
            String... attributeKeyValue) {
        if (attributeKeyValue.length % 2 != 0) {
            throw new IllegalArgumentException("attribute parameters should be a key value list");
        }
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childElementTag.equals(childNode.getNodeName())) {
                boolean hasAllAttributes = true;
                NamedNodeMap attributes = childNode.getAttributes();
                for (int j = 0; j < attributeKeyValue.length; j +=2) {
                    String attrName = attributeKeyValue[j];
                    String attrValue = attributeKeyValue[j + 1];
                    Node attrNode = attributes.getNamedItem(attrName);
                    if (attrNode == null || !attrValue.equals(attrNode.getTextContent())) {
                        hasAllAttributes = false;
                        break;
                    }
                }
                if (hasAllAttributes) {
                    return childNode;
                }
            }
        }
        return null;
    }
}
