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

import com.android.tools.pixelprobe.Guide;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.decoder.Decoder;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class ImageTest {
    @Test
    public void empty() throws IOException {
        Image image = ImageUtils.loadImage("psd/empty.psd");
        Assert.assertNotNull(image.getMergedImage());

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(1, layers.size());

        Assert.assertEquals(Layer.Type.IMAGE, layers.get(0).getType());
        Assert.assertNull(layers.get(0).getImage());
    }

    @Test
    public void thumbnail() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb.psd", new Decoder.Options().decodeThumbnail(true));
        BufferedImage thumbnail = image.getThumbnailImage();

        Assert.assertNotNull(thumbnail);

        Assert.assertTrue(thumbnail.getWidth() < image.getWidth());
        Assert.assertTrue(thumbnail.getHeight() < image.getHeight());

        Assert.assertFalse(thumbnail.getColorModel().hasAlpha());
        Assert.assertEquals(ColorSpace.TYPE_RGB, thumbnail.getColorModel().getColorSpace().getType());
    }

    @Test
    public void dimension() throws IOException {
        Image image = ImageUtils.loadImage("psd/empty.psd");

        Assert.assertEquals(256, image.getWidth());
        Assert.assertEquals(256, image.getHeight());
    }

    @Test
    public void resolution() throws IOException {
        Image image = ImageUtils.loadImage("psd/empty.psd");

        Assert.assertEquals(72.0f, image.getHorizontalResolution(), 0.001f);
        Assert.assertEquals(72.0f, image.getVerticalResolution(), 0.001f);

        image = ImageUtils.loadImage("psd/empty_300dpi.psd");

        Assert.assertEquals(300.0f, image.getHorizontalResolution(), 0.001f);
        Assert.assertEquals(300.0f, image.getVerticalResolution(), 0.001f);
    }

    @Test
    public void guides() throws IOException {
        Image image = ImageUtils.loadImage("psd/guides.psd");

        List<Guide> guides = image.getGuides();
        Assert.assertEquals(14, guides.size());

        int horizontalCount = 0;
        int verticalCount = 0;
        for (Guide guide : guides) {
            boolean horizontal = guide.getOrientation() == Guide.Orientation.HORIZONTAL;
            if (horizontal) {
                horizontalCount++;
            } else {
                verticalCount++;
            }

            Assert.assertTrue(guide.getPosition() >= 0.0f);
            Assert.assertTrue(guide.getPosition() <= (horizontal ? image.getHeight() : image.getWidth()));
        }

        Assert.assertEquals(8, horizontalCount);
        Assert.assertEquals(6, verticalCount);
    }

    @Test
    public void complex() throws IOException {
        // Load complex PSD files and see if we crash
        ImageUtils.loadImage("psd/complex1.psd");
        ImageUtils.loadImage("psd/complex2.psd");
    }
}
