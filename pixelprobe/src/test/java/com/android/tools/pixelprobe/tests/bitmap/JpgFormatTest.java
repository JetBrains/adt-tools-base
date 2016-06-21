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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.awt.color.ColorSpace;
import java.io.IOException;

public class JpgFormatTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void jpg8() throws IOException {
        Image image = ImageUtils.loadImage("jpg/jpg_8_srgb.jpg");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("sRGB IEC61966-2.1", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
    }

    @Test
    public void jpg8AdobeRgb() throws IOException {
        Image image = ImageUtils.loadImage("jpg/jpg_8_adobe_rgb.jpg");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.RGB, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        //Assert.assertEquals("Adobe RGB (1998)", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_RGB, image.getColorSpace().getType());
    }

    @Test
    public void jpg8Cmyk() throws IOException {
        // CMYK JPEGs as written by Photoshop are not supported
        thrown.expect(IOException.class);
        ImageUtils.loadImage("jpg/jpg_8_cmyk.jpg");
    }

    @Test
    public void jpg8Gray() throws IOException {
        Image image = ImageUtils.loadImage("jpg/jpg_8_gray.jpg");
        Assert.assertNotNull(image.getMergedImage());
        Assert.assertEquals(ColorMode.GRAYSCALE, image.getColorMode());
        Assert.assertEquals(8, image.getColorDepth());
        Assert.assertEquals(0, image.getLayers().size());
        Assert.assertEquals(0, image.getGuides().size());
        Assert.assertEquals("KODAK Grayscale Conversion - Gamma 1.0", image.getColorProfileDescription());
        Assert.assertEquals(ColorSpace.TYPE_GRAY, image.getColorSpace().getType());
    }
}
