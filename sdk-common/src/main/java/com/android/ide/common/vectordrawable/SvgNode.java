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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parent class for a SVG file's node, can be either group or leave element.
 */
abstract class SvgNode {
    private static Logger logger = Logger.getLogger(SvgNode.class.getSimpleName());

    private static final String TRANSFORM_TAG = "transform";

    private static final String MATRIX_ATTRIBUTE = "matrix";
    private static final String TRANSLATE_ATTRIBUTE = "translate";
    private static final String ROTATE_ATTRIBUTE = "rotate";
    private static final String SCALE_ATTRIBUTE = "scale";
    private static final String SKEWX_ATTRIBUTE = "skewX";
    private static final String SKEWY_ATTRIBUTE = "skewY";

    protected String mName;
    // Keep a reference to the tree in order to dump the error log.
    private SvgTree mSvgTree;
    // Use document node to get the line number for error reporting.
    private Node mDocumentNode;

    // Key is the attributes for vector drawable, and the value is the converted from SVG.
    protected Map<String, String> mVdAttributesMap = new HashMap<String, String>();
    // If mLocalTransform is identity, it is the same as not having any transformation.
    protected AffineTransform mLocalTransform = new AffineTransform();

    // During the flattern() operatation, we need to merge the transformation from top down.
    // This is the stacked transformation. And this will be used for the path data transform().
    protected AffineTransform mStackedTransform = new AffineTransform();

    /**
     * While parsing the translate() rotate() ..., update the <code>mLocalTransform</code>
     */
    public SvgNode(SvgTree svgTree, Node node, String name) {
        mName = name;
        mSvgTree = svgTree;
        mDocumentNode = node;
        // Parse and generate a presentation map.
        NamedNodeMap a = node.getAttributes();
        int len = a.getLength();

        for (int itemIndex = 0; itemIndex < len; itemIndex++) {
            Node n = a.item(itemIndex);
            String nodeName = n.getNodeName();
            String nodeValue = n.getNodeValue();
            // TODO: Handle style here. Refer to Svg2Vector::addStyleToPath().
            if (Svg2Vector.presentationMap.containsKey(nodeName)) {
                fillPresentationAttributes(nodeName, nodeValue, logger);
            }

            if (TRANSFORM_TAG.equals(nodeName)) {
                logger.log(Level.FINE, nodeName + " " + nodeValue);
                parseLocalTransform(nodeValue);
            }
        }
    }

    private void parseLocalTransform(String nodeValue) {
        // We separate the string into multiple parts and look like this:
        // "translate" "30" "rotate" "4.5e1  5e1  50"
        nodeValue = nodeValue.replaceAll(",", " ");
        String[] matrices = nodeValue.split("\\(|\\)");
        AffineTransform parsedTransform;
        for (int i = 0; i < matrices.length -1; i += 2) {
            parsedTransform = parseOneTransform(matrices[i].trim(), matrices[i+1].trim());
            if (parsedTransform != null) {
                mLocalTransform.concatenate(parsedTransform);
            }
        }
    }

    @NonNull
    private AffineTransform parseOneTransform(String type, String data) {
        float[] numbers = getNumbers(data);
        int numLength = numbers.length;
        AffineTransform parsedTranform = new AffineTransform();

        if (MATRIX_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 6) {
                return null;
            }
            parsedTranform.setTransform(numbers[0], numbers[1], numbers[2],
                                        numbers[3], numbers[4], numbers[5]);
        } else if (TRANSLATE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 2) {
                return null;
            }
            // Default translateY is 0
            parsedTranform.translate(numbers[0], numLength == 2 ? numbers[1] : 0);
        } else if (SCALE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 2) {
                return null;
            }
            // Default scaleY == scaleX
            parsedTranform.scale(numbers[0], numLength == 2 ? numbers[1] : numbers[0]);
        } else if (ROTATE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 3) {
                return null;
            }
            parsedTranform.rotate(Math.toRadians(numbers[0]),
                                  numLength == 3 ? numbers[1] : 0,
                                  numLength == 3 ? numbers[2] : 0);
        } else if (SKEWX_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1) {
                return null;
            }
            // Note that Swing is pass the shear value directly to the matrix as m01 or m10,
            // while SVG is using tan(a) in the matrix and a is in radians.
            parsedTranform.shear(Math.tan(Math.toRadians(numbers[0])), 0);
        } else if (SKEWY_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1) {
                return null;
            }
            parsedTranform.shear(0, Math.tan(Math.toRadians(numbers[0])));
        }
        return parsedTranform;
    }

    private float[] getNumbers(String data) {
        String[] numbers = data.split("\\s+");
        int len = numbers.length;
        if (len == 0) {
            return null;
        }

        float[] results = new float[len];
        for (int i = 0; i < len; i ++) {
            results[i] = Float.parseFloat(numbers[i]);
        }
        return results;
    }

    protected SvgTree getTree() {
        return mSvgTree;
    }

    public String getName() {
        return mName;
    }

    public Node getDocumentNode() {
        return mDocumentNode;
    }

    /**
     * dump the current node's debug info.
     */
    public abstract void dumpNode(String indent);

    /**
     * Write the Node content into the VectorDrawable's XML file.
     */
    public abstract void writeXML(OutputStreamWriter writer) throws IOException;

    /**
     * @return true the node is a group node.
     */
    public abstract boolean isGroupNode();

    /**
     * Transform the current Node with the transformation matrix.
     */
    public abstract void transformIfNeeded(AffineTransform finalTransform);

    protected void fillPresentationAttributes(String name, String value, Logger logger) {
        logger.log(Level.FINE, ">>>> PROP " + name + " = " + value);
        if (value.startsWith("url("))  {
            getTree().logErrorLine("Unsupported URL value: " + value, getDocumentNode(),
                    SvgTree.SvgLogLevel.ERROR);
            return;
        }
        mVdAttributesMap.put(name, value);
    }

    public void fillEmptyAttributes(Map<String, String> parentAttributesMap) {
        // Go through the parents' attributes, if the child misses any, then fill it.
        for (Map.Entry<String, String> entry : parentAttributesMap.entrySet()) {
            String key = entry.getKey();
            if (!mVdAttributesMap.containsKey(key)) {
                mVdAttributesMap.put(key, entry.getValue());
            }
        }
    }

    public abstract void flattern(AffineTransform transform);
}
