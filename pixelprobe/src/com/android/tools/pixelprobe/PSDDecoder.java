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

package com.android.tools.pixelprobe;

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.ChunkIO;
import com.android.tools.chunkio.Chunked;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * To understand the data model described in this class, please refer to the
 * following Photoshop file specification document:
 * http://www.adobe.com/devnet-apps/photoshop/fileformatashtml
 */
@SuppressWarnings({"unused", "WeakerAccess", "UseJBColor"})
final class PSDDecoder extends Decoder {
    /**
     * Constant to convert centimeters to inches.
     */
    private static final float CENTIMETER_TO_INCH = 2.54f;

    /**
     * Regex used to parse descriptor paths.
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9]+)\\[([0-9]+)\\]");

    /**
     * List of all the blending modes supported by Photoshop. They are all identified
     * by a 4 character key. The BlendMode enum uses Photoshop's naming conventions
     * (linear dodge is for instance commonly known as "add").
     */
    private static final Map<String, BlendMode> sBlendModes = new HashMap<>();
    static {
        sBlendModes.put("pass", BlendMode.PASS_THROUGH);
        sBlendModes.put("norm", BlendMode.NORMAL);
        sBlendModes.put("diss", BlendMode.DISSOLVE);
        sBlendModes.put("dark", BlendMode.DARKEN);
        sBlendModes.put("mul ", BlendMode.MULTIPLY);
        sBlendModes.put("idiv", BlendMode.COLOR_BURN);
        sBlendModes.put("lbrn", BlendMode.LINEAR_BURN);
        sBlendModes.put("dkCl", BlendMode.DARKER_COLOR);
        sBlendModes.put("lite", BlendMode.LIGHTEN);
        sBlendModes.put("scrn", BlendMode.SCREEN);
        sBlendModes.put("div ", BlendMode.COLOR_DODGE);
        sBlendModes.put("lddg", BlendMode.LINEAR_DODGE);
        sBlendModes.put("lgCl", BlendMode.LIGHTER_COLOR);
        sBlendModes.put("over", BlendMode.OVERLAY);
        sBlendModes.put("sLit", BlendMode.SOFT_LIGHT);
        sBlendModes.put("hLit", BlendMode.HARD_LIGHT);
        sBlendModes.put("vLit", BlendMode.VIVID_LIGHT);
        sBlendModes.put("lLit", BlendMode.LINEAR_LIGHT);
        sBlendModes.put("pLit", BlendMode.PIN_LIGHT);
        sBlendModes.put("hMix", BlendMode.HARD_MIX);
        sBlendModes.put("diff", BlendMode.DIFFERENCE);
        sBlendModes.put("smud", BlendMode.EXCLUSION);
        sBlendModes.put("fsub", BlendMode.SUBTRACT);
        sBlendModes.put("fdiv", BlendMode.DIVIDE);
        sBlendModes.put("hue ", BlendMode.HUE);
        sBlendModes.put("sat ", BlendMode.SATURATION);
        sBlendModes.put("colr", BlendMode.COLOR);
        sBlendModes.put("lum ", BlendMode.LUMINOSITY);
        // blend modes stored in descriptors have different keys
        sBlendModes.put("Nrml",             BlendMode.NORMAL);
        sBlendModes.put("Dslv",             BlendMode.DISSOLVE);
        sBlendModes.put("Drkn",             BlendMode.DARKEN);
        sBlendModes.put("Mltp",             BlendMode.MULTIPLY);
        sBlendModes.put("CBrn",             BlendMode.COLOR_BURN);
        sBlendModes.put("linearBurn",       BlendMode.LINEAR_BURN);
        sBlendModes.put("darkerColor",      BlendMode.DARKER_COLOR);
        sBlendModes.put("Lghn",             BlendMode.LIGHTEN);
        sBlendModes.put("Scrn",             BlendMode.SCREEN);
        sBlendModes.put("CDdg",             BlendMode.COLOR_DODGE);
        sBlendModes.put("linearDodge",      BlendMode.LINEAR_DODGE);
        sBlendModes.put("lighterColor",     BlendMode.LIGHTER_COLOR);
        sBlendModes.put("Ovrl",             BlendMode.OVERLAY);
        sBlendModes.put("SftL",             BlendMode.SOFT_LIGHT);
        sBlendModes.put("HrdL",             BlendMode.HARD_LIGHT);
        sBlendModes.put("vividLight",       BlendMode.VIVID_LIGHT);
        sBlendModes.put("linearLight",      BlendMode.LINEAR_LIGHT);
        sBlendModes.put("pinLight",         BlendMode.PIN_LIGHT);
        sBlendModes.put("hardMix",          BlendMode.HARD_MIX);
        sBlendModes.put("Dfrn",             BlendMode.DIFFERENCE);
        sBlendModes.put("Xclu",             BlendMode.EXCLUSION);
        sBlendModes.put("blendSubtraction", BlendMode.SUBTRACT);
        sBlendModes.put("blendDivide",      BlendMode.DIVIDE);
        sBlendModes.put("H   ",             BlendMode.HUE);
        sBlendModes.put("Strt",             BlendMode.SATURATION);
        sBlendModes.put("Clr ",             BlendMode.COLOR);
        sBlendModes.put("Lmns",             BlendMode.LUMINOSITY);
    }

    private static Set<String> sAdjustmentLayers = new HashSet<>(20);
    static {
        sAdjustmentLayers.add("SoCo");
        sAdjustmentLayers.add("GdFl");
        sAdjustmentLayers.add("PtFl");
        sAdjustmentLayers.add("brit");
        sAdjustmentLayers.add("levl");
        sAdjustmentLayers.add("curv");
        sAdjustmentLayers.add("expA");
        sAdjustmentLayers.add("vibA");
        sAdjustmentLayers.add("hue ");
        sAdjustmentLayers.add("hue2");
        sAdjustmentLayers.add("blnc");
        sAdjustmentLayers.add("blwh");
        sAdjustmentLayers.add("phfl");
        sAdjustmentLayers.add("mixr");
        sAdjustmentLayers.add("clrL");
        sAdjustmentLayers.add("nvrt");
        sAdjustmentLayers.add("post");
        sAdjustmentLayers.add("thrs");
        sAdjustmentLayers.add("grdm");
        sAdjustmentLayers.add("selc");
    }

    /**
     * Possible text alignments found in text layers. Alignments are encoded as
     * numbers in PSD file. These numbers map to the indices of this array (0 is
     * left, 1 is right, etc.).
     */
    private static final String[] sAlignments = new String[] {
        "LEFT", "RIGHT", "CENTER", "JUSTIFY"
    };

    /**
     * The PSDDecoder only supports .psd files. There is no support for .psb
     * (large Photoshop documents) at the moment.
     */
    PSDDecoder() {
        super("psd", "photoshop");
    }

    @Override
    boolean accept(InputStream in) {
        // Read the header and make sure it is valid before we accept the stream
        return ChunkIO.read(in, FileHeader.class) != null;
    }

    @Override
    Image decode(InputStream in) {
        Image image = new Image();

        // The PSD class represents the entire document
        PSD psd = ChunkIO.read(in, PSD.class);

        if (psd != null) {
            // Extract and decode raw PSD data
            // The data is transformed into a generic Image API
            extractHeaderData(image, psd.header);
            resolveBlocks(image, psd.resources);
            extractLayers(image, psd.layersInfo);
            decodeImageData(image, psd);
            image.markValid();
        }

        return image;
    }

