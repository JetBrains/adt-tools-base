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
import com.android.tools.pixelprobe.TextInfo;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.List;

public class TextTest {
    @Test
    public void font() throws IOException {
        Image image = ImageUtils.loadImage("psd/text_font.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());

        TextInfo textInfo = layer.getTextInfo();
        Assert.assertEquals("Single, Roboto", textInfo.getText());

        Assert.assertEquals(1, textInfo.getParagraphRuns().size());
        Assert.assertEquals(1, textInfo.getStyleRuns().size());

        List<TextInfo.StyleRun> runs = textInfo.getStyleRuns();
        TextInfo.StyleRun run = runs.get(0);

        Assert.assertEquals(0, run.getStart());
        Assert.assertEquals("Single, Roboto".length(), run.getEnd());
        Assert.assertEquals("Single, Roboto".length(), run.getLength());
        Assert.assertEquals("Roboto-Light", run.getFont());
        Assert.assertEquals(17.0f, run.getFontSize(), 0.01f);
    }

    @Test
    public void styleRuns() throws IOException {
        Image image = ImageUtils.loadImage("psd/text_style_runs.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());

        TextInfo textInfo = layer.getTextInfo();
        Assert.assertEquals("Red Green Blue", textInfo.getText());

        Assert.assertEquals(1, textInfo.getParagraphRuns().size());
        Assert.assertEquals(3, textInfo.getStyleRuns().size());

        List<TextInfo.StyleRun> runs = textInfo.getStyleRuns();
        TextInfo.StyleRun run = runs.get(0);

        Assert.assertEquals(0, run.getStart());
        Assert.assertEquals(4, run.getEnd());
        Assert.assertEquals(4, run.getLength());
        Assert.assertEquals("Roboto-Light", run.getFont());
        Assert.assertEquals(17.0f, run.getFontSize(), 0.01f);
        Assert.assertEquals(0.0f, run.getTracking(), 0.01f);
        Assert.assertEquals(Color.RED, run.getPaint());

        run = runs.get(1);

        Assert.assertEquals(4, run.getStart());
        Assert.assertEquals(10, run.getEnd());
        Assert.assertEquals(6, run.getLength());
        Assert.assertEquals("Roboto-Regular", run.getFont());
        Assert.assertEquals(21.0f, run.getFontSize(), 0.01f);
        Assert.assertEquals(-0.04f, run.getTracking(), 0.01f);
        Assert.assertEquals(Color.GREEN, run.getPaint());

        run = runs.get(2);

        Assert.assertEquals(10, run.getStart());
        Assert.assertEquals(14, run.getEnd());
        Assert.assertEquals(4, run.getLength());
        Assert.assertEquals("Roboto-Thin", run.getFont());
        Assert.assertEquals(23.0f, run.getFontSize(), 0.01f);
        Assert.assertEquals(0.0f, run.getTracking(), 0.01f);
        Assert.assertEquals(Color.BLUE, run.getPaint());
    }

    @Test
    public void alignments() throws IOException {
        Image image = ImageUtils.loadImage("psd/text_alignments.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());
        TextInfo textInfo = layer.getTextInfo();
        List<TextInfo.ParagraphRun> runs = textInfo.getParagraphRuns();
        Assert.assertEquals(1, runs.size());
        TextInfo.ParagraphRun run = runs.get(0);
        Assert.assertEquals(TextInfo.Alignment.JUSTIFY, run.getAlignment());

        layer = layers.get(1);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());
        textInfo = layer.getTextInfo();
        Assert.assertEquals("Center aligned", textInfo.getText());
        runs = textInfo.getParagraphRuns();
        Assert.assertEquals(1, runs.size());
        run = runs.get(0);
        Assert.assertEquals(TextInfo.Alignment.CENTER, run.getAlignment());

        layer = layers.get(2);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());
        textInfo = layer.getTextInfo();
        Assert.assertEquals("Right aligned", textInfo.getText());
        runs = textInfo.getParagraphRuns();
        Assert.assertEquals(1, runs.size());
        run = runs.get(0);
        Assert.assertEquals(TextInfo.Alignment.RIGHT, run.getAlignment());

        layer = layers.get(3);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());
        textInfo = layer.getTextInfo();
        Assert.assertEquals("Left aligned", textInfo.getText());
        runs = textInfo.getParagraphRuns();
        Assert.assertEquals(1, runs.size());
        run = runs.get(0);
        Assert.assertEquals(TextInfo.Alignment.LEFT, run.getAlignment());
    }

    @Test
    public void transform() throws IOException {
        Image image = ImageUtils.loadImage("psd/text_transform.psd");

        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());

        TextInfo textInfo = layer.getTextInfo();
        Assert.assertEquals("Scale", textInfo.getText());

        AffineTransform transform = textInfo.getTransform();
        Assert.assertFalse(transform.isIdentity());
        Assert.assertEquals(0.0f, transform.getShearX(), 0.01f);
        Assert.assertEquals(0.0f, transform.getShearY(), 0.01f);
        Assert.assertNotEquals(0.0f, transform.getScaleX());
        Assert.assertNotEquals(0.0f, transform.getScaleY());

        layer = layers.get(1);
        Assert.assertEquals(Layer.Type.TEXT, layer.getType());

        textInfo = layer.getTextInfo();
        Assert.assertEquals("Shear", textInfo.getText());

        transform = textInfo.getTransform();
        Assert.assertFalse(transform.isIdentity());
        Assert.assertNotEquals(0.0f, transform.getShearX());
        Assert.assertNotEquals(0.0f, transform.getShearY());
        Assert.assertNotEquals(0.0f, transform.getScaleX());
        Assert.assertNotEquals(0.0f, transform.getScaleY());
    }
}
