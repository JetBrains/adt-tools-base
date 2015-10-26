/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.vectordrawable;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Strings;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represent the SVG file in an internal data structure as a tree.
 */
class SvgTree {
    private static Logger logger = Logger.getLogger(SvgTree.class.getSimpleName());

    public static final String SVG_WIDTH = "width";
    public static final String SVG_HEIGHT = "height";
    public static final String SVG_VIEW_BOX = "viewBox";

    private float w = -1;
    private float h = -1;
    private AffineTransform mRootTransform = new AffineTransform();
    private float[] viewBox;
    private float mScaleFactor = 1;

    private SvgGroupNode mRoot;
    private String mFileName;

    private ArrayList<String> mErrorLines = new ArrayList<String>();

    private boolean mHasLeafNode = false;


    public float getWidth() { return w; }
    public float getHeight() { return h; }
    public float getScaleFactor() { return mScaleFactor; }
    public void setHasLeafNode(boolean hasLeafNode) {
        mHasLeafNode = hasLeafNode;
    }

    public float[] getViewBox() { return viewBox; }

    /**
     * From the root, top down, pass the transformation (TODO: attributes)
     * down the children.
     */
    public void flattern() {
        mRoot.flattern(new AffineTransform());
    }

    public enum SvgLogLevel {
        ERROR,
        WARNING
    }

    public Document parse(File f) throws Exception {
        mFileName = f.getName();
        Document doc = PositionXmlParser.parse(new FileInputStream(f), false);
        return doc;
    }

    public void normalize() {
        // mRootTransform is always setup, now just need to apply the viewbox info into.
        mRootTransform.preConcatenate(new AffineTransform(1, 0, 0, 1, -viewBox[0], -viewBox[1]));
        transform(mRootTransform);

        logger.log(Level.FINE, "matrix=" + mRootTransform);
    }

    private void transform(AffineTransform rootTransform) {
        mRoot.transformIfNeeded(rootTransform);
    }

    public void dump(SvgGroupNode root) {
        logger.log(Level.FINE, "current file is :" + mFileName);
        root.dumpNode("");
    }

    public void setRoot(SvgGroupNode root) {
        mRoot = root;
    }

    @Nullable
    public SvgGroupNode getRoot() {
        return mRoot;
    }

    public void logErrorLine(String s, Node node, SvgLogLevel level) {
        if (!Strings.isNullOrEmpty(s)) {
            if (node != null) {
                SourcePosition position = getPosition(node);
                mErrorLines.add(level.name() + "@ line " + (position.getStartLine() + 1) +
                                " " + s + "\n");
            } else {
                mErrorLines.add(s);
            }
        }
    }

    /**
     * @return Error log. Empty string if there are no errors.
     */
    @NonNull
    public String getErrorLog() {
        StringBuilder errorBuilder = new StringBuilder();
        if (!mErrorLines.isEmpty()) {
            errorBuilder.append("In " + mFileName + ":\n");
        }
        for (String log : mErrorLines) {
            errorBuilder.append(log);
        }
        return errorBuilder.toString();
    }

    /**
     * @return true when there is at least one valid child.
     */
    public boolean getHasLeafNode() {
        return mHasLeafNode;
    }

    private SourcePosition getPosition(Node node) {
        return PositionXmlParser.getPosition(node);
    }

    public float getViewportWidth() {
        return (viewBox == null) ? -1 : viewBox[2];
    }

    public float getViewportHeight() { return (viewBox == null) ? -1 : viewBox[3]; }

    private enum SizeType {
        PIXEL,
        PERCENTAGE;
    }

    public void parseDimension(Node nNode) {
        NamedNodeMap a = nNode.getAttributes();
        int len = a.getLength();
        SizeType widthType = SizeType.PIXEL;
        SizeType heightType = SizeType.PIXEL;
        for (int i = 0; i < len; i++) {
            Node n = a.item(i);
            String name = n.getNodeName().trim();
            String value = n.getNodeValue().trim();
            int subStringSize = value.length();
            SizeType currentType = SizeType.PIXEL;
            if (value.endsWith("px")) {
                subStringSize = subStringSize - 2;
            } else if (value.endsWith("%")) {
                subStringSize = subStringSize - 1;
                currentType = SizeType.PERCENTAGE;
            }

            if (SVG_WIDTH.equals(name)) {
                w = Float.parseFloat(value.substring(0, subStringSize));
                widthType = currentType;
            } else if (SVG_HEIGHT.equals(name)) {
                h = Float.parseFloat(value.substring(0, subStringSize));
                heightType = currentType;
            } else if (SVG_VIEW_BOX.equals(name)) {
                viewBox = new float[4];
                String[] strbox = value.split(" ");
                for (int j = 0; j < viewBox.length; j++) {
                    viewBox[j] = Float.parseFloat(strbox[j]);
                }
            }
        }
        // If there is no viewbox, then set it up according to w, h.
        // From now on, viewport should be read from viewBox, and size should be from w and h.
        // w and h can be set to percentage too, in this case, set it to the viewbox size.
        if (viewBox == null && w > 0 && h > 0) {
            viewBox = new float[4];
            viewBox[2] = w;
            viewBox[3] = h;
        } else if ((w < 0 || h < 0) && viewBox != null) {
            w = viewBox[2];
            h = viewBox[3];
        }

        if (widthType == SizeType.PERCENTAGE && w > 0) {
            w = viewBox[2] * w / 100;
        }
        if (heightType == SizeType.PERCENTAGE && h > 0) {
            h = viewBox[3] * h / 100;
        }
    }
}
