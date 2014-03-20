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
import com.android.utils.ILogger;
import com.android.utils.PositionXmlParser;
import com.android.utils.PositionXmlParser.Position;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Xml {@link org.w3c.dom.Element} which is mergeable.
 *
 * A mergeable element can contains 3 types of children :
 * <ul>
 *     <li>a child element, which itself may or may not be mergeable.</li>
 *     <li>xml attributes which are related to the element.</li>
 *     <li>tools oriented attributes to trigger specific behaviors from the merging tool</li>
 * </ul>
 *
 * The two main responsibilities of this class is to be capable of comparing itself against
 * another instance of the same type as well as providing XML element merging capabilities.
 */
public class XmlElement extends XmlNode {

    /**
     * Local name used in node level tools instructions attribute
     */
    public static final String TOOLS_NODE_LOCAL_NAME = "node";      //$NON-NLS-1$

    private final Element mXml;
    private final ManifestModel.NodeTypes mType;
    private final XmlDocument mDocument;

    private final NodeOperationType mNodeOperationType;
    // list of non tools related attributes.
    private final ImmutableList<XmlAttribute> mAttributes;
    // map of all tools related attributes keyed by target attribute name
    private final Map<NodeName, AttributeOperationType> mAttributesOperationTypes;
    // list of mergeable children elements.
    private final ImmutableList<XmlElement> mMergeableChildren;

    public XmlElement(@NonNull Element xml, @NonNull XmlDocument document) {

        this.mXml = Preconditions.checkNotNull(xml);
        this.mType = ManifestModel.NodeTypes.fromXmlSimpleName(mXml.getNodeName());
        this.mDocument = Preconditions.checkNotNull(document);

        ImmutableMap.Builder<NodeName, AttributeOperationType> attributeOperationTypeBuilder =
                ImmutableMap.builder();
        ImmutableList.Builder<XmlAttribute> attributesListBuilder = ImmutableList.builder();
        NamedNodeMap namedNodeMap = mXml.getAttributes();
        NodeOperationType lastNodeOperationType = null;
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node attribute = namedNodeMap.item(i);
            if (SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {
                String instruction = attribute.getLocalName();
                if (instruction.equals(TOOLS_NODE_LOCAL_NAME)) {
                    // should we flag an error when there are more than one operation type on a node ?
                    lastNodeOperationType = NodeOperationType.valueOf(
                            SdkUtils.camelCaseToConstantName(
                                    attribute.getNodeValue()));
                } else {
                    AttributeOperationType attributeOperationType =
                            AttributeOperationType.valueOf(
                                    SdkUtils.xmlNameToConstantName(instruction));
                    for (String attributeName : Splitter.on(',').trimResults()
                            .split(attribute.getNodeValue())) {
                        if (attributeName.indexOf(XmlUtils.NS_SEPARATOR) == -1) {
                            String toolsPrefix = XmlUtils
                                    .lookupNamespacePrefix(getXml(), SdkConstants.TOOLS_URI,
                                            SdkConstants.ANDROID_NS_NAME, false);
                            // automatically provide the prefix.
                            attributeName = toolsPrefix + XmlUtils.NS_SEPARATOR + attributeName;
                        }
                        NodeName nodeName = XmlNode.fromXmlName(attributeName);
                        attributeOperationTypeBuilder.put(nodeName, attributeOperationType);
                    }
                }
            }
        }
        mAttributesOperationTypes = attributeOperationTypeBuilder.build();
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node attribute = namedNodeMap.item(i);
            if (!SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {

                XmlAttribute xmlAttribute = new XmlAttribute(
                        this, (Attr) attribute, mType.getAttributeModel(attribute.getLocalName()));
                attributesListBuilder.add(xmlAttribute);
            }

        }
        mNodeOperationType = lastNodeOperationType;
        mAttributes = attributesListBuilder.build();
        mMergeableChildren = initMergeableChildren();
    }

    /**
     * Returns true if this xml element's {@link com.android.manifmerger.ManifestModel.NodeTypes} is
     * the passed one.
     */
    public boolean isA(ManifestModel.NodeTypes type) {
        return this.mType == type;
    }

    @Override
    public Element getXml() {
        return mXml;
    }


    @Override
    public String getId() {
        return getKey() == null
                ? getName().toString()
                : getName().toString() + "#" + getKey();
    }

    @Override
    public NodeName getName() {
        return XmlNode.unwrapName(mXml);
    }

    /**
     * Returns the owning {@link com.android.manifmerger.XmlDocument}
     */
    public XmlDocument getDocument() {
        return mDocument;
    }

    /**
     * Returns this xml element {@link com.android.manifmerger.ManifestModel.NodeTypes}
     */
    public ManifestModel.NodeTypes getType() {
        return mType;
    }