    /**
     * Extract layers information from the PSD raw data into the specified image.
     * Not all layers will be extracted.
     *
     * @param image The user image
     * @param layersInfo The document's layers information
     */
    private static void extractLayers(Image image, LayersInformation layersInfo) {
        LayersList layersList = layersInfo.layers;
        if (layersList == null) return;

        List<RawLayer> rawLayers = layersList.layers;
        Deque<Layer> stack = new LinkedList<>();

        int[] unnamedCounts = new int[Layer.Type.values().length];
        Arrays.fill(unnamedCounts, 1);

        // The layers count can be negative according to the Photoshop file specification
        // A negative count indicates that the first alpha channel contains the transparency
        // data for the merged (composited) result
        for (int i = Math.abs(layersList.count) - 1; i >= 0; i--) {
            RawLayer rawLayer = rawLayers.get(i);

            Map<String, LayerProperty> properties = rawLayer.extras.properties;
            LayerProperty nameProperty = properties.get(LayerProperty.KEY_NAME);
            LayerProperty sectionProperty = properties.get(LayerProperty.KEY_SECTION);

            // Assume we are decoding a bitmap (raster) layer by default
            Layer.Type type = Layer.Type.BITMAP;

            // If the section property is set, the layer is either the beginning
            // or the end of a group of layers
            if (sectionProperty != null) {
                LayerSection.Type groupType = ((LayerSection) sectionProperty.data).type;
                switch (groupType) {
                    // We don't care about recording the open state of groups
                    case OTHER:
                        continue;
                    case GROUP_OPENED:
                    case GROUP_CLOSED:
                        type = Layer.Type.GROUP;
                        break;
                    // A bounding layer is a hidden layer in Photoshop that marks
                    // the end of a group (the name is set to </Layer Group>
                    case BOUNDING:
                        stack.pollFirst();
                        continue;
                }
            } else {
                // The type of layer can only be identified by peeking at the various
                // properties set on that layer
                LayerProperty textProperty = properties.get(LayerProperty.KEY_TEXT);
                if (textProperty != null) {
                    type = Layer.Type.TEXT;
                } else {
                    LayerProperty pathProperty = properties.get(LayerProperty.KEY_VECTOR_MASK);
                    if (pathProperty != null) {
                        type = Layer.Type.PATH;
                    } else {
                        for (String key : sAdjustmentLayers) {
                            if (properties.containsKey(key)) {
                                type = Layer.Type.ADJUSTMENT;
                                break;
                            }
                        }
                    }
                }
            }

            // The layer's name appears twice in PSD files: first as a legacy
            // Pascal string, then as a Unicode string. We care a lot more about
            // the second one
            String name;
            if (nameProperty != null) {
                name = ((UnicodeString) nameProperty.data).value;
            } else {
                name = rawLayer.extras.name;
            }

            // Generate a name if the layer doesn't have one
            if (name.trim().isEmpty()) {
                name = "<" + getNameForType(type) + " " + unnamedCounts[type.ordinal()]++ + ">";
            }

            // Create the actual layer we return to the user
            Layer layer = new Layer(name, type);
            layer.setBounds(rawLayer.left, rawLayer.top, rawLayer.right, rawLayer.bottom);
            // Photoshop defines opacity in the 0..255 range
            layer.setOpacity(rawLayer.opacity / 255.0f);
            layer.setBlendMode(getBlendModeFromKey(rawLayer.blendMode));

            // Put the layer either in the image or in its parent group
            Layer parent = stack.peekFirst();
            if (parent == null) {
                image.addLayer(layer);
            } else {
                parent.addLayer(layer);
            }

            // Layer-specific decode steps
            switch (type) {
                case ADJUSTMENT:
                    break;
                case BITMAP:
                    decodeLayerImageData(layer, rawLayer, layersList.channels.get(i));
                    break;
                case GROUP:
                    stack.offerFirst(layer);
                    break;
                case PATH:
                    decodePathData(image, layer, properties);
                    break;
                case TEXT:
                    decodeTextData(image, layer, properties.get(LayerProperty.KEY_TEXT));
                    break;
            }

            extractLayerEffects(image, layer, rawLayer);
        }
    }

    private static String getNameForType(Layer.Type type) {
        switch (type) {
            case ADJUSTMENT:
                return "Adjustment";
            case BITMAP:
                return "Raster";
            case GROUP:
                return "Group";
            case PATH:
                return "Shape";
            case TEXT:
                return "Text";
        }
        return "Unnamed";
    }

    private enum Shadow {
        INNER("IrSh", "innerShadowMulti"),
        OUTER("DrSh", "dropShadowMulti");

        private final String mMultiName;
        private final String mName;

        Shadow(String name, String multiName) {
            mName = name;
            mMultiName = multiName;
        }

        String getMultiName() {
            return mMultiName;
        }

        String getName() {
            return mName;
        }
    }

    private static void extractLayerEffects(Image image, Layer layer, RawLayer rawLayer) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;

        boolean isMultiEffects = true;
        LayerProperty property = properties.get(LayerProperty.KEY_MULTI_EFFECTS);
        if (property == null) {
            property = properties.get(LayerProperty.KEY_EFFECTS);
            if (property == null) return;
            isMultiEffects = false;
        }

        LayerEffects layerEffects = (LayerEffects) property.data;

        boolean effectsEnabled = getBoolean(layerEffects.effects, "masterFXSwitch");
        if (!effectsEnabled) return;

