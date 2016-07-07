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

package com.android.tools.pixelprobe.tests;

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.decoder.Decoder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class OptionsTest {
    @Test
    public void decodeLayers() throws IOException {
        Image image = ImageUtils.loadImage("psd/layer_types.psd", new Decoder.Options().decodeLayers(false));

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(0, layers.size());
    }

    @Test
    public void decodeLayerImage() throws IOException {
        Image image = ImageUtils.loadImage("psd/layer_types.psd", new Decoder.Options().decodeLayerImageData(false));

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(7, layers.size());

        Assert.assertNull(layers.get(4).getImage());
    }

    @Test
    public void decodeLayerShape() throws IOException {
        Image image = ImageUtils.loadImage("psd/layer_types.psd", new Decoder.Options().decodeLayerShapeData(false));

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(7, layers.size());

        Assert.assertNull(layers.get(2).getShapeInfo());
    }

    @Test
    public void decodeLayerText() throws IOException {
        Image image = ImageUtils.loadImage("psd/layer_types.psd", new Decoder.Options().decodeLayerTextData(false));

        List<Layer> layers = image.getLayers();
        Assert.assertEquals(7, layers.size());

        Assert.assertNull(layers.get(5).getTextInfo());
    }

    @Test
    public void decodeLayerEffects() throws IOException {
        Image image = ImageUtils.loadImage("psd/layer_effect_multi_shadow.psd",
                new Decoder.Options().decodeLayerEffects(false));

        List<Layer> layers = image.getLayers();

        Assert.assertNull(layers.get(0).getEffects());
    }

    @Test
    public void decodeGuides() throws IOException {
        Image image = ImageUtils.loadImage("psd/guides.psd", new Decoder.Options().decodeGuides(false));

        Assert.assertEquals(0, image.getGuides().size());
    }


    @Test
    public void decodeThumbnail() throws IOException {
        Image image = ImageUtils.loadImage("psd/rgb.psd", new Decoder.Options().decodeThumbnail(false));
        Assert.assertNull(image.getThumbnailImage());
    }
}
