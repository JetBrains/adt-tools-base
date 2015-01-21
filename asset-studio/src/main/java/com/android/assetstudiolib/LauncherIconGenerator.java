/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.assetstudiolib;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.utils.Pair;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link GraphicGenerator} that generates Android "launcher" icons.
 */
public class LauncherIconGenerator extends GraphicGenerator {
    private static final Rectangle IMAGE_SIZE_WEB = new Rectangle(0, 0, 512, 512);
    private static final Rectangle IMAGE_SIZE_MDPI = new Rectangle(0, 0, 48, 48);

    private static final Map<Pair<Shape, Density>, Rectangle> TARGET_RECTS
            = new HashMap<Pair<Shape, Density>, Rectangle>();

    static {
        // None, Web
        TARGET_RECTS.put(Pair.of(Shape.NONE, (Density) null), new Rectangle(32, 32, 448, 448));
        // None, HDPI
        TARGET_RECTS.put(Pair.of(Shape.NONE, Density.HIGH), new Rectangle(4, 4, 64, 64));
        // None, MDPI
        TARGET_RECTS.put(Pair.of(Shape.NONE, Density.MEDIUM), new Rectangle(3, 3, 42, 42));

        // Circle, Web
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, (Density) null), new Rectangle(32, 43, 448, 448));
        // Circle, HDPI
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, Density.HIGH), new Rectangle(4, 6, 64, 64));
        // Circle, MDPI
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, Density.MEDIUM), new Rectangle(3, 4, 42, 42));

        // Square, Web
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, (Density) null), new Rectangle(32, 53, 448, 427));
        // Square, HDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, Density.HIGH), new Rectangle(4, 8, 64, 60));
        // Square, MDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, Density.MEDIUM), new Rectangle(3, 5, 42, 40));
    }

    @Override
    public BufferedImage generate(GraphicGeneratorContext context, Options options) {
        LauncherOptions launcherOptions = (LauncherOptions) options;

        String density;
        if (launcherOptions.isWebGraphic) {
            density = "web";
        } else {
            density = launcherOptions.density.getResourceValue();
        }

        BufferedImage backImage = null, foreImage = null, maskImage = null, maskInnerImage = null;
        if (launcherOptions.shape != Shape.NONE && launcherOptions.shape != null) {
            String shape = launcherOptions.shape.id;
            backImage = context.loadImageResource("/images/launcher_stencil/"
                    + shape + "/" + density + "/back.png");
            foreImage = context.loadImageResource("/images/launcher_stencil/"
                    + shape + "/" + density + "/" + launcherOptions.style.id + ".png");
            maskImage = context.loadImageResource("/images/launcher_stencil/"
                    + shape + "/" + density + "/mask.png");
            maskInnerImage = context.loadImageResource("/images/launcher_stencil/"
                    + shape + "/" + density + "/mask_inner.png");
        }

        Rectangle imageRect = IMAGE_SIZE_WEB;
        if (!launcherOptions.isWebGraphic) {
            imageRect = Util.scaleRectangle(IMAGE_SIZE_MDPI,
                    GraphicGenerator.getMdpiScaleFactor(launcherOptions.density));
        }

        Rectangle targetRect = TARGET_RECTS.get(
                Pair.of(launcherOptions.shape, launcherOptions.density));
        if (targetRect == null) {
            // Scale up from MDPI if no density-specific target rectangle is defined.
            targetRect = Util.scaleRectangle(
                    TARGET_RECTS.get(Pair.of(launcherOptions.shape, Density.MEDIUM)),
                    GraphicGenerator.getMdpiScaleFactor(launcherOptions.density));
        }

        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();
        if (backImage != null) {
            g.drawImage(backImage, 0, 0, null);
        }

        BufferedImage tempImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        if (maskImage != null) {
            g2.drawImage(maskImage, 0, 0, null);
            g2.setComposite(AlphaComposite.SrcAtop);
            g2.setPaint(new Color(launcherOptions.backgroundColor));
            g2.fillRect(0, 0, imageRect.width, imageRect.height);
        }

        BufferedImage tempImage2 = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g3 = (Graphics2D) tempImage2.getGraphics();
        if (maskInnerImage != null) {
            g3.drawImage(maskInnerImage, 0, 0, null);
            g3.setComposite(AlphaComposite.SrcAtop);
            g3.setPaint(new Color(launcherOptions.backgroundColor));
            g3.fillRect(0, 0, imageRect.width, imageRect.height);
        }

        if (launcherOptions.crop) {
            Util.drawCenterCrop(g3, launcherOptions.sourceImage, targetRect);
        } else {
            Util.drawCenterInside(g3, launcherOptions.sourceImage, targetRect);
        }

        g2.drawImage(tempImage2, 0, 0, null);
        g.drawImage(tempImage, 0, 0, null);
        if (foreImage != null) {
            g.drawImage(foreImage, 0, 0, null);
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    @Override
    protected boolean includeDensity(@NonNull Density density) {
        // Launcher icons should include xxxhdpi as well
        return super.includeDensity(density) || density == Density.XXXHIGH;
    }

    @Override
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options options, String name) {
        LauncherOptions launcherOptions = (LauncherOptions) options;
        boolean generateWebImage = launcherOptions.isWebGraphic;
        launcherOptions.isWebGraphic = false;
        super.generate(category, categoryMap, context, options, name);

        if (generateWebImage) {
            launcherOptions.isWebGraphic = true;
            launcherOptions.density = null;
            BufferedImage image = generate(context, options);
            if (image != null) {
                Map<String, BufferedImage> imageMap = new HashMap<String, BufferedImage>();
                categoryMap.put("Web", imageMap);
                imageMap.put(getIconPath(options, name), image);
            }
        }
    }

    @Override
    protected String getIconPath(Options options, String name) {
        if (((LauncherOptions) options).isWebGraphic) {
            return name + "-web.png"; // Store at the root of the project
        }

        return super.getIconPath(options, name);
    }

    /** Options specific to generating launcher icons */
    public static class LauncherOptions extends GraphicGenerator.Options {
        public LauncherOptions() {
            mipmap = true;
        }

        /** Background color, as an RRGGBB packed integer */
        public int backgroundColor = 0;

        /** Whether the image should be cropped or not */
        public boolean crop = true;

        /** The shape to use for the background */
        public Shape shape = Shape.SQUARE;

        /** The effects to apply to the foreground */
        public Style style = Style.SIMPLE;

        /**
         * Whether a web graphic should be generated (will ignore normal density
         * setting). The {@link #generate(GraphicGeneratorContext, Options)}
         * method will use this to decide whether to generate a normal density
         * icon or a high res web image. The
         * {@link GraphicGenerator#generate(String, Map, GraphicGeneratorContext, Options, String)}
         * method will use this flag to determine whether it should include a
         * web graphic in its iteration.
         */
        public boolean isWebGraphic;
    }
}
