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

package com.android.ide.common.vectordrawable;

import com.android.ide.common.util.GeneratorTest;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import junit.framework.TestCase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

@SuppressWarnings("javadoc")
public class VectorDrawableGeneratorTest extends GeneratorTest {
    private static final String TEST_DATA_REL_PATH =
      "tools/base/sdk-common/src/test/resources/testData/vectordrawable";

    @Override
    protected String getTestDataRelPath() {
        return TEST_DATA_REL_PATH;
    }

    private enum FileType {
        SVG,
        XML;
    };

    private void checkVectorConversion(String testFileName, FileType type) throws IOException {
        String incomingFileName;
        if (type == FileType.SVG) {
            incomingFileName = testFileName + ".svg";
        } else {
            incomingFileName = testFileName + ".xml";
        }
        String imageName = testFileName + ".png";

        String parentDir =  "vectordrawable" + File.separator;
        File parentDirFile = TestUtils.getRoot("vectordrawable");

        File incomingFile = new File(parentDirFile, incomingFileName);
        String xmlContent = null;
        if (type == FileType.SVG) {
            try {
                OutputStream outStream = new ByteArrayOutputStream();
                Svg2Vector.parseSvgToXml(incomingFile, outStream);
                xmlContent = outStream.toString();
            } catch (Exception e) {
                TestCase.fail("Failure: Exception in Svg2Vector.parseSvgToXml!" + e.getMessage());
            }
        } else {
            xmlContent = Files.toString(incomingFile, Charsets.UTF_8);
        }

        final VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createSizeFromWidth(24);
        StringBuilder builder = new StringBuilder();
        BufferedImage image = VdPreview.getPreviewFromVectorXml(imageTargetSize,
                xmlContent, builder);

        String imageNameWithParent = parentDir + imageName;
        File pngFile = new File(parentDirFile, imageName);
        if (!pngFile.exists()) {
            // Generate golden images here.
            generateGoldenImage(getTargetDir(), image, imageNameWithParent, imageName);
        } else {
            InputStream is = new FileInputStream(pngFile);
            BufferedImage goldenImage = ImageIO.read(is);
            assertImageSimilar(imageNameWithParent, goldenImage, image, 5.0f);
        }

    }

    private void checkSvgConversion(String fileName) throws IOException {
        checkVectorConversion(fileName, FileType.SVG);
    }

    private void checkXmlConversion(String filename) throws IOException {
        checkVectorConversion(filename, FileType.XML);
    }

    public void testControlPoints01() throws Exception {
        checkSvgConversion("test_control_points_01");
    }

    public void testControlPoints02() throws Exception {
        checkSvgConversion("test_control_points_02");
    }

    public void testControlPoints03() throws Exception {
        checkSvgConversion("test_control_points_03");
    }

    public void testIconContentCut() throws Exception {
        checkSvgConversion("ic_content_cut_24px");
    }

    public void testIconInput() throws Exception {
        checkSvgConversion("ic_input_24px");
    }

    public void testIconLiveHelp() throws Exception {
        checkSvgConversion("ic_live_help_24px");
    }

    public void testIconLocalLibrary() throws Exception {
        checkSvgConversion("ic_local_library_24px");
    }

    public void testIconLocalPhone() throws Exception {
        checkSvgConversion("ic_local_phone_24px");
    }

    public void testIconMicOff() throws Exception {
        checkSvgConversion("ic_mic_off_24px");
    }

    public void testShapes() throws Exception {
        checkSvgConversion("ic_shapes");
    }

    public void testIconTempHigh() throws Exception {
        checkSvgConversion("ic_temp_high");
    }

    public void testIconPlusSign() throws Exception {
        checkSvgConversion("ic_plus_sign");
    }

    public void testIconPolylineStrokeWidth() throws Exception {
        checkSvgConversion("ic_polyline_strokewidth");
    }

    public void testIconEmptyAttributes() throws Exception {
        checkSvgConversion("ic_empty_attributes");
    }

    public void testIconSimpleGroupInfo() throws Exception {
        checkSvgConversion("ic_simple_group_info");
    }

    public void testXmlIconSizeOpacity() throws Exception {
        checkXmlConversion("ic_size_opacity");
    }
}
