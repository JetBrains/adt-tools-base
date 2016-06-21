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
import static com.android.ide.common.res2.DataFile.FileType;
import static com.android.ide.common.res2.ResourceFile.ATTR_QUALIFIER;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Implementation of {@link DataMerger} for {@link ResourceSet}, {@link ResourceItem}, and
 * {@link ResourceFile}.
 */
public class ResourceMerger extends DataMerger<ResourceItem, ResourceFile, ResourceSet> {
    private static final String NODE_MERGED_ITEMS = "mergedItems";

    /**
     * The value for the min SDK.
     */
    private int mMinSdk;

    /**
     * Cached resource keys that should not be in the final artifact. If null, then the
     * cache will have to be recomputed. To clear the cache, use {@link #clearFilterCache()}.
     */
    @Nullable
    private Set<String> mRejectCache;

    /**
     * Creates a new resource merger.
     * @param minSdk the minimum SDK, used for filtering.
     */
    public ResourceMerger(int minSdk) {
        mMinSdk = minSdk;
    }

    /**
     * Override of the normal ResourceItem to handle merged item cases.
     * This is mostly to deal with items that do not have a matching source file.
     * This override the method returning the qualifier or the source type, to directly
     * return a value instead of relying on a source file (since merged items don't have any).
     */
    private static class MergedResourceItem extends SourcelessResourceItem {

        @NonNull
        private final String mQualifiers;

        /**
         * Constructs the object with a name, type and optional value.
         *
         * Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
         *
         * @param name  the name of the resource
         * @param type  the type of the resource
         * @param qualifiers the qualifiers of the resource
         * @param value an optional Node that represents the resource value.
         * @param libraryName name of library where resource came from if any
         */
        public MergedResourceItem(
                @NonNull String name,
                @NonNull ResourceType type,
                @NonNull String qualifiers,
                @Nullable Node value,
                @Nullable String libraryName) {
            super(name, type, value, libraryName);
            mQualifiers = qualifiers;
        }

        @NonNull
        @Override
        public String getQualifiers() {
            return mQualifiers;
        }

        @Override
        @NonNull
        public FileType getSourceType() {
            return FileType.XML_VALUES;
        }
    }

    /**
     * Map of items that are purely results of merges (ie item that made up of several
     * original items). The first map key is the associated qualifier for the items,
     * the second map key is the item name.
     */
    protected final Map<String, Map<String, ResourceItem>> mMergedItems = Maps.newHashMap();


    /**
     * Reads the {@link ResourceSet} from the blob XML. {@link ResourceMerger} deals with two kinds
     * of sets - {@link GeneratedResourceSet} and "plain" {@link ResourceSet} . Instances of the
     * former are marked with {@code generated="true"} attribute. Instances of the latter have a
     * {@code generated-set} attribute that references the corresponding generated set by name.
     * For any variant, the generated set has a lower priority, so it comes in the XML first. This
     * means we will find it by name at this stage.
     */
    @Override
    protected ResourceSet createFromXml(Node node) throws MergingException {
        String generated = NodeUtils.getAttribute(node, GeneratedResourceSet.ATTR_GENERATED);
        ResourceSet set;
        if (SdkConstants.VALUE_TRUE.equals(generated)) {
            set = new GeneratedResourceSet("", null);
        } else {
            set = new ResourceSet("", null);
        }
        ResourceSet newResourceSet = (ResourceSet) set.createFromXml(node);

        String generatedSetName = NodeUtils.getAttribute(node, ResourceSet.ATTR_GENERATED_SET);
        if (generatedSetName != null) {
            for (ResourceSet resourceSet : getDataSets()) {
                if (resourceSet.getConfigName().equals(generatedSetName)) {
                    newResourceSet.setGeneratedSet(resourceSet);
                    break;
                }
            }
        }

        String fromDependency = NodeUtils.getAttribute(node, ResourceSet.ATTR_FROM_DEPENDENCY);
        if (SdkConstants.VALUE_TRUE.equals(fromDependency)) {
            newResourceSet.setFromDependency(true);
        }

        return newResourceSet;
    }

    @Override
    protected boolean requiresMerge(@NonNull String dataItemKey) {
        return dataItemKey.startsWith("declare-styleable/");
    }

