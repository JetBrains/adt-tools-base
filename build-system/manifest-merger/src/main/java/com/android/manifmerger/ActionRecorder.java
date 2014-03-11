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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.ILogger;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records all the actions taken by the merging tool.
 * <p>
 * Each action generates at least one {@link com.android.manifmerger.ActionRecorder.Record}
 * containing enough information to generate a machine or human readable report.
 * <p>
 *
 * The records are not organized in a temporal structure as the merging tool takes such decisions
 * but are keyed by xml elements and attributes. For each node (elements or attributes), a linked
 * list of actions that happened to the node is recorded to display all decisions that were made
 * for that particular node.
 * <p>
 *
 * This structure will permit displaying logs with co-located decisions records for each element,
 * for instance :
 * <pre>
 * activity:com.foo.bar.MyApp
 *     Added from manifest.xml:31
 *     Rejected from lib1_manifest.xml:65
 * </pre>
 *
 * <p>
 * Each record for a node (element or attribute) will contain the following metadata :
 * <p>
 *
 * <ul>
 *     <li>{@link com.android.manifmerger.ActionRecorder.ActionTarget} to identify the action
 *     taken by the merging tool.</li>
 *     <li>{@link com.android.manifmerger.ActionRecorder.ActionType} to identify whether the action
 *     applies to an attribute or an element.</li>
 *     <li>{@link com.android.manifmerger.ActionRecorder.ActionLocation} to identify the source xml
 *     location for the node.</li>
 * </ul>
 *
 * <p>
 * Elements will also contain:
 * <ul>
 *     <li>Element name : a name composed of the element type and its key.</li>
 *     <li>{@link NodeOperationType} the highest priority tool annotation justifying the merging
 *     tool decision.</li>
 * </ul>
 *
 * <p>
 * While attributes will have:
 * <ul>
 *     <li>element name</li>
 *     <li>attribute name : the namespace aware xml name</li>
 *     <li>{@link AttributeOperationType} the highest priority annotation justifying the merging
 *     tool decision.</li>
 * </ul>
 */
@Immutable
public class ActionRecorder {

    // TODO: i18n
    @VisibleForTesting
    static final String HEADER = "-- Merging decision tree log ---\n";

    // defines all the records for the merging tool activity, indexed by element name+key.
    // iterator should be ordered by the key insertion order.
    private final ImmutableMap<String, DecisionTreeRecord> mRecords;

    private ActionRecorder(ImmutableMap<String, DecisionTreeRecord> records) {
        mRecords = records;
    }

    /**
     * Defines all possible actions taken from the merging tool for an xml element or attribute.
     */
    enum ActionType {
        /**
         * The element was added into the resulting merged manifest.
         */
        ADDED,
        /**
         * The element was merged with another element into the resulting merged manifest.
         */
        MERGED,
        /**
         * The element was rejected.
         */
        REJECTED
    }

    enum ActionTarget {
        NODE,
        ATTRIBUTE
    }

    /**
     * Defines an action location which is composed of a pointer to the source location (e.g. a
     * file) and a position within that source location.
     */
    static final class ActionLocation {
        private final XmlLoader.SourceLocation mSourceLocation;
        private final PositionXmlParser.Position mPosition;

        public ActionLocation(@NonNull XmlLoader.SourceLocation sourceLocation,
                @NonNull PositionXmlParser.Position position) {
            mSourceLocation = Preconditions.checkNotNull(sourceLocation);
            mPosition = Preconditions.checkNotNull(position);
        }

        public PositionXmlParser.Position getPosition() {
            return mPosition;
        }

        public XmlLoader.SourceLocation getSourceLocation() {
            return mSourceLocation;
        }

        @Override
        public String toString() {
            return mSourceLocation.print(true) + ":" + mPosition.getLine();
        }
    }

    /**
     * Defines an abstract record contain common metadata for elements and attributes actions.
     */
    abstract static class Record {
        protected final ActionType mActionType;
        protected final ActionLocation mActionLocation;

        private Record(@NonNull ActionType actionType,
                @NonNull ActionLocation actionLocation) {
            mActionType = Preconditions.checkNotNull(actionType);
            mActionLocation = Preconditions.checkNotNull(actionLocation);
        }

        abstract ActionTarget getActionTarget();

