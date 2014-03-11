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
import com.android.utils.PositionXmlParser;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Common behavior of any xml declaration.
 */
public abstract class XmlNode {

    /**
     * Returns an unique id within the manifest file for the element.
     */
    public abstract String getId();

    /**
     * Returns the element's position
     */
    public abstract PositionXmlParser.Position getPosition();

    /**
     * Returns the element's xml
     */
    public abstract Node getXml();

    /**
     * Returns the name of this xml element or attribute.
     */
    public abstract NodeName getName();

    /**
     * Abstraction to an xml name to isolate whether the name has a namespace or not.
     */
    public interface NodeName {

        /**
         * Returns true if this attribute name has a namespace declaration and that namespapce is
         * the same as provided, false otherwise.
         */
        boolean isInNamespace(String namespaceURI);

        /**
         * Adds a new attribute of this name to a xml element with a value.
         * @param to the xml element to add the attribute to.
         * @param withValue the new attribute's value.
         */
        void addToNode(Element to, String withValue);
    }

    /**
     * Factory method to create an instance of {@link com.android.manifmerger.XmlNode.NodeName}
     * for an existing xml node.
     * @param node the xml definition.
     * @return an instance of {@link com.android.manifmerger.XmlNode.NodeName} providing
     * namespace handling.
     */
    public static NodeName unwrapName(Node node) {
        return node.getNamespaceURI() == null
                ? new Name(node.getNodeName())
                : new NamespaceAwareName(node);
    }

    /**
     * Implementation of {@link com.android.manifmerger.XmlNode.NodeName} for an
     * node's declaration not using a namespace.
     */
    private static final class Name implements NodeName {
        private final String mName;

        private Name(@NonNull String name) {
            this.mName = Preconditions.checkNotNull(name);
        }

        @Override
        public boolean isInNamespace(String namespaceURI) {
            return false;
        }

        @Override
        public void addToNode(Element to, String withValue) {
            to.setAttribute(mName, withValue);
        }

        @Override
        public boolean equals(Object o) {
            return (o != null && o instanceof Name && ((Name) o).mName.equals(this.mName));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mName);
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    /**
     * Implementation of the {@link com.android.manifmerger.XmlNode.NodeName} for a namespace aware attribute.
     */
    private static final class NamespaceAwareName implements NodeName {
        private final String mNamespaceURI;
        // ignore for comparison and hashcoding since different documents can use different
        // prefixes for the same namespace URI.
        private final String mPrefix;
        private final String mLocalName;

        private NamespaceAwareName(@NonNull Node node) {
            this.mNamespaceURI = Preconditions.checkNotNull(node.getNamespaceURI());
            this.mPrefix = Preconditions.checkNotNull(node.getPrefix());
            this.mLocalName = Preconditions.checkNotNull(node.getLocalName());
        }


        @Override
        public boolean isInNamespace(String namespaceURI) {
            return mNamespaceURI.equals(namespaceURI);
        }

        @Override
        public void addToNode(Element to, String withValue) {
            // TODO: consider standardizing everything on "android:"
            to.setAttributeNS(mNamespaceURI, mPrefix + ":" + mLocalName, withValue);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mNamespaceURI, mLocalName);
        }

        @Override
        public boolean equals(Object o) {
            return (o != null && o instanceof NamespaceAwareName
                    && ((NamespaceAwareName) o).mLocalName.equals(this.mLocalName)
                    && ((NamespaceAwareName) o).mNamespaceURI.equals(this.mNamespaceURI));
        }

        @Override
        public String toString() {
            return mPrefix + ":" + mLocalName;
        }
    }
}
