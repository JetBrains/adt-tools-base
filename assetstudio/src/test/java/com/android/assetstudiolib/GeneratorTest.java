/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.assetstudiolib;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

/**
 * Shared test infrastructure for code generator
 */
public abstract class GeneratorTest extends TestCase implements GraphicGeneratorContext {
    private static final String TEST_DATA_REL_PATH =
            "assetstudio/tests/src/com/android/assetstudiolib/testdata";

    protected void checkGraphic(int expectedFileCount, String folderName, String baseName,
            GraphicGenerator generator, GraphicGenerator.Options options)
            throws IOException {
        Map<String, Map<String, BufferedImage>> categoryMap =
                new HashMap<String, Map<String, BufferedImage>>();
        options.sourceImage = GraphicGenerator.getClipartImage("android.png");
        generator.generate(null, categoryMap, this, options, baseName);

        File targetDir = getTargetDir();

        List<String> errors = new ArrayList<String>();
        int fileCount = 0;
        for (Map<String, BufferedImage> previews : categoryMap.values()) {
            for (Map.Entry<String, BufferedImage> entry : previews.entrySet()) {
                String relativePath = entry.getKey();
                BufferedImage image = entry.getValue();

                String path = "testdata" + File.separator + folderName + File.separator
                        + relativePath;
                InputStream is = GeneratorTest.class.getResourceAsStream(path);
                if (is == null) {
                    if (targetDir == null) {
                        fail("Did not find " + path
                                + ". Set ADT_SDK_SOURCE_PATH to have it created automatically");
                    }
                    File fileName = new File(targetDir, folderName + File.separator
                            + relativePath);
                    assertFalse(fileName.exists());
                    if (!fileName.getParentFile().exists()) {
                        boolean mkdir = fileName.getParentFile().mkdirs();
                        assertTrue(fileName.getParent(), mkdir);
                    }

                    ImageIO.write(image, "PNG", fileName);
                    errors.add("File did not exist, created " + fileName.getPath());
                } else {
                    BufferedImage goldenImage = ImageIO.read(is);
                    assertImageSimilar(relativePath, goldenImage, image, 5.0f);
                }
            }

            fileCount += previews.values().size();
        }
        if (errors.size() > 0) {
            fail(errors.toString());
        }

        assertEquals("Wrong number of generated files", expectedFileCount, fileCount);
    }

    private void assertImageSimilar(String imageName, BufferedImage goldenImage,
            BufferedImage image, float maxPercentDifferent) throws IOException {
        assertTrue("Widths differ too much for " + imageName, Math.abs(goldenImage.getWidth()
                - image.getWidth()) < 2);
        assertTrue("Widths differ too much for " + imageName, Math.abs(goldenImage.getHeight()
                - image.getHeight()) < 2);

        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());

        if (goldenImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage temp = new BufferedImage(goldenImage.getWidth(), goldenImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            temp.getGraphics().drawImage(goldenImage, 0, 0, null);
            goldenImage = temp;
        }
        assertEquals(BufferedImage.TYPE_INT_ARGB, goldenImage.getType());

        int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
        int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());

        // Blur the images to account for the scenarios where there are pixel
        // differences
        // in where a sharp edge occurs
        // goldenImage = blur(goldenImage, 6);
        // image = blur(image, 6);

        int width = 3 * imageWidth;
        int height = imageHeight;
        BufferedImage deltaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = deltaImage.getGraphics();

        // Compute delta map
        long delta = 0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int goldenRgb = goldenImage.getRGB(x, y);
                int rgb = image.getRGB(x, y);
                if (goldenRgb == rgb) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080);
                    continue;
                }

                // If the pixels have no opacity, don't delta colors at all
                if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080);
                    continue;
                }

                int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
                int newR = 128 + deltaR & 0xFF;
                int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
                int newG = 128 + deltaG & 0xFF;
                int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
                int newB = 128 + deltaB & 0xFF;

                int avgAlpha = ((((goldenRgb & 0xFF000000) >>> 24)
                        + ((rgb & 0xFF000000) >>> 24)) / 2) << 24;

                int newRGB = avgAlpha | newR << 16 | newG << 8 | newB;
                deltaImage.setRGB(imageWidth + x, y, newRGB);

                delta += Math.abs(deltaR);
                delta += Math.abs(deltaG);
                delta += Math.abs(deltaB);
            }
        }

        // 3 different colors, 256 color levels
        long total = imageHeight * imageWidth * 3L * 256L;
        float percentDifference = (float) (delta * 100 / (double) total);

        if (percentDifference > maxPercentDifferent) {
            // Expected on the left
            // Golden on the right
            g.drawImage(goldenImage, 0, 0, null);
            g.drawImage(image, 2 * imageWidth, 0, null);

            // Labels
            if (imageWidth > 80) {
                g.setColor(Color.RED);
                g.drawString("Expected", 10, 20);
                g.drawString("Actual", 2 * imageWidth + 10, 20);
            }

            File output = new File(getTempDir(), "delta-"
                    + imageName.replace(File.separatorChar, '_'));
            if (output.exists()) {
                output.delete();
            }
            ImageIO.write(deltaImage, "PNG", output);
            String message = String.format("Images differ (by %.1f%%) - see details in %s",
                    percentDifference, output);
            System.out.println(message);
            fail(message);
        }

        g.dispose();
    }

    protected File getTempDir() {
        if (System.getProperty("os.name").equals("Mac OS X")) {
            return new File("/tmp"); //$NON-NLS-1$
        }

        return new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
    }

    @Override
    public BufferedImage loadImageResource(String path) {
        try {
            return GraphicGenerator.getStencilImage(path);
        } catch (IOException e) {
            fail(e.toString());
        }

        return null;
    }

    /** Get the location to write missing golden files to */
    protected File getTargetDir() {
        // Set $ADT_SDK_SOURCE_PATH to point to your git "sdk" directory
        String sdk = System.getenv("ADT_SDK_SOURCE_PATH");
        if (sdk != null) {
            File sdkPath = new File(sdk);
            if (sdkPath.exists()) {
                File testData = new File(sdkPath, TEST_DATA_REL_PATH.replace('/',
                        File.separatorChar));
                if (testData.exists()) {
                    return testData;
                }
            }
        }

        return null;
    }
}
