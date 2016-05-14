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

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.PixelProbe;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple PSD to VectorDrawable converter.
 * This can currently only extract one layer at a time.
 */
public class PSDtoVectorDrawable {
    private static final Pattern SIZE_PATTERN = Pattern.compile("([0-9]+)x([0-9]+)");

    private static String sourceFile;
    private static String layerName;

    private static int width = 64;
    private static int height = 64;

    private static int indent = 0;

    public static void main(String[] args) {
        try {
            extractArguments(args);
            validateArguments();
        } catch (IllegalArgumentException e) {
            stop(e.getMessage());
        }

        try {
            InputStream in = new BufferedInputStream(new FileInputStream(sourceFile));
            Image image = PixelProbe.probe(in);
            if (image.isValid()) {
                outputDrawable(image);
            } else {
                stop("Could not parse specified PSD file: " + sourceFile);
            }
        } catch (FileNotFoundException e) {
            stop("Error reading specified PSD file: " + sourceFile);
        }
    }

    private static void outputDrawable(Image image) {
        Layer exportLayer = null;
        for (Layer layer : image.getLayers()) {
            if (layerName == null || layerName.equalsIgnoreCase(layer.getName())) {
                if (layer.getType() == Layer.Type.PATH) {
                    exportLayer = layer;
                } else {
                    stop("The specified layer " + layerName + " is not a vector layer");
                }
            }
        }

        if (exportLayer == null) {
            if (layerName != null) stop("Could not find layer: " + layerName);
            else stop("Could not find a vector layer");
        }

        //noinspection ConstantConditions
        Rectangle2D bounds = exportLayer.getBounds();

        Element vector = new Element("vector")
                .attribute("width", String.valueOf(width) + "dp")
                .attribute("height", String.valueOf(height) + "dp")
                .attribute("viewportWidth", String.valueOf(bounds.getWidth()))
                .attribute("viewportHeight", String.valueOf(bounds.getHeight()))
                .child(new Element("path")
                        .attribute("name", exportLayer.getName())
                        .attribute("fillColor", toHexColor(exportLayer.getPathColor()))
                        .attribute("fillAlpha", String.valueOf(exportLayer.getOpacity()))
                        .attribute("pathData", toPathData(exportLayer.getPath()))
                );

        output(vector);
    }

    private static void output(Element element) {
        PrintWriter out = new PrintWriter(System.out);
        outputElement(element, out, true);
        out.flush();
    }

    private static void outputElement(Element element, PrintWriter out, boolean isRoot) {
        indent(out);
        out.write("<");
        out.write(element.name);
        if (isRoot) out.write(" xmlns:android=\"http://schemas.android.com/apk/res/android\"");
        out.write("\n");

        boolean hasChildren = element.children.size() > 0;

        indent++;
        outputAttributes(element, out);
        if (hasChildren) {
            out.write(">\n");
            outputChildren(element, out);
        } else {
            out.write(" />");
        }
        indent--;

        if (hasChildren) {
            indent(out);
            out.write("</");
            out.write(element.name);
            out.write(">");
        }
        out.write("\n");
    }

    private static void outputChildren(Element element, PrintWriter out) {
        for (Element child : element.children) {
            outputElement(child, out, false);
        }
    }

    private static void outputAttributes(Element element, PrintWriter out) {
        List<Attribute> attributes = element.attributes;
        int size = attributes.size();

        for (int i = 0; i < size; i++) {
            Attribute attribute = attributes.get(i);
            indent(out);
            out.write("android:");
            out.write(attribute.name);
            out.write("=\"");
            out.write(attribute.value);
            out.write("\"");
            if (i != size - 1) out.write("\n");
        }
    }

    private static void indent(PrintWriter out) {
        for (int i = 0; i < indent; i++) {
            out.write("    ");
        }
    }

    private static String toPathData(Shape path) {
        StringBuilder buffer = new StringBuilder(1024);

        float[] coords = new float[6];
        int previousSegment = -1;
        PathIterator iterator = path.getPathIterator(new AffineTransform());

        while (!iterator.isDone()) {
            int segment = iterator.currentSegment(coords);
            switch (segment) {
                case PathIterator.SEG_MOVETO:
                    buffer.append('M');
                    buffer.append(coords[0]);
                    buffer.append(',');
                    buffer.append(coords[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    throw new IllegalStateException("Unexpected lineTo in path");
                case PathIterator.SEG_CUBICTO:
                    if (previousSegment != PathIterator.SEG_CUBICTO) {
                        buffer.append('C');
                    }
                    buffer.append(coords[0]);
                    buffer.append(',');
                    buffer.append(coords[1]);
                    buffer.append(' ');
                    buffer.append(coords[2]);
                    buffer.append(',');
                    buffer.append(coords[3]);
                    buffer.append(' ');
                    buffer.append(coords[4]);
                    buffer.append(',');
                    buffer.append(coords[5]);
                    break;
                case PathIterator.SEG_QUADTO:
                    throw new IllegalStateException("Unexpected quadTo in path");
                case PathIterator.SEG_CLOSE:
                    buffer.append('z');
                    break;
            }

            previousSegment = segment;
            iterator.next();
            if (!iterator.isDone()) buffer.append(' ');
        }

        return buffer.toString();
    }

    private static String toHexColor(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static void stop(String message) {
        System.out.println(message);
        System.out.println();
        System.out.println("Usage: psd2drawable [-layer name] [-size WxH] file.psd");
        System.out.println("-layer  Name of the layer to extract");
        System.out.println("-size   Width and height of the generated drawable");
        System.exit(1);
    }

    private static void validateArguments() {
        if (sourceFile == null) {
            throw new IllegalArgumentException("You must specify an input PSD file");
        }
        if (!(new File(sourceFile).exists())) {
            throw new IllegalArgumentException("Cannot find specified PSD file: " + sourceFile);
        }
    }

    private static void extractArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.charAt(0) == '-') {
                if (arg.equalsIgnoreCase("-layer")) {
                    if (i < args.length - 1) {
                        layerName = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing layer name after -layer");
                    }
                } else if (arg.equalsIgnoreCase("-size")) {
                    if (i < args.length - 1) {
                        Matcher matcher = SIZE_PATTERN.matcher(args[++i]);
                        if (matcher.matches()) {
                            width = Integer.parseInt(matcher.group(1));
                            height = Integer.parseInt(matcher.group(2));
                        } else {
                            throw new IllegalArgumentException("Dimensions must be in the format " +
                                    "WxH, for instance -size 64x64");
                        }
                    } else {
                        throw new IllegalArgumentException("Missing dimensions after -size");
                    }
                }
            } else {
                sourceFile = arg;
            }
        }
    }

    private static class Attribute {
        private final String name;
        private final String value;

        Attribute(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class Element {
        private final String name;
        private final List<Element> children = new ArrayList<Element>();
        private final List<Attribute> attributes = new ArrayList<Attribute>();

        Element(String name) {
            this.name = name;
        }

        Element attribute(String name, String value) {
            attributes.add(new Attribute(name, value));
            return this;
        }

        Element child(Element child) {
            children.add(child);
            return this;
        }
    }
}