        public ActionType getActionType() {
            return mActionType;
        }

        public ActionLocation getActionLocation() {
            return mActionLocation;
        }

        public void print(StringBuilder stringBuilder) {
            stringBuilder.append(mActionType)
                    .append(" from ")
                    .append(mActionLocation);
        }
    }

    /**
     * Defines a merging tool action for an xml element.
     */
    static class NodeRecord extends Record {

        private final NodeOperationType mNodeOperationType;

        private NodeRecord(@NonNull ActionType actionType,
                ActionLocation actionLocation,
                NodeOperationType nodeOperationType) {
            super(actionType, actionLocation);
            this.mNodeOperationType = Preconditions.checkNotNull(nodeOperationType);
        }

        @Override
        ActionTarget getActionTarget() {
            return ActionTarget.NODE;
        }
    }

    /**
     * Structure on how {@link Record}s are kept for an xml element.
     *
     * Each xml element should have an associated DecisionTreeRecord which keeps a list of
     * {@link com.android.manifmerger.ActionRecorder.NodeRecord} for all the node actions related
     * to this xml element.
     *
     * It will also contain a map indexed by attribute name on all the attribute actions related
     * to that particular attribute within the xml element.
     *
     */
    static class DecisionTreeRecord {
        // all other occurrences of the nodes decisions, in order of decisions.
        private final List<NodeRecord> mNodeRecords = new ArrayList<NodeRecord>();

        // all attributes decisions indexed by attribute name.
        private final Map<XmlNode.NodeName, List<AttributeRecord>> mAttributeRecords =
                new HashMap<XmlNode.NodeName, List<AttributeRecord>>();

        ImmutableList<NodeRecord> getNodeRecords() {
            return ImmutableList.copyOf(mNodeRecords);
        }

        ImmutableMap<XmlNode.NodeName, List<AttributeRecord>> getAttributesRecords() {
            return ImmutableMap.copyOf(mAttributeRecords);
        }

        List<AttributeRecord> getAttributeRecords(XmlNode.NodeName attributeName) {
            return ImmutableList.copyOf(mAttributeRecords.get(attributeName));
        }
    }

    /**
     * Defines a merging tool action for an xml attribute
     */
    static class AttributeRecord extends Record {

        // first in wins which should be fine, the first
        // operation type will be the highest priority one
        private final AttributeOperationType mOperationType;

        private AttributeRecord(
                @NonNull ActionType actionType,
                @NonNull ActionLocation actionLocation,
                @NonNull AttributeOperationType operationType) {
            super(actionType, actionLocation);
            this.mOperationType = Preconditions.checkNotNull(operationType);
        }

        @Override
        ActionTarget getActionTarget() {
            return ActionTarget.ATTRIBUTE;
        }

        public AttributeOperationType getOperationType() {
            return mOperationType;
        }
    }

    /**
     * Mutable package private recorder to record all operations.
     */
    static class Builder {

        // defines all the records for the merging tool activity, indexed by element name+key.
        // iterator should be ordered by the key insertion order. This is not a concurrent map so we
        // will need to guard multi-threaded access when adding/removing elements.
        @GuardedBy("this")
        private final Map<String, DecisionTreeRecord> mRecords =
                new LinkedHashMap<String, DecisionTreeRecord>();

        /**
         * When the first xml file is loaded, there is nothing to merge with, however, each xml
         * element and attribute added to the initial merged file need to be recorded.
         *
         * @param xmlElement xml element added to the initial merged document.
         */
        void recordDefaultNodeAction(XmlElement xmlElement) {
            String storageKey = xmlElement.getId();
            if (!mRecords.containsKey(storageKey)) {
                recordNodeAction(xmlElement, ActionType.ADDED);
                for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
                    AttributeOperationType attributeOperation = xmlElement
                            .getAttributeOperationType(xmlAttribute.getName());
                    recordAttributeAction(
                            xmlAttribute, ActionType.ADDED,
                            attributeOperation);
                }
                for (XmlElement childNode : xmlElement.getMergeableElements()) {
                    recordDefaultNodeAction(childNode);
                }
            }
        }

        /**
         * Record a node action taken by the merging tool.
         *
         * @param xmlElement the action's target xml element
         * @param actionType the action's type
         */
        synchronized void recordNodeAction(
                XmlElement xmlElement,
                ActionType actionType) {
            recordNodeAction(xmlElement, actionType, xmlElement);
        }

