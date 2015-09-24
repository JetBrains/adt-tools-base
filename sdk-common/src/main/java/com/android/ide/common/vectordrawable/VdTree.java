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

import com.android.ide.common.util.AssetUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used to represent the whole VectorDrawable XML file's tree.
 */
class VdTree {
    private static Logger logger = Logger.getLogger(VdTree.class.getSimpleName());

    private static final String SHAPE_VECTOR = "vector";
    private static final String SHAPE_PATH = "path";
    private static final String SHAPE_GROUP = "group";

    private VdGroup mRootGroup = new VdGroup();

    private float mBaseWidth = 1;
    private float mBaseHeight = 1;
    private float mPortWidth = 1;
    private float mPortHeight = 1;
    private float mRootAlpha = 1;

    private final boolean DBG_PRINT_TREE = false;

    private static final String INDENT = "  ";

    /*package*/ float getBaseWidth(){
        return mBaseWidth;
    }

    /*package*/ float getBaseHeight(){
        return mBaseHeight;
    }

    /*package*/ float getPortWidth(){
        return mPortWidth;
    }

    /*package*/ float getPortHeight(){
        return mPortHeight;
    }

    private void drawTree(Graphics2D g, int w, int h) {
        float scaleX = w / mPortWidth;
        float scaleY = h / mPortHeight;

        AffineTransform rootMatrix = new AffineTransform(); // identity

        mRootGroup.draw(g, rootMatrix, scaleX, scaleY);
    }

    /**
     * Draw the VdTree into an image.
     * If the root alpha is less than 1.0, then draw into a temporary image,
     * then draw into the result image applying alpha blending.
     */
    public void drawIntoImage(BufferedImage image) {
        Graphics2D gFinal = (Graphics2D) image.getGraphics();
        int width = image.getWidth();
        int height = image.getHeight();
        gFinal.setColor(new Color(255, 255, 255, 0));
        gFinal.fillRect(0, 0, width, height);

        float rootAlpha = mRootAlpha;
        if (rootAlpha < 1.0) {
            BufferedImage alphaImage = AssetUtil.newArgbBufferedImage(width, height);
            Graphics2D gTemp = (Graphics2D)alphaImage.getGraphics();
            drawTree(gTemp, width, height);
            gFinal.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rootAlpha));
            gFinal.drawImage(alphaImage, 0, 0, null);
            gTemp.dispose();
        } else {
            drawTree(gFinal, width, height);
        }
        gFinal.dispose();
    }

    public void parse(Document doc) {
        NodeList rootNodeList = doc.getElementsByTagName(SHAPE_VECTOR);
        assert rootNodeList.getLength() == 1;
        Node rootNode = rootNodeList.item(0);

        parseRootNode(rootNode);
        parseTree(rootNode, mRootGroup);

        if (DBG_PRINT_TREE) {
            debugPrintTree(0, mRootGroup);
        }
    }

    private void parseTree(Node currentNode, VdGroup currentGroup) {
        NodeList childrenNodes = currentNode.getChildNodes();
        int length = childrenNodes.getLength();
        for (int i = 0; i < length; i ++) {
            Node child = childrenNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (SHAPE_GROUP.equals(child.getNodeName())) {
                    VdGroup newGroup = parseGroupAttributes(child.getAttributes());
                    currentGroup.add(newGroup);
                    parseTree(child, newGroup);
                } else if (SHAPE_PATH.equals(child.getNodeName())) {
                    VdPath newPath = parsePathAttributes(child.getAttributes());
                    currentGroup.add(newPath);
                }
            }
        }
    }

    private void debugPrintTree(int level, VdGroup mRootGroup) {
        int len = mRootGroup.size();
        if (len == 0) {
            return;
        }
        String prefix = "";
        for (int i = 0; i < level; i ++) {
            prefix += INDENT;
        }
        ArrayList<VdElement> children = mRootGroup.getChildren();
        for (int i = 0; i < len; i ++) {
            VdElement child = children.get(i);
            System.out.println(prefix  + child.toString());
            if (child.isGroup()) {
                // TODO: print group info
                debugPrintTree(level + 1, (VdGroup) child);
            }
        }
    }

    private void parseRootNode(Node rootNode) {
        if (rootNode.hasAttributes()) {
            parseSize(rootNode.getAttributes());
        }
    }

    private void parseSize(NamedNodeMap attributes) {

        Pattern pattern = Pattern.compile("^\\s*(\\d+(\\.\\d+)*)\\s*([a-zA-Z]+)\\s*$");

        int len = attributes.getLength();

        for (int i = 0; i < len; i++) {
            String name = attributes.item(i).getNodeName();
            String value = attributes.item(i).getNodeValue();
            Matcher matcher = pattern.matcher(value);
            float size = 0;
            if (matcher.matches()) {
                float v = Float.parseFloat(matcher.group(1));
                size = v;
            }

            // TODO: Extract dimension units like px etc. Right now all are treated as "dp".
            if ("android:width".equals(name)) {
                mBaseWidth = size;
            } else if ("android:height".equals(name)) {
                mBaseHeight = size;
            } else if ("android:viewportWidth".equals(name)) {
                mPortWidth = Float.parseFloat(value);
            } else if ("android:viewportHeight".equals(name)) {
                mPortHeight = Float.parseFloat(value);
            } else if ("android:alpha".equals(name)) {
                mRootAlpha = Float.parseFloat(value);
            }
        }
    }

    private VdPath parsePathAttributes(NamedNodeMap attributes) {
        VdPath vgPath = new VdPath();
        vgPath.parseAttributes(attributes);
        return vgPath;
    }

    private VdGroup parseGroupAttributes(NamedNodeMap attributes) {
        VdGroup vgGroup = new VdGroup();
        vgGroup.parseAttributes(attributes);
        return vgGroup;
    }
}
