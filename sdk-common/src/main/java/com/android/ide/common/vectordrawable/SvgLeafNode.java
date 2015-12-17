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

import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableMap;
import org.w3c.dom.Node;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represent a SVG file's leave element.
 */
class SvgLeafNode extends SvgNode {
    private static Logger logger = Logger.getLogger(SvgLeafNode.class.getSimpleName());

    private String mPathData;

    public SvgLeafNode(SvgTree svgTree, Node node, String nodeName) {
        super(svgTree, node, nodeName);
    }

    private String getAttributeValues(ImmutableMap<String, String> presentationMap) {
        StringBuilder sb = new StringBuilder("/>\n");
        for (String key : mVdAttributesMap.keySet()) {
            String vectorDrawableAttr = presentationMap.get(key);
            String svgValue = mVdAttributesMap.get(key);
            String vdValue = svgValue.trim();
            // There are several cases we need to convert from SVG format to
            // VectorDrawable format. Like "none", "3px" or "rgb(255, 0, 0)"
            if ("none".equals(vdValue)) {
                vdValue = "#00000000";
            } else if (vdValue.endsWith("px")){
                vdValue = vdValue.substring(0, vdValue.length() - 2);
            } else if (vdValue.startsWith("rgb")) {
                vdValue = vdValue.substring(3, vdValue.length());
                vdValue = convertRGBToHex(vdValue);
                if (vdValue == null) {
                    getTree().logErrorLine("Unsupported Color format " + vdValue, getDocumentNode(),
                                           SvgTree.SvgLogLevel.ERROR);
                }
            }
            String attr = "\n        " + vectorDrawableAttr + "=\"" +
                          vdValue + "\"";
            sb.insert(0, attr);

        }
        return sb.toString();
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * SVG allows using rgb(int, int, int) or rgb(float%, float%, float%) to
     * represent a color, but Android doesn't. Therefore, we need to convert
     * them into #RRGGBB format.
     * @param svgValue in either "(int, int, int)" or "(float%, float%, float%)"
     * @return #RRGGBB in hex format, or null, if an error is found.
     */
    @Nullable
    private String convertRGBToHex(String svgValue) {
        // We don't support color keyword yet.
        // http://www.w3.org/TR/SVG11/types.html#ColorKeywords
        String result = null;
        String functionValue = svgValue.trim();
        functionValue = svgValue.substring(1, functionValue.length() - 1);
        // After we cut the "(", ")", we can deal with the numbers.
        String[] numbers = functionValue.split(",");
        if (numbers.length != 3) {
            return null;
        }
        int[] color = new int[3];
        for (int i = 0; i < 3; i ++) {
            String number = numbers[i];
            number = number.trim();
            if (number.endsWith("%")) {
                float value = Float.parseFloat(number.substring(0, number.length() - 1));
                color[i] = clamp((int)(value * 255.0f / 100.0f), 0, 255);
            } else {
                int value = Integer.parseInt(number);
                color[i] = clamp(value, 0, 255);
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("#");
        for (int i = 0; i < 3; i ++) {
            builder.append(String.format("%02X", color[i]));
        }
        result = builder.toString();
        assert result.length() == 7;
        return result;
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
    public void transformIfNeeded(AffineTransform rootTransform) {
        if ((mPathData == null)) {
            // Nothing to draw and transform, early return.
            return;
        }
        VdPath.Node[] n = PathParser.parsePath(mPathData);
        AffineTransform finalTransform = new AffineTransform(rootTransform);
        finalTransform.concatenate(mStackedTransform);
        boolean needsConvertRelativeMoveAfterClose = VdPath.Node.hasRelMoveAfterClose(n);
        if (!finalTransform.isIdentity() || needsConvertRelativeMoveAfterClose) {
            VdPath.Node.transform(finalTransform, n);
        }
        String decimalFormatString = getDecimalFormatString();
        mPathData = VdPath.Node.NodeListToString(n, decimalFormatString);
    }

    private String getDecimalFormatString() {
        float viewportWidth = getTree().getViewportWidth();
        float viewportHeight = getTree().getViewportHeight();
        float minSize = Math.min(viewportHeight, viewportWidth);
        float exponent = Math.round(Math.log10(minSize));
        int decimalPlace = (int) Math.floor(exponent - 4);
        String decimalFormatString = "#";
        if (decimalPlace < 0) {
            // Build a string with decimal places for "#.##...", and cap on 6 digits.
            if (decimalPlace < -6) {
                decimalPlace = -6;
            }
            decimalFormatString += ".";
            for (int i = 0 ; i < -decimalPlace; i++) {
                decimalFormatString += "#";
            }
        }
        return decimalFormatString;
    }

    @Override
    public void flattern(AffineTransform transform) {
        mStackedTransform.setTransform(transform);
        mStackedTransform.concatenate(mLocalTransform);

        if (mVdAttributesMap.containsKey(Svg2Vector.SVG_STROKE_WIDTH)
                && ((mStackedTransform.getType() | AffineTransform.TYPE_MASK_SCALE) != 0) ) {
            getTree().logErrorLine("We don't scale the stroke width!",  getDocumentNode(),
                    SvgTree.SvgLogLevel.WARNING);
        }
    }

    @Override
    public void writeXML(OutputStreamWriter writer) throws IOException {
        // First decide whether or not we can skip this path, since it draw nothing out.
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

        // Second, write the color info handling the default values.
        writer.write("    <path\n");
        if (!mVdAttributesMap.containsKey(Svg2Vector.SVG_FILL_COLOR)) {
            logger.log(Level.FINE, "ADDING FILL SVG_FILL_COLOR");
            writer.write("        android:fillColor=\"#FF000000\"\n");
        }
        if (!emptyStroke && !mVdAttributesMap.containsKey(Svg2Vector.SVG_STROKE_WIDTH)) {
            logger.log(Level.FINE, "Adding default stroke width");
            writer.write("        android:strokeWidth=\"1\"\n");
        }

        // Last, write the path data and all associated attributes.
        writer.write("        android:pathData=\"" + mPathData + "\"");
        writer.write(getAttributeValues(Svg2Vector.presentationMap));
    }

    public void fillPresentationAttributes(String name, String value) {
        fillPresentationAttributes(name, value, logger);
    }
}