    @Override
    protected void mergeItems(
            @NonNull String dataItemKey,
            @NonNull List<ResourceItem> items,
            @NonNull MergeConsumer<ResourceItem> consumer) throws MergingException {
        boolean touched = false; // touched becomes true if one is touched.
        boolean removed = true; // removed stays true if all items are removed.
        for (ResourceItem item : items) {
            touched |= item.isTouched();
            removed &= item.isRemoved();
        }

        // get the name of the item (the key is the full key not just the same).
        ResourceItem sourceItem = items.get(0);
        String itemName = sourceItem.getName();
        String qualifier = sourceItem.getQualifiers();
        String libraryName = sourceItem.getLibraryName();
        // get the matching mergedItem
        ResourceItem previouslyWrittenItem = getMergedItem(qualifier, itemName);

        try {
            if (touched || (previouslyWrittenItem == null && !removed)) {
                DocumentBuilder builder = mFactory.newDocumentBuilder();
                Document document = builder.newDocument();

                Node declareStyleableNode = document.createElementNS(null, TAG_DECLARE_STYLEABLE);

                Attr nameAttr = document.createAttribute(ATTR_NAME);
                nameAttr.setValue(itemName);
                declareStyleableNode.getAttributes().setNamedItem(nameAttr);

                // loop through all the items and gather a unique list of nodes.
                // because we start with the lower priority items, this means that attr with
                // format inside declare-styleable will be processed first, and added first
                // while the redundant attr (with no format) will be ignored.
                Set<String> attrs = Sets.newHashSet();

                for (ResourceItem item : items) {
                    if (!item.isRemoved()) {
                        Node oldDeclareStyleable = item.getValue();
                        if (oldDeclareStyleable != null) {
                            NodeList children = oldDeclareStyleable.getChildNodes();
                            for (int i = 0; i < children.getLength(); i++) {
                                Node attrNode = children.item(i);
                                if (attrNode.getNodeType() != Node.ELEMENT_NODE) {
                                    continue;
                                }

                                if (SdkConstants.TAG_EAT_COMMENT.equals(attrNode.getLocalName())) {
                                    continue;
                                }

                                // get the name
                                NamedNodeMap attributes = attrNode.getAttributes();
                                nameAttr = (Attr) attributes.getNamedItemNS(null, ATTR_NAME);
                                if (nameAttr == null) {
                                    continue;
                                }

                                String name = nameAttr.getNodeValue();
                                if (attrs.contains(name)) {
                                    continue;
                                }

                                // duplicate the node.
                                attrs.add(name);
                                Node newAttrNode = NodeUtils.duplicateNode(document, attrNode);
                                declareStyleableNode.appendChild(newAttrNode);
                            }
                        }
                    }
                }

                // always write it for now.
                MergedResourceItem newItem = new MergedResourceItem(
                        itemName,
                        sourceItem.getType(),
                        qualifier,
                        declareStyleableNode,
                        libraryName);

                // check whether the result of the merge is new or touched compared
                // to the previous state.
                //noinspection ConstantConditions
                if (previouslyWrittenItem == null ||
                        !NodeUtils.compareElementNode(newItem.getValue(), previouslyWrittenItem.getValue(), false)) {
                    newItem.setTouched();
                }

                // then always add it both to the list of merged items in the merge
                // and to the consumer.
                addMergedItem(qualifier, newItem);
                consumer.addItem(newItem);

            } else if (previouslyWrittenItem != null) {
                // since we are keeping the previous merge item, no need
                // to add it internally, just send it to the consumer.
                if (removed) {
                    consumer.removeItem(previouslyWrittenItem, null);
                } else {
                    // don't need to compute but we need to write the item anyway since
                    // the item might be written due to the values file requiring (re)writing due
                    // to another res change
                    consumer.addItem(previouslyWrittenItem);
                }
            }
        } catch (ParserConfigurationException e) {
            throw MergingException.wrapException(e).build();
        }
    }

    @Nullable
    private ResourceItem getMergedItem(@NonNull String qualifiers, @NonNull String name) {
        Map<String, ResourceItem> map = mMergedItems.get(qualifiers);
        if (map != null) {
            return map.get(name);
        }

        return null;
    }

