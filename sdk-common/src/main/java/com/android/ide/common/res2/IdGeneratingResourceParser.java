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
package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Parser for scanning id-generating XML files (layout or menu).
 *
 * Does not handle data-binding files (throws an exception if parsed).
 */
class IdGeneratingResourceParser {

    private ResourceItem mFileResourceItem;
    private List<ResourceItem> mIdResourceItems;

    /**
     * Parse the file for new IDs, given the source document's name and type.
     * After this completes, the getters can be used to grab the items
     * (the items for the IDs, and the item for the file itself).
     *
     * @param file the file to parse
     * @param sourceName the name of the file-based resource (derived from xml filename)
     * @param sourceType the type of the file-based resource (e.g., menu).
     * @throws MergingException if given a data-binding file, or fails to parse.
     */
    IdGeneratingResourceParser(@NonNull File file,
                               @NonNull String sourceName, @NonNull ResourceType sourceType) throws MergingException {
        Document mDocument = readDocument(file);
        if (hasDataBindings(mDocument)) {
            throw MergingException.withMessage("Does not handle data-binding files").build();
        }
        mFileResourceItem = new IdResourceItem(sourceName, sourceType);
        mIdResourceItems = Lists.newArrayList();
        final Set<String> pendingResourceIds = Sets.newHashSet();
        NodeList nodes = mDocument.getChildNodes();
        for (int i = 0; i < nodes.getLength(); ++i) {
            Node child = nodes.item(i);
            parseIds(mIdResourceItems, child, pendingResourceIds);
        }
        for (String id : pendingResourceIds) {
            ResourceItem resourceItem = new IdResourceItem(id, ResourceType.ID);
            mIdResourceItems.add(resourceItem);
        }
    }

    @NonNull
    private static Document readDocument(@NonNull File file) throws MergingException {
        try {
            return XmlUtils.parseUtfXmlFile(file, true /* namespaceAware */);
        }
        catch (SAXException e) {
            throw MergingException.wrapException(e).withFile(file).build();
        }
        catch (ParserConfigurationException e) {
            throw MergingException.wrapException(e).withFile(file).build();
        }
        catch (IOException e) {
            throw MergingException.wrapException(e).withFile(file).build();
        }
    }

    private static boolean hasDataBindings(@NonNull Document document) {
        Node rootNode = document.getDocumentElement();
        if (rootNode != null && TAG_LAYOUT.equals(rootNode.getNodeName())) {
            return true;
        }
        return false;
    }

    @NonNull
    public ResourceItem getFileResourceItem() {
        return mFileResourceItem;
    }

    @NonNull
    public List<ResourceItem> getIdResourceItems() {
        return mIdResourceItems;
    }

    private static void parseIds(@NonNull List<ResourceItem> items, @NonNull Node node,
                                 @NonNull Set<String> pendingResourceIds) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            // For all attributes in the android namespace, check if something has a value of the form "@+id/"
            for (int i = 0; i < attributes.getLength(); ++i) {
                Node attribute = attributes.item(i);
                String attrNamespace = attribute.getNamespaceURI();
                if (ANDROID_URI.equals(attrNamespace)) {
                    String attrName = attribute.getLocalName();
                    String value = attribute.getNodeValue();
                    if (value == null) {
                        continue;
                    }
                    // If the attribute is not android:id, and an item for it hasn't been created yet, add it to
                    // the list of pending ids.
                    if (value.startsWith(NEW_ID_PREFIX) && !ATTR_ID.equals(attrName)) {
                        String id = value.substring(NEW_ID_PREFIX.length());
                        if (!pendingResourceIds.contains(id)) {
                            pendingResourceIds.add(id);
                        }
                    }
                    else if (ATTR_ID.equals(attrName)) {
                        // Now process the android:id attribute.
                        String id;
                        if (value.startsWith(ID_PREFIX)) {
                            // If the id is not "@+id/", it may still have been declared as "@+id/" in a preceding view (eg. layout_above).
                            // So, we test if this is such a pending id.
                            id = value.substring(ID_PREFIX.length());
                            if (!pendingResourceIds.contains(id)) {
                                continue;
                            }
                        }
                        else if (value.startsWith(NEW_ID_PREFIX)) {
                            id = value.substring(NEW_ID_PREFIX.length());
                        }
                        else {
                            continue;
                        }
                        pendingResourceIds.remove(id);
                        ResourceItem item = new IdResourceItem(id, ResourceType.ID);
                        items.add(item);
                    }
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node child = children.item(i);
            parseIds(items, child, pendingResourceIds);
        }
    }

    /**
     * A ResourceItem representing an ID item or the source XML file item (ResourceType.LAYOUT, etc),
     * from an ID-generating XML file that supports blob writing without having to link to the source XML.
     */
    private static class IdResourceItem extends ResourceItem {
        /**
         * Constructs the resource with a given name and type.
         * Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
         *
         * @param name the name of the resource
         * @param type the type of the resource (ID, layout, menu).
         */
        public IdResourceItem(@NonNull String name, @NonNull ResourceType type) {
            // Use a null value, since the source XML is something like:
            //     <LinearLayout ... id="@+id/xxx">...</LinearLayout>
            // which is large and inefficient for encoding the resource item, and inefficient to hold on to.
            // Instead synthesize <item name=x type={id/layout/menu} /> as needed.
            super(name, type, null /* value */);
        }

        @Override
        Node getDetailsXml(Document document) {
            Node newNode = document.createElement(TAG_ITEM);
            NodeUtils.addAttribute(document, newNode, null, ATTR_NAME, getName());
            NodeUtils.addAttribute(document, newNode, null, ATTR_TYPE, getType().getName());
            // Normally layouts are file-based resources and the ResourceValue is the file path.
            // However, we're serializing it as XML and in that case the ResourceValue comes from
            // parsing the XML. So store the file path in the XML to make the ResourceValues equivalent.
            if (getType() != ResourceType.ID) {
                ResourceFile sourceFile = getSource();
                assert sourceFile != null;
                newNode.setTextContent(sourceFile.getFile().getAbsolutePath());
            }
            return newNode;
        }
    }
}
