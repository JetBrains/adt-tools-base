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
import com.android.tools.pixelprobe.tests.ImageUtils;
import com.android.tools.pixelprobe.util.Images;
import org.junit.Assert;
import org.junit.Test;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.io.IOException;

/**
 * Important note for these tests: the color space decoded from the PSD file
 * and associated with the Image instance might be different than the color
 * space associated with the merged BufferedImage. For instance, Photoshop
 * saves Lab images with an sRGB profile, even though the image data is in
 * the Lab color space. In this case, the merged image will have a Lab color
 * space but the Image will have an RGB color space.
 */
public class ColorProfileTest {
    @Test
    public void cmyk() throws IOException {
        Image image = ImageUtils.loadImage("psd/cmyk.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_CMYK, colorSpace.getType());
        Assert.assertTrue(colorSpace instanceof ICC_ColorSpace);
        Assert.assertFalse(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void adobeRgb() throws IOException {
        Image image = ImageUtils.loadImage("psd/color_profile_adobe_rgb.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertTrue(colorSpace instanceof ICC_ColorSpace);
        Assert.assertEquals("Adobe RGB (1998)", image.getColorProfileDescription());
        Assert.assertFalse(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void sRgb() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertTrue(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void rgbNoProfile() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb_no_profile.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertTrue(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void bitmap() throws IOException {
        Image image = ImageUtils.loadImage("psd/bitmap.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertTrue(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void duotone() throws IOException {
        Image image = ImageUtils.loadImage("psd/duotone.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertFalse(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void grayscale() throws IOException {
        Image image = ImageUtils.loadImage("psd/grayscale.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_GRAY, colorSpace.getType());
        Assert.assertEquals("sGray", image.getColorProfileDescription());
        Assert.assertFalse(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void indexed() throws IOException {
        Image image = ImageUtils.loadImage("psd/indexed.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertTrue(Images.isColorSpace_sRGB(image.getMergedImage()));
    }

    @Test
    public void lab() throws IOException {
        Image image = ImageUtils.loadImage("psd/lab.psd");
        ColorSpace colorSpace = image.getColorSpace();
        Assert.assertEquals(ColorSpace.TYPE_RGB, colorSpace.getType());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertFalse(Images.isColorSpace_sRGB(image.getMergedImage()));
    }
}
