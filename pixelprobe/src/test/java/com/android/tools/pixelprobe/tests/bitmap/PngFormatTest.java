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

package com.android.tools.pixelprobe.tests.bitmap;

import com.android.tools.pixelprobe.ColorMode;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.io.IOException;

public class PngFormatTest {
    @Test
    public void png8() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_srgb.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png8Alpha() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_alpha.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png8AdobeRgb() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_adobe_rgb.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("Adobe RGB (1998)", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png16() throws IOException {
        Image image = ImageUtils.loadImage("png/png_16_adobe_rgb.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("Adobe RGB (1998)", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png16Alpha() throws IOException {
        Image image = ImageUtils.loadImage("png/png_16_alpha.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png8Gray() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_gray.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sGray", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(1, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png8GrayAlpha() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_gray_alpha.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sGray", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(2, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png16Gray() throws IOException {
        Image image = ImageUtils.loadImage("png/png_16_gray.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sGray", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(1, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png16GrayAlpha() throws IOException {
        Image image = ImageUtils.loadImage("png/png_16_gray_alpha.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(16, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sGray", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(2, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void png8Indexed() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_indexed.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.INDEXED, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        // ImageIO can read opaque, indexed PNG as translucent images, and this
        // behavior might change with the version of the JDK
    }

    @Test
    public void png8IndexedAlpha() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_indexed_alpha.png");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.INDEXED, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.BITMASK, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void resolution() throws IOException {
        Image image = ImageUtils.loadImage("png/png_8_srgb.png");
        Assert.assertEquals(72.0f, image.getVerticalResolution(), 0.01f);
        image = ImageUtils.loadImage("png/png_300dpi.png");
        Assert.assertEquals(300.0f, image.getVerticalResolution(), 0.01f);
    }
}
