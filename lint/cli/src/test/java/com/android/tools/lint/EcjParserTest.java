/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint;

import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.SdCardDetector;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.LintUtilsTest;

import lombok.ast.Node;
import lombok.ast.printer.SourcePrinter;
import lombok.ast.printer.TextFormatter;

public class EcjParserTest extends AbstractCheckTest {
    public void testTryCatchHang() throws Exception {
        // Ensure that we're really using this parser
        IJavaParser javaParser = createClient().getJavaParser();
        assertNotNull(javaParser);
        assertTrue(javaParser.getClass().getName(), javaParser instanceof EcjParser);

        // See https://code.google.com/p/projectlombok/issues/detail?id=573#c6
        // With lombok.ast 0.2.1 and the parboiled-based Java parser this test will hang forever.
        assertEquals(
                "No warnings.",

                lintProject("src/test/pkg/TryCatchHang.java.txt=>src/test/pkg/TryCatchHang.java"));
    }

    public void testKitKatLanguageFeatures() throws Exception {
        String testClass = "" +
                "package test.pkg;\n" +
                "\n" +
                "import java.io.BufferedReader;\n" +
                "import java.io.FileReader;\n" +
                "import java.io.IOException;\n" +
                "import java.lang.reflect.InvocationTargetException;\n" +
                "import java.util.List;\n" +
                "import java.util.Map;\n" +
                "import java.util.TreeMap;\n" +
                "\n" +
                "public class Java7LanguageFeatureTest {\n" +
                "    public void testDiamondOperator() {\n" +
                "        Map<String, List<Integer>> map = new TreeMap<>();\n" +
                "    }\n" +
                "\n" +
                "    public int testStringSwitches(String value) {\n" +
                "        final String first = \"first\";\n" +
                "        final String second = \"second\";\n" +
                "\n" +
                "        switch (value) {\n" +
                "            case first:\n" +
                "                return 41;\n" +
                "            case second:\n" +
                "                return 42;\n" +
                "            default:\n" +
                "                return 0;\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    public String testTryWithResources(String path) throws IOException {\n" +
                "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n" +
                "            return br.readLine();\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    public void testNumericLiterals() {\n" +
                "        int thousand = 1_000;\n" +
                "        int million = 1_000_000;\n" +
                "        int binary = 0B01010101;\n" +
                "    }\n" +
                "\n" +
                "    public void testMultiCatch() {\n" +
                "\n" +
                "        try {\n" +
                "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n" +
                "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n" +
                "            e.printStackTrace();\n" +
                "        } catch (ClassNotFoundException e) {\n" +
                "            // TODO: Logging here\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        Node unit = LintUtilsTest.getCompilationUnit(testClass);
        assertNotNull(unit);

        // Now print the AST back and make sure that it contains at least the essence of the AST
        TextFormatter formatter = new TextFormatter();
        unit.accept(new SourcePrinter(formatter));
        String actual = formatter.finish();
        assertEquals(""
                + "package test.pkg;\n"
                + "\n"
                + "import java.io.BufferedReader;\n"
                + "import java.io.FileReader;\n"
                + "import java.io.IOException;\n"
                + "import java.lang.reflect.InvocationTargetException;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.TreeMap;\n"
                + "\n"
                + "public class Java7LanguageFeatureTest {\n"
                + "    public void testDiamondOperator() {\n"
                + "        Map<String, List<Integer>> map = new TreeMap();\n" // missing types on rhs
                + "    }\n"
                + "    \n"
                + "    public int testStringSwitches(String value) {\n"
                + "        final String first = \"first\";\n"
                + "        final String second = \"second\";\n"
                + "        switch (value) {\n"
                + "        case first:\n"
                + "            return 41;\n"
                + "        case second:\n"
                + "            return 42;\n"
                + "        default:\n"
                + "            return 0;\n"
                + "        }\n"
                + "    }\n"
                + "    \n"
                + "    public String testTryWithResources(String path) throws IOException {\n"
                + "        try {\n" // Note how the initialization clause is gone here
                + "            return br.readLine();\n"
                + "        }\n"
                + "    }\n"
                + "    \n"
                + "    public void testNumericLiterals() {\n"
                + "        int thousand = 1_000;\n"
                + "        int million = 1_000_000;\n"
                + "        int binary = 0B01010101;\n"
                + "    }\n"
                + "    \n"
                + "    public void testMultiCatch() {\n"
                + "        try {\n"
                + "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n"
                + "        } catch (IllegalAccessException e) {\n" // Note: missing other union types
                + "            e.printStackTrace();\n"
                + "        } catch (ClassNotFoundException e) {\n"
                + "        }\n"
                + "    }\n"
                + "}",
                actual);
    }

    @Override
    protected Detector getDetector() {
        return new SdCardDetector();
    }
}
