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

import org.w3c.dom.NamedNodeMap;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to represent one VectorDrawble's path element.
 */
class VdPath extends VdElement{
    private static Logger logger = Logger.getLogger(VdPath.class.getSimpleName());

    private static final String PATH_ID = "android:name";
    private static final String PATH_DESCRIPTION = "android:pathData";
    private static final String PATH_FILL = "android:fillColor";
    private static final String PATH_FILL_OPACTIY = "android:fillAlpha";
    private static final String PATH_STROKE = "android:strokeColor";
    private static final String PATH_STROKE_OPACTIY = "android:strokeAlpha";

    private static final String PATH_STROKE_WIDTH = "android:strokeWidth";
    private static final String PATH_TRIM_START = "android:trimPathStart";
    private static final String PATH_TRIM_END = "android:trimPathEnd";
    private static final String PATH_TRIM_OFFSET = "android:trimPathOffset";
    private static final String PATH_STROKE_LINECAP = "android:strokeLineCap";
    private static final String PATH_STROKE_LINEJOIN = "android:strokeLineJoin";
    private static final String PATH_STROKE_MITERLIMIT = "android:strokeMiterLimit";
    private static final String PATH_CLIP = "android:clipToPath";
    private static final String LINECAP_BUTT = "butt";
    private static final String LINECAP_ROUND = "round";
    private static final String LINECAP_SQUARE = "square";
    private static final String LINEJOIN_MITER = "miter";
    private static final String LINEJOIN_ROUND = "round";
    private static final String LINEJOIN_BEVEL = "bevel";

    private Node[] mNode = null;
    private int mStrokeColor = 0;
    private int mFillColor = 0;
    private float mStrokeWidth = 0;
    private int mStrokeLineCap = 0;
    private int mStrokeLineJoin = 0;
    private float mStrokeMiterlimit = 4;
    private float mStrokeAlpha = 1.0f;
    private float mFillAlpha = 1.0f;
    // TODO: support trim path.
    private float mTrimPathStart = 0;
    private float mTrimPathEnd = 1;
    private float mTrimPathOffset = 0;

    public void toPath(Path2D path) {
        path.reset();
        if (mNode != null) {
            VdNodeRender.creatPath(mNode, path);
        }
    }

    /**
     * Represent one segment of the path data. Like "l 0,0 1,1"
     */
    public static class Node {
        char type;
        float[] params;

        public Node(char type, float[] params) {
            this.type = type;
            this.params = params;
        }

        public Node(Node n) {
            this.type = n.type;
            this.params = Arrays.copyOf(n.params, n.params.length);
        }

        public static String NodeListToString(Node[] nodes) {
            String s = "";
            for (int i = 0; i < nodes.length; i++) {
                Node n = nodes[i];
                s += n.type;
                int len = n.params.length;
                boolean implicitLineTo = false;
                char lineToType = ' ';
                if ((n.type == 'm' || n.type == 'M') && len > 2) {
                    implicitLineTo = true;
                    lineToType = n.type == 'm' ? 'l' : 'L';
                }
                for (int j = 0; j < len; j++) {
                    if (j > 0) {
                        s += ((j & 1) == 1) ? "," : " ";
                    }
                    if (implicitLineTo && j == 2) {
                        s += lineToType;
                    }
                    // To avoid trailing zeros like 17.0, use this trick
                    float value = n.params[j];
                    if (value == (long) value) {
                        s += String.valueOf((long) value);
                    } else {
                        s += String.valueOf(value);
                    }

                }
            }
            return s;
        }

