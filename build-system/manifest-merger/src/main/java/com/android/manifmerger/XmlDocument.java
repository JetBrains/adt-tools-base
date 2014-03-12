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
import com.android.utils.PositionXmlParser;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.w3c.dom.Element;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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

    public boolean write(OutputStream outputStream) {

        try {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");         //$NON-NLS-1$
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");                   //$NON-NLS-1$
            tf.setOutputProperty(OutputKeys.INDENT, "yes");                       //$NON-NLS-1$
            tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",     //$NON-NLS-1$
                    "4");                                            //$NON-NLS-1$
            tf.transform(new DOMSource(getRootNode().getXml()), new StreamResult(outputStream));
            return true;
        } catch (TransformerException e) {
            return false;
        }
    }

    // merge this higher priority document with a higher priority document.
    public Optional<XmlDocument> merge(
            XmlDocument lowerPriorityDocument,
            MergingReport.Builder mergingReportBuilder) {

        mergingReportBuilder.getActionRecorder().recordDefaultNodeAction(getRootNode());

        getRootNode().mergeWithLowerPriorityNode(
                lowerPriorityDocument.getRootNode(), mergingReportBuilder);

        // force re-parsing as new nodes may have appeared.
        return Optional.of(reparse());
    }

    /**
     * Forces a re-parsing of the document
     * @return a new {@link com.android.manifmerger.XmlDocument} with up to date information.
     */
    public XmlDocument reparse() {
        return new XmlDocument(mPositionXmlParser, mSourceLocation, mRootElement);
    }

    public boolean compareXml(
            XmlDocument other,
            MergingReport.Builder mergingReport) throws Exception {

        return getRootNode().compareTo(other.getRootNode(), mergingReport);
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

    public String getPackageName() {
        // TODO: allow injection through invocation parameters.
        return mRootElement.getAttribute("package");
    }
}