        /**
         * Record a node action taken by the merging tool.
         *
         * @param mergedElement the merged xml element
         * @param actionType    the action's type
         * @param targetElement the action's target when the action is rejected or replaced, it
         *                      indicates what is the element being rejected or replaced.
         */
        synchronized void recordNodeAction(
                XmlElement mergedElement,
                ActionType actionType,
                XmlElement targetElement) {

            String storageKey = mergedElement.getId();
            DecisionTreeRecord nodeDecisionTree = mRecords.get(storageKey);
            if (nodeDecisionTree == null) {
                nodeDecisionTree = new DecisionTreeRecord();
                mRecords.put(storageKey, nodeDecisionTree);
            }
            NodeRecord record = new NodeRecord(actionType,
                    new ActionLocation(
                            targetElement.getDocument().getSourceLocation(),
                            targetElement.getPosition()),
                    mergedElement.getOperationType());
            nodeDecisionTree.mNodeRecords.add(record);
        }

        /**
         * Records an attribute action taken by the merging tool
         *
         * @param attribute              the attribute in question.
         * @param actionType             the action's type
         * @param attributeOperationType the original tool annotation leading to the merging tool
         *                               decision.
         */
        synchronized void recordAttributeAction(
                XmlAttribute attribute,
                ActionType actionType,
                AttributeOperationType attributeOperationType) {

            XmlElement originElement = attribute.getOwnerElement();
            String storageKey = originElement.getId();
            DecisionTreeRecord nodeDecisionTree = mRecords.get(storageKey);
            // by now the node should have been added for this element.
            Preconditions.checkState(nodeDecisionTree != null);
            List<AttributeRecord> attributeRecords =
                    nodeDecisionTree.mAttributeRecords.get(attribute.getName());
            if (attributeRecords == null) {
                attributeRecords = new ArrayList<AttributeRecord>();
                nodeDecisionTree.mAttributeRecords.put(attribute.getName(), attributeRecords);
            }
            AttributeRecord attributeRecord = new AttributeRecord(
                    actionType,
                    new ActionLocation(
                            originElement.getDocument().getSourceLocation(), 
                            attribute.getPosition()),
                    attributeOperationType);
            attributeRecords.add(attributeRecord);
        }

        ActionRecorder build() {
            return new ActionRecorder(new ImmutableMap.Builder<String, DecisionTreeRecord>()
                    .putAll(mRecords).build());
        }
    }

    /**
     * Returns all recorded activities from the merging tool as map indexed by
     * {@link XmlElement#getId()}. For each element, a
     * {@link com.android.manifmerger.ActionRecorder.DecisionTreeRecord} represents all decisions
     * made for that particular XML element, including the element's attributes
     * {@link com.android.manifmerger.ActionRecorder.DecisionTreeRecord#getAttributesRecords()}
     *
     * @return a map of {@link com.android.manifmerger.ActionRecorder.DecisionTreeRecord} indexed
     * by {@link XmlElement#getId()}
     */
    ImmutableMap<String, DecisionTreeRecord> getAllRecords() {
        return mRecords;
    }

    /**
     * Initial dump of the merging tool actions, need to be refined and spec'ed out properly.
     * @param logger logger to log to at INFO level.
     */
    void log(ILogger logger) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(HEADER);
        for (Map.Entry<String, DecisionTreeRecord> record : mRecords.entrySet()) {
            stringBuilder.append(record.getKey()).append("\n");
            for (NodeRecord nodeRecord : record.getValue().mNodeRecords) {
                nodeRecord.print(stringBuilder);
                stringBuilder.append("\n");
            }
            for (Map.Entry<XmlNode.NodeName, List<AttributeRecord>> attributeRecords :
                    record.getValue().mAttributeRecords.entrySet()) {
                stringBuilder.append("\t").append(attributeRecords.getKey());
                for (AttributeRecord attributeRecord : attributeRecords.getValue()) {
                    stringBuilder.append("\t\t");
                    attributeRecord.print(stringBuilder);
                    stringBuilder.append("\n");
                }

            }
        }
        logger.info(stringBuilder.toString());
    }
}
