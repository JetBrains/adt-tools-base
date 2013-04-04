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

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A resource.
 *
 * This includes the name, type, source file as a {@link ResourceFile} and an optional {@link Node}
 * in case of a resource coming from a value file.
 *
 */
class ResourceItem extends DataItem<ResourceFile> {

    private static final String ATTR_TYPE = "type";


    private final ResourceType mType;
    private Node mValue;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param type the type of the resource
     * @param value an optional Node that represents the resource value.
     */
    ResourceItem(@NonNull String name, @NonNull ResourceType type, Node value) {
        super(name);
        mType = type;
        mValue = value;
    }

    /**
     * Returns the type of the resource.
     * @return the type.
     */
    @NonNull
    public ResourceType getType() {
        return mType;
    }

    /**
     * Returns the optional value of the resource. Can be null
     * @return the value or null.
     */
    public Node getValue() {
        return mValue;
    }

    /**
     * Sets the value of the resource and set its state to TOUCHED.
     * @param from the resource to copy the value from.
     */
    void setValue(ResourceItem from) {
        mValue = from.mValue;
        setTouched();
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, and name.
     *
     * If the resource has not been added to a {@link ResourceFile}, this will throw an
     * {@link IllegalStateException}.
     *
     * @return the key for this resource.
     *
     * @throws IllegalStateException if the resource is not added to a ResourceFile
     */
    @Override
    String getKey() {
        if (getSource() == null) {
            throw new IllegalStateException(
                    "ResourceItem.getKey called on object with no ResourceFile: " + this);
        }
        String qualifiers = getSource().getQualifiers();
        if (qualifiers != null && qualifiers.length() > 0) {
            return mType.getName() + "-" + qualifiers + "/" + getName();
        }

        return mType.getName() + "/" + getName();
    }

    @Override
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        NodeUtils.addAttribute(document, node, null, ATTR_TYPE, mType.getName());
    }

    @Override
    Node getAdoptedNode(Document document) {
        return NodeUtils.adoptNode(document, mValue);
    }

    /**
     * Compares the ResourceItem {@link #getValue()} together and returns true if they are the same.
     * @param resource The ResourceItem object to compare to.
     * @return true if equal
     */
    public boolean compareValueWith(ResourceItem resource) {
        if (mValue != null && resource.mValue != null) {
            return NodeUtils.compareElementNode(mValue, resource.mValue);
        }

        return mValue == resource.mValue;
    }

    @Override
    public String toString() {
        return "ResourceItem{" +
                "mName='" + getName() + '\'' +
                ", mType=" + mType +
                ", mStatus=" + getStatus() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ResourceItem that = (ResourceItem) o;

        if (mType != that.mType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mType.hashCode();
        return result;
    }
}
