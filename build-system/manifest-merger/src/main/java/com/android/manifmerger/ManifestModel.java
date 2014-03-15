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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.SdkUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;

/**
 * Model for the manifest file merging activities.
 * <p>
 *
 * This model will describe each element that is eligible for merging and associated merging
 * policies. It is not reusable as most of its interfaces are private but a future enhancement
 * could easily make this more generic/reusable if we need to merge more than manifest files.
 *
 */
@Immutable
class ManifestModel {

    /**
     * Interface responsible for providing a key extraction capability from a xml element.
     * Some elements store their keys as an attribute, some as a sub-element attribute, some don't
     * have any key.
     */
    @Immutable
    interface NodeKeyResolver {

        /**
         * Returns the key associated with this xml element.
         * @param xmlElement the xml element to get the key from
         * @return the key as a string to uniquely identify xmlElement from similarly typed elements
         * in the xml document or null if there is no key.
         */
        @Nullable String getKey(XmlElement xmlElement);
    }

    /**
     * Implementation of {@link com.android.manifmerger.ManifestModel.NodeKeyResolver} that do not
     * provide any key (the element has to be unique in the xml document).
     */
    private static class NoKeyNodeResolver implements NodeKeyResolver {

        @Override
        @Nullable
        public String getKey(XmlElement xmlElement) {
            return null;
        }
    }

    /**
     * Implementation of {@link com.android.manifmerger.ManifestModel.NodeKeyResolver} that uses an
     * attribute to resolve the key value.
     */
    private static class AttributeBasedNodeKeyResolver implements NodeKeyResolver {

        @Nullable private final String namespaceUri;
        private final String attributeName;

        /**
         * Build a new instance capable of resolving an xml element key from the passed attribute
         * namespace and local name.
         * @param namespaceUri optional namespace for the attribute name.
         * @param attributeName attribute name
         */
        private AttributeBasedNodeKeyResolver(@Nullable String namespaceUri,
                @NonNull String attributeName) {
            this.namespaceUri = namespaceUri;
            this.attributeName = Preconditions.checkNotNull(attributeName);
        }

        @Override
        @Nullable
        public String getKey(XmlElement xmlElement) {
            return namespaceUri == null
                ? xmlElement.getXml().getAttribute(attributeName)
                : xmlElement.getXml().getAttributeNS(namespaceUri, attributeName);
        }
    }

    /**
     * Subclass of {@link com.android.manifmerger.ManifestModel.AttributeBasedNodeKeyResolver} that
     * uses "android:name" as the attribute.
     */
    private static class NameAttributeNodeKeyResolver extends AttributeBasedNodeKeyResolver {

        private NameAttributeNodeKeyResolver() {
            super(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        }
    }

    private static final NameAttributeNodeKeyResolver defaultNameAttributeResolver =
            new NameAttributeNodeKeyResolver();

    private static final NoKeyNodeResolver defaultNoKeyNodeResolver = new NoKeyNodeResolver();

    /**
     * Definitions of the support node types in the Android Manifest file.
     * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-intro.html/>}
     * for more details about the xml format.
     *
     * There is no DTD or schema associated with the file type so this is best effort in providing
     * some metadata on the elements of the Android's xml file.
     *
     * Each xml element is defined as an enum value and for each node, extra metadata is added
     * <ul>
     *     <li>{@link com.android.manifmerger.MergeType} to identify how the merging engine
     *     should process this element.</li>
     *     <li>{@link com.android.manifmerger.ManifestModel.NodeKeyResolver} to resolve the
     *     element's key. Elements can have an attribute like "android:name", others can use
     *     a sub-element, and finally some do not have a key and are meant to be unique.</li>
     *     <li>List of attributes that support smart substitution of class names to fully qualified
     *     class names using the document's package declaration. The list's size can be 0..n</li>
     * </ul>
     *
     * It is of the outermost importance to keep this model correct as it is used by the merging
     * engine to make all its decisions. There should not be special casing in the engine, all
     * decisions must be represented here.
     *
     * If you find yourself needing to extend the model to support future requirements, do it here
     * and modify the engine to make proper decision based on the added metadata.
     */
    enum NodeTypes {

        /**
         * Action (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/action-element.html>
         *     Action Xml documentation</a>}
         */
        ACTION(MergeType.MERGE, defaultNameAttributeResolver),

        /**
         * Activity (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-element.html>
         *     Activity Xml documentation</a>}
         */
        ACTIVITY(MergeType.MERGE, defaultNameAttributeResolver,
                "parentActivityName", SdkConstants.ATTR_NAME),

        /**
         * Activity-alias (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-alias-element.html>
         *     Activity-alias Xml documentation</a>}
         */
        ACTIVITY_ALIAS(MergeType.MERGE, defaultNameAttributeResolver,
                "targetActivity", SdkConstants.ATTR_NAME),

        /**
         * Application (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/application-element.html>
         *     Application Xml documentation</a>}
         */
        APPLICATION(MergeType.MERGE, defaultNoKeyNodeResolver, "backupAgent", SdkConstants.ATTR_NAME),

