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

package com.android.assetstudiolib.vectordrawable;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represent a SVG file's leave element.
 */
class SvgLeafNode extends SvgNode {
    private static Logger logger = Logger.getLogger(SvgLeafNode.class.getSimpleName());

    private String mPathData;
    // Key is the attributes for vector drawable, and the value is the converted from SVG.
    private HashMap<String, String> mVdAttributesMap = new HashMap<String, String>();

    public SvgLeafNode(String nodeName) {
        super(nodeName);
    }

    private String getAttributeValues(ImmutableMap<String, String> presentationMap) {
        StringBuilder sb = new StringBuilder("/>\n");
        for (String key : mVdAttributesMap.keySet()) {
            String vectorDrawableAttr = presentationMap.get(key);
            if (!"none".equals(mVdAttributesMap.get(key))) {
                String attr = "\n        " + vectorDrawableAttr + "=\"" +
                              mVdAttributesMap.get(key) + "\"";
                sb.insert(0, attr);
            } else {
                String attr = "\n        " + vectorDrawableAttr + "=\"#00000000\"";
                sb.insert(0, attr);
            }
        }
        return sb.toString();
    }

    @Override
    public void dumpNode(String indent) {
        logger.log(Level.FINE, indent + (mPathData != null ? mPathData : " null pathData ") +
                               (mName != null ? mName : " null name "));
    }

    public void setPathData(String pathData) {
        mPathData = pathData;
    }

    @Override
    public boolean isGroupNode() {
        return false;
    }

    @Override
    public void transform(float a, float b, float c, float d, float e, float f) {
        if ("none".equals(mVdAttributesMap.get("fill")) || (mPathData == null)) {
            // Nothing to draw and transform, early return.
            return;
        }
        // TODO: We need to just apply the transformation to group.
        VdPath.Node[] n = VdParser.parsePath(mPathData);
        if (!(a == 1 && d == 1 && b == 0 && c == 0 && e == 0 && f == 0)) {
            VdPath.Node.transform(a, b, c, d, e, f, n);
        }
        mPathData = VdPath.Node.NodeListToString(n);
    }

    @Override
    public void writeXML(OutputStreamWriter writer) throws IOException {
        String fillColor = mVdAttributesMap.get(Svg2Vector.SVG_FILL_COLOR);
        String strokeColor = mVdAttributesMap.get(Svg2Vector.SVG_STROKE_COLOR);
        logger.log(Level.FINE, "fill color " + fillColor);
        boolean emptyFill = fillColor != null && ("none".equals(fillColor) || "#0000000".equals(fillColor));
        boolean emptyStroke = strokeColor == null || "none".equals(strokeColor);
        boolean emptyPath = mPathData == null;
        boolean nothingToDraw = emptyPath || emptyFill && emptyStroke;
        if (nothingToDraw) {
            return;
        }

        writer.write("    <path\n");
        if (!mVdAttributesMap.containsKey(Svg2Vector.SVG_FILL_COLOR)) {
            logger.log(Level.FINE, "ADDING FILL SVG_FILL_COLOR");
            writer.write("        android:fillColor=\"#FF000000\"\n");
        }
        writer.write("        android:pathData=\"" + mPathData + "\"");
        writer.write(getAttributeValues(Svg2Vector.presentationMap));
    }

    public void fillPresentationAttributes(String name, String value) {
        logger.log(Level.FINE, ">>>> PROP " + name + " = " + value);
        mVdAttributesMap.put(name, value);
    }
}
