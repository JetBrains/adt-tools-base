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

import com.android.tools.pixelprobe.ColorMode;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.color.ColorSpace;
import java.io.IOException;

public class ColorModeTest {
    @Test
    public void bitmap() throws IOException {
        Image image = ImageUtils.loadImage("psd/bitmap.psd");
        Assert.assertEquals(ColorMode.BITMAP, image.getColorMode());
        // Indexed color models are always RGB
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace(image).getType());
    }

    @Test
    public void cmyk() throws IOException {
        Image image = ImageUtils.loadImage("psd/cmyk.psd");
        Assert.assertEquals(ColorMode.CMYK, image.getColorMode());
        Assert.assertEquals(ColorSpace.TYPE_CMYK, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_CMYK, colorSpace(image).getType());
    }

    @Test
    public void duotone() throws IOException {
        Image image = ImageUtils.loadImage("psd/duotone.psd");
        Assert.assertEquals(ColorMode.DUOTONE, image.getColorMode());
        // The embedded color space might be different from the merged image's
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, colorSpace(image).getType());
    }

    @Test
    public void grayscale() throws IOException {
        Image image = ImageUtils.loadImage("psd/grayscale.psd");
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, colorSpace(image).getType());
    }

    @Test
    public void indexed() throws IOException {
        Image image = ImageUtils.loadImage("psd/indexed.psd");
        Assert.assertEquals(ColorMode.INDEXED, image.getColorMode());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace(image).getType());
    }

    @Test
    public void lab() throws IOException {
        Image image = ImageUtils.loadImage("psd/lab.psd");
        Assert.assertEquals(ColorMode.LAB, image.getColorMode());
        // The embedded color space might be different from the merged image's
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_Lab, colorSpace(image).getType());
    }

    @Test
    public void rgb() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb.psd");
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace(image).getType());
    }

    private static ColorSpace colorSpace(Image image) {
        return image.getMergedImage().getColorModel().getColorSpace();
    }
}
