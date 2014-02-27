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
    public static final String TOOLS_NODE_LOCAL_NAME = "node";

    private final Element mXml;
    private final ManifestModel.NodeTypes mType;
    private final XmlDocument mDocument;

    private final NodeOperationType mNodeOperationType;
    // list of non tools related attributes.
    private final ImmutableList<XmlAttribute> mAttributes;
    // map of all tools related attributes keyed by target attribute name
    private final Map<String, AttributeOperationType> mAttributesOperationTypes;
    // list of mergeable children elements.
    private final ImmutableList<XmlElement> mMergeableChildren;

    public XmlElement(@NonNull Element xml, @NonNull XmlDocument document) {

        this.mXml = Preconditions.checkNotNull(xml);
        this.mType = ManifestModel.NodeTypes.fromXmlSimpleName(mXml.getNodeName());
        this.mDocument = Preconditions.checkNotNull(document);

        ImmutableMap.Builder<String, AttributeOperationType> attributeOperationTypeBuilder =
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
                            XmlUtils.camelCaseToConstantName(
                                    attribute.getNodeValue()));
                } else {
                    AttributeOperationType attributeOperationType =
                            AttributeOperationType.valueOf(
                                    XmlUtils.xmlNameToConstantName(instruction));
                    for (String attributeName : Splitter.on(',').trimResults()
                            .split(attribute.getNodeValue())) {
                        attributeOperationTypeBuilder.put(attributeName, attributeOperationType);
                    }
                }
            }
        }
        mAttributesOperationTypes = attributeOperationTypeBuilder.build();
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node attribute = namedNodeMap.item(i);
            if (!SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {

                XmlAttribute xmlAttribute = new XmlAttribute(this, (Attr) attribute);
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
     * {@link com.android.manifmerger.NodeOperationType#STRICT} will be returned.
     */
    public NodeOperationType getOperationType() {
        return mNodeOperationType != null
                ? mNodeOperationType
                : NodeOperationType.STRICT;
    }

    public AttributeOperationType getAttributeOperationType(String attributeName) {
        return mAttributesOperationTypes.containsKey(attributeName)
                ? mAttributesOperationTypes.get(attributeName)
                : AttributeOperationType.STRICT;
    }

    public Collection<AttributeOperationType> getAttributeOperations() {
        return mAttributesOperationTypes.values();
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
     * @param lowerPriorityNode
     * @param mergingReport
     */
    public void mergeWithLowerPriorityNode(
            XmlElement lowerPriorityNode,
            MergingReport.Builder mergingReport) {

        mergingReport.getLogger().info("Merging " + getId()
                + " with lower " + lowerPriorityNode.printPosition());

        // merge attributes.
        for (XmlAttribute lowerPriorityAttribute : lowerPriorityNode.getAttributes()) {
            Optional<XmlAttribute> myOptionalAttribute = getAttribute(lowerPriorityAttribute.getName());
            if (myOptionalAttribute.isPresent()) {
                XmlAttribute myAttribute = myOptionalAttribute.get();
                // this is conflict, depending on tools:replace, tools:strict
                // for now we keep the higher priority value and log it.
                String error = "Attribute " + myAttribute.getId()
                        + " is also present at " + lowerPriorityAttribute.printPosition()
                        + " use tools:replace to override it.";
                mergingReport.addWarning(error);
                mergingReport.getActionRecorder().recordAttributeAction(lowerPriorityNode,
                        myAttribute.getName(),
                        ActionRecorder.ActionType.REJECTED,
                        AttributeOperationType.REMOVE);
            } else {
                // cool, does not exist, just add it.
                // TODO: handle tools:remove and other user specified merging directions when
                // merging attributes from lower priority files
                lowerPriorityAttribute.getName().addToNode(mXml, lowerPriorityAttribute.getValue());
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
    public boolean mergeChildren(XmlElement lowerPriorityNode,
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
            Optional<XmlElement> thisChildNodeOptional =
                    getNodeByTypeAndKey(lowerPriorityChild.getType(),lowerPriorityChild.getKey());

            if (thisChildNodeOptional.isPresent()) {
                // it's defined in both files
                logger.verbose(
                        lowerPriorityChild.getXml().toString() + " defined in both files...");
                XmlElement thisChildNode = thisChildNodeOptional.get();
                // are we merging no matter what or the two nodes equals ?
                if (thisChildNode.getType().getMergeType() != MergeType.MERGE
                        && !thisChildNode.compareTo(lowerPriorityChild, mergingReport)) {

                    String info = "Node abandoned : " + lowerPriorityChild.printPosition();
                    mergingReport.addError(info);
                    mergingReport.getActionRecorder().recordNodeAction(
                            thisChildNode, ActionRecorder.ActionType.REJECTED, lowerPriorityChild);
                    return false;
                }
                // this is a sophisticated xml element which attributes and children need be
                // merged modulo tools instructions.
                thisChildNodeOptional.get().mergeWithLowerPriorityNode(lowerPriorityChild, mergingReport);
            } else {
                List<Node> comments = getLeadingComments(lowerPriorityChild.getXml());
                // only in the new file, just import it.
                // TODO:need to check the prefixes...
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
        return true;
    }

    public boolean compareTo(XmlElement otherNode, MergingReport.Builder mergingReport) {

        ILogger logger = mergingReport.getLogger();
        // compare element names
        if (mXml.getNamespaceURI() != null) {
            if (!mXml.getLocalName().equals(otherNode.mXml.getLocalName())) {
                logger.error(null /* throwable */, "Element names do not match: "
                        + mXml.getLocalName() + " "
                        + otherNode.mXml.getLocalName());
                return false;
            }
            // compare element ns
            String thisNS = mXml.getNamespaceURI();
            String otherNS = otherNode.mXml.getNamespaceURI();
            if ((thisNS == null && otherNS != null)
                    || (thisNS != null && !thisNS.equals(otherNS))) {
                logger.error(null /* throwable */, "Element namespaces names do not match: "
                        + thisNS + " " + otherNS);
                return false;
            }
        } else {
            if (!mXml.getNodeName().equals(otherNode.mXml.getNodeName())) {
                logger.error(null /* nullable */, "Element names do not match: "
                        + mXml.getNodeName() + " "
                        + otherNode.mXml.getNodeName());
                return false;
            }
        }

        // compare attributes, we do it twice to identify added/missing elements in both lists.
        if (!checkAttributes(this, otherNode, mergingReport)) {
            return false;
        }
        if (!checkAttributes(otherNode, this, mergingReport)) {
            return false;
        }

        // compare children
        List<Node> expectedChildren = filterUninterestingNodes(mXml.getChildNodes());
        List<Node> actualChildren = filterUninterestingNodes(otherNode.mXml.getChildNodes());
        if (expectedChildren.size() != actualChildren.size()) {
            // TODO: i18n
            String error = getId() + ": Number of children do not match up: "
                + expectedChildren.size() + " versus " + actualChildren.size()
                + " in " + otherNode.getId();
            mergingReport.addError(error);
            return false;
        }
        for (Node expectedChild : expectedChildren) {
            if (expectedChild.getNodeType() == Node.ELEMENT_NODE) {
                XmlElement expectedChildNode = new XmlElement((Element) expectedChild, mDocument);
                if (!findAndCompareNode(actualChildren, expectedChildNode, mergingReport)) {
                    // bail out.
                    return false;
                }
            }
        }
        return true;
    }

    private boolean findAndCompareNode(
            List<Node> actualChildren,
            XmlElement childNode,
            MergingReport.Builder mergingReport) {

        for (Node potentialNode : actualChildren) {
            if (potentialNode.getNodeType() == Node.ELEMENT_NODE) {
                XmlElement otherChildNode = new XmlElement((Element) potentialNode, mDocument);
                if (childNode.getType() == otherChildNode.getType()
                        && ((childNode.getKey() == null && otherChildNode.getKey() == null)
                        || childNode.getKey().equals(otherChildNode.getKey()))) {
                    return childNode.compareTo(otherChildNode, mergingReport);
                }
            }
        }
        return false;
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

    private static boolean checkAttributes(
            XmlElement expected,
            XmlElement actual,
            MergingReport.Builder mergingReport) {

        for (XmlAttribute expectedAttr : expected.getAttributes()) {
            XmlAttribute.NodeName attributeName = expectedAttr.getName();
            if (attributeName.isInNamespace(SdkConstants.TOOLS_URI)) {
                continue;
            }
            Optional<XmlAttribute> actualAttr = actual.getAttribute(attributeName);
            if (actualAttr.isPresent()) {
                if (!expectedAttr.getValue().equals(actualAttr.get().getValue())) {
                    mergingReport.addError(
                            "Attribute " + expectedAttr.getId()
                                    + " do not match:" + expectedAttr.getValue()
                                    + " versus " + actualAttr.get().getValue() + " at " + actual.printPosition());
                    return false;
                }
            } else {
                mergingReport.addError(
                        "Attribute " + expectedAttr.getId() + " not found at " + actual.printPosition());
                return false;
            }
        }
        return true;
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
    private List<Node> getLeadingComments(Node nodeToBeAdopted) {
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