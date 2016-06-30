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

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

// Note: 16 and 32 bit images are stored with 1 float per component,
//       or 32 bits per component. Half floats are not supported.
public class DepthTest {
    @Test
    public void bitmap() throws IOException {
        Image image = ImageUtils.loadImage("psd/bitmap.psd");
        Assert.assertEquals(1, image.getColorDepth());
        Assert.assertEquals(1, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void cmyk() throws IOException {
        Image image = ImageUtils.loadImage("psd/cmyk.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(32, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void cmyk16() throws IOException {
        Image image = ImageUtils.loadImage("psd/cmyk_16.psd");
        Assert.assertEquals(16, image.getColorDepth());
        // 16 bit CMYK images are loaded as 8 bit images for now
        Assert.assertEquals(32, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void duotone() throws IOException {
        Image image = ImageUtils.loadImage("psd/duotone.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(8, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void grayscale() throws IOException {
        Image image = ImageUtils.loadImage("psd/grayscale.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(8, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void grayscale16() throws IOException {
        Image image = ImageUtils.loadImage("psd/grayscale_16.psd");
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(32, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void grayscale32() throws IOException {
        Image image = ImageUtils.loadImage("psd/grayscale_32.psd");
        Assert.assertEquals(32, image.getColorDepth());
        Assert.assertEquals(32, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void indexed() throws IOException {
        Image image = ImageUtils.loadImage("psd/indexed.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(8, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void lab() throws IOException {
        Image image = ImageUtils.loadImage("psd/lab.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(24, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void lab16() throws IOException {
        Image image = ImageUtils.loadImage("psd/lab_16.psd");
        Assert.assertEquals(16, image.getColorDepth());
        // 16 bit Lab images are loaded as 8 bit images for now
        Assert.assertEquals(24, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void rgb() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb.psd");
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(24, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void rgb16() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb_16.psd");
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(96, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void rgb32() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb_32.psd");
        Assert.assertEquals(32, image.getColorDepth());
        Assert.assertEquals(96, image.getMergedImage().getColorModel().getPixelSize());
    }

    @Test
    public void layeredRgb16() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb_16.psd");

        List<Layer> layers = image.getLayers();

        // With translucency
        Layer layer = layers.get(1);
        Assert.assertTrue(layer.getImage().getColorModel().hasAlpha());
        Assert.assertEquals(128, layer.getImage().getColorModel().getPixelSize());

        // Opaque
        layer = layers.get(2);
        Assert.assertFalse(layer.getImage().getColorModel().hasAlpha());
        Assert.assertEquals(96, layer.getImage().getColorModel().getPixelSize());
    }

    @Test
    public void layeredRgb32() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb_32.psd");

        List<Layer> layers = image.getLayers();

        // With translucency
        Layer layer = layers.get(1);
        Assert.assertTrue(layer.getImage().getColorModel().hasAlpha());
        Assert.assertEquals(128, layer.getImage().getColorModel().getPixelSize());

        // Opaque
        layer = layers.get(2);
        Assert.assertFalse(layer.getImage().getColorModel().hasAlpha());
        Assert.assertEquals(96, layer.getImage().getColorModel().getPixelSize());
    }
}
