/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.pixelprobe.BlendMode;
import com.android.tools.pixelprobe.ShapeInfo;
import com.android.tools.pixelprobe.ShapeInfo.PathType;
import com.android.tools.pixelprobe.color.Colors;
import com.android.tools.pixelprobe.decoder.psd.PsdFile.ColorProfileBlock;
import com.android.tools.pixelprobe.decoder.psd.PsdFile.Descriptor;
import com.android.tools.pixelprobe.decoder.psd.PsdFile.DescriptorItem.UnitDouble;
import com.android.tools.pixelprobe.decoder.psd.PsdFile.PathRecord;
import com.android.tools.pixelprobe.decoder.psd.PsdFile.ShapeMask;
import com.android.tools.pixelprobe.util.Bytes;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.pixelprobe.decoder.psd.PsdFile.PathRecord.*;

/**
 * Various utilities to decode PSD file data chunks.
 */
@SuppressWarnings("UseJBColor")
final class PsdUtils {
    /**
     * Constant to convert centimeters to inches.
     */
    static final float CENTIMETER_TO_INCH = 1.0f / 2.54f;
    /**
     * Constant to convert millimeters to inches.
     */
    static final float MILLIMETER_TO_INCH = 1.0f / 25.4f;

    // Used to parse descriptor paths
    private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9]+)\\[([0-9]+)\\]");

    // List of all the blending modes supported by Photoshop. They are all identified
    // by a 4 character key. The BlendMode enum uses Photoshop's naming conventions
    // (linear dodge is for instance commonly known as "add").
    private static final Map<String, BlendMode> blendModes = new HashMap<>();
    static {
        blendModes.put("pass", BlendMode.PASS_THROUGH);
        blendModes.put("norm", BlendMode.NORMAL);
        blendModes.put("diss", BlendMode.DISSOLVE);
        blendModes.put("dark", BlendMode.DARKEN);
        blendModes.put("mul ", BlendMode.MULTIPLY);
        blendModes.put("idiv", BlendMode.COLOR_BURN);
        blendModes.put("lbrn", BlendMode.LINEAR_BURN);
        blendModes.put("dkCl", BlendMode.DARKER_COLOR);
        blendModes.put("lite", BlendMode.LIGHTEN);
        blendModes.put("scrn", BlendMode.SCREEN);
        blendModes.put("div ", BlendMode.COLOR_DODGE);
        blendModes.put("lddg", BlendMode.LINEAR_DODGE);
        blendModes.put("lgCl", BlendMode.LIGHTER_COLOR);
        blendModes.put("over", BlendMode.OVERLAY);
        blendModes.put("sLit", BlendMode.SOFT_LIGHT);
        blendModes.put("hLit", BlendMode.HARD_LIGHT);
        blendModes.put("vLit", BlendMode.VIVID_LIGHT);
        blendModes.put("lLit", BlendMode.LINEAR_LIGHT);
        blendModes.put("pLit", BlendMode.PIN_LIGHT);
        blendModes.put("hMix", BlendMode.HARD_MIX);
        blendModes.put("diff", BlendMode.DIFFERENCE);
        blendModes.put("smud", BlendMode.EXCLUSION);
        blendModes.put("fsub", BlendMode.SUBTRACT);
        blendModes.put("fdiv", BlendMode.DIVIDE);
        blendModes.put("hue ", BlendMode.HUE);
        blendModes.put("sat ", BlendMode.SATURATION);
        blendModes.put("colr", BlendMode.COLOR);
        blendModes.put("lum ", BlendMode.LUMINOSITY);
        // blend modes stored in descriptors have different keys
        blendModes.put("Nrml", BlendMode.NORMAL);
        blendModes.put("Dslv", BlendMode.DISSOLVE);
        blendModes.put("Drkn", BlendMode.DARKEN);
        blendModes.put("Mltp", BlendMode.MULTIPLY);
        blendModes.put("CBrn", BlendMode.COLOR_BURN);
        blendModes.put("linearBurn", BlendMode.LINEAR_BURN);
        blendModes.put("darkerColor", BlendMode.DARKER_COLOR);
        blendModes.put("Lghn", BlendMode.LIGHTEN);
        blendModes.put("Scrn", BlendMode.SCREEN);
        blendModes.put("CDdg", BlendMode.COLOR_DODGE);
        blendModes.put("linearDodge", BlendMode.LINEAR_DODGE);
        blendModes.put("lighterColor", BlendMode.LIGHTER_COLOR);
        blendModes.put("Ovrl", BlendMode.OVERLAY);
        blendModes.put("SftL", BlendMode.SOFT_LIGHT);
        blendModes.put("HrdL", BlendMode.HARD_LIGHT);
        blendModes.put("vividLight", BlendMode.VIVID_LIGHT);
        blendModes.put("linearLight", BlendMode.LINEAR_LIGHT);
        blendModes.put("pinLight", BlendMode.PIN_LIGHT);
        blendModes.put("hardMix", BlendMode.HARD_MIX);
        blendModes.put("Dfrn", BlendMode.DIFFERENCE);
        blendModes.put("Xclu", BlendMode.EXCLUSION);
        blendModes.put("blendSubtraction", BlendMode.SUBTRACT);
        blendModes.put("blendDivide", BlendMode.DIVIDE);
        blendModes.put("H   ", BlendMode.HUE);
        blendModes.put("Strt", BlendMode.SATURATION);
        blendModes.put("Clr ", BlendMode.COLOR);
        blendModes.put("Lmns", BlendMode.LUMINOSITY);
    }

    private static final Set<String> adjustmentLayers = new HashSet<>(20);
    static {
        adjustmentLayers.add("SoCo");
        adjustmentLayers.add("GdFl");
        adjustmentLayers.add("PtFl");
        adjustmentLayers.add("brit");
        adjustmentLayers.add("levl");
        adjustmentLayers.add("curv");
        adjustmentLayers.add("expA");
        adjustmentLayers.add("vibA");
        adjustmentLayers.add("hue ");
        adjustmentLayers.add("hue2");
        adjustmentLayers.add("blnc");
        adjustmentLayers.add("blwh");
        adjustmentLayers.add("phfl");
        adjustmentLayers.add("mixr");
        adjustmentLayers.add("clrL");
        adjustmentLayers.add("nvrt");
        adjustmentLayers.add("post");
        adjustmentLayers.add("thrs");
        adjustmentLayers.add("grdm");
        adjustmentLayers.add("selc");
    }

    private PsdUtils() {
    }

    /**
     * Finds a descriptor value in the specified descriptor.
     * The path must be of the form <pre>child.value</pre>, with an
     * optional index for descriptor arrays. For instance:
     *
     * <pre>
     * children.properties[2].key
     * </pre>
     *
     * @param descriptor The descriptor on which to apply the path
     * @param path The path of the descriptor value
     *
     * @return The value pointed to by the specified path or null
     *         if the path is invalid
     */
    @SuppressWarnings("unchecked")
    static <T> T get(Descriptor descriptor, String path) {
        Object result = null;
        Descriptor currentDescriptor = descriptor;

        String[] elements = path.split("\\.");
        for (String element : elements) {
            int index = -1;
            Matcher matcher = PATH_PATTERN.matcher(element);
            if (matcher.matches()) {
                element = matcher.group(1);
                index = Integer.parseInt(matcher.group(2));
            }

            PsdFile.DescriptorItem item = currentDescriptor.items.get(element);
            if (item == null) break;

            Object data = item.value.data;
            if (data == null) break;

            if (data instanceof PsdFile.DescriptorItem.ValueList) {
                if (index >= 0) {
                    data = ((PsdFile.DescriptorItem.ValueList) data).items.get(index).data;
                }
            } else if (data instanceof PsdFile.DescriptorItem.Reference) {
                if (index >= 0) {
                    data = ((PsdFile.DescriptorItem.Reference) data).items.get(index).data;
                }
            }

            if (data instanceof Descriptor) {
                result = currentDescriptor = (Descriptor) data;
            } else if (data instanceof PsdFile.FixedString ||
                       data instanceof PsdFile.UnicodeString ||
                       data instanceof PsdFile.DescriptorItem.Enumerated) {
                result = data.toString();
            } else if (data instanceof PsdFile.FixedByteArray) {
                result = ((PsdFile.FixedByteArray) data).value;
            } else {
                result = data;
            }
        }

        return (T) result;
    }

    static float getFloat(Descriptor descriptor, String path) {
        return ((Double) get(descriptor, path)).floatValue();
    }

    static float getFloat(Descriptor descriptor, String path, float defaultValue) {
        Double v = get(descriptor, path);
        return v == null ? defaultValue : v.floatValue();
    }

    static float getUnitFloat(Descriptor descriptor, String path, float resolution) {
        return (float) resolveUnit(get(descriptor, path), resolution);
    }

    static double resolveUnit(UnitDouble unitDouble, float resolution) {
        if (UnitDouble.PIXELS.equals(unitDouble.unit)) {
            return unitDouble.value;
        } else if (UnitDouble.POINTS.equals(unitDouble.unit)) {
            return unitDouble.value * resolution / 72.0;
        } else if (UnitDouble.INCHES.equals(unitDouble.unit)) {
            return unitDouble.value * resolution;
        } else if (UnitDouble.MILLIMETERS.equals(unitDouble.unit)) {
            return unitDouble.value * MILLIMETER_TO_INCH * resolution;
        } else if (UnitDouble.CENTIMETERS.equals(unitDouble.unit)) {
            return unitDouble.value * CENTIMETER_TO_INCH * resolution;
        } else if (UnitDouble.PERCENT.equals(unitDouble.unit)) {
            return unitDouble.value / 100.0;
        } else if (UnitDouble.ANGLE_DEGREES.equals(unitDouble.unit)) {
            return unitDouble.value / 360.0;
        }
        return unitDouble.value;
    }

    static boolean getBoolean(Descriptor descriptor, String path) {
        Object value = get(descriptor, path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value == null) {
            return true;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * Returns the blend mode associated with the key found
     * in a PSD file.
     *
     * @param mode Blending mode key as found in a PSD file
     *
     * @return The blend mode matching the specified key, or
     * {@link BlendMode#NORMAL} if the key is unknown
     */
    static BlendMode getBlendMode(String mode) {
        BlendMode blendMode = blendModes.get(mode);
        return blendMode != null ? blendMode : BlendMode.NORMAL;
    }

    /**
     * Returns the known list of adjustment layer keys.
     */
    static Set<String> getAdjustmentLayerKeys() {
        return adjustmentLayers;
    }

    /**
     * Creates a color space from a color profile block. Can return null
     * if the specified block is null.
     */
    static ColorSpace createColorSpace(ColorProfileBlock block) {
        if (block == null) return null;
        ICC_Profile iccProfile = ICC_Profile.getInstance(block.icc);
        return new ICC_ColorSpace(iccProfile);
    }

    /**
     * Returns the image resource block that matches the specified id.
     * Can return null if the block cannot be found.
     */
    @SuppressWarnings("unchecked")
    static <T> T get(PsdFile.ImageResources resources, int id) {
        PsdFile.ImageResourceBlock block = resources.blocks.get(id);
        return block == null ? null : (T) block.data;
    }

    /**
     * Returns the color present in the specified descriptor.
     * The returned color always has an alpha of 1.0f.
     * If no color can be found, this method returns black.
     */
    static Color getColor(Descriptor descriptor) {
        return getColor(descriptor, 1.0f);
    }

    /**
     * Returns the color present in the specified descriptor.
     * If no color can be found, this method returns black.
     */
    static Color getColor(Descriptor descriptor, float alpha) {
        Descriptor color = get(descriptor, "Clr ");
        if (color == null) return Color.BLACK;

        String colorType = color.classId.toString();
        switch (colorType) {
            case Descriptor.CLASS_ID_COLOR_RGB:
                return colorFromRgb(color, alpha);
            case Descriptor.CLASS_ID_COLOR_HSB:
                return colorFromHsb(color, alpha);
            case Descriptor.CLASS_ID_COLOR_CMYK:
                return colorFromCmyk(color, alpha);
            case Descriptor.CLASS_ID_COLOR_LAB:
                return colorFromLab(color, alpha);
            case Descriptor.CLASS_ID_COLOR_GRAY:
                return colorFromGray(color, alpha);
        }

        if (alpha == 1.0f) {
            return Color.BLACK;
        }
        return new Color(0.0f, 0.0f, 0.0f, alpha);
    }

    private static Color colorFromRgb(Descriptor color, float alpha) {
        return new Color(
            getFloat(color, "Rd  ") / 255.0f,
            getFloat(color, "Grn ") / 255.0f,
            getFloat(color, "Bl  ") / 255.0f,
            alpha);
    }

    private static Color colorFromHsb(Descriptor color, float alpha) {
        float[] rgb = Colors.hsbToRgb(
            getUnitFloat(color, "H   ", 0.0f),
            getFloat(color, "Strt") / 100.0f,
            getFloat(color, "Brgh") / 100.0f);
        return new Color(rgb[0], rgb[1], rgb[2], alpha);
    }

    private static Color colorFromCmyk(Descriptor color, float alpha) {
        float[] rgb = Colors.getCmykColorSpace().toRGB(new float[] {
            getFloat(color, "Cyn "),
            getFloat(color, "Mgnt"),
            getFloat(color, "Ylw "),
            getFloat(color, "Blck")
        });
        return new Color(rgb[0], rgb[1], rgb[2], alpha);
    }

    private static Color colorFromLab(Descriptor color, float alpha) {
        float[] rgb = Colors.getLabColorSpace().toRGB(new float[] {
            getFloat(color, "Lmnc"),
            getFloat(color, "A   "),
            getFloat(color, "B   ")
        });
        return new Color(rgb[0], rgb[1], rgb[2], alpha);
    }

    private static Color colorFromGray(Descriptor color, float alpha) {
        float gray = Colors.linearRgbToRgb(getFloat(color, "Gry ") / 255.0f);
        return new Color(gray, gray, gray, alpha);
    }

    /**
     * Creates one or more path from a {@link PsdFile.ShapeMask} and add them to the
     * specified shape info.
     */
    static void createPaths(ShapeMask mask, ShapeInfo.Builder shapeInfo, AffineTransform transform) {
        Path2D.Float path = null;
        ShapeInfo.PathOp op = ShapeInfo.PathOp.ADD;

        // Each Bézier knot in a PSD is made of three points:
        //   - the anchor (the knot or point itself)
        //   - the control point before the anchor
        //   - the control point after the anchor
        //
        // PSD Bézier knots must be converted to moveTo/curveTo commands.
        // A curveTo() describes a cubic curve. To generate a curveTo() we
        // need three points:
        //   - the next anchor (the destination point of the curveTo())
        //   - the control point after the previous anchor
        //   - the control point before the next anchor

        PathType type = PathType.NONE;
        BezierKnot firstKnot = null;
        BezierKnot lastKnot = null;

        for (PathRecord record : mask.pathRecords) {
            switch (record.selector) {
                // A "LENGTH" record marks the beginning of a new sub-path
                // Closed subpath needs special handling at the end
                case CLOSED_SUBPATH_LENGTH:
                case OPEN_SUBPATH_LENGTH:
                    if (type == PathType.CLOSED) {
                        // If the previous subpath is of the closed type, close it now
                        addToPath(path, firstKnot, lastKnot);
                        path.closePath();
                    }

                    if (path != null) {
                        path.transform(transform);
                        shapeInfo.addSubPath(new ShapeInfo.SubPath.Builder().path(path).op(op).type(type).build());
                    }

                    SubPath subPath = (SubPath) record.data;
                    op = getPathOp(subPath.op);
                    path = new Path2D.Float(Path2D.WIND_EVEN_ODD, subPath.knotCount);

                    type = record.selector == OPEN_SUBPATH_LENGTH ? PathType.OPEN : PathType.CLOSED;
                    firstKnot = lastKnot = null;
                    break;
                // Open and closed subpath knots can be handled the same way
                // The linked/unlinked characteristic only matters to interactive
                // editors and we happily throw away that information
                case CLOSED_SUBPATH_KNOT_LINKED:
                case CLOSED_SUBPATH_KNOT_UNLINKED:
                case OPEN_SUBPATH_KNOT_LINKED:
                case OPEN_SUBPATH_KNOT_UNLINKED:
                    if (path == null) continue;
                    BezierKnot knot = (BezierKnot) record.data;
                    if (lastKnot == null) {
                        // If we just started a subpath we need to insert a moveTo()
                        // using the new anchor
                        path.moveTo(
                                Bytes.fixed8_24ToFloat(knot.anchorX),
                                Bytes.fixed8_24ToFloat(knot.anchorY));
                        firstKnot = knot;
                    } else {
                        // Otherwise let's curve to the new anchor
                        addToPath(path, knot, lastKnot);
                    }
                    lastKnot = knot;
                    break;
            }
        }

        // Close the subpath if needed
        if (type == PathType.CLOSED) {
            addToPath(path, firstKnot, lastKnot);
            path.closePath();
        }

        if (path != null) {
            path.transform(transform);
            shapeInfo.addSubPath(new ShapeInfo.SubPath.Builder().path(path).op(op).type(type).build());
        }
    }

    private static ShapeInfo.PathOp getPathOp(int op) {
        switch (op) {
            case SubPath.OP_XOR:
                return ShapeInfo.PathOp.EXCLUSIVE_OR;
            case SubPath.OP_MERGE:
                return ShapeInfo.PathOp.ADD;
            case SubPath.OP_SUBTRACT:
                return ShapeInfo.PathOp.SUBTRACT;
            case SubPath.OP_INTERSECT:
                return ShapeInfo.PathOp.INTERSECT;
        }
        return ShapeInfo.PathOp.ADD;
    }

    private static void addToPath(Path2D.Float path, BezierKnot toKnot, BezierKnot lastKnot) {
        float fromX = Bytes.fixed8_24ToFloat(lastKnot.anchorX);
        float fromY = Bytes.fixed8_24ToFloat(lastKnot.anchorY);
        float exitX = Bytes.fixed8_24ToFloat(lastKnot.controlExitX);
        float exitY = Bytes.fixed8_24ToFloat(lastKnot.controlExitY);
        float enterX = Bytes.fixed8_24ToFloat(toKnot.controlEnterX);
        float enterY = Bytes.fixed8_24ToFloat(toKnot.controlEnterY);
        float toX = Bytes.fixed8_24ToFloat(toKnot.anchorX);
        float toY = Bytes.fixed8_24ToFloat(toKnot.anchorY);

        if (exitX == fromX && exitY == fromY && enterX == toX && enterY == toY) {
            path.lineTo(toX, toY);
        } else {
            path.curveTo(exitX, exitY, enterX, enterY, toX, toY);
        }
    }
}