        extractShadowEffects(image, layer, layerEffects, isMultiEffects, Shadow.INNER);
        extractShadowEffects(image, layer, layerEffects, isMultiEffects, Shadow.OUTER);
    }

    private static void extractShadowEffects(Image image, Layer layer,
            LayerEffects effects, boolean isMultiEffects, Shadow shadowType) {
        if (isMultiEffects) {
            DescriptorItem.ValueList list = get(effects.effects, shadowType.getMultiName());
            if (list != null) {
                for (int i = 0; i < list.count; i++) {
                    Descriptor descriptor = (Descriptor) list.items.get(i).data;
                    addShadowEffect(image, layer, descriptor, shadowType);
                }
            }
        } else {
            Descriptor descriptor = get(effects.effects, shadowType.getName());
            if (descriptor != null) {
                addShadowEffect(image, layer, descriptor, shadowType);
            }
        }
    }

    private static void addShadowEffect(Image image, Layer layer,
            Descriptor descriptor, Shadow type) {
        float scale = image.getVerticalResolution() / 72.0f;

        float blur = (float) getUnitDouble(descriptor, "blur", scale);
        float angle = (float) getUnitDouble(descriptor, "lagl", scale);
        float distance = (float) getUnitDouble(descriptor, "Dstn", scale);
        float opacity = (float) getUnitDouble(descriptor, "Opct", scale);

        Color color = getColor(descriptor);
        BlendMode blendMode = getBlendModeFromKey(getString(descriptor, "Md  "));

        layer.getEffects().addShadow(blur, angle, distance, opacity,
                color, blendMode, type == Shadow.INNER);
    }

    private static <T> T get(Descriptor descriptor, String path) {
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

            DescriptorItem item = currentDescriptor.items.get(element);
            if (item == null) break;

            Object data = item.value.data;
            if (data == null) break;

            if (data instanceof DescriptorItem.ValueList) {
                if (index >= 0) {
                    data = ((DescriptorItem.ValueList) data).items.get(index).data;
                }
            } else if (data instanceof DescriptorItem.Reference) {
                if (index >= 0) {
                    data = ((DescriptorItem.Reference) data).items.get(index).data;
                }
            }

            if (data instanceof Descriptor) {
                result = currentDescriptor = (Descriptor) data;
            } else if (data instanceof FixedString || data instanceof UnicodeString ||
                    data instanceof DescriptorItem.Enumerated) {
                result = data.toString();
            } else if (data instanceof FixedByteArray) {
                result = ((FixedByteArray) data).value;
            } else {
                result = data;
            }
        }

        //noinspection unchecked
        return (T) result;
    }

    private static Color getColor(Descriptor descriptor) {
        return new Color(
                (float) getDouble(descriptor, "Clr .Rd  ") / 255.0f,
                (float) getDouble(descriptor, "Clr .Grn ") / 255.0f,
                (float) getDouble(descriptor, "Clr .Bl  ") / 255.0f);
    }

    private static String getString(Descriptor descriptor, String path) {
        return get(descriptor, path);
    }

    private static double getDouble(Descriptor descriptor, String path) {
        return (Double) get(descriptor, path);
    }

    private static double getUnitDouble(Descriptor descriptor, String path, float scale) {
        DescriptorItem.UnitDouble value = get(descriptor, path);
        if (DescriptorItem.UnitDouble.PERCENT.equals(value.unit)) {
            return value.value / 100.0;
        } else if (DescriptorItem.UnitDouble.POINTS.equals(value.unit)) {
            return value.value * scale;
        }

        return value.value;
    }

    private static boolean getBoolean(Descriptor descriptor, String path) {
        return (Boolean) get(descriptor, path);
    }

    /**
     * Decodes the text data of a text layer.
     */
    private static void decodeTextData(Image image, Layer layer, LayerProperty property) {
        TypeToolObject typeTool = (TypeToolObject) property.data;

        TextInfo info = new TextInfo();
        layer.setTextInfo(info);

        // The text data uses \r to indicate new lines
        // Replace them with \n instead
        info.setText(getString(typeTool.text, TypeToolObject.KEY_TEXT).replace('\r', '\n'));

        // Compute the text layer's transform
        AffineTransform transform = new AffineTransform(
                typeTool.xx, typeTool.yx,  // scaleX, shearY
                typeTool.xy, typeTool.yy,  // shearX, scaleY
                typeTool.tx, typeTool.ty); // translateX, translateY
        info.setTransform(transform);

        float resolutionScale = image.getVerticalResolution() / 72.0f;

        // Retrieves the text's bounding box. The bounding box is required
        // to properly apply alignment properties. The translation found
        // in the affine transform above gives us the origin for text
        // alignment and the bounding box gives us the actual position of
        // the text box from that origin
        DescriptorItem.UnitDouble left = get(typeTool.text, "boundingBox.Left");
        DescriptorItem.UnitDouble top = get(typeTool.text, "boundingBox.Top ");
        DescriptorItem.UnitDouble right = get(typeTool.text, "boundingBox.Rght");
        DescriptorItem.UnitDouble bottom = get(typeTool.text, "boundingBox.Btom");
        info.setBounds(
                unitToPx(left, resolutionScale), unitToPx(top, resolutionScale),
                unitToPx(right, resolutionScale), unitToPx(bottom, resolutionScale));

        // Retrieves styles from the structured text data
        byte[] data = get(typeTool.text, TypeToolObject.KEY_ENGINE_DATA);

        PSDTextEngine parser = new PSDTextEngine();
        PSDTextEngine.MapProperty properties = parser.parse(data);

        // Find the list of fonts
        List<PSDTextEngine.Property<?>> fontProperties = ((PSDTextEngine.ListProperty)
                properties.get("ResourceDict.FontSet")).getValue();
        List<String> fonts = new ArrayList<>(fontProperties.size());
        fonts.addAll(fontProperties.stream()
                .map(element -> ((PSDTextEngine.MapProperty) element).get("Name").toString())
                .collect(Collectors.toList()));

        // By default, Photoshop creates unstyled runs that rely on the
        // default stylesheet. Look it up.
        int defaultSheetIndex = ((PSDTextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalStyleSheet")).getValue();
        PSDTextEngine.MapProperty defaultSheet = (PSDTextEngine.MapProperty) properties.get(
                "ResourceDict.StyleSheetSet[" + defaultSheetIndex + "].StyleSheetData");

        // List of style runs
        int pos = 0;
        int[] runs = ((PSDTextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunLengthArray")).toIntArray();
        List<PSDTextEngine.Property<?>> styles = ((PSDTextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            PSDTextEngine.MapProperty style = (PSDTextEngine.MapProperty) styles.get(i);
            PSDTextEngine.MapProperty sheet = (PSDTextEngine.MapProperty) style.get("StyleSheet.StyleSheetData");

            // Get the typeface, font size and color from each style run
            // If the run does not have a style, fall back to the default stylesheet
            int index = ((PSDTextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Font")).getValue();
            float size = ((PSDTextEngine.FloatProperty)
                    getFromMap(sheet, defaultSheet, "FontSize")).getValue() / resolutionScale;
            float[] rgb = ((PSDTextEngine.ListProperty)
                    getFromMap(sheet, defaultSheet, "FillColor.Values")).toFloatArray();
            int tracking = ((PSDTextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Tracking")).getValue();

            TextInfo.StyleRun styleRun = info.addStyleRun(pos, pos += runs[i]);
            styleRun.setStyle(fonts.get(index), size, new Color(rgb[1], rgb[2], rgb[3], rgb[0]));
            styleRun.setTracking(tracking / 1000.0f);
        }

        // Thankfully there's always a default paragraph stylesheet
        defaultSheetIndex = ((PSDTextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalParagraphSheet")).getValue();
        defaultSheet = (PSDTextEngine.MapProperty) properties.get(
                "ResourceDict.ParagraphSheetSet[" + defaultSheetIndex + "].Properties");

        // List of paragraph runs
        pos = 0;
        runs = ((PSDTextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunLengthArray")).toIntArray();
        styles = ((PSDTextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            PSDTextEngine.MapProperty style = (PSDTextEngine.MapProperty) styles.get(i);
            PSDTextEngine.MapProperty sheet = (PSDTextEngine.MapProperty) style.get("ParagraphSheet.Properties");

            int justification = ((PSDTextEngine.IntProperty)
                    getFromMap(sheet, defaultSheet, "Justification")).getValue();

            TextInfo.ParagraphRun styleRun = info.addParagraphRun(pos, pos += runs[i]);
            styleRun.setAlignment(
                    TextInfo.ParagraphRun.Alignment.valueOf(sAlignments[justification]));
        }
    }

    private static double unitToPx(DescriptorItem.UnitDouble d, float resolutionScale) {
        if (DescriptorItem.UnitDouble.PIXELS.equals(d.unit)) {
            return d.value;
        } else if (DescriptorItem.UnitDouble.POINTS.equals(d.unit)) {
            return d.value * resolutionScale;
        }
        throw new DecoderException("Cannot convert from unit: " + d.unit, null);
    }

    /**
     * Attempts retrieve a value from "map", then from "defaultMap"
     * if the value is null.
     */
    private static PSDTextEngine.Property<?> getFromMap(PSDTextEngine.MapProperty map, PSDTextEngine.MapProperty defaultMap, String name) {
        PSDTextEngine.Property<?> property = map.get(name);
        if (property == null) {
            property = defaultMap.get(name);
        }
        return property;
    }

    /**
     * Simple enum used to track the state of path records.
     * See decodePathData().
     */
    private enum Subpath {
        NONE,
        CLOSED,
        OPEN
    }

    /**
     * Decodes the path data for a given layer. The path data is encoded as a
     * series of path records that are fairly straightforward to interpret.
     */
    private static void decodePathData(Image image, Layer layer,
            Map<String, LayerProperty> properties) {
        // We are guaranteed to have this property if this method is called
        LayerProperty property = properties.get(LayerProperty.KEY_VECTOR_MASK);

        // Photoshop only uses the even/odd fill rule
        GeneralPath path = new GeneralPath(Path2D.WIND_EVEN_ODD);

        // Each Bézier knot in a PSD is made of three points:
        //   - the anchor (the know or point itself)
        //   - the control point before the anchor
        //   - the control point after the anchor
        //
        // PSD Bézier knots must be converted to moveTo/curveTo commands.
        // A curveTo() describes a cubic curve. To generate a curveTo() we
        // need three points:
        //   - the next anchor (the destination point of the curveTo())
        //   - the control point after the previous anchor
        //   - the control point before the next anchor

        Subpath currentSubpath = Subpath.NONE;
        PathRecord.BezierKnot firstKnot = null;
        PathRecord.BezierKnot lastKnot = null;

        VectorMask mask = (VectorMask) property.data;
        for (PathRecord record : mask.pathRecords) {
            switch (record.selector) {
                // A "LENGTH" record marks the beginning of a new sub-path
                // Closed subpath needs special handling at the end
                case PathRecord.CLOSED_SUBPATH_LENGTH:
                    if (currentSubpath == Subpath.CLOSED) {
                        // If the previous subpath is of the closed type, close it now
                        addToPath(path, firstKnot, lastKnot);
                    }
                    currentSubpath = Subpath.CLOSED;
                    firstKnot = lastKnot = null;
                    break;
                // New subpath
                case PathRecord.OPEN_SUBPATH_LENGTH:
                    if (currentSubpath == Subpath.CLOSED) {
                        // Close the previous subpath if needed
                        addToPath(path, firstKnot, lastKnot);
                    }
                    currentSubpath = Subpath.OPEN;
                    firstKnot = lastKnot = null;
                    break;
                // Open and closed subpath knots can be handled the same way
                // The linked/unlinked characteristic only matters to interactive
                // editors and we happily throw away that information
                case PathRecord.CLOSED_SUBPATH_KNOT_LINKED:
                case PathRecord.CLOSED_SUBPATH_KNOT_UNLINKED:
                case PathRecord.OPEN_SUBPATH_KNOT_LINKED:
                case PathRecord.OPEN_SUBPATH_KNOT_UNLINKED:
                    PathRecord.BezierKnot knot = (PathRecord.BezierKnot) record.data;
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
        if (currentSubpath == Subpath.CLOSED) {
            addToPath(path, firstKnot, lastKnot);
        }

        // Vector data is stored in relative coordinates in PSD files
        // For instance, a point at 0.5,0.5 is in the center of the document
        // We apply a transform to convert these relative coordinates into
        // absolute pixel coordinates
        Rectangle2D bounds = layer.getBounds();

        AffineTransform transform = new AffineTransform();
        transform.translate(-bounds.getX(), -bounds.getY());
        transform.scale(image.getWidth(), image.getHeight());

        Shape shape = path.createTransformedShape(transform);
        layer.setPath(shape);

        if (properties.containsKey(LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR)) {
            SolidColorAdjustment adjustment = (SolidColorAdjustment)
                    properties.get(LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR).data;
            layer.setPathColor(getColor(adjustment.solidColor));
        }
    }

    private static void addToPath(GeneralPath path, PathRecord.BezierKnot firstKnot,
            PathRecord.BezierKnot lastKnot) {
        path.curveTo(
                Bytes.fixed8_24ToFloat(lastKnot.controlExitX),
                Bytes.fixed8_24ToFloat(lastKnot.controlExitY),
                Bytes.fixed8_24ToFloat(firstKnot.controlEnterX),
                Bytes.fixed8_24ToFloat(firstKnot.controlEnterY),
                Bytes.fixed8_24ToFloat(firstKnot.anchorX),
                Bytes.fixed8_24ToFloat(firstKnot.anchorY));
    }

    /**
     * Decodes the image data of a specific layer. The image data is encoded
     * separately for each channel. Each channel could theoretically have its
     * own encoding so let's pretend this can happen.
     * There are 4 encoding formats: RAW, RLE, ZIP and ZIP without prediction.
     * Since we have yet to encounter the ZIP case, we only support RAW and RLE.
     */
    private static void decodeLayerImageData(Layer layer, RawLayer rawLayer, ChannelsContainer channelsList) {
        Rectangle2D bounds = layer.getBounds();
        BufferedImage bitmap = BitmapDecoder.createBitmap(
                (int) bounds.getWidth(), (int) bounds.getHeight(), rawLayer.channels);

        for (int i = 0; i < rawLayer.channelsInfo.size(); i++) {
            ChannelInformation info = rawLayer.channelsInfo.get(i);
            if (info.id < -1) continue; // skip mask channel

            ChannelImageData imageData = channelsList.imageData.get(i);
            switch (imageData.compression) {
                case RAW:
                    // TODO: don't assume 8 bit depth
                    BitmapDecoder.decodeChannelRaw(imageData.data, 0, info.id, bitmap, 8);
                    break;
                case RLE:
                    int offset = (int) (bounds.getHeight() * 2);
                    BitmapDecoder.decodeChannelRLE(imageData.data, offset, info.id, bitmap);
                    break;
                case ZIP:
                    break;
                case ZIP_NO_PREDICTION:
                    break;
            }
        }

        layer.setBitmap(bitmap);
    }

    private static BlendMode getBlendModeFromKey(String mode) {
        BlendMode blendMode = sBlendModes.get(mode);
        return blendMode != null ? blendMode : BlendMode.NORMAL;
    }

    private static void extractHeaderData(Image image, FileHeader header) {
        image.setDimensions(header.width, header.height);
        image.setColorMode(header.colorMode);
    }

    /**
     * Image resource blocks contain a lot of information in PSD files but not
     * all of it is interesting to us. Here we only look for the few blocks that
     * we actually care about.
     */
    private static void resolveBlocks(Image image, ImageResources resources) {
        for (ImageResourceBlock block : resources.blocks) {
            switch (block.id) {
                case GuidesResourceBlock.ID:
                    extractGuides((GuidesResourceBlock) block.data, image);
                    break;
                case ThumbnailResourceBlock.ID:
                    extractThumbnail((ThumbnailResourceBlock) block.data, image);
                    break;
                case ResolutionInfoBlock.ID:
                    // Extract dpi information, required to properly handle text
                    extractResolution((ResolutionInfoBlock) block.data, image);
                    break;
            }
        }
    }

    /**
     * Extracts the horizontal and vertical dpi information from the PSD
     * file. This information is important to properly handle the point
     * unit used in text layers.
     */
    private static void extractResolution(ResolutionInfoBlock resolutionBlock, Image image) {
        float hRes = Bytes.fixed16_16ToFloat(resolutionBlock.horizontalResolution);
        ResolutionUnit unit = resolutionBlock.horizontalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            hRes *= CENTIMETER_TO_INCH;
        }

        float vRes = Bytes.fixed16_16ToFloat(resolutionBlock.verticalResolution);
        unit = resolutionBlock.verticalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            vRes *= CENTIMETER_TO_INCH;
        }

        image.setResolution(hRes, vRes);
    }

    private static void extractThumbnail(ThumbnailResourceBlock thumbnailBlock, Image image) {
        try {
            image.setThumbnail(ImageIO.read(new ByteArrayInputStream(thumbnailBlock.thumbnail)));
        } catch (IOException e) {
            // Ignore
        }
    }

    private static void extractGuides(GuidesResourceBlock guidesBlock, Image image) {
        // Guides are stored in a 27.5 fixed-point format, kind of weird
        for (GuideBlock block : guidesBlock.guides) {
            image.addGuide(new Guide(block.orientation, block.location / 32.0f));
        }
    }

    /**
     * Decodes the flattened image data. Just like layers, the data is stored as
     * separate planes for each channel. The difference is that the encoding is
     * the same for all channels. The ZIP formats are not supported.
     */
    private static void decodeImageData(Image image, PSD psd) {
        BufferedImage bitmap = null;
        switch (psd.imageData.compression) {
            case RAW:
                bitmap = BitmapDecoder.decodeRaw(psd.imageData.data, 0,
                        image.getWidth(), image.getHeight(), psd.header.channels, psd.header.depth);
                break;
            case RLE:
                int offset = image.getHeight() * psd.header.channels * 2;
                bitmap = BitmapDecoder.decodeRLE(
                        psd.imageData.data, offset,
                        image.getWidth(), image.getHeight(), psd.header.channels);
                break;
            case ZIP:
                break;
            case ZIP_NO_PREDICTION:
                break;
        }
        image.setFlattenedBitmap(bitmap);
    }

    /**
     * This class describes the structure of a PSD file.
     * Read on to learn how a PSD file is stored on disk.
     */
    @Chunked
    static final class PSD {
        @Chunk
        FileHeader header;
        @Chunk
        ColorData colorData;
        @Chunk
        ImageResources resources;
        @Chunk
        LayersInformation layersInfo;
        @Chunk
        ImageData imageData;
    }

    /**
     * PSD header. A few magic values and important information
     * about the image's dimensions, color depth and mode.
     */
    @Chunked
    static final class FileHeader {
        // Magic marker
        @Chunk(byteCount = 4, match = "\"8BPS\"")
        String signature;

        // Version is always 1
        @Chunk(match = "1")
        short version;

        // 6 reserved bytes that must always be set to 0
        @Chunk(byteCount = 6)
        Void reserved;

        @Chunk(byteCount = 2)
        int channels;

        // Height comes before width here
        @Chunk
        int height;
        @Chunk
        int width;

        @Chunk
        short depth;
        // We only support RGB documents
        @Chunk(byteCount = 2, match = "ColorMode.RGB")
        ColorMode colorMode;
    }

    /**
     * Only useful for paletted images.
     */
    @Chunked
    static final class ColorData {
        @Chunk(byteCount = 4)
        long length;

        // We don't care about color data so let's skip it
        // We should care if we supported paletted color modes
        @SuppressWarnings("unused")
        @Chunk(dynamicByteCount = "colorData.length")
        Void data;
    }

    /**
     * The image resources section is a mix-bag of a lot of stuff
     * (thumbnail, guides, printing data, EXIF, XMPP, etc.).
     * This section is divided into typed blocks.
     */
    @Chunked
    static final class ImageResources {
        @Chunk(byteCount = 4)
        long length;

        // Each block has a padded size to make it even
        // Specifying how many bytes we want to read upfront
        // ensures we'll be able to successfully read the rest
        // of the document
        @Chunk(dynamicByteCount = "imageResources.length")
        List<ImageResourceBlock> blocks;
    }

    /**
     * An image resource block has a type (or ID) and an optional
     * name. We'll use the ID to find the blocks we want.
     */
    @Chunked
    static final class ImageResourceBlock {
        @Chunk(byteCount = 4, match = "\"8BIM\"")
        String signature;

        @Chunk(byteCount = 2)
        int id;

        // The name is stored as a Pascal string with even padding
        // The padding takes into account the length byte
        @Chunk(byteCount = 1)
        short nameLength;
        @Chunk(dynamicByteCount = "imageResourceBlock.nameLength")
        String name;
        @SuppressWarnings("unused")
        @Chunk(dynamicByteCount = "Math.max(1, imageResourceBlock.nameLength & 1)")
        Void padding;

        // The actual byte length of the block
        @Chunk(byteCount = 4)
        long length;

        // The length must be padded to make it even if we want to
        // successfully read a block. We could also add a Void pad after.
        @Chunk(dynamicByteCount = "imageResourceBlock.length + (imageResourceBlock.length & 1)",
            switchType = {
                @Chunk.Case(test = "imageResourceBlock.id == 0x0408",
                        type = GuidesResourceBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x040C",
                        type = ThumbnailResourceBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x03ED",
                        type = ResolutionInfoBlock.class)
            }
        )
        Object data;
    }

    /**
     * Stores the documents guides. Pretty straightforward.
     */
    @Chunked
    static final class GuidesResourceBlock {
        static final int ID = 0x0408;

        @Chunk
        int version;

        // Reserved chunk of 8 bytes. Ignore.
        @Chunk(byteCount = 8)
        Void future;

        @Chunk
        int guideCount;

        @Chunk(dynamicSize = "guidesResourceBlock.guideCount")
        List<GuideBlock> guides;
    }

    /**
     * An actual guide. It has a location (fixed-point 27.5 format)
     * and an orientation.
     */
    @Chunked
    static final class GuideBlock {
        @Chunk
        int location;

        @Chunk(byteCount = 1)
        Guide.Orientation orientation;
    }

    /**
     * Resolution units for the resolution info block.
     */
    enum ResolutionUnit {
        UNKNOWN,
        PIXEL_PER_INCH,
        PIXEL_PER_CM
    }

    /**
     * Display units for the resolution info block.
     * We don't really care about this unit.
     */
    enum DisplayUnit {
        UNKNOWN,
        INCHES,
        CENTIMETERS,
        POINTS,
        PICAS,
        COLUMNS
    }

    /**
     * Stores the document's vertical and horizontal resolution
     * as well as information on how to display dimensions in the UI.
     */
    @Chunked
    static final class ResolutionInfoBlock {
        static final int ID = 0x03ED;

        @Chunk
        int horizontalResolution;
        @Chunk(byteCount = 2)
        ResolutionUnit horizontalUnit;
        @Chunk(byteCount = 2)
        DisplayUnit widthUnit;

        @Chunk
        int verticalResolution;
        @Chunk(byteCount = 2)
        ResolutionUnit verticalUnit;
        @Chunk(byteCount = 2)
        DisplayUnit heightUnit;
    }

    /**
     * Thumbnails can be stored as RAW data or JPEGs.
     * We currently only support the JPEG format.
     */
    @Chunked
    static final class ThumbnailResourceBlock {
        static final int ID = 0x040C;

        // RAW=0, JPEG=1, we only want JPEG
        @Chunk(match = "1")
        int format;

        @Chunk(byteCount = 4)
        long width;
        @Chunk(byteCount = 4)
        long height;

        @Chunk(byteCount = 4)
        long rowBytes;
        @Chunk(byteCount = 4)
        long size;
        @Chunk(byteCount = 4)
        long compressedSize;

        // JPEG guarantees 24bpp and 1 plane
        @Chunk(match = "24")
        short bpp;
        @Chunk(match = "1")
        short planes;

        @Chunk(dynamicByteCount = "thumbnailResourceBlock.compressedSize")
        byte[] thumbnail;
    }

    /**
     * The layers information section contains the list of layers
     * and a lot of data for each layer.
     */
    @Chunked
    static final class LayersInformation {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "layersInformation.length",
                readIf = "layersInformation.length > 0")
        LayersList layers;
    }

    /**
     * The list of layers is actually made of two lists in the PSD
     * file. First, the description and extra data for each layer
     * (name, bounds, etc.). Then a bitmap representation for each
     * layer, as a series of independently encoded channels.
     */
    @Chunked
    static final class LayersList {
        @Chunk(byteCount = 4)
        long length;

        // The count can be negative, which means the first
        // alpha channel contains the transparency data for
        // the flattened image. This means we must ensure
        // we always take the absolute value of the layer
        // count to build lists
        @Chunk
        short count;

        @Chunk(dynamicSize = "Math.abs(layersList.count)")
        List<RawLayer> layers;

        @Chunk(dynamicSize = "Math.abs(layersList.count)")
        List<ChannelsContainer> channels;
    }

    /**
     * A layer contains a few static properties and a list of
     * keyed "extras", The extras are crucial for non-bitmap
     * layers. They also contain layer effects (drop shadows,
     * inner glow, etc.).
     */
    @Chunked
    static final class RawLayer {
        // Firs we have the layer's bounds, in pixels,
        // in absolute image coordinates
        @Chunk(byteCount = 4)
        long top;
        @Chunk(byteCount = 4)
        long left;
        @Chunk(byteCount = 4)
        long bottom;
        @Chunk(byteCount = 4)
        long right;

        // The channels count, 3 or 4 in our case since we
        // only support RGB files
        @Chunk(byteCount = 2)
        short channels;
        // Important stuff in there, read on
        @Chunk(dynamicSize = "rawLayer.channels")
        List<ChannelInformation> channelsInfo;

        @Chunk(byteCount = 4, match = "\"8BIM\"")
        String signature;
        @Chunk(byteCount = 4)
        String blendMode;

        // The opacity is stored as an unsigned byte,
        // from 0 (transparent) to 255 (opaque)
        @Chunk(byteCount = 1)
        short opacity;
        @Chunk(byteCount = 1)
        byte clipping;
        @Chunk(byteCount = 1)
        byte flags;

        // Padding gunk
        @Chunk(byteCount = 1)
        Void filler;

        // The number of bytes taken by all the extras
        @Chunk(byteCount = 4)
        long extraLength;

        @Chunk(dynamicByteCount = "rawLayer.extraLength")
        LayerExtras extras;
    }

    /**
     * The layer's extras contains important values such as
     * the layer's name and a series of named properties.
     */
    @Chunked
    static final class LayerExtras {
        @Chunk
        MaskAdjustment maskAdjustment;

        @Chunk(byteCount = 4)
        long blendRangesLength;

        // The first blend range is always composite gray
        @Chunk(dynamicByteCount = "layerExtras.blendRangesLength")
        List<BlendRange> layerBlendRanges;

        // The layer's name is stored as a Pascal string,
        // padded to a multiple of 4 bytes
        @Chunk(byteCount = 1)
        short nameLength;
        @Chunk(dynamicByteCount = "layerExtras.nameLength")
        String name;
        @Chunk(dynamicByteCount =
                "((layerExtras.nameLength + 4) & ~3) - (layerExtras.nameLength + 1)")
        Void namePadding;

        @Chunk(key = "layerProperty.key")
        Map<String, LayerProperty> properties;
    }

    /**
     * Layer properties encode a lot of interesting attributes but we will
     * only decode a few of them.
     */
    @Chunked
    static final class LayerProperty {
        // The property holds the layer's effects (drop shadows, etc.)
        static final String KEY_EFFECTS = "lfx2";
        // The property holds the layer's stacked effects (multiple
        // drop shadows, etc.) If this property is present, the
        // "lfx2" property above should not be present
        static final String KEY_MULTI_EFFECTS = "lmfx";
        // Indicates that this layer is a section (start or end of a layer group)
        static final String KEY_SECTION = "lsct";
        // The property holds the Unicode name of the layer
        static final String KEY_NAME = "luni";
        // The property holds the layer's ID
        static final String KEY_ID = "lyid";
        // The property holds the solid color adjustment information
        static final String KEY_ADJUSTMENT_SOLID_COLOR = "SoCo";
        // The property holds the text information (styles, text data, etc.)
        static final String KEY_TEXT = "TySh";
        // The property holds the vector data
        static final String KEY_VECTOR_MASK = "vmsk";
        // The layer has a depth of 16 bit per channel
        static final String KEY_LAYER_DEPTH_16 = "Lr16";
        // The layer has a depth of 32 bit per channel
        static final String KEY_LAYER_DEPTH_32 = "Lr32";

        @Chunk(byteCount = 4)
        String signature;

        @Chunk(byteCount = 4)
        String key;

        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "layerProperty.length",
            switchType = {
                @Chunk.Case(test = "layerProperty.key.equals(\"lmfx\")", type = LayerEffects.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"lfx2\")", type = LayerEffects.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"lsct\")", type = LayerSection.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"luni\")", type = UnicodeString.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"SoCo\")", type = SolidColorAdjustment.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"TySh\")", type = TypeToolObject.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"vmsk\")", type = VectorMask.class)
            }
        )
        Object data;
    }

    @Chunked
    static final class SolidColorAdjustment {
        @Chunk
        int version;

        @Chunk
        Descriptor solidColor;
    }

    @Chunked
    static final class LayerEffects {
        @Chunk(match = "0")
        int version;
        @Chunk
        int descriptorVersion;

        @Chunk
        Descriptor effects;
    }

    /**
     * The TypeToolObject layer property contains all the data needed to
     * render a text layer.
     */
    @Chunked
    static final class TypeToolObject {
        // Descriptor that holds the actual text
        static final String KEY_TEXT = "Txt ";
        // Descriptor that holds structured text data used for styling, see PSDTextEngine
        static final String KEY_ENGINE_DATA = "EngineData";
        // Descriptor that holds the text's bounding box, required to apply alignment
        static final String KEY_BOUNDING_BOX = "boundingBox";

        @Chunk
        short version;

        // The text's transform (translation, scale and shear)
        @Chunk
        double xx;
        @Chunk
        double xy;
        @Chunk
        double yx;
        @Chunk
        double yy;
        @Chunk
        double tx;
        @Chunk
        double ty;

        @Chunk
        short textVersion;

        @Chunk
        int testDescriptorVersion;

        // The descriptor is a horrifyingly generic object
        // that happens to hold important data (the actual
        // text, styling info, etc.)
        @Chunk
        Descriptor text;

        @Chunk
        short warpVersion;

        @Chunk
        int warpDescriptorVersion;

        @Chunk
        Descriptor warp;

        // These always seem to be set to 0
        @Chunk
        int left;
        @Chunk
        int top;
        @Chunk
        int right;
        @Chunk
        int bottom;
    }

    /**
     * A descriptor is a generic way to represent typed and named
     * data. Photoshop seems to abuse descriptor quite a bit in
     * few places, particularly for text layers.
     */
    @Chunked
    static final class Descriptor {
        // Name from classID, usually not set
        @Chunk
        UnicodeString name;

        // ClassID, whatever that is
        @Chunk
        MinimumString classId;

        // Number of items in the descriptor
        @Chunk
        int count;

        @Chunk(dynamicSize = "descriptor.count", key = "descriptorItem.key")
        Map<String, DescriptorItem> items;

        @Override
        public String toString() {
            return "Descriptor{" +
                    "name=" + name +
                    ", items=" + StringUtils.join(items.values(), ", ") +
                    '}';
        }
    }

    /**
     * A descriptor item has a name and a value.
     * The value itself is typed.
     */
    @Chunked
    static final class DescriptorItem {
        /**
         * Enum reference (see Reference descriptor item)
         */
        @Chunked
        static final class Enumerated {
            @Chunk
            MinimumString type;
            @Chunk
            MinimumString value;

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        /**
         * A double value with a unit.
         */
        @Chunked
        static final class UnitDouble {
            static final String POINTS = "#Pnt";
            static final String MILLIMETERS = "#Mlm";
            static final String ANGLE_DEGREES = "#Ang";
            static final String DENSITY = "#Rsl";
            static final String RELATIVE = "#Rlt";
            static final String COERCED = "#Nne";
            static final String PERCENT = "#Prc";
            static final String PIXELS = "#Pxl";

            @Chunk(byteCount = 4)
            String unit;

            @Chunk
            double value;

            @Override
            public String toString() {
                return Double.toString(value);
            }
        }

        /**
         * A float value with a unit. Possible units:
         * #Pnt, points
         * #Mlm, millimeters
         * #Ang, angle in degrees
         * #Rsl, density in inch
         * #Rlt, distance base 72ppi
         * #Nne, coerced
         * #Prc, percent
         * #Pxl, pixels
         */
        @Chunked
        static final class UnitFloat {
            @Chunk(byteCount = 4)
            String unit;

            @Chunk
            float value;

            @Override
            public String toString() {
                return Float.toString(value);
            }
        }

        /**
         * A class name + class ID. Used in references.
         */
        @Chunked
        static final class ClassType {
            @Chunk
            UnicodeString name;
            @Chunk
            MinimumString classId;
        }

        /**
         * A property reference.
         */
        @Chunked
        static final class Property {
            @Chunk
            ClassType classType;
            @Chunk
            MinimumString keyId;
        }

        /**
         * A reference is actually a list of items.
         * Hard to say why.
         */
        @Chunked
        static final class Reference {
            @Chunked
            static final class Item {
                @Chunk(byteCount = 4)
                String type;

                @Chunk(switchType = {
                    @Chunk.Case(test = "item.type.equals(\"Enmr\")", type = Enumerated.class),
                    @Chunk.Case(test = "item.type.equals(\"Clss\")", type = ClassType.class),
                    @Chunk.Case(test = "item.type.equals(\"Idnt\")", type = int.class),
                    @Chunk.Case(test = "item.type.equals(\"indx\")", type = int.class),
                    @Chunk.Case(test = "item.type.equals(\"name\")", type = UnicodeString.class),
                    @Chunk.Case(test = "item.type.equals(\"prop\")", type = Property.class),
                    @Chunk.Case(test = "item.type.equals(\"rele\")", type = int.class)
                })
                Object data;
            }

            @Chunk
            int count;

            @Chunk(dynamicSize = "reference.count")
            List<Item> items;
        }

        /**
         * Holds a list of descriptor values.
         */
        @Chunked
        static final class ValueList {
            @Chunk
            int count;

            @Chunk(dynamicSize = "valueList.count")
            List<DescriptorItem.Value> items;

            @Override
            public String toString() {
                return "ValueList{" +
                        "items=" + StringUtils.join(items, ", ") +
                        '}';
            }
        }

        /**
         * The value of a descriptor. A value can be one of many types,
         * see the annotation below to find out the exact list.
         */
        @Chunked
        static final class Value {
            @Chunk(byteCount = 4)
            String type;

            @Chunk(switchType = {
                @Chunk.Case(test = "value.type.equals(\"alis\")", type = FixedString.class),
                @Chunk.Case(test = "value.type.equals(\"bool\")", type = boolean.class),
                @Chunk.Case(test = "value.type.equals(\"comp\")", type = long.class),
                @Chunk.Case(test = "value.type.equals(\"doub\")", type = double.class),
                @Chunk.Case(test = "value.type.equals(\"enum\")", type = Enumerated.class),
                @Chunk.Case(test = "value.type.equals(\"GlbC\")", type = ClassType.class),
                @Chunk.Case(test = "value.type.equals(\"GlbO\")", type = Descriptor.class),
                @Chunk.Case(test = "value.type.equals(\"long\")", type = int.class),
                @Chunk.Case(test = "value.type.equals(\"obj\" )", type = Reference.class),
                @Chunk.Case(test = "value.type.equals(\"Objc\")", type = Descriptor.class),
                @Chunk.Case(test = "value.type.equals(\"TEXT\")", type = UnicodeString.class),
                @Chunk.Case(test = "value.type.equals(\"tdta\")", type = FixedByteArray.class),
                @Chunk.Case(test = "value.type.equals(\"type\")", type = ClassType.class),
                @Chunk.Case(test = "value.type.equals(\"UnFl\")", type = UnitFloat.class),
                @Chunk.Case(test = "value.type.equals(\"UntF\")", type = UnitDouble.class),
                @Chunk.Case(test = "value.type.equals(\"VlLs\")", type = ValueList.class)
            })
            Object data;

            @Override
            public String toString() {
                if (data == null) return null;
                return data.toString();
            }
        }

        // The name of the descriptor
        @Chunk
        MinimumString key;

        @Chunk
        Value value;

        Object getData() {
            return value.data;
        }

        @Override
        public String toString() {
            return "DescriptorItem{" +
                    "key=" + key +
                    ", value=" + value +
                    '}';
        }
    }

    /**
     * A vector mask is a layer property that contains a list of path
     * records, used to descript a path (or vector shape).
     */
    @Chunked
    static final class VectorMask {
        @Chunk
        int version;

        @Chunk
        int flags;

        // LayerProperty.length is rounded to an even byte count
        // Subtract the 8 bytes used for version and flags, and divide
        // by the length of each path record (26 bytes) to know how many
        // path records to read
        @Chunk(dynamicSize = "(int) Math.floor(((($T) stack.get(1)).length - 8) / 26)",
            sizeParams = { LayerProperty.class })
        List<PathRecord> pathRecords;

    }

    /**
     * A path records is either a single point in a path, a subpath
     * marker or a command. A subpath can be closed or open.
     */
    @Chunked
    static final class PathRecord {
        // Marks the beginning of a closed subpath
        static final int CLOSED_SUBPATH_LENGTH = 0;
        // Linked/unlinked matters only to interactive editing
        // Linked just means that the two control points of a knot
        // move together when one of them moves
        static final int CLOSED_SUBPATH_KNOT_LINKED = 1;
        static final int CLOSED_SUBPATH_KNOT_UNLINKED = 2;
        // Marks the beginning of an open subpath
        static final int OPEN_SUBPATH_LENGTH = 3;
        static final int OPEN_SUBPATH_KNOT_LINKED = 4;
        static final int OPEN_SUBPATH_KNOT_UNLINKED = 5;
        // Photoshop only deal with even/odd fill rules, we can ignore it
        static final int PATH_FILL_RULE = 6;
        // Not sure what this does
        static final int CLIPBOARD = 7;
        // Initial fill rule, always present as first item in the list
        // of path records
        static final int INITIAL_FILL_RULE = 8;

        /**
         * A curve (or path) is made of a series of Bézier knot.
         * Each knot is made of an anchor (point on the curve/path)
         * and of two control points. One defines the slope of the
         * curve before the anchor, the other the slope of the curve
         * after the anchor.
         */
        @Chunked
        static final class BezierKnot {
            @Chunk
            int controlEnterY;
            @Chunk
            int controlEnterX;
            @Chunk
            int anchorY;
            @Chunk
            int anchorX;
            @Chunk
            int controlExitY;
            @Chunk
            int controlExitX;
        }

        // Indicates the path record type
        @Chunk
        short selector;

        @Chunk(byteCount = 24,
            switchType = {
                @Chunk.Case(test = "pathRecord.selector == 0 || pathRecord.selector == 3",
                        type = int.class),
                @Chunk.Case(test = "pathRecord.selector == 1 || pathRecord.selector == 2 || " +
                        "pathRecord.selector == 4 || pathRecord.selector == 5",
                        type = BezierKnot.class)
            }
        )
        Object data;
    }

    /**
     * Helper class to read a byte array whose length is stored
     * as a 32 bits unsigned integer.
     */
    @Chunked
    static final class FixedByteArray {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "fixedByteArray.length")
        byte[] value;

        @Override
        public String toString() {
            if (value == null) return null;
            return new String(value);
        }
    }

    /**
     * Helper class to read an ASCII string whose length is
     * stored as a 32 bits unsigned integer.
     */
    @Chunked
    static final class FixedString {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "fixedString.length")
        String value;

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Helper class to read an ASCII string whose length is
     * stored as a 32 bits unsigned integer. If the length
     * is 0, the string is assumed to have a length of 4 bytes.
     */
    @Chunked
    static final class MinimumString {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "Math.max(minimumString.length, 4)")
        String value;

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Helper class to read a Unicode string, encoded in UTF-16.
     * The length, in characters, of the string is stored as a 32
     * bits unsigned integer. The string is made of length*2 bytes.
     */
    @Chunked
    static final class UnicodeString {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "unicodeString.length * 2", encoding = "UTF-16")
        String value;

        @Override
        public String toString() {
            if (value == null) return null;
            int lastChar = value.length() - 1;
            if (value.charAt(lastChar) == '\0') {
                return value.substring(0, lastChar);
            }
            return value;
        }
    }

    /**
     * A layer section is a property that marks layer groups.
     */
    @Chunked
    static final class LayerSection {
        /**
         * The section type.
         */
        enum Type {
            /**
             * Who knows.
             */
            OTHER,
            /**
             * Open group of layers.
             */
            GROUP_OPENED,
            /**
             * Closed group of layers.
             */
            GROUP_CLOSED,
            /**
             * End of a group (invisible in the UI).
             */
            BOUNDING
        }

        @Chunk(byteCount = 4)
        Type type;

        @Chunk(byteCount = 4,
            readIf = "(($T) stack.get(1)).length >= 12",
            readIfParams = { LayerProperty.class }
        )
        String signature;

        @Chunk(byteCount = 4,
            readIf = "(($T) stack.get(1)).length >= 12",
            readIfParams = { LayerProperty.class }
        )
        String blendMode;

        // Apparently only used for the animation timeline
        @Chunk(byteCount = 4,
            readIf = "(($T) stack.get(1)).length >= 16",
            readIfParams = { LayerProperty.class }
        )
        int subType;
    }

    /**
     * A blend range indicates the tonal range for a given channel.
     */
    @Chunked
    static final class BlendRange {
        @Chunk(byteCount = 1)
        short srcBlackIn;
        @Chunk(byteCount = 1)
        short srcWhiteIn;
        @Chunk(byteCount = 1)
        short srcBlackOut;
        @Chunk(byteCount = 1)
        short srcWhiteOut;

        @Chunk(byteCount = 1)
        short dstBlackIn;
        @Chunk(byteCount = 1)
        short dstWhiteIn;
        @Chunk(byteCount = 1)
        short dstBlackOut;
        @Chunk(byteCount = 1)
        short dstWhiteOut;
    }

    /**
     * Specific to masks. This section has to be read carefully from the
     * file has its length varies depending on a set of flags.
     */
    @Chunked
    static final class MaskAdjustment {
        @Chunk(byteCount = 4, stopIf = "maskAdjustment.length == 0")
        long length;

        @Chunk(byteCount = 4)
        long top;
        @Chunk(byteCount = 4)
        long left;
        @Chunk(byteCount = 4)
        long bottom;
        @Chunk(byteCount = 4)
        long right;

        @Chunk(byteCount = 1)
        short defaultColor;

        @Chunk
        byte flags;

        @Chunk(readIf = "(maskAdjustment.flags & 0x10) != 0")
        byte maskParameters;

        @Chunk(byteCount = 1, readIf = "(maskAdjustment.maskParameters & 0x1) != 0")
        short userMaskDensity;
        @Chunk(readIf = "(maskAdjustment.maskParameters & 0x2) != 0")
        double userMaskFeather;
        @Chunk(byteCount = 1, readIf = "(maskAdjustment.maskParameters & 0x4) != 0")
        short vectorMaskDensity;
        @Chunk(readIf = "(maskAdjustment.maskParameters & 0x8) != 0")
        double vectorMaskFeather;

        @Chunk(readIf = "maskAdjustment.length == 20", stopIf = "maskAdjustment.length == 20")
        short padding;

        @Chunk
        byte realFlags;

        @Chunk(byteCount = 1)
        short userMaskBackground;

        @Chunk(byteCount = 4)
        long realTop;
        @Chunk(byteCount = 4)
        long realLeft;
        @Chunk(byteCount = 4)
        long realBottom;
        @Chunk(byteCount = 4)
        long realRight;
    }

    /**
     * Extremely important: this section gives us the number of bytes
     * used to encode the bitmap data of each channel in a given layer.
     */
    @Chunked
    static final class ChannelInformation {
        @Chunk
        short id;
        @Chunk(byteCount = 4)
        long dataLength;
    }

    /**
     * Photoshop's compression method for layer channels and
     * the flattened planar image.
     */
    enum CompressionMethod {
        RAW,
        RLE,
        ZIP,
        ZIP_NO_PREDICTION
    }

    /**
     * Contains the image data for each channel of a layer.
     * There is one ChannelsContainer per layer.
     */
    @Chunked
    static final class ChannelsContainer {
        @Chunk(
            dynamicSize =
                "$T list = ($T) stack.get(1);\n" +
                "size = list.layers.get(list.channels.size()).channels",
            sizeParams = {
                LayersList.class, LayersList.class
            }
        )
        List<ChannelImageData> imageData;
    }

    /**
     * The image data for a layer's channel. The compression method can
     * in theory vary per layer. The overall length of the data comes from
     * the ChannelInformation section seen earlier.
     */
    @Chunked
    static final class ChannelImageData {
        @Chunk(byteCount = 2)
        CompressionMethod compression;

        // Subtract 2 bytes because the channel info data length takes the
        // compression method into account
        @Chunk(
            dynamicByteCount =
                "$T list = ($T) stack.get(2);\n" +
                "$T layer = list.layers.get(list.channels.size());\n" +
                "$T container = ($T) stack.get(1);\n" +
                "$T info = layer.channelsInfo.get(container.imageData.size());\n" +
                "byteCount = info.dataLength - 2",
            byteCountParams = {
                LayersList.class, LayersList.class,
                RawLayer.class,
                ChannelsContainer.class, ChannelsContainer.class,
                ChannelInformation.class
            }
        )
        byte[] data;
    }

    /**
     * The flattened image data. The compression method applies to all
     * the channels. The channels are stored sequentially in the data array.
     */
     @Chunked
    static final class ImageData {
        @Chunk(byteCount = 2)
        CompressionMethod compression;

        // Use a 128K buffer because images can be large
        // We might want to choose a buffer size that's function of
        // the dimensions/compression method
        @Chunk(bufferSize = 0x20000)
        byte[] data;
    }
}
