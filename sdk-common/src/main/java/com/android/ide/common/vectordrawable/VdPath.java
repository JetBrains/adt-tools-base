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

import com.google.common.collect.ImmutableMap;
import org.w3c.dom.NamedNodeMap;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.math.RoundingMode;
import java.text.DecimalFormat;
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

    private Node[] mNodeList = null;
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
        if (mNodeList != null) {
            VdNodeRender.creatPath(mNodeList, path);
        }
    }

    /**
     * Represent one segment of the path data. Like "l 0,0 1,1"
     */
    public static class Node {
        private char mType;
        private float[] mParams;

        public char getType() {
            return mType;
        }

        public float[] getmParams() {
            return mParams;
        }

        public Node(char type, float[] params) {
            this.mType = type;
            this.mParams = params;
        }

        public Node(Node n) {
            this.mType = n.mType;
            this.mParams = Arrays.copyOf(n.mParams, n.mParams.length);
        }

        public static boolean hasRelMoveAfterClose(Node[] nodes) {
            char preType = ' ';
            for (Node n : nodes) {
                if ((preType == 'z' || preType == 'Z') && n.mType == 'm') {
                    return true;
                }
                preType = n.mType;
            }
            return false;
        }

        public static String NodeListToString(Node[] nodes, String decimalPlaceString) {
            String s = "";
            for (int i = 0; i < nodes.length; i++) {
                Node n = nodes[i];
                s += n.mType;
                int len = n.mParams.length;
                boolean implicitLineTo = false;
                char lineToType = ' ';
                if ((n.mType == 'm' || n.mType == 'M') && len > 2) {
                    implicitLineTo = true;
                    lineToType = n.mType == 'm' ? 'l' : 'L';
                }
                for (int j = 0; j < len; j++) {
                    if (j > 0) {
                        s += ((j & 1) == 1) ? "," : " ";
                    }
                    if (implicitLineTo && j == 2) {
                        s += lineToType;
                    }
                    // To avoid trailing zeros like 17.0, use this trick
                    float value = n.mParams[j];
                    if (value == (long) value) {
                        s += String.valueOf((long) value);
                    } else {
                        DecimalFormat df = new DecimalFormat(decimalPlaceString);
                        df.setRoundingMode(RoundingMode.HALF_UP);
                        s += df.format(value);
                    }

                }
            }
            return s;
        }

        private static final char INIT_TYPE = ' ';
        public static void transform(AffineTransform totalTransform,
                                     Node[] nodes) {
            Point2D.Float currentPoint = new Point2D.Float();
            Point2D.Float currentSegmentStartPoint = new Point2D.Float();
            char previousType = INIT_TYPE;
            for (int i = 0; i < nodes.length; i++) {
                nodes[i].transform(totalTransform, currentPoint, currentSegmentStartPoint, previousType);
                previousType= nodes[i].mType;
            }
        }

        private static final ImmutableMap<Character, Integer> commandStepMap =
          ImmutableMap.<Character, Integer>builder()
            .put('z', 2)
            .put('Z', 2)
            .put('m', 2)
            .put('M', 2)
            .put('l', 2)
            .put('L', 2)
            .put('t', 2)
            .put('T', 2)
            .put('h', 1)
            .put('H', 1)
            .put('v', 1)
            .put('V', 1)
            .put('c', 6)
            .put('C', 6)
            .put('s', 4)
            .put('S', 4)
            .put('q', 4)
            .put('Q', 4)
            .put('a', 7)
            .put('A', 7)
            .build();

        private void transform(AffineTransform totalTransform, Point2D.Float currentPoint,
                               Point2D.Float currentSegmentStartPoint, char previousType) {
            // For Horizontal / Vertical lines, we have to convert to LineTo with 2 parameters
            // And for arcTo, we also need to isolate the parameters for transformation.
            // Therefore a looping will be necessary for such commands.
            //
            // Note that if the matrix is translation only, then we can save many computations.

            int paramsLen = mParams.length;
            float[] tempParams = new float[2 * paramsLen];
            // These has to be pre-transformed value. In another word, the same as it is
            // in the pathData.
            float currentX = currentPoint.x;
            float currentY = currentPoint.y;
            float currentSegmentStartX = currentSegmentStartPoint.x;
            float currentSegmentStartY = currentSegmentStartPoint.y;

            int step = commandStepMap.get(mType);
            switch (mType) {
                case 'z':
                case 'Z':
                    currentX = currentSegmentStartX;
                    currentY = currentSegmentStartY;
                    break;
                case 'M':
                case 'L':
                case 'T':
                case 'C':
                case 'S':
                case 'Q':
                    currentX = mParams[paramsLen - 2];
                    currentY = mParams[paramsLen - 1];
                    if (mType == 'M') {
                        currentSegmentStartX = currentX;
                        currentSegmentStartY = currentY;
                    }

                    totalTransform.transform(mParams, 0, mParams, 0, paramsLen / 2);
                    break;
                case 'm':
                    // We also need to workaround a bug in API 21 that 'm' after 'z'
                    // is not picking up the relative value correctly.
                    if (previousType == 'z' || previousType == 'Z') {
                        mType = 'M';
                        mParams[0] += currentSegmentStartX;
                        mParams[1] += currentSegmentStartY;
                        currentSegmentStartX = mParams[0];
                        currentSegmentStartY = mParams[1];
                        for (int i = 1; i < paramsLen / step; i++) {
                            mParams[i * step + 0] += mParams[(i - 1) * step + 0];
                            mParams[i * step + 1] += mParams[(i - 1) * step + 1];
                        }
                        currentX = mParams[paramsLen - 2];
                        currentY = mParams[paramsLen - 1];

                        totalTransform.transform(mParams, 0, mParams, 0, paramsLen / 2);
                    } else {

                        // We need to handle the initial 'm' similar to 'M' for first pair.
                        // Then all the following numbers are handled as 'l'
                        int startIndex = 0;
                        if (previousType == INIT_TYPE) {
                            int paramsLenInitialM = 2;
                            currentX = mParams[paramsLenInitialM - 2];
                            currentY = mParams[paramsLenInitialM - 1];
                            currentSegmentStartX = currentX;
                            currentSegmentStartY = currentY;

                            totalTransform.transform(mParams, 0, mParams, 0, paramsLenInitialM / 2);
                            startIndex = 1;
                        }
                        for (int i = startIndex; i < paramsLen / step; i++) {
                            int indexX = i * step + (step - 2);
                            int indexY = i * step + (step - 1);
                            currentX += mParams[indexX];
                            currentY += mParams[indexY];
                        }

                        if (!isTranslationOnly(totalTransform)) {
                            deltaTransform(totalTransform, mParams, 2 * startIndex,
                                    paramsLen - 2 * startIndex);
                        }
                    }

                    break;
                case 'l':
                case 't':
                case 'c':
                case 's':
                case 'q':
                    for (int i = 0; i < paramsLen / step; i ++) {
                        int indexX = i * step + (step - 2);
                        int indexY = i * step + (step - 1);
                        currentX += mParams[indexX];
                        currentY += mParams[indexY];
                    }
                    if (!isTranslationOnly(totalTransform)) {
                        deltaTransform(totalTransform, mParams, 0, paramsLen);
                    }
                    break;
                case 'H':
                    mType = 'L';
                    for (int i = 0; i < paramsLen; i ++) {
                        tempParams[i * 2 + 0] = mParams[i];
                        tempParams[i * 2 + 1] = currentY;
                        currentX = mParams[i];
                    }
                    totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen /*points*/);
                    mParams = tempParams;
                    break;
                case 'V':
                    mType = 'L';
                    for (int i = 0; i < paramsLen; i ++) {
                        tempParams[i * 2 + 0] = currentX;
                        tempParams[i * 2 + 1] = mParams[i];
                        currentY = mParams[i];
                    }
                    totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen /*points*/);
                    mParams = tempParams;
                    break;
                case 'h':
                    for (int i = 0; i < paramsLen; i ++) {
                        // tempParams may not be used, but I would rather merge the code here.
                        tempParams[i * 2 + 0] = mParams[i];
                        currentX += mParams[i];
                        tempParams[i * 2 + 1] = 0;
                    }
                    if (!isTranslationOnly(totalTransform)) {
                        mType = 'l';
                        deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen);
                        mParams = tempParams;
                    }
                    break;
                case 'v':
                    for (int i = 0; i < paramsLen; i++) {
                        // tempParams may not be used, but I would rather merge the code here.
                        tempParams[i * 2 + 0] = 0;
                        tempParams[i * 2 + 1] = mParams[i];
                        currentY += mParams[i];
                    }

                    if (!isTranslationOnly(totalTransform)) {
                        mType = 'l';
                        deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen);
                        mParams = tempParams;
                    }
                    break;
                case 'A':
                    for (int i = 0; i < paramsLen / step; i ++) {
                        // (0:rx 1:ry 2:x-axis-rotation 3:large-arc-flag 4:sweep-flag 5:x 6:y)
                        // [0, 1, 2]
                        if (!isTranslationOnly(totalTransform)) {
                            EllipseSolver ellipseSolver = new EllipseSolver(totalTransform,
                                    currentX, currentY,
                                    mParams[i * step + 0], mParams[i * step + 1], mParams[i * step + 2],
                                    mParams[i * step + 3], mParams[i * step + 4],
                                    mParams[i * step + 5], mParams[i * step + 6]);
                            mParams[i * step + 0] = ellipseSolver.getMajorAxis();
                            mParams[i * step + 1] = ellipseSolver.getMinorAxis();
                            mParams[i * step + 2] = ellipseSolver.getRotationDegree();
                            if (ellipseSolver.getDirectionChanged()) {
                                mParams[i * step + 4] = 1 - mParams[i * step + 4];
                            }
                        } else {
                            // No need to change the value of rx , ry, rotation, and flags.
                        }
                        // [5, 6]
                        currentX = mParams[i * step + 5];
                        currentY = mParams[i * step + 6];

                        totalTransform.transform(mParams, i * step + 5, mParams, i * step + 5, 1 /*1 point only*/);
                    }
                    break;
                case 'a':
                    for (int i = 0; i < paramsLen / step; i ++) {
                        float oldCurrentX = currentX;
                        float oldCurrentY = currentY;

                        currentX += mParams[i * step + 5];
                        currentY += mParams[i * step + 6];
                        if (!isTranslationOnly(totalTransform)) {
                            EllipseSolver ellipseSolver = new EllipseSolver(totalTransform,
                                    oldCurrentX, oldCurrentY,
                                    mParams[i * step + 0], mParams[i * step + 1], mParams[i * step + 2],
                                    mParams[i * step + 3], mParams[i * step + 4],
                                    oldCurrentX + mParams[i * step + 5],
                                    oldCurrentY + mParams[i * step + 6]);
                            // (0:rx 1:ry 2:x-axis-rotation 3:large-arc-flag 4:sweep-flag 5:x 6:y)
                            // [5, 6]
                            deltaTransform(totalTransform, mParams, i * step + 5, 2);
                            // [0, 1, 2]
                            mParams[i * step + 0] = ellipseSolver.getMajorAxis();
                            mParams[i * step + 1] = ellipseSolver.getMinorAxis();
                            mParams[i * step + 2] = ellipseSolver.getRotationDegree();
                            if (ellipseSolver.getDirectionChanged()) {
                                mParams[i * step + 4] = 1 - mParams[i * step + 4];
                            }
                        }

                    }
                    break;
                default:
                    throw new IllegalArgumentException("Type is not right!!!");
            }
            currentPoint.setLocation(currentX, currentY);
            currentSegmentStartPoint.setLocation(currentSegmentStartX, currentSegmentStartY);
            return;
        }

        private boolean isTranslationOnly(AffineTransform totalTransform) {
            int type = totalTransform.getType();
            if (type == AffineTransform.TYPE_IDENTITY || type == AffineTransform.TYPE_TRANSLATION) {
                return true;
            }
            return false;
        }

        /**
         * Convert the <code>tempParams</code> into a double array, then apply the
         * delta transform and convert it back to float array.
         * @params: offset in number of floats, not points.
         * @params: paramsLen in number of floats, not points.
         */
        private void deltaTransform(AffineTransform totalTransform, float[] tempParams, int offset,  int paramsLen) {
            double[] doubleArray = new double[paramsLen];
            for (int i = 0; i < paramsLen; i++)
            {
                doubleArray[i] = (double) tempParams[i + offset];
            }

            totalTransform.deltaTransform(doubleArray, 0, doubleArray, 0, paramsLen / 2);

            for (int i = 0; i < paramsLen; i++)
            {
                tempParams[i + offset] = (float) doubleArray[i];
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
            mNodeList = PathParser.parsePath(value);
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
        pathInfo.append(" Node: " + mNodeList.toString());
        pathInfo.append(" mFillColor: " + Integer.toHexString(mFillColor));
        pathInfo.append(" mFillAlpha:" + mFillAlpha);
        pathInfo.append(" mStrokeColor:" + Integer.toHexString(mStrokeColor));
        pathInfo.append(" mStrokeWidth:" + mStrokeWidth);
        pathInfo.append(" mStrokeAlpha:" + mStrokeAlpha);

        return pathInfo.toString();

    }

};