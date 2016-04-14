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

package com.android.tools.lint.detector.api;

import com.android.tools.lint.client.api.JavaParser.TypeDescriptor;

import junit.framework.TestCase;

import org.intellij.lang.annotations.Language;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import lombok.ast.CompilationUnit;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.VariableDefinitionEntry;

@SuppressWarnings("ClassNameDiffersFromFileName")
public class TypeEvaluatorTest extends TestCase {
    private static void check(Object expected, @Language("JAVA") String source,
            final String targetVariable) {
        JavaContext context = LintUtilsTest.parse(source, new File("src/test/pkg/Test.java"));
        assertNotNull(context);
        CompilationUnit unit = (CompilationUnit) context.getCompilationUnit();
        assertNotNull(unit);

        // Find the expression
        final AtomicReference<Expression> reference = new AtomicReference<Expression>();
        unit.accept(new ForwardingAstVisitor() {
            @Override
            public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
                if (node.astName().astValue().equals(targetVariable)) {
                    reference.set(node.astInitializer());
                }
                return super.visitVariableDefinitionEntry(node);
            }
        });
        Expression expression = reference.get();
        TypeDescriptor actual = TypeEvaluator.evaluate(context, expression);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull("Couldn't compute type for " + source + ", expected " + expected,
                    actual);
            String expectedString = expected.toString();
            if (expectedString.startsWith("class ")) {
                expectedString = expectedString.substring("class ".length());
            }
            assertEquals(expectedString, actual.getName());
        }
    }

    private static void checkStatements(Object expected, String statementsSource,
            final String targetVariable) {
        @Language("JAVA")
        String source = ""
                + "package test.pkg;\n"
                + "public class Test {\n"
                + "    public void test() {\n"
                + "        " + statementsSource + "\n"
                + "    }\n"
                + "    public static final int MY_INT_FIELD = 5;\n"
                + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                + "    public static final String MY_STRING_FIELD = \"test\";\n"
                + "    public static final String MY_OBJECT_FIELD = \"test\";\n"
                + "}\n";

        check(expected, source, targetVariable);
    }

    private static void checkExpression(Object expected, String expressionSource) {
        @Language("JAVA")
        String source = ""
                + "package test.pkg;\n"
                + "public class Test {\n"
                + "    public void test() {\n"
                + "        Object expression = " + expressionSource + ";\n"
                + "    }\n"
                + "    public static final int MY_INT_FIELD = 5;\n"
                + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                + "    public static final String MY_STRING_FIELD = \"test\";\n"
                + "    public static final String MY_OBJECT_FIELD = \"test\";\n"
                + "}\n";

        check(expected, source, "expression");
    }

    public void testNull() throws Exception {
        checkExpression(null, "null");
    }

    public void testStrings() throws Exception {
        checkExpression(String.class, "\"hello\"");
        checkExpression(String.class, "\"ab\" + \"cd\"");
    }

    public void testBooleans() throws Exception {
        checkExpression(Boolean.TYPE, "true");
        checkExpression(Boolean.TYPE, "false");
        checkExpression(Boolean.TYPE, "false && true");
        checkExpression(Boolean.TYPE, "false || true");
        checkExpression(Boolean.TYPE, "!false");
    }

    public void testCasts() throws Exception {
        checkExpression(Integer.TYPE, "(int)1");
        checkExpression(Long.TYPE, "(long)1");
        checkExpression(Integer.TYPE, "(int)1.1f");
        checkExpression(Short.TYPE, "(short)65537");
        checkExpression(Byte.TYPE, "(byte)1023");
        checkExpression(Double.TYPE, "(double)1.5f");
        checkExpression(Double.TYPE, "(double)-5");
    }

    public void testArithmetic() throws Exception {
        checkExpression(Integer.TYPE, "1");
        checkExpression(Long.TYPE, "1L");
        checkExpression(Integer.TYPE, "1 + 3");
        checkExpression(Integer.TYPE, "1 - 3");
        checkExpression(Integer.TYPE, "2 * 5");
        checkExpression(Integer.TYPE, "10 / 5");
        checkExpression(Integer.TYPE, "11 % 5");
        checkExpression(Integer.TYPE, "1 << 3");
        checkExpression(Integer.TYPE, "32 >> 1");
        checkExpression(Integer.TYPE, "32 >>> 1");
        checkExpression(Integer.TYPE, "5 | 1");
        checkExpression(Integer.TYPE, "5 & 1");
        checkExpression(Integer.TYPE, "~5");
        checkExpression(Long.TYPE, "~(long)5");
        checkExpression(Short.TYPE, "~(short)5");
        checkExpression(Byte.TYPE, "~(byte)5");
        checkExpression(Long.TYPE, "-(long)5");
        checkExpression(Short.TYPE, "-(short)5");
        checkExpression(Byte.TYPE, "-(byte)5");
        checkExpression(Double.TYPE, "-(double)5");
        checkExpression(Float.TYPE, "-(float)5");
        checkExpression(Integer.TYPE, "1 + -3");

        checkExpression(Boolean.TYPE, "11 == 5");
        checkExpression(Boolean.TYPE, "11 == 11");
        checkExpression(Boolean.TYPE, "11 != 5");
        checkExpression(Boolean.TYPE, "11 != 11");
        checkExpression(Boolean.TYPE, "11 > 5");
        checkExpression(Boolean.TYPE, "5 > 11");
        checkExpression(Boolean.TYPE, "11 < 5");
        checkExpression(Boolean.TYPE, "5 < 11");
        checkExpression(Boolean.TYPE, "11 >= 5");
        checkExpression(Boolean.TYPE, "5 >= 11");
        checkExpression(Boolean.TYPE, "11 <= 5");
        checkExpression(Boolean.TYPE, "5 <= 11");

        checkExpression(Float.TYPE, "1.0f + 2.5f");
    }

    public void testFieldReferences() throws Exception {
        checkExpression(Integer.TYPE, "MY_INT_FIELD");
        checkExpression(String.class, "MY_STRING_FIELD");
        checkExpression(String.class, "\"prefix-\" + MY_STRING_FIELD + \"-postfix\"");
        checkExpression(Integer.TYPE, "3 - (MY_INT_FIELD + 2)");
    }

    public void testStatements() throws Exception {
        checkStatements(Integer.TYPE, ""
                        + "int x = +5;\n"
                        + "int y = x;\n"
                        + "int w;\n"
                        + "w = -1;\n"
                        + "int z = x + 5 + w;\n",
                "z");
        checkStatements(String.class, ""
                        + "String initial = \"hello\";\n"
                        + "String other;\n"
                        + "other = \" world\";\n"
                        + "String finalString = initial + other;\n",
                "finalString");
    }

    public void testConditionals() throws Exception {
        checkStatements(Integer.TYPE, ""
                        + "boolean condition = false;\n"
                        + "condition = !condition;\n"
                        + "int z = condition ? -5 : 4;\n",
                "z");
        checkStatements(Integer.TYPE, ""
                        + "boolean condition = true && false;\n"
                        + "int z = condition ? 5 : -4;\n",
                "z");
    }

    public void testConstructorInvocation() throws Exception {
        checkStatements(String.class, ""
                        + "Object o = new String(\"test\");\n"
                        + "Object bar = o;\n",
                "bar");
    }

    public void testFieldInitializerType() throws Exception {
        checkStatements(String.class, ""
                        + "Object o = MY_OBJECT_FIELD;\n"
                        + "Object bar = o;\n",
                "bar");
    }
}