        // TODO: use group transform to replace this transform.
        // Only thing used is the viewBox translation.
        public static void transform(float a,
                float b,
                float c,
                float d,
                float e,
                float f,
                Node[] nodes) {
            float[] pre = new float[2];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i].transform(a, b, c, d, e, f, pre);
            }
        }

        public void transform(float a,
                float b,
                float c,
                float d,
                float e,
                float f,
                float[] pre) {
            int incr = 0;
            float[] tempParams;
            float[] origParams;
            switch (type) {

                case 'z':
                case 'Z':
                    return;
                case 'M':
                case 'L':
                case 'T':
                    incr = 2;
                    pre[0] = params[params.length - 2];
                    pre[1] = params[params.length - 1];
                    for (int i = 0; i < params.length; i += incr) {
                        matrix(a, b, c, d, e, f, i, i + 1);
                    }
                    break;
                case 'm':
                case 'l':
                case 't':
                    incr = 2;
                    pre[0] += params[params.length - 2];
                    pre[1] += params[params.length - 1];
                    for (int i = 0; i < params.length; i += incr) {
                        matrix(a, b, c, d, 0, 0, i, i + 1);
                    }
                    break;
                case 'h':
                    type = 'l';
                    pre[0] += params[params.length - 1];

                    tempParams = new float[params.length * 2];
                    origParams = params;
                    params = tempParams;
                    for (int i = 0; i < params.length; i += 2) {
                        params[i] = origParams[i / 2];
                        params[i + 1] = 0;
                        matrix(a, b, c, d, 0, 0, i, i + 1);
                    }

                    break;
                case 'H':
                    type = 'L';
                    pre[0] = params[params.length - 1];
                    tempParams = new float[params.length * 2];
                    origParams = params;
                    params = tempParams;
                    for (int i = 0; i < params.length; i += 2) {
                        params[i] = origParams[i / 2];
                        params[i + 1] = pre[1];
                        matrix(a, b, c, d, e, f, i, i + 1);
                    }
                    break;
                case 'v':
                    pre[1] += params[params.length - 1];
                    type = 'l';
                    tempParams = new float[params.length * 2];
                    origParams = params;
                    params = tempParams;
                    for (int i = 0; i < params.length; i += 2) {
                        params[i] = 0;
                        params[i + 1] = origParams[i / 2];
                        matrix(a, b, c, d, 0, 0, i, i + 1);
                    }
                    break;
                case 'V':
                    type = 'L';
                    pre[1] = params[params.length - 1];
                    tempParams = new float[params.length * 2];
                    origParams = params;
                    params = tempParams;
                    for (int i = 0; i < params.length; i += 2) {
                        params[i] = pre[0];
                        params[i + 1] = origParams[i / 2];
                        matrix(a, b, c, d, e, f, i, i + 1);
                    }
                    break;
                case 'C':
                case 'S':
                case 'Q':
                    pre[0] = params[params.length - 2];
                    pre[1] = params[params.length - 1];
                    for (int i = 0; i < params.length; i += 2) {
                        matrix(a, b, c, d, e, f, i, i + 1);
                    }
                    break;
                case 's':
                case 'q':
                case 'c':
                    pre[0] += params[params.length - 2];
                    pre[1] += params[params.length - 1];
                    for (int i = 0; i < params.length; i += 2) {
                        matrix(a, b, c, d, 0, 0, i, i + 1);
                    }
                    break;
                case 'a':
                    incr = 7;
                    pre[0] += params[params.length - 2];
                    pre[1] += params[params.length - 1];
                    for (int i = 0; i < params.length; i += incr) {
                        matrix(a, b, c, d, 0, 0, i, i + 1);
                        double ang = Math.toRadians(params[i + 2]);
                        params[i + 2] = (float) Math.toDegrees(ang + Math.atan2(b, d));
                        matrix(a, b, c, d, 0, 0, i + 5, i + 6);
                    }
                    break;
                case 'A':
                    incr = 7;
                    pre[0] = params[params.length - 2];
                    pre[1] = params[params.length - 1];
                    for (int i = 0; i < params.length; i += incr) {
                        matrix(a, b, c, d, e, f, i, i + 1);
                        double ang = Math.toRadians(params[i + 2]);
                        params[i + 2] = (float) Math.toDegrees(ang + Math.atan2(b, d));
                        matrix(a, b, c, d, e, f, i + 5, i + 6);
                    }
                    break;

            }
        }

        void matrix(float a,
                float b,
                float c,
                float d,
                float e,
                float f,
                int offx,
                int offy) {
            float inx = (offx < 0) ? 1 : params[offx];
            float iny = (offy < 0) ? 1 : params[offy];
            float x = inx * a + iny * c + e;
            float y = inx * b + iny * d + f;
            if (offx >= 0) {
                params[offx] = x;
            }
            if (offy >= 0) {
                params[offy] = y;
            }
        }
    }

    /**
     * @return color value in #AARRGGBB format.
     */
    private int calculateColor(String value) {
        int len = value.length();
        int ret;
        int k = 0;
        switch (len) {
            case 7: // #RRGGBB
                ret = (int) Long.parseLong(value.substring(1), 16);
                ret |= 0xFF000000;
                break;
            case 9: // #AARRGGBB
                ret = (int) Long.parseLong(value.substring(1), 16);
                break;
            case 4: // #RGB
                ret = (int) Long.parseLong(value.substring(1), 16);

                k |= ((ret >> 8) & 0xF) * 0x110000;
                k |= ((ret >> 4) & 0xF) * 0x1100;
                k |= ((ret) & 0xF) * 0x11;
                ret = k | 0xFF000000;
                break;
            case 5: // #ARGB
                ret = (int) Long.parseLong(value.substring(1), 16);
                k |= ((ret >> 12) & 0xF) * 0x11000000;
                k |= ((ret >> 8) & 0xF) * 0x110000;
                k |= ((ret >> 4) & 0xF) * 0x1100;
                k |= ((ret) & 0xF) * 0x11;
                ret = k;
                break;
            default:
                return 0xFF000000;
        }
        return ret;
    }

    private void setNameValue(String name, String value) {
        if (PATH_DESCRIPTION.equals(name)) {
            mNode = PathParser.parsePath(value);
        } else if (PATH_ID.equals(name)) {
            mName = value;
        } else if (PATH_FILL.equals(name)) {
            mFillColor = calculateColor(value);
        } else if (PATH_STROKE.equals(name)) {
            mStrokeColor = calculateColor(value);
        } else if (PATH_FILL_OPACTIY.equals(name)) {
            mFillAlpha = Float.parseFloat(value);
        } else if (PATH_STROKE_OPACTIY.equals(name)) {
            mStrokeAlpha = Float.parseFloat(value);
        } else if (PATH_STROKE_WIDTH.equals(name)) {
            mStrokeWidth = Float.parseFloat(value);
        } else if (PATH_TRIM_START.equals(name)) {
            mTrimPathStart = Float.parseFloat(value);
        } else if (PATH_TRIM_END.equals(name)) {
            mTrimPathEnd = Float.parseFloat(value);
        } else if (PATH_TRIM_OFFSET.equals(name)) {
            mTrimPathOffset = Float.parseFloat(value);
        } else if (PATH_STROKE_LINECAP.equals(name)) {
            if (LINECAP_BUTT.equals(value)) {
                mStrokeLineCap = 0;
            } else if (LINECAP_ROUND.equals(value)) {
                mStrokeLineCap = 1;
            } else if (LINECAP_SQUARE.equals(value)) {
                mStrokeLineCap = 2;
            }
        } else if (PATH_STROKE_LINEJOIN.equals(name)) {
            if (LINEJOIN_MITER.equals(value)) {
                mStrokeLineJoin = 0;
            } else if (LINEJOIN_ROUND.equals(value)) {
                mStrokeLineJoin = 1;
            } else if (LINEJOIN_BEVEL.equals(value)) {
                mStrokeLineJoin = 2;
            }
        } else if (PATH_STROKE_MITERLIMIT.equals(name)) {
            mStrokeMiterlimit = Float.parseFloat(value);
        } else {
            logger.log(Level.WARNING, ">>>>>> DID NOT UNDERSTAND ! \"" + name + "\" <<<<");
        }

    }

    /**
     * TODO: support rotation attribute for stroke width
     */
    public void transform(float a, float b, float c, float d, float e, float f) {
        mStrokeWidth *= Math.hypot(a + b, c + d);
        Node.transform(a, b, c, d, e, f, mNode);
    }

    /**
     * Multiply the <code>alpha</code> value into the alpha channel <code>color</code>.
     */
    private static int applyAlpha(int color, float alpha) {
        int alphaBytes = (color >> 24) & 0xff;
        color &= 0x00FFFFFF;
        color |= ((int) (alphaBytes * alpha)) << 24;
        return color;
    }

    /**
     * Draw the current path
     */
    @Override
    public void draw(Graphics2D g, AffineTransform currentMatrix, float scaleX, float scaleY) {

        Path2D path2d = new Path2D.Double();
        toPath(path2d);

        // SWing operate the matrix is using pre-concatenate by default.
        // Below is how this is handled in Android framework.
        // pathMatrix.set(groupStackedMatrix);
        // pathMatrix.postScale(scaleX, scaleY);
        g.setTransform(new AffineTransform());
        g.scale(scaleX, scaleY);
        g.transform(currentMatrix);

        // TODO: support clip path here.
        if (mFillColor != 0) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fillColor = new Color(applyAlpha(mFillColor, mFillAlpha), true);
            g.setColor(fillColor);
            g.fill(path2d);
        }
        if (mStrokeColor != 0) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            BasicStroke stroke = new BasicStroke(mStrokeWidth, mStrokeLineCap, mStrokeLineJoin, mStrokeMiterlimit);
            g.setStroke(stroke);
            Color strokeColor = new Color(applyAlpha(mStrokeColor, mStrokeAlpha), true);
            g.setColor(strokeColor);
            g.draw(path2d);
        }
        return;
    }

    @Override
    public void parseAttributes(NamedNodeMap attributes) {
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            String name = attributes.item(i).getNodeName();
            String value = attributes.item(i).getNodeValue();
            setNameValue(name, value);
        }
    }

    @Override
    public boolean isGroup() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder pathInfo = new StringBuilder();
        pathInfo.append("Path:");
        pathInfo.append(" Name: " + mName);
        // pathInfo.append(" Node: " + mNode.toString());
        pathInfo.append(" mFillColor: " + Integer.toHexString(mFillColor));
        pathInfo.append(" mFillAlpha:" + mFillAlpha);
        pathInfo.append(" mStrokeColor:" + Integer.toHexString(mStrokeColor));
        pathInfo.append(" mStrokeWidth:" + mStrokeWidth);
        pathInfo.append(" mStrokeAlpha:" + mStrokeAlpha);

        return pathInfo.toString();

    }
};