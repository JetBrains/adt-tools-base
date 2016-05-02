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

    public static final ImmutableMap<String, String> colorMap =
        ImmutableMap.<String, String>builder()
            .put("aliceblue", "#f0f8ff")
            .put("antiquewhite", "#faebd7")
            .put("aqua", "#00ffff")
            .put("aquamarine", "#7fffd4")
            .put("azure", "#f0ffff")
            .put("beige", "#f5f5dc")
            .put("bisque", "#ffe4c4")
            .put("black", "#000000")
            .put("blanchedalmond", "#ffebcd")
            .put("blue", "#0000ff")
            .put("blueviolet", "#8a2be2")
            .put("brown", "#a52a2a")
            .put("burlywood", "#deb887")
            .put("cadetblue", "#5f9ea0")
            .put("chartreuse", "#7fff00")
            .put("chocolate", "#d2691e")
            .put("coral", "#ff7f50")
            .put("cornflowerblue", "#6495ed")
            .put("cornsilk", "#fff8dc")
            .put("crimson", "#dc143c")
            .put("cyan", "#00ffff")
            .put("darkblue", "#00008b")
            .put("darkcyan", "#008b8b")
            .put("darkgoldenrod", "#b8860b")
            .put("darkgray", "#a9a9a9")
            .put("darkgrey", "#a9a9a9")
            .put("darkgreen", "#006400")
            .put("darkkhaki", "#bdb76b")
            .put("darkmagenta", "#8b008b")
            .put("darkolivegreen", "#556b2f")
            .put("darkorange", "#ff8c00")
            .put("darkorchid", "#9932cc")
            .put("darkred", "#8b0000")
            .put("darksalmon", "#e9967a")
            .put("darkseagreen", "#8fbc8f")
            .put("darkslateblue", "#483d8b")
            .put("darkslategray", "#2f4f4f")
            .put("darkslategrey", "#2f4f4f")
            .put("darkturquoise", "#00ced1")
            .put("darkviolet", "#9400d3")
            .put("deeppink", "#ff1493")
            .put("deepskyblue", "#00bfff")
            .put("dimgray", "#696969")
            .put("dimgrey", "#696969")
            .put("dodgerblue", "#1e90ff")
            .put("firebrick", "#b22222")
            .put("floralwhite", "#fffaf0")
            .put("forestgreen", "#228b22")
            .put("fuchsia", "#ff00ff")
            .put("gainsboro", "#dcdcdc")
            .put("ghostwhite", "#f8f8ff")
            .put("gold", "#ffd700")
            .put("goldenrod", "#daa520")
            .put("gray", "#808080")
            .put("grey", "#808080")
            .put("green", "#008000")
            .put("greenyellow", "#adff2f")
            .put("honeydew", "#f0fff0")
            .put("hotpink", "#ff69b4")
            .put("indianred", "#cd5c5c")
            .put("indigo", "#4b0082")
            .put("ivory", "#fffff0")
            .put("khaki", "#f0e68c")
            .put("lavender", "#e6e6fa")
            .put("lavenderblush", "#fff0f5")
            .put("lawngreen", "#7cfc00")
            .put("lemonchiffon", "#fffacd")
            .put("lightblue", "#add8e6")
            .put("lightcoral", "#f08080")
            .put("lightcyan", "#e0ffff")
            .put("lightgoldenrodyellow", "#fafad2")
            .put("lightgray", "#d3d3d3")
            .put("lightgrey", "#d3d3d3")
            .put("lightgreen", "#90ee90")
            .put("lightpink", "#ffb6c1")
            .put("lightsalmon", "#ffa07a")
            .put("lightseagreen", "#20b2aa")
            .put("lightskyblue", "#87cefa")
            .put("lightslategray", "#778899")
            .put("lightslategrey", "#778899")
            .put("lightsteelblue", "#b0c4de")
            .put("lightyellow", "#ffffe0")
            .put("lime", "#00ff00")
            .put("limegreen", "#32cd32")
            .put("linen", "#faf0e6")
            .put("magenta", "#ff00ff")
            .put("maroon", "#800000")
            .put("mediumaquamarine", "#66cdaa")
            .put("mediumblue", "#0000cd")
            .put("mediumorchid", "#ba55d3")
            .put("mediumpurple", "#9370db")
            .put("mediumseagreen", "#3cb371")
            .put("mediumslateblue", "#7b68ee")
            .put("mediumspringgreen", "#00fa9a")
            .put("mediumturquoise", "#48d1cc")
            .put("mediumvioletred", "#c71585")
            .put("midnightblue", "#191970")
            .put("mintcream", "#f5fffa")
            .put("mistyrose", "#ffe4e1")
            .put("moccasin", "#ffe4b5")
            .put("navajowhite", "#ffdead")
            .put("navy", "#000080")
            .put("oldlace", "#fdf5e6")
            .put("olive", "#808000")
            .put("olivedrab", "#6b8e23")
            .put("orange", "#ffa500")
            .put("orangered", "#ff4500")
            .put("orchid", "#da70d6")
            .put("palegoldenrod", "#eee8aa")
            .put("palegreen", "#98fb98")
            .put("paleturquoise", "#afeeee")
            .put("palevioletred", "#db7093")
            .put("papayawhip", "#ffefd5")
            .put("peachpuff", "#ffdab9")
            .put("peru", "#cd853f")
            .put("pink", "#ffc0cb")
            .put("plum", "#dda0dd")
            .put("powderblue", "#b0e0e6")
            .put("purple", "#800080")
            .put("rebeccapurple", "#663399")
            .put("red", "#ff0000")
            .put("rosybrown", "#bc8f8f")
            .put("royalblue", "#4169e1")
            .put("saddlebrown", "#8b4513")
            .put("salmon", "#fa8072")
            .put("sandybrown", "#f4a460")
            .put("seagreen", "#2e8b57")
            .put("seashell", "#fff5ee")
            .put("sienna", "#a0522d")
            .put("silver", "#c0c0c0")
            .put("skyblue", "#87ceeb")
            .put("slateblue", "#6a5acd")
            .put("slategray", "#708090")
            .put("slategrey", "#708090")
            .put("snow", "#fffafa")
            .put("springgreen", "#00ff7f")
            .put("steelblue", "#4682b4")
            .put("tan", "#d2b48c")
            .put("teal", "#008080")
            .put("thistle", "#d8bfd8")
            .put("tomato", "#ff6347")
            .put("turquoise", "#40e0d0")
            .put("violet", "#ee82ee")
            .put("wheat", "#f5deb3")
            .put("white", "#ffffff")
            .put("whitesmoke", "#f5f5f5")
            .put("yellow", "#ffff00")
            .put("yellowgreen", "#9acd32")
            .build();

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
            // or HTML defined color names like "black"
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
            } else if (colorMap.containsKey(vdValue.toLowerCase())) {
                vdValue = colorMap.get(vdValue.toLowerCase());
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
