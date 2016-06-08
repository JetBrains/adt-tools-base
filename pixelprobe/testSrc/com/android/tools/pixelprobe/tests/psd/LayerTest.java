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

public class LayerTest {
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
}
