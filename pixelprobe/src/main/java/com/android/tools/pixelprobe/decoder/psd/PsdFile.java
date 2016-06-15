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

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.Chunked;
import com.android.tools.pixelprobe.ColorMode;
import com.android.tools.pixelprobe.util.Strings;

import java.util.List;
import java.util.Map;

/**
 * This class describes the structure of a PSD file.
 * Read on to learn how a PSD file is stored on disk.
 */
@SuppressWarnings("unused")
@Chunked
final class PsdFile {
    @Chunk
    Header header;
    @Chunk
    ColorData colorData;
    @Chunk
    ImageResources resources;
    @Chunk
    LayersInformation layersInfo;
    @Chunk
    ImageData imageData;

    /**
     * PSD header. A few magic values and important information
     * about the image's dimensions, color depth and mode.
     */
    @Chunked
    static final class Header {
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
        @Chunk(byteCount = 2)
        ColorMode colorMode;
    }

    /**
     * Only useful for indexed and duotone images.
     */
    @Chunked
    static final class ColorData {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "colorData.length")
        byte[] data;
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
        @Chunk(dynamicByteCount = "imageResources.length", key = "imageResourceBlock.id")
        Map<Integer, ImageResourceBlock> blocks;

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
                @Chunk.Case(test = "imageResourceBlock.id == 0x0408", type = GuidesResourceBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x040C", type = ThumbnailResourceBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x03ED", type = ResolutionInfoBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x040F", type = ColorProfileBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x0416", type = UnsignedShortBlock.class),
                @Chunk.Case(test = "imageResourceBlock.id == 0x0417", type = UnsignedShortBlock.class)
            }
        )
        Object data;
    }

    /**
     * The layers information section contains the list of layers
     * and a lot of data for each layer.
     */
    @Chunked
    static final class LayersInformation {
        @Chunk(byteCount = 4)
        long length;

        @Chunk(dynamicByteCount = "layersInformation.length", readIf = "layersInformation.length > 0")
        LayersList layers;
    }

    /**
     * The list of layers is actually made of two lists in the PSD
     * file. First, the description and extra data for each layer
     * (name, bounds, etc.). Then an image representation for each
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
     * keyed "extras", The extras are crucial for non-image
     * layers. They also contain layer effects (drop shadows,
     * inner glow, etc.).
     */
    @Chunked
    static final class RawLayer {
        // Mask for the flags field, indicating whether the layer is visible or not
        static final int INVISIBLE = 0x2;

        // Firs we have the layer's bounds, in pixels,
        // in absolute image coordinates
        @Chunk
        int top;
        @Chunk
        int left;
        @Chunk
        int bottom;
        @Chunk
        int right;

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
        @Chunk
        byte clipping;
        @Chunk
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
     * Extremely important: this section gives us the number of bytes
     * used to encode the image data of each channel in a given layer.
     */
    @Chunked
    static final class ChannelInformation {
        @Chunk
        short id;
        @Chunk(byteCount = 4)
        long dataLength;
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
        @Chunk(dynamicByteCount = "((layerExtras.nameLength + 4) & ~3) - (layerExtras.nameLength + 1)")
        Void namePadding;

        @Chunk(key = "layerProperty.key")
        Map<String, LayerProperty> properties;
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
        // The property holds the solid color adjustment information
        static final String KEY_ADJUSTMENT_SOLID_COLOR = "SoCo";
        // Fill opacity
        static final String KEY_FILL_OPACITY = "iOpa";
        // The property holds the text information (styles, text data, etc.)
        static final String KEY_TEXT = "TySh";
        // The property holds the vector data (can be "vsms" instead)
        static final String KEY_VECTOR_MASK = "vmsk";
        // The property holds the vector data (can be "vmsk" instead)
        // When this key is present, we must also look for "vscg"
        static final String KEY_SHAPE_MASK = "vsms";
        // The property holds graphics data for a shape mask defined by "vsms"
        static final String KEY_SHAPE_GRAPHICS = "vscg";
        // The property holds the stroke data
        static final String KEY_STROKE = "vstk";
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
                @Chunk.Case(test = "layerProperty.key.equals(\"iOpa\")", type = byte.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"TySh\")", type = TypeToolObject.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"vmsk\")", type = ShapeMask.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"vsms\")", type = ShapeMask.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"vscg\")", type = ShapeGraphics.class),
                @Chunk.Case(test = "layerProperty.key.equals(\"vstk\")", type = ShapeStroke.class)
            }
        )
        Object data;
    }

    @Chunked
    static final class LayerEffects {
        // Boolean to toggle effects on/off
        static final String KEY_MASTER_SWITCH = "masterFXSwitch";
        static final String KEY_PRESENT = "present";
        static final String KEY_ENABLED = "enab";
        // Shadows
        static final String KEY_INNER_SHADOW = "IrSh";
        static final String KEY_INNER_SHADOW_MULTI = "innerShadowMulti";
        static final String KEY_DROP_SHADOW = "DrSh";
        static final String KEY_DROP_SHADOW_MULTI = "dropShadowMulti";
        // Shape stroke
        static final String KEY_STROKE = "FrFX";

        @Chunk(match = "0")
        int version;
        @Chunk
        int descriptorVersion;

        @Chunk
        Descriptor effects;
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

    @Chunked
    static final class SolidColorAdjustment {
        @Chunk
        int version;

        @Chunk
        Descriptor solidColor;
    }

    /**
     * The TypeToolObject layer property contains all the data needed to
     * render a text layer.
     */
    @Chunked
    static final class TypeToolObject {
        // Descriptor that holds the actual text
        static final String KEY_TEXT = "Txt ";
        // Descriptor that holds structured text data used for styling, see TextEngine
        static final String KEY_ENGINE_DATA = "EngineData";

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
     * A vector mask is a layer property that contains a list of path
     * records, used to describe a path (or vector shape).
     */
    @Chunked
    static final class ShapeMask {
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
     * A shape graphics object describes all the graphics properties of
     * a shape mask (color, gradient, etc.)
     */
    @Chunked
    static final class ShapeGraphics {
        @Chunk(byteCount =  4)
        String key;

        @Chunk(byteCount = 4)
        long version;

        @Chunk
        Descriptor graphics;
    }

    /**
     * Describes a shape layer's stroke properties.
     */
    @Chunked
    static final class ShapeStroke {
        @Chunk(byteCount = 4)
        long version;

        @Chunk
        Descriptor stroke;
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

        /**
         * Marks the start of a sub-path. The first command of a
         * sub-path is always a "move to". While sub-paths could
         * all be recorded in a single path, we must take into
         * account Photoshop's operators: merge, subtract,
         * intersect and XOR). We therefore record the sub-paths
         * individually to be able to apply the operators
         * properly later on.
         */
        @Chunked
        static final class SubPath {
            // If the tag equals this value, then the subpath is
            // not a path operation and must be added to the
            // current path
            static final int NO_OP = 0x0;
            static final int OP_XOR = 0x0;
            static final int OP_MERGE = 0x1;
            static final int OP_SUBTRACT = 0x2;
            static final int OP_INTERSECT = 0x3;

            @Chunk(byteCount = 2)
            int knotCount;

            @Chunk(byteCount = 2)
            int op;

            @Chunk(byteCount = 2)
            int tag;
        }

        // Indicates the path record type
        @Chunk
        short selector;

        @Chunk(byteCount = 24,
            switchType = {
                @Chunk.Case(test = "pathRecord.selector == 0 || pathRecord.selector == 3",
                            type = SubPath.class),
                @Chunk.Case(test = "pathRecord.selector == 1 || pathRecord.selector == 2 || " +
                                   "pathRecord.selector == 4 || pathRecord.selector == 5",
                            type = BezierKnot.class)
            }
        )
        Object data;
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
                LayersList.class,
                LayersList.class
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

    /**
     * A descriptor is a generic way to represent typed and named
     * data. Photoshop seems to abuse descriptor quite a bit in
     * few places, particularly for text layers.
     */
    @Chunked
    static final class Descriptor {
        static final String CLASS_ID_COLOR_RGB = "RGBC";
        static final String CLASS_ID_COLOR_HSB = "HSBC";
        static final String CLASS_ID_COLOR_CMYK = "CMYC";
        static final String CLASS_ID_COLOR_LAB = "LbCl";
        static final String CLASS_ID_COLOR_GRAY = "Grsc";

        // Name from classID, usually not set
        @Chunk
        UnicodeString name;

        // ClassID
        @Chunk
        MinimumString classId;

        // Number of items in the descriptor
        @Chunk
        int count;

        @Chunk(dynamicSize = "descriptor.count", key = "String.valueOf(descriptorItem.key)")
        Map<String, DescriptorItem> items;

        @Override
        public String toString() {
            return "<descriptor name=\"" + name + "\" classId=\"" + classId + "\">" +
                   Strings.join(items.values(), "\n") +
                   "</descriptor>";
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
            static final String CENTIMETERS = "RrCm";
            static final String INCHES = "RrIn";
            static final String ANGLE_DEGREES = "#Ang";
            static final String RESOLUTION = "#Rsl"; // base per inch
            static final String RELATIVE = "#Rlt"; // base 72ppi
            static final String NONE = "#Nne";
            static final String PERCENT = "#Prc";
            static final String PIXELS = "#Pxl";

            @Chunk(byteCount = 4)
            String unit;

            @Chunk
            double value;

            @Override
            public String toString() {
                String s = Double.toString(value);
                switch (unit) {
                    case POINTS:
                        s += "pt";
                        break;
                    case MILLIMETERS:
                        s += "mm";
                        break;
                    case CENTIMETERS:
                        s += "cm";
                        break;
                    case INCHES:
                        s += "in";
                        break;
                    case ANGLE_DEGREES:
                        s += "°";
                        break;
                    case RESOLUTION:
                        s += "dpi";
                        break;
                    case RELATIVE:
                        s += "dpp";
                        break;
                    case NONE:
                        break;
                    case PERCENT:
                        s += "%";
                        break;
                    case PIXELS:
                        s += "px";
                        break;
                }
                return s;
            }
        }

        /**
         * A float value with a unit.
         * See {@link UnitDouble} for possible units.
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
            List<Value> items;

            @Override
            public String toString() {
                return "<list>" + Strings.join(items, ", ") + "</list>";
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

        @Override
        public String toString() {
            return "<item key=\"" + key + "\">" + String.valueOf(value) + "</item>";
        }
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
     * Orientation, used for guides.
     */
    enum Orientation {
        VERTICAL,
        HORIZONTAL
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
        Orientation orientation;
    }

    /**
     * Stores an ICC color profile.
     */
    @Chunked
    static final class ColorProfileBlock {
        static final int ID = 0x040F;

        @Chunk
        byte[] icc;
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
     * Stores an unsigned short.
     */
    @Chunked
    static final class UnsignedShortBlock {
        static final int ID_INDEX_TABLE_COUNT = 0x0416;
        static final int ID_INDEX_TRANSPARENCY = 0x0417;

        @Chunk(byteCount = 2)
        int data;
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
            if (value.length() == 0) return "";
            int lastChar = value.length() - 1;
            if (value.charAt(lastChar) == '\0') {
                return value.substring(0, lastChar);
            }
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
}
