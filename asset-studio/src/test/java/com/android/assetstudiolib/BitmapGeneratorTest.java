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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Shared test infrastructure for bitmap generator
 */
public abstract class BitmapGeneratorTest extends GeneratorTest implements GraphicGeneratorContext {
    private static final String TEST_DATA_REL_PATH =
            "tools/base/asset-studio/src/test/java/com/android/assetstudiolib/testdata";

    @Override
    protected String getTestDataRelPath() {
        return TEST_DATA_REL_PATH;
    };

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

                if (image == null) continue;

                String path = "testdata" + File.separator + folderName + File.separator
                        + relativePath;
                InputStream is = BitmapGeneratorTest.class.getResourceAsStream(path);
                if (is == null) {
                    String filePath = folderName + File.separator + relativePath;
                    String generatedFilePath = generateGoldenImage(targetDir, image, path, filePath);
                    errors.add("File did not exist, created " + generatedFilePath);
                } else {
                    BufferedImage goldenImage = ImageIO.read(is);
                    assertImageSimilar(relativePath, goldenImage, image, 5.0f);
                }
            }

            fileCount += previews.values().size();
        }
        if (!errors.isEmpty()) {
            fail(errors.toString());
        }

        assertEquals("Wrong number of generated files", expectedFileCount, fileCount);
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
}