    @NonNull
    @Override
    protected String getAdditionalDataTagName() {
        return NODE_MERGED_ITEMS;
    }

    @Override
    protected void loadAdditionalData(@NonNull Node mergedItemsNode, boolean incrementalState) throws MergingException {
        // only load the merged item in incremental state.
        // In non incremental state, they will be recreated by the touched
        // items anyway.
        if (!incrementalState) {
            return;
        }

        // loop on the qualifiers.
        NodeList configurationList = mergedItemsNode.getChildNodes();

        for (int j = 0, n2 = configurationList.getLength(); j < n2; j++) {
            Node configuration = configurationList.item(j);

            if (configuration.getNodeType() != Node.ELEMENT_NODE ||
                    !NODE_CONFIGURATION.equals(configuration.getLocalName())) {
                continue;
            }

            // get the qualifier value.
            Attr qualifierAttr = (Attr) configuration.getAttributes().getNamedItem(
                    ATTR_QUALIFIER);
            if (qualifierAttr == null) {
                continue;
            }

            String qualifier = qualifierAttr.getValue();

            // get the resource items
            NodeList itemList = configuration.getChildNodes();

            for (int k = 0, n3 = itemList.getLength(); k < n3; k++) {
                Node itemNode = itemList.item(k);

                if (itemNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                ResourceItem item = getMergedResourceItem(itemNode, qualifier);
                if (item != null) {
                    addMergedItem(qualifier, item);
                }
            }
        }
    }

    @Override
    protected void writeAdditionalData(Document document, Node rootNode) {
        Node mergedItemsNode = document.createElement(getAdditionalDataTagName());
        rootNode.appendChild(mergedItemsNode);

        for (String qualifier : mMergedItems.keySet()) {
            Map<String, ResourceItem> itemMap = mMergedItems.get(qualifier);

            Node qualifierNode = document.createElement(NODE_CONFIGURATION);
            NodeUtils.addAttribute(document, qualifierNode, null, ATTR_QUALIFIER,
                    qualifier);

            mergedItemsNode.appendChild(qualifierNode);

            for (ResourceItem item : itemMap.values()) {
                Node adoptedNode = item.getDetailsXml(document);
                if (adoptedNode != null) {
                    qualifierNode.appendChild(adoptedNode);
                }
            }
        }
    }

    private void addMergedItem(@NonNull String qualifier, @NonNull ResourceItem item) {
        Map<String, ResourceItem> map = mMergedItems.get(qualifier);
        if (map == null) {
            map = Maps.newHashMap();
            mMergedItems.put(qualifier, map);
        }

        map.put(item.getName(), item);
    }

    /**
     * Returns a new ResourceItem object for a given node.
     * @param node the node representing the resource.
     * @return a ResourceItem object or null.
     */
    static MergedResourceItem getMergedResourceItem(@NonNull Node node, @NonNull String qualifiers)
            throws MergingException {
        ResourceType type = ValueResourceParser2.getType(node, null);
        String name = ValueResourceParser2.getName(node);

        if (name != null && type != null) {
            return new MergedResourceItem(name, type, qualifiers, node, null);
        }

        return null;
    }

    @Override
    public void addDataSet(ResourceSet resourceSet) {
        super.addDataSet(resourceSet);
    }


    /*
     * Overridden to clear the cache filter between runs. Building the cache is relatively cheap
     * and we're safer not reusing it between runs of mergeData.
     */
    @Override
    public void mergeData(@NonNull MergeConsumer<ResourceItem> consumer, boolean doCleanUp)
            throws MergingException {
        clearFilterCache();
        super.mergeData(consumer, doCleanUp);
    }


    @Override
    protected boolean filterAccept(@NonNull ResourceItem dataItem) {
        if (mRejectCache == null) {
            buildCache();
        }

        /*
         * We will accept all resources except those we explicitly know we can reject.
         */
        boolean accepted;
        if (mRejectCache.contains(dataItem.getKey())) {
            accepted = false;
        } else {
            accepted = true;
        }

        return accepted;
    }

    /**
     * Builds the reject filter cache.
     */
    private void buildCache() {
        mRejectCache = Sets.newHashSet();

        /*
         * Temporary cache. For each resource name, maps it to the best resource (min SDK and
         * resource item) found so far. Because we need to filter by folder configuration, we
         * maintain the best resource per folder configuration per resource.
         *
         * Only resource items whose min SDK is less
         * than or equal to minSdk will be included here as all others will be accepted.
         */
        Table<String, FolderConfiguration, Pair<Integer, ResourceItem>> itemCache =
                HashBasedTable.create();

        /*
         * Keys of resources we know we will accept. Only used to speed up resources with
         * duplicate keys.
         */
        Set<String> acceptCache = Sets.newHashSet();

        for (ResourceSet resourceSet : getDataSets()) {
            ListMultimap<String, ResourceItem> map = resourceSet.getDataMap();
            for (ResourceItem resourceItem : map.values()) {
                /*
                 * Resources in different libraries may end up with the same key.
                 */
                String resourceKey = resourceItem.getKey();

                if (acceptCache.contains(resourceKey) || mRejectCache.contains(resourceKey)) {
                    /*
                     * Second time we're seeing this resource. We already know whether to
                     * accept or reject it, so there's nothing to do.
                     */
                    continue;
                }

                if (resourceItem.getSourceType() != FileType.SINGLE_FILE) {
                    /*
                     * This is a resource that is contained in a file that has multiple items.
                     * We never filter these out.
                     */
                    acceptCache.add(resourceKey);
                    continue;
                }

                /*
                 * Compute what the resource's qualifier is. Keep the SDK version separate
                 * because we'll handle that in a special way.
                 */
                FolderConfiguration config = resourceItem.getConfiguration();
                FolderConfiguration qualifierWithoutSdk;

                int resourceMinSdk;
                if (!ResourceQualifier.isValid(config.getVersionQualifier())) {
                    resourceMinSdk = 0;
                    qualifierWithoutSdk = config;
                } else {
                    resourceMinSdk = config.getVersionQualifier().getVersion();
                    qualifierWithoutSdk = FolderConfiguration.copyOf(config);
                    qualifierWithoutSdk.removeQualifier(
                            qualifierWithoutSdk.getVersionQualifier());
                }

                if (resourceMinSdk > mMinSdk) {
                    /*
                     * We only filter resources that have min SDK <= minSdk because others
                     * cannot be guaranteed that won't be needed.
                     */
                    acceptCache.add(resourceKey);
                    continue;
                }

                /*
                 * Get the cache entry for the resource. resourceCacheId will contains a string
                 * which is unique for resource type / resource name. Resources with the same
                 * type or name but different qualifiers will have the same cache ID.
                 */
                String resourceCacheId = resourceItem.getType().getName()
                        + SdkConstants.RES_QUALIFIER_SEP + resourceItem.getName();
                Pair<Integer, ResourceItem> selectedResource = itemCache.get(resourceCacheId,
                        qualifierWithoutSdk);

                if (selectedResource == null) {
                    /*
                     * We have never found a resource for this folder configuration with an
                     * SDK lower than or equal to minSdk.
                     */
                    selectedResource = Pair.of(resourceMinSdk, resourceItem);
                    itemCache.put(resourceCacheId, qualifierWithoutSdk, selectedResource);
                    acceptCache.add(resourceKey);
                    continue;
                }

                if (selectedResource.getFirst() > resourceMinSdk) {
                    /*
                     * Cache has a better resource that is still lower than or equal to minSdk.
                     * The current resource will never be used so it will be added to the
                     * reject set.
                     */
                    mRejectCache.add(resourceKey);
                } else {
                    /*
                     * The current resource is better than or equal to the one in the cache
                     * and is still lower than or equal to minSdk. This means we will
                     * want to use the current one instead of the one we placed in the cache.
                     */
                    String removeKey = selectedResource.getSecond().getKey();
                    acceptCache.remove(removeKey);
                    mRejectCache.add(removeKey);
                    acceptCache.add(resourceKey);

                    selectedResource = Pair.of(resourceMinSdk, resourceItem);
                    itemCache.put(resourceCacheId, qualifierWithoutSdk, selectedResource);
                }
            }
        }
    }

    /**
     * Clears the filter cache.
     */
    private void clearFilterCache() {
        mRejectCache = null;
    }
}