    /**
     * Returns the unique key for this xml element within the xml file or null if there can be only
     * one element of this type.
     */
    @Nullable
    public String getKey() {
        return mType.getKey(this);
    }

    /**
     * Returns the list of attributes for this xml element.
     */
    public List<XmlAttribute> getAttributes() {
        return mAttributes;
    }

    /**
     * Returns the {@link com.android.manifmerger.XmlAttribute} for an attribute present on this
     * xml element, or {@link com.google.common.base.Optional#absent} if not present.
     * @param attributeName the attribute name.
     */
    public Optional<XmlAttribute> getAttribute(NodeName attributeName) {
        for (XmlAttribute xmlAttribute : mAttributes) {
            if (xmlAttribute.getName().equals(attributeName)) {
                return Optional.of(xmlAttribute);
            }
        }
        return Optional.absent();
    }

    /**
     * Get the node operation type as optionally specified by the user. If the user did not
     * explicitly specify how conflicting elements should be handled, a
     * {@link com.android.manifmerger.NodeOperationType#MERGE} will be returned.
     */
    public NodeOperationType getOperationType() {
        return mNodeOperationType != null
                ? mNodeOperationType
                : NodeOperationType.MERGE;
    }

    /**
     * Get the attribute operation type as optionally specified by the user. If the user did not
     * explicitly specify how conflicting attributes should be handled, a
     * {@link AttributeOperationType#STRICT} will be returned.
     */
    public AttributeOperationType getAttributeOperationType(NodeName attributeName) {
        return mAttributesOperationTypes.containsKey(attributeName)
                ? mAttributesOperationTypes.get(attributeName)
                : AttributeOperationType.STRICT;
    }

    public Collection<Map.Entry<NodeName, AttributeOperationType>> getAttributeOperations() {
        return mAttributesOperationTypes.entrySet();
    }


    @Override
    public PositionXmlParser.Position getPosition() {
        return mDocument.getNodePosition(this);
    }

    public void printPosition(StringBuilder stringBuilder) {
        PositionXmlParser.Position position = getPosition();
        if (position == null) {
            stringBuilder.append("Unknown position");
            return;
        }
        dumpPosition(stringBuilder, position);
    }

    public String printPosition() {
        StringBuilder stringBuilder = new StringBuilder();
        printPosition(stringBuilder);
        return stringBuilder.toString();
    }

    private void dumpPosition(StringBuilder stringBuilder, Position position) {
      stringBuilder
          .append("(").append(position.getLine())
          .append(",").append(position.getColumn()).append(") ")
          .append(mDocument.getSourceLocation().print(true))
          .append(":").append(position.getLine());
    }

    /**
     * Merge this xml element with a lower priority node.
     *
     * This is WIP.
     *
     * For now, attributes will be merged. If present on both xml elements, a warning will be
     * issued and the attribute merge will be rejected.
     *
     * @param lowerPriorityNode lower priority Xml element to merge with.
     * @param mergingReport the merging report to log errors and actions.
     */
    public void mergeWithLowerPriorityNode(
            XmlElement lowerPriorityNode,
            MergingReport.Builder mergingReport) {

        mergingReport.getLogger().info("Merging " + getId()
                + " with lower " + lowerPriorityNode.printPosition());

        if (getType().getMergeType() != MergeType.MERGE_CHILDREN_ONLY) {
            // merge attributes.
            for (XmlAttribute lowerPriorityAttribute : lowerPriorityNode.getAttributes()) {
                lowerPriorityAttribute.mergeInHigherPriorityElement(this, mergingReport);
            }
        }
        // merge children.
        mergeChildren(lowerPriorityNode, mergingReport);
    }

    public ImmutableList<XmlElement> getMergeableElements() {
        return mMergeableChildren;
    }

    public Optional<XmlElement> getNodeByTypeAndKey(
            ManifestModel.NodeTypes type,
            @Nullable String keyValue) {

        for (XmlElement xmlElement : mMergeableChildren) {
            if (xmlElement.isA(type) &&
                    (keyValue == null || keyValue.equals(xmlElement.getKey()))) {
                return Optional.of(xmlElement);
            }
        }
        return Optional.absent();
    }

