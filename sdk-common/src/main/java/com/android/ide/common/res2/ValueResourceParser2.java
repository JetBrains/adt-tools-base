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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Parser for "values" files.
 *
 * This parses the file and returns a list of {@link ResourceItem} object.
 */
class ValueResourceParser2 {

    private final File mFile;

    /**
     * Creates the parser for a given file.
     * @param file the file to parse.
     */
    ValueResourceParser2(File file) {
        mFile = file;
    }

    /**
     * Parses the file and returns a list of {@link ResourceItem} objects.
     * @return a list of resources.
     *
     * @throws MergingException if a merging exception happens
     */
    @NonNull
    List<ResourceItem> parseFile() throws MergingException {
        Document document = parseDocument(mFile);

        // get the root node
        Node rootNode = document.getDocumentElement();
        if (rootNode == null) {
            return Collections.emptyList();
        }
        NodeList nodes = rootNode.getChildNodes();

        final int count = nodes.getLength();
        // list containing the result
        List<ResourceItem> resources = Lists.newArrayListWithExpectedSize(count);
        // Multimap to detect dups
        Map<ResourceType, Set<String>> map = Maps.newEnumMap(ResourceType.class);

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            ResourceItem resource = getResource(node);
            if (resource != null) {
                // check this is not a dup
                checkDuplicate(resource, map);

                resources.add(resource);

                if (resource.getType() == ResourceType.DECLARE_STYLEABLE) {
                    // Need to also create ATTR items for its children
                    try {
                        ValueResourceParser2.addStyleableItems(node, resources, map);
                    } catch (MergingException e) {
                        e.setFile(mFile);
                        throw e;
                    }
                }
            }
        }

        return resources;
    }

    /**
     * Returns a new ResourceItem object for a given node.
     * @param node the node representing the resource.
     * @return a ResourceItem object or null.
     */
    static ResourceItem getResource(Node node) {
        ResourceType type = getType(node);
        String name = getName(node);

        if (type != null && name != null) {
            return new ResourceItem(name, type, node);
        }

        return null;
    }

    /**
     * Returns the type of the ResourceItem based on a node's attributes.
     * @param node the node
     * @return the ResourceType or null if it could not be inferred.
     */
    static ResourceType getType(Node node) {
        String nodeName = node.getLocalName();
        String typeString = null;

        if (TAG_ITEM.equals(nodeName)) {
            Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_TYPE);
            if (attribute != null) {
                typeString = attribute.getValue();
            }
        } else {
            // the type is the name of the node.
            typeString = nodeName;
        }

        if (typeString != null) {
            return ResourceType.getEnum(typeString);
        }

        return null;
    }

    /**
     * Returns the name of the resource based a node's attributes.
     * @param node the node.
     * @return the name or null if it could not be inferred.
     */
    static String getName(Node node) {
        Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_NAME);

        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    /**
     * Loads the DOM for a given file and returns a {@link Document} object.
     * @param file the file to parse
     * @return a Document object.
     * @throws MergingException if a merging exception happens
     */
    @NonNull
    static Document parseDocument(File file) throws MergingException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream(file));
            InputSource is = new InputSource(stream);
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (SAXParseException e) {
            String message = e.getLocalizedMessage();
            MergingException exception = new MergingException(message, e);
            exception.setFile(file);
            int lineNumber = e.getLineNumber();
            if (lineNumber != -1) {
                exception.setLine(lineNumber - 1); // make line numbers 0-based
                exception.setColumn(e.getColumnNumber() - 1);
            }
            throw exception;
        } catch (ParserConfigurationException e) {
            throw new MergingException(e).setFile(file);
        } catch (SAXException e) {
            throw new MergingException(e).setFile(file);
        } catch (IOException e) {
            throw new MergingException(e).setFile(file);
        } finally {
            Closeables.closeQuietly(stream);
        }
    }

    /**
     * Adds any declare styleable attr items below the given declare styleable nodes
     * into the given list
     *
     * @param styleableNode the declare styleable node
     * @param list the list to add items into
     * @param map map of existing items to detect dups.
     */
    static void addStyleableItems(@NonNull Node styleableNode,
                                  @NonNull List<ResourceItem> list,
                                  @Nullable Map<ResourceType, Set<String>> map)
            throws MergingException {
        assert styleableNode.getNodeName().equals(ResourceType.DECLARE_STYLEABLE.getName());
        NodeList nodes = styleableNode.getChildNodes();

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            ResourceItem resource = getResource(node);
            if (resource != null) {
                assert resource.getType() == ResourceType.ATTR;

                // is the attribute in the android namespace?
                if (!resource.getName().startsWith(ANDROID_NS_NAME_PREFIX)) {
                    if (hasFormatAttribute(node) || XmlUtils.hasElementChildren(node)) {
                        checkDuplicate(resource, map);
                        resource.setIgnoredFromDiskMerge(true);
                        list.add(resource);
                    }
                }
            }
        }
    }

    private static void checkDuplicate(@NonNull ResourceItem resource,
                                       @Nullable Map<ResourceType, Set<String>> map)
            throws MergingException {
        if (map == null) {
            return;
        }

        String name = resource.getName();
        Set<String> set = map.get(resource.getType());
        if (set == null) {
            set = Sets.newHashSet(name);
            map.put(resource.getType(), set);
        } else {
            if (set.contains(name)) {
                throw new MergingException(String.format(
                        "Found item %s/%s more than one time",
                        resource.getType().getDisplayName(), name));
            }

            set.add(name);
        }
    }

    private static boolean hasFormatAttribute(Node node) {
        return node.getAttributes().getNamedItemNS(null, ATTR_FORMAT) != null;
    }
}
