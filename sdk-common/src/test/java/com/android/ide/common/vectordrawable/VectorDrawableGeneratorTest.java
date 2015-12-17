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

import com.android.SdkConstants;
import com.android.ide.common.util.GeneratorTest;
import com.android.testutils.TestUtils;
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

    private static final int IMAGE_SIZE = 64;

    @Override
    protected String getTestDataRelPath() {
        return TEST_DATA_REL_PATH;
    }

    private enum FileType {
        SVG,
        XML;
    };

    private void checkVectorConversion(String testFileName, FileType type,
                                       boolean dumpXml, String expectedError) throws IOException {
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
                String errorLog = Svg2Vector.parseSvgToXml(incomingFile, outStream);
                if (expectedError != null) {
                    TestCase.assertNotNull(errorLog);
                    TestCase.assertFalse(errorLog.isEmpty());
                    TestCase.assertTrue(errorLog.contains(expectedError));
                }
                xmlContent = outStream.toString();
                if (xmlContent == null || xmlContent.isEmpty()) {
                    TestCase.fail("Empty Xml file.");
                }
                if (dumpXml) {
                    File tempXmlFile = new File(parentDirFile, imageName + ".xml");
                    PrintWriter writer = new PrintWriter(tempXmlFile);
                    writer.println(xmlContent);
                    writer.close();
                }
            } catch (Exception e) {
                TestCase.fail("Failure: Exception in Svg2Vector.parseSvgToXml!" + e.getMessage());
            }
        } else {
            xmlContent = Files.toString(incomingFile, Charsets.UTF_8);
        }

        final VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createSizeFromWidth(IMAGE_SIZE);
        StringBuilder builder = new StringBuilder();
        BufferedImage image = VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlContent,
                builder);

        String imageNameWithParent = parentDir + imageName;
        File pngFile = new File(parentDirFile, imageName);
        if (!pngFile.exists()) {
            // Generate golden images here.
            generateGoldenImage(getTargetDir(), image, imageNameWithParent, imageName);
        } else {
            InputStream is = new FileInputStream(pngFile);
            BufferedImage goldenImage = ImageIO.read(is);
            // Mostly, this threshold is for JDK versions. The golden image is generated
            // on Linux with JDK 6, the expected delta is 0 on the same platform and JDK version.
            float diffThreshold = 1.5f;
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
                diffThreshold = 5.0f;
            }
            assertImageSimilar(imageNameWithParent, goldenImage, image, diffThreshold);
        }

    }

    private void checkSvgConversion(String fileName) throws IOException {
        checkVectorConversion(fileName, FileType.SVG, false, null);
    }

    private void checkXmlConversion(String filename) throws IOException {
        checkVectorConversion(filename, FileType.XML, false, null);
    }

    private void checkSvgConversionAndContainsError(String filename, String errorLog) throws IOException {
        checkVectorConversion(filename, FileType.SVG, false, errorLog);
    }

    private void checkSvgConversionDebug(String fileName) throws IOException {
        checkVectorConversion(fileName, FileType.SVG, true, null);
    }

    //////////////////////////////////////////////////////////
    // Tests starts here:
    public void testSvgArcto1() throws Exception {
        checkSvgConversion("test_arcto_1");
    }

    public void testSvgControlPoints01() throws Exception {
        checkSvgConversion("test_control_points_01");
    }

    public void testSvgControlPoints02() throws Exception {
        checkSvgConversion("test_control_points_02");
    }

    public void testSvgControlPoints03() throws Exception {
        checkSvgConversion("test_control_points_03");
    }

    public void testSvgContentCut() throws Exception {
        checkSvgConversion("ic_content_cut_24px");
    }

    public void testSvgInput() throws Exception {
        checkSvgConversion("ic_input_24px");
    }

    public void testSvgLiveHelp() throws Exception {
        checkSvgConversion("ic_live_help_24px");
    }

    public void testSvgLocalLibrary() throws Exception {
        checkSvgConversion("ic_local_library_24px");
    }

    public void testSvgLocalPhone() throws Exception {
        checkSvgConversion("ic_local_phone_24px");
    }

    public void testSvgMicOff() throws Exception {
        checkSvgConversion("ic_mic_off_24px");
    }

    public void testSvgShapes() throws Exception {
        checkSvgConversion("ic_shapes");
    }

    public void testSvgEllipse() throws Exception {
        checkSvgConversion("test_ellipse");
    }

    public void testSvgTempHigh() throws Exception {
        checkSvgConversion("ic_temp_high");
    }

    public void testSvgPlusSign() throws Exception {
        checkSvgConversion("ic_plus_sign");
    }

    public void testSvgPolylineStrokeWidth() throws Exception {
        checkSvgConversion("ic_polyline_strokewidth");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgStrokeWidthTransform() throws Exception {
        checkSvgConversionAndContainsError("ic_strokewidth_transform",
                "We don't scale the stroke width!");
    }

    public void testSvgEmptyAttributes() throws Exception {
        checkSvgConversion("ic_empty_attributes");
    }

    public void testSvgSimpleGroupInfo() throws Exception {
        checkSvgConversion("ic_simple_group_info");
    }

    public void testSvgContainsError() throws Exception {
        checkSvgConversionAndContainsError("ic_contains_ignorable_error",
                "ERROR@ line 16 <switch> is not supported\n"
                        + "ERROR@ line 17 <foreignObject> is not supported");
    }

    public void testSvgLineToMoveTo() throws Exception {
        checkSvgConversion("test_lineto_moveto");
    }

    public void testSvgLineToMoveTo2() throws Exception {
        checkSvgConversion("test_lineto_moveto2");
    }

    public void testSvgLineToMoveToViewbox1() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox1");
    }

    public void testSvgLineToMoveToViewbox2() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox2");
    }

    public void testSvgLineToMoveToViewbox3() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox3");
    }

    // It seems like different implementations has different results on this svg.
    public void testSvgLineToMoveToViewbox4() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox4");
    }

    public void testSvgLineToMoveToViewbox5() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox5");
    }

    public void testSvgColorFormats() throws Exception {
        checkSvgConversion("test_color_formats");
    }

    public void testSvgTransformArcComplex1() throws Exception {
        checkSvgConversion("test_transform_arc_complex1");
    }

    public void testSvgTransformArcComplex2() throws Exception {
        checkSvgConversion("test_transform_arc_complex2");
    }

    public void testSvgTransformArcRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_arc_rotate_scale_translate");
    }

    public void testSvgTransformArcScale() throws Exception {
        checkSvgConversion("test_transform_arc_scale");
    }

    public void testSvgTransformArcScaleRotate() throws Exception {
        checkSvgConversion("test_transform_arc_scale_rotate");
    }

    public void testSvgTransformArcSkewx() throws Exception {
        checkSvgConversion("test_transform_arc_skewx");
    }

    public void testSvgTransformArcSkewy() throws Exception {
        checkSvgConversion("test_transform_arc_skewy");
    }

    public void testSvgTransformBigArcComplex() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex");
    }

    public void testSvgTransformBigArcComplexViewbox() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex_viewbox");
    }

    public void testSvgTransformBigArcScale() throws Exception {
        checkSvgConversion("test_transform_big_arc_translate_scale");
    }

    public void testSvgTransformCircleRotate() throws Exception {
        checkSvgConversion("test_transform_circle_rotate");
    }

    public void testSvgTransformCircleScale() throws Exception {
        checkSvgConversion("test_transform_circle_scale");
    }

    public void testSvgTransformRectMatrix() throws Exception {
        checkSvgConversion("test_transform_rect_matrix");
    }

    public void testSvgTransformRoundRectMatrix() throws Exception {
        checkSvgConversion("test_transform_round_rect_matrix");
    }

    public void testSvgTransformRectRotate() throws Exception {
        checkSvgConversion("test_transform_rect_rotate");
    }

    public void testSvgTransformRectScale() throws Exception {
        checkSvgConversion("test_transform_rect_scale");
    }

    public void testSvgTransformRectSkewx() throws Exception {
        checkSvgConversion("test_transform_rect_skewx");
    }

    public void testSvgTransformRectSkewy() throws Exception {
        checkSvgConversion("test_transform_rect_skewy");
    }

    public void testSvgTransformRectTranslate() throws Exception {
        checkSvgConversion("test_transform_rect_translate");
    }

    public void testSvgTransformHVLoopBasic() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_basic");
    }

    public void testSvgTransformHVLoopTranslate() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_translate");
    }

    public void testSvgTransformHVLoopMatrix() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_matrix");
    }

    public void testSvgTransformHVACComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_c_complex");
    }

    public void testSvgTransformHVAComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_complex");
    }

    public void testSvgTransformHVCQ() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q");
    }

    public void testSvgTransformHVCQComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q_complex");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformHVLoopComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_complex");
    }

    public void testSvgTransformHVSTComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex");
    }

    public void testSvgTransformHVSTComplex2() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex2");
    }

    public void testSvgTransformCQNoMove() throws Exception {
        checkSvgConversion("test_transform_c_q_no_move");
    }
    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple1() throws Exception {
        checkSvgConversion("test_transform_multiple_1");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple2() throws Exception {
        checkSvgConversion("test_transform_multiple_2");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple3() throws Exception {
        checkSvgConversion("test_transform_multiple_3");
    }

    public void testSvgTransformMultiple4() throws Exception {
        checkSvgConversion("test_transform_multiple_4");
    }

    public void testSvgTransformGroup1() throws Exception {
        checkSvgConversion("test_transform_group_1");
    }

    public void testSvgTransformGroup2() throws Exception {
        checkSvgConversion("test_transform_group_2");
    }

    public void testSvgTransformGroup3() throws Exception {
        checkSvgConversion("test_transform_group_3");
    }

    public void testSvgTransformGroup4() throws Exception {
        checkSvgConversion("test_transform_group_4");
    }

    public void testSvgTransformEllipseRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_ellipse_rotate_scale_translate");
    }

    public void testSvgTransformEllipseComplex() throws Exception {
        checkSvgConversion("test_transform_ellipse_complex");
    }

    public void testSvgMoveAfterCloseTransform() throws Exception {
        checkSvgConversion("test_move_after_close");
    }

    public void testSvgMoveAfterClose() throws Exception {
        checkSvgConversion("test_move_after_close_transform");
    }

    // XML files start here.
    public void testXmlIconSizeOpacity() throws Exception {
        checkXmlConversion("ic_size_opacity");
    }

    public void testXmlColorFormats() throws Exception {
        checkXmlConversion("test_xml_color_formats");
    }

    public void testXmlColorAlpha() throws Exception {
        checkXmlConversion("test_fill_stroke_alpha");
    }

    public void testXmlTransformation1() throws Exception {
        checkXmlConversion("test_xml_transformation_1");
    }

    public void testXmlTransformation2() throws Exception {
        checkXmlConversion("test_xml_transformation_2");
    }

    public void testXmlTransformation3() throws Exception {
        checkXmlConversion("test_xml_transformation_3");
    }

    public void testXmlTransformation4() throws Exception {
        checkXmlConversion("test_xml_transformation_4");
    }

    public void testXmlTransformation5() throws Exception {
        checkXmlConversion("test_xml_transformation_5");
    }

    public void testXmlTransformation6() throws Exception {
        checkXmlConversion("test_xml_transformation_6");
    }

    public void testXmlScaleStroke1() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_1");
    }

    public void testXmlScaleStroke2() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_2");
    }

    public void testXmlRenderOrder1() throws Exception {
        checkXmlConversion("test_xml_render_order_1");
    }

    public void testXmlRenderOrder2() throws Exception {
        checkXmlConversion("test_xml_render_order_2");
    }

    public void testXmlRepeatedA1() throws Exception {
        checkXmlConversion("test_xml_repeated_a_1");
    }

    public void testXmlRepeatedA2() throws Exception {
        checkXmlConversion("test_xml_repeated_a_2");
    }

    public void testXmlRepeatedCQ() throws Exception {
        checkXmlConversion("test_xml_repeated_cq");
    }

    public void testXmlRepeatedST() throws Exception {
        checkXmlConversion("test_xml_repeated_st");
    }

    public void testXmlStroke1() throws Exception {
        checkXmlConversion("test_xml_stroke_1");
    }

    public void testXmlStroke2() throws Exception {
        checkXmlConversion("test_xml_stroke_2");
    }

    public void testXmlStroke3() throws Exception {
        checkXmlConversion("test_xml_stroke_3");
    }
}