    // merge this higher priority node with a lower priority node.
    public void mergeChildren(XmlElement lowerPriorityNode,
            MergingReport.Builder mergingReport) {

        ILogger logger = mergingReport.getLogger();
        // read all lower priority mergeable nodes.
        // if the same node is not defined in this document merge it in.
        // if the same is defined, so far, give an error message.
        for (XmlElement lowerPriorityChild : lowerPriorityNode.getMergeableElements()) {
            if (lowerPriorityChild.getType() != null &&
                    lowerPriorityChild.getType().getMergeType() == MergeType.IGNORE) {
                continue;
            }
            Optional<XmlElement> thisChildOptional =
                    getNodeByTypeAndKey(lowerPriorityChild.getType(),lowerPriorityChild.getKey());

            if (thisChildOptional.isPresent()) {
                // it's defined in both files
                logger.verbose(
                        lowerPriorityChild.getId() + " defined in both files...");

                XmlElement thisChild = thisChildOptional.get();

                if (thisChild.getType().getMergeType() == MergeType.CONFLICT) {
                    mergingReport.addError(String.format(
                            "Node %1$s cannot be present in more than one input file and it's "
                                    + "present at %2$s and %3$s",
                            thisChild.getType(),
                            thisChild.printPosition(),
                            lowerPriorityChild.printPosition()));
                    return;
                }

                // 2 nodes exist, 3 possibilities :
                //  higher priority one has a tools:node="remove", remove the low priority one
                //  higher priority one has a tools:node="replace", replace the low priority one
                //  higher priority one has a tools:node="strict", flag the error if not equals.
                switch(thisChild.getOperationType()) {
                    case MERGE:
                        // record the action
                        mergingReport.getActionRecorder().recordNodeAction(thisChild,
                                ActionRecorder.ActionType.MERGED, lowerPriorityChild);
                        // and perform the merge
                        thisChildOptional.get().mergeWithLowerPriorityNode(lowerPriorityChild,
                                mergingReport);
                        break;
                    case REMOVE:
                    case REPLACE:
                        // so far remove and replace and similar, the post validation will take
                        // care of removing this node in the case of REMOVE.

                        // just don't import the lower priority node and record the action.
                        mergingReport.getActionRecorder().recordNodeAction(thisChild,
                                ActionRecorder.ActionType.REJECTED, lowerPriorityChild);
                        break;
                    case STRICT:
                        Optional<String> compareMessage = thisChild.compareTo(lowerPriorityChild);
                        if (compareMessage.isPresent()) {
                            // flag error.
                            mergingReport.addError(String.format(
                                    "Node %1$s at %2$s is tagged with tools:node=\"strict\", yet "
                                            + "%3$s at %4$s is different : %5$s",
                                    thisChild.getId(),
                                    thisChild.printPosition(),
                                    lowerPriorityChild.getId(),
                                    lowerPriorityChild.printPosition(),
                                    compareMessage.get()));
                        }
                        break;
                    default:
                        mergingReport.getLogger().error(null /* throwable */,
                                "Unhandled node operation type %s", thisChild.getOperationType());
                        break;
                }
            } else {
                List<Node> comments = getLeadingComments(lowerPriorityChild.getXml());
                // only in the new file, just import it.
                Node node = mXml.getOwnerDocument().adoptNode(lowerPriorityChild.getXml());
                mXml.appendChild(node);

                // also adopt the child's comments if any.
                for (Node comment : comments) {
                    Node newComment = mXml.getOwnerDocument().adoptNode(comment);
                    mXml.insertBefore(newComment, node);
                }

                mergingReport.getActionRecorder().recordNodeAction(lowerPriorityChild,
                        ActionRecorder.ActionType.ADDED);
                logger.verbose("Adopted " + node);
            }
        }
    }

    /**
     * Compares this element with another {@link XmlElement} ignoring all attributes belonging to
     * the {@link com.android.SdkConstants#TOOLS_URI} namespace.
     *
     * @param otherNode the other element to compare against.
     * @return a {@link String} describing the differences between the two XML elements or
     * {@link Optional#absent()} if they are equals.
     */
    public Optional<String> compareTo(XmlElement otherNode) {

        // compare element names
        if (mXml.getNamespaceURI() != null) {
            if (!mXml.getLocalName().equals(otherNode.mXml.getLocalName())) {
                return Optional.of(
                        String.format("Element names do not match: %1$s versus %2$s",
                                mXml.getLocalName(),
                                otherNode.mXml.getLocalName()));
            }
            // compare element ns
            String thisNS = mXml.getNamespaceURI();
            String otherNS = otherNode.mXml.getNamespaceURI();
            if ((thisNS == null && otherNS != null)
                    || (thisNS != null && !thisNS.equals(otherNS))) {
                return Optional.of(
                        String.format("Element namespaces names do not match: %1$s versus %2$s",
                                thisNS, otherNS));
            }
        } else {
            if (!mXml.getNodeName().equals(otherNode.mXml.getNodeName())) {
                return Optional.of(String.format("Element names do not match: %1$s versus %2$s",
                        mXml.getNodeName(),
                        otherNode.mXml.getNodeName()));
            }
        }

        // compare attributes, we do it twice to identify added/missing elements in both lists.
        Optional<String> message = checkAttributes(this, otherNode);
        if (message.isPresent()) {
            return message;
        }
        message = checkAttributes(otherNode, this);
        if (message.isPresent()) {
            return message;
        }

        // compare children
        List<Node> expectedChildren = filterUninterestingNodes(mXml.getChildNodes());
        List<Node> actualChildren = filterUninterestingNodes(otherNode.mXml.getChildNodes());
        if (expectedChildren.size() != actualChildren.size()) {
            return Optional.of(String.format(
                    "%1$s: Number of children do not match up: expected %2$d versus %3$d at %4$s",
                    getId(),
                    expectedChildren.size(),
                    actualChildren.size(),
                    otherNode.printPosition()));
        }
        for (Node expectedChild : expectedChildren) {
            if (expectedChild.getNodeType() == Node.ELEMENT_NODE) {
                XmlElement expectedChildNode = new XmlElement((Element) expectedChild, mDocument);
                message = findAndCompareNode(otherNode, actualChildren, expectedChildNode);
                if (message.isPresent()) {
                    return message;
                }
            }
        }
        return Optional.absent();
    }

