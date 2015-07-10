/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.assetstudiolib.vectordrawable.Svg2Vector;
import com.android.assetstudiolib.vectordrawable.VdPreview;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;

@SuppressWarnings("javadoc")
public class VectorDrawbleGeneratorTest extends GeneratorTest {
    private void checkVectorConversion(String testFileName) throws IOException {
        String imageName = testFileName + ".png";
        String svgName = testFileName + ".svg";

        String parentDir =  "vectordrawable" + File.separator;
        String grandParentDir = "testdata" + File.separator + parentDir;
        String svgPath = grandParentDir + svgName;
        URL url = GeneratorTest.class.getResource(svgPath);

        OutputStream outStream = new ByteArrayOutputStream();
        try {
            Svg2Vector.parseSvgToXml(new File(url.toURI()), outStream);
        }
        catch (Exception e) {
            assertTrue("Failure: Exception in Svg2Vector.parseSvgToXml!", false);
        }

        final VdPreview.Size imageSize = VdPreview.Size.createSizeFromWidth(24);
        StringBuilder builder = new StringBuilder();
        BufferedImage image = VdPreview.getPreviewFromVectorXml(imageSize, outStream.toString(), builder);

        String path = grandParentDir + imageName;
        InputStream is = GeneratorTest.class.getResourceAsStream(path);
        if (is == null) {
            // Generate golden images here.
            generateGoldenImage(getTargetDir(), image, path, parentDir + imageName);
        } else {
            BufferedImage goldenImage = ImageIO.read(is);
            assertImageSimilar(path, goldenImage, image, 1.0f);
        }
    }

    //public void testControlPoints01() throws Exception {
    //    checkVectorConversion("test_control_points_01");
    //}
    //
    //public void testControlPoints02() throws Exception {
    //    checkVectorConversion("test_control_points_02");
    //}

    public void testControlPoints03() throws Exception {
        checkVectorConversion("test_control_points_03");
    }

    public void testIconContentCut() throws Exception {
        checkVectorConversion("ic_content_cut_24px");
    }

    public void testIconInput() throws Exception {
        checkVectorConversion("ic_input_24px");
    }

    public void testIconLiveHelp() throws Exception {
        checkVectorConversion("ic_live_help_24px");
    }

    public void testIconLocalLibrary() throws Exception {
        checkVectorConversion("ic_local_library_24px");
    }

    public void testIconLocalPhone() throws Exception {
        checkVectorConversion("ic_local_phone_24px");
    }

    public void testIconMicOff() throws Exception {
        checkVectorConversion("ic_mic_off_24px");
    }

    public void testShapes() throws Exception {
        checkVectorConversion("ic_shapes");
    }

    public void testIconTempHigh() throws Exception {
        checkVectorConversion("ic_temp_high");
    }

    public void testIconPlusSign() throws Exception {
        checkVectorConversion("ic_plus_sign");
    }
}
