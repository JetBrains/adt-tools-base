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

    private static final boolean IS_PACKAGE_DEPENDENT = true;
    private static final AttributeModel.Validator NO_VALIDATOR = null;
    private static final String NO_DEFAULT_VALUE = null;

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

    private static final NameAttributeNodeKeyResolver DEFAULT_NAME_ATTRIBUTE_RESOLVER =
            new NameAttributeNodeKeyResolver();

    private static final NoKeyNodeResolver DEFAULT_NO_KEY_NODE_RESOLVER = new NoKeyNodeResolver();
    private static final AttributeModel.BooleanValidator BOOLEAN_VALIDATOR =
            new AttributeModel.BooleanValidator();

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
     *     <li>List of attributes models with special behaviors :
     *     <ul>
     *         <li>Smart substitution of class names to fully qualified class names using the
     *         document's package declaration. The list's size can be 0..n</li>
     *         <li>Implicit default value when no defined on the xml element.</li>
     *         <li>{@link AttributeModel.Validator} to validate attribute value against.</li>
     *     </ul>
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
        ACTION(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Activity (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-element.html>
         *     Activity Xml documentation</a>}
         */
        ACTIVITY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel("parentActivityName",
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR),
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Activity-alias (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-alias-element.html>
         *     Activity-alias Xml documentation</a>}
         */
        ACTIVITY_ALIAS(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel("targetActivity",
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR),
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Application (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/application-element.html>
         *     Application Xml documentation</a>}
         */
        APPLICATION(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER,
                new AttributeModel("backupAgent",
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR),
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Category (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/category-element.html>
         *     Category Xml documentation</a>}
         */
        CATEGORY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Instrumentation (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/instrumentation-element.html>
         *     Instrunentation Xml documentation</a>}
         */
        INSTRUMENTATION(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

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
        MANIFEST(MergeType.MERGE_CHILDREN_ONLY, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Meta-data (contained in activity, activity-alias, application, provider, receiver)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/meta-data-element.html>
         *     Meta-data Xml documentation</a>}
         */
        META_DATA(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Provider (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/provider-element.html>
         *     Provider Xml documentation</a>}
         */
        PROVIDER(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Receiver (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/receiver-element.html>
         *     Receiver Xml documentation</a>}
         */
        RECEIVER(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Service (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/application-element.html>
         *     Service Xml documentation</a>}
         */
        SERVICE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel(SdkConstants.ATTR_NAME,
                        IS_PACKAGE_DEPENDENT, NO_DEFAULT_VALUE, NO_VALIDATOR)),

        /**
         * Support-screens (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/supports-screens-element.html>
         *     Support-screens Xml documentation</a>}
         */
        SUPPORTS_SCREENS(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Uses-feature (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-feature-element.html>
         *     Uses-feature Xml documentation</a>}
         */
        USES_FEATURE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Use-library (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-library-element.html>
         *     Use-library Xml documentation</a>}
         */
        USES_LIBRARY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                new AttributeModel("required",
                        !IS_PACKAGE_DEPENDENT, "true", BOOLEAN_VALIDATOR)),

        /**
         * Uses-permission (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-permission-element.html>
         *     Uses-permission Xml documentation</a>}
         */
        USES_PERMISSION(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Uses-sdk (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-sdk-element.html>
         *     Uses-sdk Xml documentation</a>}
         */
        USES_SDK(MergeType.CONFLICT, DEFAULT_NO_KEY_NODE_RESOLVER);


        private final MergeType mMergeType;
        private final NodeKeyResolver mNodeKeyResolver;
        private final ImmutableList<AttributeModel> mAttributeModels;

        private NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                @Nullable AttributeModel... attributeModels) {
            this.mMergeType = Preconditions.checkNotNull(mergeType);
            this.mNodeKeyResolver = Preconditions.checkNotNull(nodeKeyResolver);
            this.mAttributeModels = ImmutableList.copyOf(attributeModels);
        }

        // TODO: we need to support cases where the key is actually provided by a sub-element
        // like intent-filter.
        String getKey(XmlElement xmlElement) {
            return mNodeKeyResolver.getKey(xmlElement);
        }

        @Nullable
        AttributeModel getAttributeModel(String attributeLocalName) {
            // mAttributeModels could be replaced with a Map if the number of models grows.
            for (AttributeModel attributeModel : mAttributeModels) {
                if (attributeModel.getName().equals(attributeLocalName)) {
                    return attributeModel;
                }
            }
            return null;
        }

        /**
         * Returns the Xml name for this node type
         */
        String toXmlName() {
            return SdkUtils.constantNameToXmlName(this.name());
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
