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

package com.android.tools.pixelprobe.tests.psd;

import com.android.tools.pixelprobe.BlendMode;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.ShapeInfo;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShapeTest {
    @SuppressWarnings("InspectionUsingGrayColors")
    @Test
    public void colorTypes() throws IOException {
        // In this test, the file defines fill colors for various shapes in the
        // following color spaces: Grayscale, Lab, CMYK, HSB and RGB
        Image image = ImageUtils.loadImage("psd/color_spaces.psd");

        Map<String, Color> convertedColors = new HashMap<>();
        convertedColors.put("Grayscale", new Color(116, 116, 116));
        convertedColors.put("LAB", new Color(214, 44, 247));
        convertedColors.put("CMYK", new Color(255, 242, 0));
        convertedColors.put("HSB", new Color(36, 217, 0));
        convertedColors.put("RGB", new Color(255, 0, 0));

        for (Layer layer : image.getLayers()) {
            if (layer.getType() == Layer.Type.SHAPE) {
                Paint fillPaint = layer.getShapeInfo().getFillPaint();
                Assert.assertTrue(fillPaint instanceof Color);
                Assert.assertEquals(convertedColors.get(layer.getName()), fillPaint);
            }
        }
    }

    @Test
    public void fill() throws IOException {
        Image image = ImageUtils.loadImage("psd/fill_opacity.psd");

        Layer layer = image.getLayers().get(0);
        Assert.assertEquals(Layer.Type.SHAPE, layer.getType());

        ShapeInfo shapeInfo = layer.getShapeInfo();
        Assert.assertEquals(ShapeInfo.Style.FILL, shapeInfo.getStyle());
        Assert.assertEquals(0.5f, shapeInfo.getFillOpacity(), 0.05f);
    }

    @Test
    public void stroke() throws IOException {
        Image image = ImageUtils.loadImage("psd/stroked_shape.psd");

        List<Layer> layers = image.getLayers();
        ShapeInfo shape;

        // Style: none
        shape = layers.get(0).getShapeInfo();
        Assert.assertEquals(ShapeInfo.Style.NONE, shape.getStyle());
        Assert.assertNotNull(shape.getStroke());
        Assert.assertNotNull(shape.getStrokePaint());
        Assert.assertEquals(1.0f, shape.getStrokeOpacity(), 0.01f);
        Assert.assertEquals(BlendMode.NORMAL, shape.getStrokeBlendMode());
        Assert.assertEquals(ShapeInfo.Alignment.INSIDE, shape.getStrokeAlignment());

        // Style: stroke
        shape = layers.get(1).getShapeInfo();
        Assert.assertEquals(ShapeInfo.Style.STROKE, shape.getStyle());
        Assert.assertNotNull(shape.getStroke());
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        Assert.assertEquals(Color.BLACK, shape.getStrokePaint());
        Assert.assertEquals(1.0f, shape.getStrokeOpacity(), 0.01f);
        Assert.assertEquals(BlendMode.NORMAL, shape.getStrokeBlendMode());
        Assert.assertEquals(ShapeInfo.Alignment.INSIDE, shape.getStrokeAlignment());

        // Style: fill and stroke
        shape = layers.get(4).getShapeInfo();
        Assert.assertEquals(ShapeInfo.Style.FILL_AND_STROKE, shape.getStyle());
        Assert.assertNotNull(shape.getStroke());
        Assert.assertEquals(Color.BLACK, shape.getStrokePaint());
        Assert.assertEquals(1.0f, shape.getStrokeOpacity(), 0.01f);
        Assert.assertEquals(BlendMode.NORMAL, shape.getStrokeBlendMode());
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        BasicStroke stroke = (BasicStroke) shape.getStroke();
        Assert.assertEquals(BasicStroke.CAP_SQUARE, stroke.getEndCap());
        Assert.assertEquals(BasicStroke.JOIN_BEVEL, stroke.getLineJoin());
        Assert.assertEquals(ShapeInfo.Alignment.OUTSIDE, shape.getStrokeAlignment());

        // Center alignment, round cap, round join
        shape = layers.get(3).getShapeInfo();
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        stroke = (BasicStroke) shape.getStroke();
        Assert.assertEquals(BasicStroke.CAP_ROUND, stroke.getEndCap());
        Assert.assertEquals(BasicStroke.JOIN_ROUND, stroke.getLineJoin());
        Assert.assertEquals(ShapeInfo.Alignment.CENTER, shape.getStrokeAlignment());

        // Inside alignment, butt cap, mitter join
        shape = layers.get(5).getShapeInfo();
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        stroke = (BasicStroke) shape.getStroke();
        Assert.assertEquals(BasicStroke.CAP_BUTT, stroke.getEndCap());
        Assert.assertEquals(BasicStroke.JOIN_MITER, stroke.getLineJoin());
        Assert.assertEquals(ShapeInfo.Alignment.INSIDE, shape.getStrokeAlignment());

        // Dashes
        shape = layers.get(2).getShapeInfo();
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        stroke = (BasicStroke) shape.getStroke();
        Assert.assertEquals(0.0f, stroke.getDashPhase(), 0.0001f);
        // Pixel values converted from various units (pt, cm, mm, in, default)
        Assert.assertArrayEquals(new float[] { 4.0f, 11.82f, 18.99f, 4.17f, 56.69f, 30.24f },
                                 stroke.getDashArray(), 0.005f);

        // Stroke width
        shape = layers.get(5).getShapeInfo();
        Assert.assertTrue(shape.getStroke() instanceof BasicStroke);
        stroke = (BasicStroke) shape.getStroke();
        Assert.assertEquals(3.0f, stroke.getLineWidth(), 0.01f);
    }

    @Test
    public void pathOps() throws IOException {
        Image image = ImageUtils.loadImage("psd/path_ops.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals("Merge", layer.getName());
        List<ShapeInfo.Path> paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathOp.ADD, paths.get(0).getOp());
        Assert.assertEquals(ShapeInfo.PathOp.ADD, paths.get(1).getOp());

        layer = layers.get(1);
        Assert.assertEquals("Subtract", layer.getName());
        paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathOp.ADD, paths.get(0).getOp());
        Assert.assertEquals(ShapeInfo.PathOp.SUBTRACT, paths.get(1).getOp());

        layer = layers.get(2);
        Assert.assertEquals("XOR", layer.getName());
        paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathOp.EXCLUSIVE_OR, paths.get(0).getOp());
        Assert.assertEquals(ShapeInfo.PathOp.EXCLUSIVE_OR, paths.get(1).getOp());

        layer = layers.get(4);
        Assert.assertEquals("Intersect", layer.getName());
        paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathOp.INTERSECT, paths.get(0).getOp());
        Assert.assertEquals(ShapeInfo.PathOp.INTERSECT, paths.get(1).getOp());
    }

    @Test
    public void pathTypes() throws IOException {
        Image image = ImageUtils.loadImage("psd/path_type.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals("Closed", layer.getName());
        List<ShapeInfo.Path> paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathType.CLOSED, paths.get(0).getType());
        Assert.assertTrue(isClosed(paths));

        layer = layers.get(1);
        Assert.assertEquals("Open", layer.getName());
        paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathType.OPEN, paths.get(0).getType());
        Assert.assertFalse(isClosed(paths));

        image = ImageUtils.loadImage("psd/layer_effect_single_shadow.psd");

        layers = image.getLayers();

        layer = layers.get(1);
        Assert.assertEquals("Inner", layer.getName());
        paths = layer.getShapeInfo().getPaths();
        Assert.assertEquals(ShapeInfo.PathType.UNKNOWN, paths.get(0).getType());
        Assert.assertTrue(isClosed(paths));
    }

    private static boolean isClosed(List<ShapeInfo.Path> paths) {
        PathIterator iterator = paths.get(0).getPath().getPathIterator(null);
        boolean isClosed = false;
        float[] coords = new float[6];
        while (!iterator.isDone()) {
            if (iterator.currentSegment(coords) == PathIterator.SEG_CLOSE) {
                isClosed = true;
                break;
            }
            iterator.next();
        }
        return isClosed;
    }
}