        /**
         * Category (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/category-element.html>
         *     Category Xml documentation</a>}
         */
        CATEGORY(MergeType.MERGE, defaultNameAttributeResolver),

        /**
         * Instrumentation (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/instrumentation-element.html>
         *     Instrunentation Xml documentation</a>}
         */
        INSTRUMENTATION(MergeType.MERGE, defaultNameAttributeResolver, SdkConstants.ATTR_NAME),

        /**
         * Intent-filter (contained in activity, activity-alias, service, receiver)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/intent-filter-element.html>
         *     Intent-filter Xml documentation</a>}
         */
        // TODO: key is provided by sub elements...
        INTENT_FILTER(MergeType.MERGE, new NoKeyNodeResolver()),

        /**
         * Manifest (top level node)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>
         *     Manifest Xml documentation</a>}
         */
        MANIFEST(MergeType.MERGE_CHILDREN_ONLY, defaultNoKeyNodeResolver),

        /**
         * Meta-data (contained in activity, activity-alias, application, provider, receiver)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/meta-data-element.html>
         *     Meta-data Xml documentation</a>}
         */
        META_DATA(MergeType.MERGE, defaultNameAttributeResolver),

        /**
         * Provider (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/provider-element.html>
         *     Provider Xml documentation</a>}
         */
        PROVIDER(MergeType.MERGE, defaultNameAttributeResolver, SdkConstants.ATTR_NAME),

        /**
         * Receiver (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/receiver-element.html>
         *     Receiver Xml documentation</a>}
         */
        RECEIVER(MergeType.MERGE, defaultNameAttributeResolver, SdkConstants.ATTR_NAME),

        /**
         * Service (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/application-element.html>
         *     Service Xml documentation</a>}
         */
        SERVICE(MergeType.MERGE, defaultNameAttributeResolver, SdkConstants.ATTR_NAME),

        /**
         * Support-screens (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/supports-screens-element.html>
         *     Support-screens Xml documentation</a>}
         */
        SUPPORTS_SCREENS(MergeType.MERGE, defaultNoKeyNodeResolver),

        /**
         * Uses-feature (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-feature-element.html>
         *     Uses-feature Xml documentation</a>}
         */
        USES_FEATURE(MergeType.MERGE, defaultNameAttributeResolver),

        /**
         * Use-library (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-library-element.html>
         *     Use-library Xml documentation</a>}
         */
        USE_LIBRARY(MergeType.CONFLICT, defaultNameAttributeResolver),

        /**
         * Uses-permission (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-permission-element.html>
         *     Uses-permission Xml documentation</a>}
         */
        USES_PERMISSION(MergeType.MERGE, defaultNameAttributeResolver),

        /**
         * Uses-sdk (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-sdk-element.html>
         *     Uses-sdk Xml documentation</a>}
         */
        USES_SDK(MergeType.CONFLICT, defaultNoKeyNodeResolver);


        private final MergeType mMergeType;
        private final NodeKeyResolver mNodeKeyResolver;
        private final ImmutableList<String> mFqcnAttributes;

        private NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                @Nullable String... fqcnAttributes) {
            this.mMergeType = Preconditions.checkNotNull(mergeType);
            this.mNodeKeyResolver = Preconditions.checkNotNull(nodeKeyResolver);
            this.mFqcnAttributes = ImmutableList.copyOf(fqcnAttributes);
        }

        // TODO: we need to support cases where the key is actually provided by a sub-element
        // like intent-filter.
        String getKey(XmlElement xmlElement) {
            return mNodeKeyResolver.getKey(xmlElement);
        }

        /**
         * Return true if the attribute support smart substitution of partially fully qualified
         * class names with package settings as provided by the manifest node's package attribute
         * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>}
         *
         * @param attribute the xml attribute definition.
         * @return true if this name supports smart substitution or false if not.
         */
        boolean isAttributePackageDependent(Attr attribute) {
            return mFqcnAttributes != null
                    && SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())
                    && mFqcnAttributes.contains(attribute.getLocalName());
        }

        /**
         * Returns the {@link NodeTypes} instance from an xml element name (without namespace
         * decoration). For instance, an xml element
         * <pre>
         *     {@code
         *     <activity android:name="foo">
         *         ...
         *     </activity>}
         * </pre>
         * has a xml simple name of "activity" which will resolve to {@link NodeTypes#ACTIVITY} value.
         *
         * Note : a runtime exception will be generated if no mapping from the simple name to a
         * {@link com.android.manifmerger.ManifestModel.NodeTypes} exists.
         *
         * @param xmlSimpleName the xml (lower-hyphen separated words) simple name.
         * @return the {@link NodeTypes} associated with that element name.
         */
        static NodeTypes fromXmlSimpleName(String xmlSimpleName) {
            String constantName = SdkUtils.xmlNameToConstantName(xmlSimpleName);

            // TODO: is legal to have non standard xml elements in manifest files ? if yes
            // consider adding a CUSTOM NodeTypes and not generate exception here.
            return NodeTypes.valueOf(constantName);
        }

        MergeType getMergeType() {
            return mMergeType;
        }
    }
}
