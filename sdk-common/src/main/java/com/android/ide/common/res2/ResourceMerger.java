/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;

import com.android.annotations.NonNull;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Implementation of {@link DataMerger} for {@link ResourceSet}, {@link ResourceItem}, and
 * {@link ResourceFile}.
 */
public class ResourceMerger extends DataMerger<ResourceItem, ResourceFile, ResourceSet> {

    @Override
    protected ResourceSet createFromXml(Node node) {
        ResourceSet set = new ResourceSet("");
        return (ResourceSet) set.createFromXml(node);
    }

    @Override
    protected boolean needsCustomHandling(@NonNull String dataItemKey) {
        return dataItemKey.startsWith("declare-styleable/");
    }

    @Override
    protected void customHandle(
            @NonNull String dataItemKey,
            @NonNull List<ResourceItem> items,
            @NonNull MergeConsumer<ResourceItem> consumer) throws MergingException {
        boolean mustCompute = false;
        for (ResourceItem item : items) {
            mustCompute |= item.getStatus() != 0;
        }


        if (mustCompute) {
            ResourceItem oldItem = items.get(0);

            try {
                DocumentBuilder builder = mFactory.newDocumentBuilder();
                Document document = builder.newDocument();
                Node declareStyleableNode = document.createElement(TAG_DECLARE_STYLEABLE);

                Attr nameAttr = document.createAttribute(ATTR_NAME);
                nameAttr.setValue(oldItem.getName());
                declareStyleableNode.getAttributes().setNamedItem(nameAttr);

                // keep track of attr added to it.
                Set<String> attrs = Sets.newHashSet();

                for (ResourceItem item : items) {
                    if (!item.isRemoved()) {
                        Node node = item.getValue();
                        if (node != null) {
                            NodeList children = node.getChildNodes();
                            for (int i = 0; i < children.getLength(); i++) {
                                Node child = children.item(i);
                                if (child.getNodeType() != Node.ELEMENT_NODE) {
                                    continue;
                                }

                                // get the name
                                NamedNodeMap attributes = child.getAttributes();
                                nameAttr = (Attr) attributes.getNamedItemNS(null, ATTR_NAME);
                                if (nameAttr == null) {
                                    continue;
                                }

                                String name = nameAttr.getNodeValue();
                                if (attrs.contains(name)) {
                                    continue;
                                }

                                // adopt the node.
                                attrs.add(name);
                                Node adoptedChild = NodeUtils.adoptNode(document, child);
                                declareStyleableNode.appendChild(adoptedChild);
                            }
                        }
                    }
                }

                // always write it for now.
                ResourceItem newItem = new ResourceItem(oldItem.getName(), oldItem.getType(), declareStyleableNode);
                newItem.setTouched();

                // tmp workaround, set the source of the new item from an old item
                // This needs to be fixed and be a custom source (merged item).
                newItem.setSource(oldItem.getSource());

                consumer.addItem(newItem);
            } catch (ParserConfigurationException e) {
                throw new MergingException(e);
            } finally {

            }
        }
    }
}
