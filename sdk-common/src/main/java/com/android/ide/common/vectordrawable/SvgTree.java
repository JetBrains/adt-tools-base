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

    public float w = -1;
    public float h = -1;
    public AffineTransform mRootTransform = new AffineTransform();
    public float[] viewBox;
    public float mScaleFactor = 1;

    private SvgGroupNode mRoot;
    private String mFileName;

    private ArrayList<String> mErrorLines = new ArrayList<String>();

    private boolean mHasLeafNode = false;

    public void setHasLeafNode(boolean hasLeafNode) {
        mHasLeafNode = hasLeafNode;
    }

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

}
