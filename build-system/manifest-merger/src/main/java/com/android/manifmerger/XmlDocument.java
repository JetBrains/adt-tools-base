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
import com.android.annotations.Nullable;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.concurrent.atomic.AtomicReference;


/**
 * Represents a loaded xml document.
 *
 * Has pointers to the root {@link XmlElement} element and provides services to persist the document
 * to an external format. Also provides abilities to be merged with other
 * {@link com.android.manifmerger.XmlDocument} as well as access to the line numbers for all
 * document's xml elements and attributes.
 *
 */
public class XmlDocument {

    private final Element mRootElement;
    // this is initialized lazily to avoid un-necessary early parsing.
    private final AtomicReference<XmlElement> mRootNode = new AtomicReference<XmlElement>(null);
    private final PositionXmlParser mPositionXmlParser;
    private final XmlLoader.SourceLocation mSourceLocation;

    public XmlDocument(@NonNull PositionXmlParser positionXmlParser,
            @NonNull XmlLoader.SourceLocation sourceLocation,
            @NonNull Element element) {
        this.mPositionXmlParser = Preconditions.checkNotNull(positionXmlParser);
        this.mSourceLocation = Preconditions.checkNotNull(sourceLocation);
        this.mRootElement = Preconditions.checkNotNull(element);
    }

    /**
     * Returns a pretty string representation of this document.
     */
    public String prettyPrint() {
        return XmlPrettyPrinter.prettyPrint(
                getXml(),
                XmlFormatPreferences.defaults(),
                XmlFormatStyle.get(getRootNode().getXml()),
                null, /* endOfLineSeparator */
                false /* endWithNewLine */);
    }

    /**
     * merge this higher priority document with a higher priority document.
     * @param lowerPriorityDocument the lower priority document to merge in.
     * @param mergingReportBuilder the merging report to record errors and actions.
     * @return a new merged {@link com.android.manifmerger.XmlDocument} or
     * {@link Optional#absent()} if there were errors during the merging activities.
     */
    public Optional<XmlDocument> merge(
            XmlDocument lowerPriorityDocument,
            MergingReport.Builder mergingReportBuilder) {

        mergingReportBuilder.getActionRecorder().recordDefaultNodeAction(getRootNode());

        getRootNode().mergeWithLowerPriorityNode(
                lowerPriorityDocument.getRootNode(), mergingReportBuilder);

        // force re-parsing as new nodes may have appeared.
        return mergingReportBuilder.hasErrors()
                ? Optional.<XmlDocument>absent()
                : Optional.of(reparse());
    }

    /**
     * Forces a re-parsing of the document
     * @return a new {@link com.android.manifmerger.XmlDocument} with up to date information.
     */
    public XmlDocument reparse() {
        return new XmlDocument(mPositionXmlParser, mSourceLocation, mRootElement);
    }

    /**
     * Compares this document to another {@link com.android.manifmerger.XmlDocument} ignoring all
     * attributes belonging to the {@link com.android.SdkConstants#TOOLS_URI} namespace.
     *
     * @param other the other document to compare against.
     * @return  a {@link String} describing the differences between the two XML elements or
     * {@link Optional#absent()} if they are equals.
     */
    public Optional<String> compareTo(XmlDocument other) {
        return getRootNode().compareTo(other.getRootNode());
    }

    /**
     * Returns a {@link org.w3c.dom.Node} position automatically offsetting the line and number
     * columns by one (for PositionXmlParser, document starts at line 0, however for the common
     * understanding, document should start at line 1).
     */
    PositionXmlParser.Position getNodePosition(XmlNode node) {
        final PositionXmlParser.Position position =  mPositionXmlParser.getPosition(node.getXml());
        if (position == null) {
            return null;
        }
        return new PositionXmlParser.Position() {
            @Nullable
            @Override
            public PositionXmlParser.Position getEnd() {
                return position.getEnd();
            }

            @Override
            public void setEnd(@NonNull PositionXmlParser.Position end) {
                position.setEnd(end);
            }

            @Override
            public int getLine() {
                return position.getLine() + 1;
            }

            @Override
            public int getOffset() {
                return position.getOffset();
            }

            @Override
            public int getColumn() {
                return position.getColumn() +1;
            }
        };
    }

    public XmlLoader.SourceLocation getSourceLocation() {
        return mSourceLocation;
    }

    public synchronized XmlElement getRootNode() {
        if (mRootNode.get() == null) {
            this.mRootNode.set(new XmlElement(mRootElement, this));
        }
        return mRootNode.get();
    }

    public Optional<XmlElement> getNodeByTypeAndKey(
            ManifestModel.NodeTypes type,
            @Nullable String keyValue) {

        return getRootNode().getNodeByTypeAndKey(type, keyValue);
    }

    public String getPackageName() {
        // TODO: allow injection through invocation parameters.
        return mRootElement.getAttribute("package");
    }

    public Document getXml() {
        return mRootElement.getOwnerDocument();
    }
}
