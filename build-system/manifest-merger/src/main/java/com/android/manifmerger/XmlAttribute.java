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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.w3c.dom.Attr;

/**
 * Defines an XML attribute inside a {@link XmlElement}.
 *
 * Basically a facade object on {@link Attr} objects with some added features like automatic
 * namespace handling, manifest merger friendly identifiers and smart replacement of shortened
 * full qualified class names using manifest node's package setting from the the owning Android's
 * document.
 */
public class XmlAttribute extends XmlNode {

    private static final String UNKNOWN_POSITION = "Unknown position";

    private final XmlElement mOwnerElement;
    private final Attr mXml;

    /**
     * Creates a new facade object to a {@link Attr} xml attribute in a
     * {@link XmlElement}.
     *
     * @param ownerElement the xml node object owning this attribute.
     * @param xml the xml definition of the attribute.
     */
    public XmlAttribute(@NonNull XmlElement ownerElement, @NonNull Attr xml) {
        this.mOwnerElement = Preconditions.checkNotNull(ownerElement);
        this.mXml = Preconditions.checkNotNull(xml);
        if (mOwnerElement.getType().isAttributePackageDependent(mXml)) {
            String value = mXml.getValue();
            String pkg = mOwnerElement.getDocument().getPackageName();
            // We know it's a shortened FQCN if it starts with a dot
            // or does not contain any dot.
            if (value != null && !value.isEmpty() &&
                    (value.indexOf('.') == -1 || value.charAt(0) == '.')) {
                if (value.charAt(0) == '.') {
                    value = pkg + value;
                } else {
                    value = pkg + '.' + value;
                }
                mXml.setValue(value);
            }
        }
    }

    /**
     * Returns the attribute's name, providing isolation from details like namespaces handling.
     */
    @Override
    public NodeName getName() {
        return XmlNode.unwrapName(mXml);
    }

    /**
     * Returns the attribute's value
     */
    public String getValue() {
        return mXml.getValue();
    }

    /**
     * Returns a display friendly identification string that can be used in machine and user
     * readable messages.
     */
    @Override
    public String getId() {
        // (Id of the parent element)@(my name)
        return mOwnerElement.getId() + "@" + mXml.getName();
    }

    @Override
    public PositionXmlParser.Position getPosition() {
        return mOwnerElement.getDocument().getNodePosition(this);
    }

    @Override
    public Attr getXml() {
        return mXml;
    }

    XmlElement getOwnerElement() {
        return mOwnerElement;
    }

    void mergeInHigherPriorityElement(XmlElement higherPriorityElement,
            MergingReport.Builder mergingReport) {

        // does the higher priority has the same attribute as myself ?
        Optional<XmlAttribute> higherPriorityAttribute =
                higherPriorityElement.getAttribute(getName());

        if (higherPriorityAttribute.isPresent()) {
            XmlAttribute myAttribute = higherPriorityAttribute.get();
            // this is conflict, depending on tools:replace, tools:strict
            // for now we keep the higher priority value and log it.
            String error = "Attribute " + myAttribute.getId()
                    + " is also present at " + printPosition()
                    + " use tools:replace to override it.";
            mergingReport.addWarning(error);
            mergingReport.getActionRecorder().recordAttributeAction(
                    this,
                    ActionRecorder.ActionType.REJECTED,
                    AttributeOperationType.REMOVE);
        } else {
            // it does not exist, verify if we are supposed to remove it.
            AttributeOperationType attributeOperationType =
                    higherPriorityElement.getAttributeOperationType(getName());

            // Strict being the default, and the attribute does not exist in the higher priority
            // element, we can safely merge it.
            if (attributeOperationType == AttributeOperationType.STRICT) {
                getName().addToNode(higherPriorityElement.getXml(), getValue());

                // and record the action.
                mergingReport.getActionRecorder().recordAttributeAction(
                        this,
                        ActionRecorder.ActionType.ADDED,
                        getOwnerElement().getAttributeOperationType(getName()));
            } else {
                // the only operation that make sense at this point is a remove.
                Preconditions.checkState(attributeOperationType == AttributeOperationType.REMOVE);
                // record the fact the attribute was actively removed.
                mergingReport.getActionRecorder().recordAttributeAction(
                        this,
                        ActionRecorder.ActionType.REJECTED,
                        AttributeOperationType.REMOVE);
            }
        }
    }

    /**
     * Returns the position of this attribute in the original xml file. This may return an invalid
     * location as this xml fragment does not exist in any xml file but is the temporary result
     * of the merging process.
     * @return a human readable position or {@link #UNKNOWN_POSITION}
     */
    public String printPosition() {
        PositionXmlParser.Position position = getPosition();
        if (position == null) {
            return UNKNOWN_POSITION;
        }
        StringBuilder stringBuilder = new StringBuilder();
        dumpPosition(stringBuilder, position);
        return stringBuilder.toString();
    }

    private void dumpPosition(StringBuilder stringBuilder, PositionXmlParser.Position position) {
        stringBuilder
                .append("(").append(position.getLine())
                .append(",").append(position.getColumn()).append(") ")
                .append(mOwnerElement.getDocument().getSourceLocation().print(true))
                .append(":").append(position.getLine());
    }
}
