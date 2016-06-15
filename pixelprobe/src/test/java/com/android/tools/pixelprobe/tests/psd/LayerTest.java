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
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.List;

public class LayerTest {
    @Test
    public void name() throws IOException {
        Image image = ImageUtils.loadImage("layer_names.psd");
        List<Layer> layers = image.getLayers();

        // Test ASCII compatible name
        Assert.assertEquals("Layer", layers.get(0).getName());
        // Test Unicode only name
        Assert.assertEquals("レイヤ", layers.get(1).getName());
    }

    @Test
    public void types() throws IOException {
        Image image = ImageUtils.loadImage("layer_types.psd");

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(7, layers.size());

        Assert.assertEquals(Layer.Type.ADJUSTMENT, layers.get(0).getType());
        Assert.assertEquals(Layer.Type.ADJUSTMENT, layers.get(1).getType());
        Assert.assertEquals(Layer.Type.SHAPE, layers.get(2).getType());
        Assert.assertEquals(Layer.Type.TEXT, layers.get(3).getType());
        Assert.assertEquals(Layer.Type.IMAGE, layers.get(4).getType());
        Assert.assertEquals(Layer.Type.TEXT, layers.get(5).getType());
        Assert.assertEquals(Layer.Type.IMAGE, layers.get(6).getType());
    }

    @Test
    public void groups() throws IOException {
        Image image = ImageUtils.loadImage("groups.psd");

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(3, layers.size());

        Assert.assertEquals("Closed", layers.get(0).getName());
        Assert.assertEquals(Layer.Type.GROUP, layers.get(0).getType());
        Assert.assertEquals(1, layers.get(0).getChildren().size());
        Assert.assertFalse(layers.get(0).isOpen());

        Assert.assertEquals("Open", layers.get(1).getName());
        Assert.assertEquals(Layer.Type.GROUP, layers.get(1).getType());
        Assert.assertEquals(1, layers.get(1).getChildren().size());
        Assert.assertTrue(layers.get(1).isOpen());
    }

    @Test
    public void visibility() throws IOException {
        Image image = ImageUtils.loadImage("visibility.psd");

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(3, layers.size());

        Assert.assertEquals("Invisible", layers.get(0).getName());
        Assert.assertFalse(layers.get(0).isVisible());

        Assert.assertEquals("Visible", layers.get(1).getName());
        Assert.assertTrue(layers.get(1).isVisible());
    }

    @Test
    public void blendModes() throws IOException {
        Image image = ImageUtils.loadImage("blend_modes.psd");

        List<Layer> layers = image.getLayers();
        BlendMode[] modes = BlendMode.values();
        for (int i = 0; i < modes.length; i++) {
            Assert.assertEquals(modes[i], layers.get(i).getBlendMode());
        }
    }

    @Test
    public void opacity() throws IOException {
        Image image = ImageUtils.loadImage("opacity.psd");

        List<Layer> layers = image.getLayers();
        float[] opacities = { 0.25f, 0.50f, 0.75f, 1.0f };
        for (int i = 0; i < opacities.length; i++) {
            Assert.assertEquals(opacities[i], layers.get(i).getOpacity(), 0.01f);
        }
    }

    @Test
    public void clipBase() throws IOException {
        Image image = ImageUtils.loadImage("clip_base.psd");

        List<Layer> layers = image.getLayers();

        Assert.assertFalse(layers.get(0).isClipBase());
        Assert.assertFalse(layers.get(1).isClipBase());
        Assert.assertTrue(layers.get(2).isClipBase());
    }

    @Test
    public void bounds() throws IOException {
        Image image = ImageUtils.loadImage("bounds.psd");

        List<Layer> layers = image.getLayers();

        Assert.assertEquals(new Rectangle2D.Float(200.0f, 200.0f, 56.0f, 56.0f), layers.get(0).getBounds());
        Assert.assertEquals(new Rectangle2D.Float(-24.0f, -24.0f, 96.0f, 96.0f), layers.get(1).getBounds());
        Assert.assertEquals(new Rectangle2D.Float(0.0f, 0.0f, 96.0f, 96.0f), layers.get(2).getBounds());
    }
}