    private Optional<String> findAndCompareNode(
            XmlElement otherElement,
            List<Node> otherElementChildren,
            XmlElement childNode) {

        for (Node potentialNode : otherElementChildren) {
            if (potentialNode.getNodeType() == Node.ELEMENT_NODE) {
                XmlElement otherChildNode = new XmlElement((Element) potentialNode, mDocument);
                if (childNode.getType() == otherChildNode.getType()
                        && ((childNode.getKey() == null && otherChildNode.getKey() == null)
                        || childNode.getKey().equals(otherChildNode.getKey()))) {
                    return childNode.compareTo(otherChildNode);
                }
            }
        }
        return Optional.of(String.format("Child %1$s not found in document %2$s",
                childNode.getId(),
                otherElement.printPosition()));
    }

    private static List<Node> filterUninterestingNodes(NodeList nodeList) {
        List<Node> interestingNodes = new ArrayList<Node>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                Text t = (Text) node;
                if (!t.getData().trim().isEmpty()) {
                    interestingNodes.add(node);
                }
            } else if (node.getNodeType() != Node.COMMENT_NODE) {
                interestingNodes.add(node);
            }

        }
        return interestingNodes;
    }

    private static Optional<String> checkAttributes(
            XmlElement expected,
            XmlElement actual) {

        for (XmlAttribute expectedAttr : expected.getAttributes()) {
            XmlAttribute.NodeName attributeName = expectedAttr.getName();
            if (attributeName.isInNamespace(SdkConstants.TOOLS_URI)) {
                continue;
            }
            Optional<XmlAttribute> actualAttr = actual.getAttribute(attributeName);
            if (actualAttr.isPresent()) {
                if (!expectedAttr.getValue().equals(actualAttr.get().getValue())) {
                    return Optional.of(
                            String.format("Attribute %1$s do not match: %2$s versus %3$s at %4$s",
                                    expectedAttr.getId(),
                                    expectedAttr.getValue(),
                                    actualAttr.get().getValue(),
                                    actual.printPosition()));
                }
            } else {
                return Optional.of(String.format("Attribute %1$s not found at %2$s",
                        expectedAttr.getId(), actual.printPosition()));
            }
        }
        return Optional.absent();
    }

    private ImmutableList<XmlElement> initMergeableChildren() {
        ImmutableList.Builder<XmlElement> mergeableNodes = new ImmutableList.Builder<XmlElement>();
        NodeList nodeList = mXml.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                XmlElement xmlElement = new XmlElement((Element) node, mDocument);
                mergeableNodes.add(xmlElement);
            }
        }
        return mergeableNodes.build();
    }

    /**
     * Returns all leading comments in the source xml before the node to be adopted.
     * @param nodeToBeAdopted node that will be added as a child to this node.
     */
    private static List<Node> getLeadingComments(Node nodeToBeAdopted) {
        ImmutableList.Builder<Node> nodesToAdopt = new ImmutableList.Builder<Node>();
        Node previousSibling = nodeToBeAdopted.getPreviousSibling();
        while (previousSibling != null
                && (previousSibling.getNodeType() == Node.COMMENT_NODE
                || previousSibling.getNodeType() == Node.TEXT_NODE)) {
            // we really only care about comments.
            if (previousSibling.getNodeType() == Node.COMMENT_NODE) {
                nodesToAdopt.add(previousSibling);
            }
            previousSibling = previousSibling.getPreviousSibling();
        }
        return nodesToAdopt.build().reverse();
    }
}