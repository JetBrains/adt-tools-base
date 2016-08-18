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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtilsTest;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.util.PsiTreeUtil;

import junit.framework.TestCase;

import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.lookup.ProblemBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EcjPsiBuilderTest extends TestCase {
    // This test class attempts to exercise all the various AST constructs relevant
    // to ECJ and PSI. It gets 95% code coverage in EcjPsiBuilder (the remainder
    // is some extra cautionary code to throw exceptions if we receive unexpected
    // AST nodes (for example in a future ECJ update) etc, as well as
    @Language("JAVA")
    @SuppressWarnings("all") // testcase code intentionally not following recommended practices :-)
    private String mTestClass = ""
            + "\n"
            + "package test.pkg;\n"
            + "\n"
            + "import java.io.BufferedReader;\n"
            + "import java.io.FileReader;\n"
            + "import java.io.IOException;\n"
            + "import java.lang.annotation.*;\n"
            + "import java.lang.reflect.InvocationTargetException;\n"
            + "import java.util.ArrayList;\n"
            + "import java.util.Collection;\n"
            + "import java.util.Collections;\n"
            + "import java.util.HashSet;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n"
            + "import java.util.Set;\n"
            + "import java.util.TreeMap;\n"
            + "import java.util.function.Consumer;\n"
            + "import java.util.function.Supplier;\n"
            + "import java.util.regex.MatchResult;\n"
            + "import android.database.Cursor;\n"
            + "import android.database.CursorWrapper;\n"
            + "import android.view.Gravity;\n"
            + "import android.view.ViewDebug;\n"
            + "\n"
            + "import static java.lang.annotation.ElementType.FIELD;\n"
            + "import static java.lang.annotation.ElementType.LOCAL_VARIABLE;\n"
            + "import static java.lang.annotation.ElementType.METHOD;\n"
            + "import static java.lang.annotation.ElementType.PARAMETER;\n"
            + "import static java.lang.annotation.ElementType.TYPE_PARAMETER;\n"
            + "import static java.lang.annotation.ElementType.TYPE_USE;\n"
            + "import static java.lang.annotation.RetentionPolicy.*;\n"
            + "import static java.util.regex.Matcher.quoteReplacement;\n"
            + "import static java.util.regex.Pattern.DOTALL;\n"
            + "\n"
            + "/**\n"
            + " * Comprehensive language test to verify ECJ PSI bridge\n"
            + " */\n"
            + "@SuppressWarnings(\"all\")\n"
            + "public abstract class LanguageTest<K> extends ArrayList<K> implements Comparable<K>, Cloneable {\n"
            + "    public LanguageTest(@SuppressWarnings(\"unused\") int x) {\n"
            + "        super(x);\n"
            + "    }\n"
            + "\n"
            + "    public LanguageTest() {\n"
            + "        this(5);\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public boolean add(K k) {\n"
            + "        this.add(k);\n"
            + "        return super.add(k);\n"
            + "    }\n"
            + "\n"
            + "    private static double ourStatic;\n"
            + "    private static final int ourStatic2 = (int)(long)new Long(System.currentTimeMillis());\n"
            + "    private int myField;\n"
            + "    private final int myField2 = 52;\n"
            + "    @MyAnnotation1\n"
            + "    private int myField3;\n"
            + "\n"
            + "    // Static initializer\n"
            + "    static {\n"
            + "        ourStatic = 5;\n"
            + "    }\n"
            + "\n"
            + "    // Instance initializer\n"
            + "    {\n"
            + "        myField = 5;\n"
            + "    }\n"
            + "\n"
            + "    void imports(MatchResult result) {\n"
            + "        // Referenced to not lose these on import cleanup as unused\n"
            + "        Object dotAll = DOTALL;\n"
            + "        Object quoted = quoteReplacement(\"5\");\n"
            + "    }\n"
            + "\n"
            + "    public void constructors(final String param) {\n"
            + "        Object o1 = new String(\"some call\");\n"
            + "        Object o2 = new ArrayList<String>();\n"
            + "        List<String> o3 = new java.util.ArrayList<>();\n"
            + "        new Runnable() {\n"
            + "            @Override\n"
            + "            public void run() {\n"
            + "            }\n"
            + "        };\n"
            + "        Object o4 = new ArrayList<String>() {\n"
            + "            @Override\n"
            + "            public String get(int index) {\n"
            + "                Object o = LanguageTest.this;\n"
            + "                return param;\n"
            + "            }\n"
            + "        };\n"
            + "\n"
            + "        InstanceInnerClass o5 = new InstanceInnerClass(5);\n"
            + "        this.new InstanceInnerClass(6);\n"
            + "        LanguageTest o6 = null;\n"
            + "        o6.new InstanceInnerClass(7);\n"
            + "        // Constructing an inner class with an anonymous enclosing instance!\n"
            + "        Object o7 = new LanguageTest<String>() {\n"
            + "            @Override\n"
            + "            public void methodWithoutBody(int param1) {\n"
            + "\n"
            + "            }\n"
            + "\n"
            + "            @Override\n"
            + "            public int compareTo(String o) {\n"
            + "                return 0;\n"
            + "            }\n"
            + "        }.new InstanceInnerClass(5);\n"
            + "    }\n"
            + "\n"
            + "    public void literals() {\n"
            + "        char x = 'x';\n"
            + "        char y = '\\u1234';\n"
            + "        Object n = null;\n"
            + "        Boolean b1 = true;\n"
            + "        Boolean b2 = false;\n"
            + "        int digits = 100_000_000;\n"
            + "        int hex = 0xAB;\n"
            + "        int value1 = 42;\n"
            + "        long value2 = 42L;\n"
            + "        float value3 = 42f;\n"
            + "        float value4 = 42.0F;\n"
            + "        String s = \"myString\";\n"
            + "        int[] array1 = new int[5];\n"
            + "        int[] array2 = new int[] { 1, 2, 3, 4 };\n"
            + "    }\n"
            + "\n"
            + "    public int operators(int x, int y, boolean a, boolean b, int[] array, float... varargs) {\n"
            + "        int result = 0;\n"
            + "        result += x + y;\n"
            + "        result += x - y;\n"
            + "        result += x * y;\n"
            + "        result += x % y;\n"
            + "        result += x ^ y;\n"
            + "        result += x | y;\n"
            + "        result += x & y;\n"
            + "        result += x << x;\n"
            + "        result %= x >> x;\n"
            + "        result /= x >>> x;\n"
            + "        result -= x++;\n"
            + "        result -= ~x;\n"
            + "        result *= --x;\n"
            + "\n"
            + "        boolean bool = false;\n"
            + "        bool ^= a && b;\n"
            + "        bool ^= a || b;\n"
            + "        bool ^= a ^ b;\n"
            + "        bool ^= a & b;\n"
            + "        bool ^= a | b;\n"
            + "        bool ^= x > y;\n"
            + "        bool ^= x >= y;\n"
            + "        bool ^= x < y;\n"
            + "        bool ^= x <= y;\n"
            + "        bool ^= x == y;\n"
            + "        bool |= x != y;\n"
            + "        bool = !bool;\n"
            + "        bool &= this instanceof List;\n"
            + "\n"
            + "        result += bool ? x : y;\n"
            + "        result = (result);\n"
            + "\n"
            + "        result = (int)(double)result;\n"
            + "\n"
            + "        result += array.length;\n"
            + "\n"
            + "        return 0;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public String toString() {\n"
            + "        return LanguageTest.class.getSimpleName();\n"
            + "    }\n"
            + "\n"
            + "    public int statementTypes(char[][] array, int value, String path, List<String> list) {\n"
            + "        // Empty\n"
            + "        ;;;\n"
            + "\n"
            + "        {\n"
            + "            // Nested code block\n"
            + "            int y; // local\n"
            + "        }\n"
            + "\n"
            + "        // Variable declarations\n"
            + "        int x;\n"
            + "        int y = 1, z = 2 + 3;\n"
            + "        int z1, z2[] = new int[] { 1, 2, 3};\n"
            + "        int [] z3;\n"
            + "\n"
            + "        for (char[] a : array) {\n"
            + "            if (a == null) {\n"
            + "                return -1;\n"
            + "            }\n"
            + "        }\n"
            + "\n"
            + "        for (String s : list) {\n"
            + "            System.out.println(s);\n"
            + "        }\n"
            + "\n"
            + "        // Empty for\n"
            + "        if (false) {\n"
            + "            for (; ; ) {\n"
            + "                ;\n"
            + "            }\n"
            + "        }\n"
            + "\n"
            + "        for (int i = 0; i < list.size(); i++) {\n"
            + "            String s = list.get(i);\n"
            + "            System.out.println(s);\n"
            + "        }\n"
            + "\n"
            + "        for (int i = 0, j = 0, n = list.size(); i < n; i++, j++) {\n"
            + "            String s = list.get(i);\n"
            + "            System.out.println(s);\n"
            + "        }\n"
            + "\n"
            + "        int m, n;\n"
            + "        for (m = 0, n = 0; m < list.size(); m++) {\n"
            + "            String s = list.get(m);\n"
            + "            System.out.println(s);\n"
            + "        }\n"
            + "\n"
            + "        Cursor cursor = new CursorWrapper(null);\n"
            + "        for (cursor.moveToFirst(); (! cursor.isAfterLast()); cursor.moveToNext()) {\n"
            + "            cursor.getColumnCount();\n"
            + "        }\n"
            + "\n"
            + "        label:\n"
            + "        for (int i = 0; i < array.length; i++) {\n"
            + "            char[] inner = array[i];\n"
            + "            for (int j = 0; j < inner.length; j++) {\n"
            + "                if (inner[i] == 5) {\n"
            + "                    return i + j;\n"
            + "                } else if (inner[i] == 3) {\n"
            + "                    continue label;\n"
            + "                } else if (inner[i] == 2) {\n"
            + "                    continue;\n"
            + "                } else if (inner[i] == 1) {\n"
            + "                    break label;\n"
            + "                } else if (inner[i] == 0) {\n"
            + "                    break;\n"
            + "                }\n"
            + "                System.out.println(\"loop iteration\");\n"
            + "            }\n"
            + "        }\n"
            + "\n"
            + "        int val = value;\n"
            + "        while (val > 0) {\n"
            + "            if (array[2][val] == 4) {\n"
            + "                return val;\n"
            + "            }\n"
            + "            val--;\n"
            + "        }\n"
            + "\n"
            + "        do {\n"
            + "            --val;\n"
            + "            value++;\n"
            + "        } while (val < 10);\n"
            + "\n"
            + "        // Switches with and without default\n"
            + "        switch (value) {\n"
            + "            case 1:\n"
            + "            case 2:\n"
            + "                break;\n"
            + "            case 3: {\n"
            + "                int x6 = 5;\n"
            + "                return x6;\n"
            + "            }\n"
            + "        }\n"
            + "\n"
            + "        switch (value) {\n"
            + "            case 1:\n"
            + "            case 2:\n"
            + "            default:\n"
            + "                break;\n"
            + "        }\n"
            + "\n"
            + "        // Try statements: with each part optional\n"
            + "        try {\n"
            + "            int error = 1 / 0;\n"
            + "        } catch (Throwable t) {\n"
            + "            return (int)Float.NaN;\n"
            + "        } finally {\n"
            + "            val++;\n"
            + "        }\n"
            + "\n"
            + "        try {\n"
            + "            int error = 1 / 0;\n"
            + "        } finally {\n"
            + "            val++;\n"
            + "        }\n"
            + "\n"
            + "        try {\n"
            + "            int error = 1 / 0;\n"
            + "        } finally {\n"
            + "            val++;\n"
            + "        }\n"
            + "\n"
            + "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n"
            + "            return br.readLine().length();\n"
            + "        } catch (IOException ignore) {\n"
            + "        }\n"
            + "\n"
            + "        x = 5;\n"
            + "        if (y < x) {\n"
            + "        }\n"
            + "\n"
            + "        if (x > y) {\n"
            + "            y++;\n"
            + "        }\n"
            + "\n"
            + "        if (y < x) {\n"
            + "        } else if (x > y) {\n"
            + "        } else {\n"
            + "        }\n"
            + "\n"
            + "        if (y < x) {\n"
            + "            x++;\n"
            + "        } else if (x > y) {\n"
            + "            y++;\n"
            + "        } else {\n"
            + "            x--;\n"
            + "        }\n"
            + "\n"
            + "        class MyTypeDeclarationStatement {\n"
            + "            public void test() {\n"
            + "                System.out.println(\"test\");\n"
            + "            }\n"
            + "        };\n"
            + "        new MyTypeDeclarationStatement().test();\n"
            + "\n"
            + "        assert y != x;\n"
            + "        assert y != x + 1 : \"Description\";\n"
            + "\n"
            + "        if (false) {\n"
            + "            labeledStatement:\n"
            + "            throw new IllegalArgumentException(\"test\");\n"
            + "        }\n"
            + "\n"
            + "        synchronized (this) {\n"
            + "            remove(z);\n"
            + "        }\n"
            + "\n"
            + "        return -1;\n"
            + "    }\n"
            + "\n"
            + "    public void references() {\n"
            + "        java.util.List typeReferenceVariable;\n"
            + "        java.util.List<String> typeReferenceVariable2;\n"
            + "        Object fieldReference = LanguageTest.ourStatic;\n"
            + "        double x = this.ourStatic;\n"
            + "        double y = ourStatic;\n"
            + "    }\n"
            + "\n"
            + "    private void calls() {\n"
            + "        listIterator();\n"
            + "        Object iterator;\n"
            + "        iterator = listIterator();\n"
            + "        super.toString();\n"
            + "\n"
            + "\n"
            + "        // varargs call\n"
            + "        operators(1, 2, true, false, new int[5], 1, 2, 3f, 4F, 5.0F);\n"
            + "    }\n"
            + "\n"
            + "    // Enums\n"
            + "\n"
            + "    enum MyEnum1 {\n"
            + "        V1, V2\n"
            + "    }\n"
            + "\n"
            + "    enum MyEnum2 {\n"
            + "        V1, V2;\n"
            + "\n"
            + "        public boolean isV1() {\n"
            + "            return this == V1;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    enum MyEnum3 {\n"
            + "        V1(1), V2(2);\n"
            + "\n"
            + "        MyEnum3(int param) {\n"
            + "            this.param = param;\n"
            + "        }\n"
            + "\n"
            + "        int param;\n"
            + "    }\n"
            + "\n"
            + "    enum MyEnum4 {\n"
            + "        V1 {\n"
            + "            @Override\n"
            + "            public String toString() {\n"
            + "                return super.toString();\n"
            + "            }\n"
            + "        }, V2;\n"
            + "    }\n"
            + "\n"
            + "    // Method declarations\n"
            + "    public void instanceMethod() {\n"
            + "    }\n"
            + "\n"
            + "    public static void staticMethod() {\n"
            + "    }\n"
            + "\n"
            + "    @MyAnnotation2(\"test\")\n"
            + "    public void annotated() {\n"
            + "    }\n"
            + "\n"
            + "    @MyAnnotation2(value = \"test\")\n"
            + "    public abstract void methodWithoutBody(int param1);\n"
            + "\n"
            + "    @MyAnnotation2(allOf = \"test\")\n"
            + "    public synchronized void methodWithParameters(int param1, int param2, int...varargs) {\n"
            + "    }\n"
            + "\n"
            + "    @MyAnnotation2(allOf = {\"test\"}, conditional = true)\n"
            + "    @NestedAnnotation(@MyAnnotation2)\n"
            + "    public void withThrows(List<? extends Number> param1, List<? super Number> param2) throws IOException {\n"
            + "        throw new IOException(\"method\");\n"
            + "    }\n"
            + "\n"
            + "    // Type Variables\n"
            + "    private void invokeWithTypeVariables() {\n"
            + "        String[] array = null;\n"
            + "        String element = null;\n"
            + "        int count = methodWithTypeParameters(array, element);\n"
            + "        Cls cls = new Cls(element);\n"
            + "        List<String> roster = null;\n"
            + "        Set<String> rosterSet = methodWithTypeArgs(roster, HashSet::new);\n"
            + "    }\n"
            + "\n"
            + "    public static <T, X extends Collection<T>, Y extends Collection<T>> Y methodWithTypeArgs(\n"
            + "            X sourceCollection,\n"
            + "            Supplier<Y> collectionFactory) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    private class Cls {\n"
            + "        public <T extends Comparable<T>> Cls(T foo) {\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static <T extends Comparable<T>> int methodWithTypeParameters(T[] anArray, T elem) {\n"
            + "        return 0;\n"
            + "    }\n"
            + "\n"
            + "    // Interfaces\n"
            + "    public interface MyInterface1 {\n"
            + "        void myInterfaceMethod();\n"
            + "    }\n"
            + "\n"
            + "    public interface MyInterface2 extends MyInterface1 {\n"
            + "        @MyAnnotation1\n"
            + "        void myInterfaceMethod2();\n"
            + "    }\n"
            + "\n"
            + "    // Annotations\n"
            + "\n"
            + "    @Documented\n"
            + "    @Retention(SOURCE)\n"
            + "    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_PARAMETER, TYPE_USE})\n"
            + "    public @interface MyAnnotation1 {\n"
            + "    }\n"
            + "\n"
            + "    @Documented\n"
            + "    @Retention(SOURCE)\n"
            + "    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})\n"
            + "    public @interface MyAnnotation2 {\n"
            + "        String value() default \"\";\n"
            + "        String[] allOf() default {};\n"
            + "        boolean conditional() default false;\n"
            + "    }\n"
            + "\n"
            + "    @Target({FIELD,METHOD})\n"
            + "    @interface NestedAnnotation {\n"
            + "        MyAnnotation2 value();\n"
            + "    }\n"
            + "\n"
            + "    @ViewDebug.ExportedProperty(mapping = {\n"
            + "            @ViewDebug.IntToString(from =  -1,                       to = \"NONE\"),\n"
            + "            @ViewDebug.IntToString(from = Gravity.TOP,               to = \"TOP\"),\n"
            + "            @ViewDebug.IntToString(from = Gravity.BOTTOM,            to = \"BOTTOM\")\n"
            + "    })\n"
            + "    public int gravity = -1;\n"
            + "\n"
            + "    protected volatile int modifierFields;\n"
            + "\n"
            + "    int defaultPackage;\n"
            + "    private int privateField;\n"
            + "\n"
            + "    private static class StaticInnerClass {\n"
            + "        private static class StaticInnerInnerClass {\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private class InstanceInnerClass {\n"
            + "        private final int myVar;\n"
            + "\n"
            + "        public InstanceInnerClass(int var) {\n"
            + "            myVar = var;\n"
            + "        }\n"
            + "\n"
            + "        private void method() {\n"
            + "            int x = (int) LanguageTest.this.ourStatic;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // this/super invocations\n"
            + "    class A {\n"
            + "        class B {\n"
            + "            class C {\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    class AB extends A.B {\n"
            + "        AB(A abc) {\n"
            + "            abc.super();\n"
            + "        }\n"
            + "\n"
            + "        AB() {\n"
            + "            this(new A());\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Java 7\n"
            + "    public void testDiamondOperator() {\n"
            + "        Map<String, List<Integer>> map = new TreeMap<>();\n"
            + "    }\n"
            + "\n"
            + "    public int testStringSwitches(String value) {\n"
            + "        final String first = \"first\";\n"
            + "        final String second = \"second\";\n"
            + "\n"
            + "        switch (value) {\n"
            + "            case first:\n"
            + "                return 41;\n"
            + "            case second:\n"
            + "                return 42;\n"
            + "            default:\n"
            + "                return 0;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public String testTryWithResources(String path) throws IOException {\n"
            + "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n"
            + "            return br.readLine();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public void testNumericLiterals() {\n"
            + "        int thousand = 1_000;\n"
            + "        int million = 1_000_000;\n"
            + "        int binary = 0B01010101;\n"
            + "    }\n"
            + "\n"
            + "    public void testMultiCatch() {\n"
            + "\n"
            + "        try {\n"
            + "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n"
            + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
            + "            e.printStackTrace();\n"
            + "        } catch (ClassNotFoundException e) {\n"
            + "            // TODO: Logging here\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Java 8\n"
            + "\n"
            + "    public void lambda(List<Number> numbers) {\n"
            + "        Runnable r2 = () -> System.out.println(\"Hello world two!\");\n"
            + "        Collections.sort(numbers, (p1, p2) -> (int)(p2.longValue() - p1.longValue()));\n"
            + "    }\n"
            + "\n"
            + "    interface InterfaceWithDefaultMethod {\n"
            + "        public void someMethod();\n"
            + "        default public void method2() {\n"
            + "            System.out.println(\"test\");\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public void methodReferences(LanguageTest instance) {\n"
            + "        // Reference to a static method\n"
            + "        Runnable refToInstanceMethodOnInstance = instance::instanceMethod;\n"
            + "        Consumer<LanguageTest> reftoInstanceOnType = LanguageTest::instanceMethod;\n"
            + "        Runnable refToStaticMethod = LanguageTest::staticMethod;\n"
            + "        Runnable constructorRef = OtherType::new;\n"
            + "    }\n"
            + "\n"
            + "    // Type annotations\n"
            + "    List<@MyAnnotation1 String> myList;\n"
            + "}\n"
            + "\n"
            + "class OtherType {\n"
            + "    public OtherType() {\n"
            + "    }\n"
            + "}";
    
    private PsiJavaFile mJavaFile;
    {
        JavaContext context = LintUtilsTest.parsePsi(mTestClass);
        if (context != null) {
            mJavaFile = context.getJavaFile();
        }
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testOffsets() throws Exception {
        JavaContext context = LintUtilsTest.parsePsi(""
                + "package test.pkg;\n"
                + "\n"
                + "public class MyClass {\n"
                + "    @interface GAndDInstruction {\n"
                + "        int ACTIVATE_MODE     = 0xA0;\n"
                + "        int REQUEST_SLOTS     = 0xA1;\n"
                + "    }\n"
                + "\n"
                + "    @interface GAndDEnvelopeInstruction {\n"
                + "        int ACTIVATE_MODE = 0;\n"
                + "        int REQUEST_SLOTS = 1;\n"
                + "    }\n"
                + "}\n");
        assertNotNull(context);
    }

    public void testLanguageConstructs() throws Exception {
        // Simple parse: make sure we can convert all the language constructs
        assertNotNull(mJavaFile);
    }

    public void testImports() throws Exception {
        PsiImportList importList = mJavaFile.getImportList();
        assertNotNull(importList);
        PsiImportStatementBase[] imports = importList.getAllImportStatements();
        assertEquals(29, imports.length);

        PsiImportStatement statement = importList.findSingleClassImportStatement("java.util.List");
        assertNotNull(statement);
        assertEquals("java.util.List", statement.getQualifiedName());
        assertFalse(statement.isOnDemand());
        assertFalse(statement.isForeignFileImport());
        assertSame(mJavaFile, statement.getContainingFile());

        PsiElement resolved = statement.resolve();
        assertNotNull(resolved);
        assertTrue(resolved instanceof PsiClass);
        PsiClass cls = (PsiClass)resolved;
        assertEquals("java.util.List", cls.getQualifiedName());
        assertTrue(cls.isInterface());
    }
    
    public void testPackages() throws Exception {
        assertEquals("test.pkg", mJavaFile.getPackageName());
        PsiPackageStatement packageStatement = mJavaFile.getPackageStatement();
        assertNotNull(packageStatement);
        assertEquals("test.pkg", packageStatement.getPackageName());
        String[] implicitlyImportedPackages = mJavaFile.getImplicitlyImportedPackages();
        assertEquals(1, implicitlyImportedPackages.length);
        assertEquals("java.lang", implicitlyImportedPackages[0]);
    }

    public void testCls() throws Exception {
        PsiClass[] classes = mJavaFile.getClasses();
        assertNotNull(classes);
        assertEquals(classes.length, 2);
        PsiClass cls = classes[0];
        assertEquals("test.pkg.LanguageTest", cls.getQualifiedName());
        assertEquals("LanguageTest", cls.getName());
        assertNotNull(cls.getNameIdentifier());
        assertEquals("LanguageTest", cls.getNameIdentifier().getText());
        assertFalse(cls.isInterface());
        assertFalse(cls.isAnnotationType());
        assertFalse(cls.isEnum());
        assertNull(cls.getContainingClass());
        assertSame(mJavaFile, cls.getContainingFile());

        // Type Parameters
        PsiTypeParameterList typeParameterList = cls.getTypeParameterList();
        assertNotNull(typeParameterList);
        PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
        assertNotNull(typeParameters);
        assertEquals(1, typeParameters.length);
        PsiTypeParameter typeParameter = typeParameters[0];
        assertEquals(0, typeParameter.getIndex());
        assertSame(cls, typeParameter.getOwner());
        assertFalse(typeParameter.isAnnotationType());
        assertFalse(typeParameter.isInterface());
        assertFalse(typeParameter.isEnum());
        assertEquals("K", typeParameter.getName());
        //assertNotNull(typeParameter.getNameIdentifier());
        //assertEquals("K", typeParameter.getNameIdentifier().getText());

        // Inheritance
        assertNotNull(cls.getSuperClass());
        assertEquals("ArrayList", cls.getSuperClass().getName());
        assertEquals("java.util.ArrayList", cls.getSuperClass().getQualifiedName());
        assertNotNull(cls.getExtendsList());
        assertNotNull(cls.getExtendsList().getReferenceElements());
        assertEquals(1, cls.getExtendsList().getReferenceElements().length);
        assertEquals("java.util.ArrayList",
                cls.getExtendsList().getReferenceElements()[0].getQualifiedName());

        assertNotNull(cls.getInterfaces());
        assertEquals(2, cls.getInterfaces().length);
        PsiClass comparable = cls.getInterfaces()[0];
        PsiClass cloneable = cls.getInterfaces()[1];
        assertEquals("java.lang.Comparable", comparable.getQualifiedName());
        assertTrue(comparable.isInterface());
        assertEquals("java.lang.Cloneable", cloneable.getQualifiedName());
        assertTrue(cloneable.isInterface());

        // Inner classes
        PsiClass[] innerClasses = cls.getInnerClasses();
        assertNotNull(innerClasses);
        assertEquals(15, innerClasses.length);
        //assertEquals("", Arrays.asList(innerClasses).toString());
        for (PsiClass innerClass : innerClasses) {
            assertNotNull(innerClass.getContainingClass());
        }

        // Enum
        PsiClass enum1 = innerClasses[0];
        PsiClass enum2 = innerClasses[1];
        PsiClass enum3 = innerClasses[2];
        assertEquals("MyEnum1", enum1.getName());
        assertEquals("test.pkg.LanguageTest.MyEnum1", enum1.getQualifiedName());
        assertTrue(enum1.isEnum());
        assertTrue(enum2.isEnum());
        assertTrue(enum3.isEnum());
        assertEquals(3, enum3.getFields().length);
        assertTrue(enum3.getFields()[1] instanceof PsiEnumConstant); // V2
        assertFalse(enum3.getFields()[2] instanceof PsiEnumConstant); // myParam
        PsiEnumConstant enumConstant = (PsiEnumConstant)enum3.getFields()[1];
        PsiMethod enumConstructor = enumConstant.resolveConstructor();
        assertNotNull(enumConstructor);
        assertEquals(enum3, enumConstructor.getContainingClass());
        assertNull(enumConstant.getInitializingClass());
        assertEquals("V2", enumConstant.getName());

        // Members
        PsiField ourStatic = cls.findFieldByName("ourStatic", false);
        assertNotNull(ourStatic);
        assertEquals("ourStatic", ourStatic.getName());
        assertEquals(PsiType.DOUBLE, ourStatic.getType());
        assertNull(ourStatic.getInitializer());
        assertNotNull(ourStatic.getNameIdentifier());
        assertEquals("ourStatic", ourStatic.getNameIdentifier().getText());
        PsiField[] fields = cls.getFields();
        assertNotNull(fields);
        assertEquals(10, fields.length);

        PsiMethod[] constructors = cls.getConstructors();
        assertNotNull(constructors);
        assertEquals(2, constructors.length);
        assertTrue(constructors[0].isConstructor());

        PsiMethod[] methods = cls.getMethods();
        assertNotNull(methods);
        assertEquals(27, methods.length);
        assertTrue(cls.getAllMethods().length >= 35); // java lang methods etc. Can vary by target.

        methods = cls.findMethodsByName("operators", false);
        assertNotNull(methods);
        assertEquals(1, methods.length);
        PsiMethod method = methods[0];
        assertEquals("operators", method.getName());
        assertNotNull(method.getNameIdentifier());
        assertEquals("operators", method.getNameIdentifier().getText());
        assertEquals(PsiType.INT, method.getReturnType());
        assertNotNull(method.getReturnTypeElement());
        assertEquals(6, method.getParameterList().getParametersCount());
        PsiParameter[] parameters = method.getParameterList().getParameters();
        assertEquals(PsiType.INT, parameters[0].getType());
        assertEquals(PsiType.BOOLEAN, parameters[2].getType());
        assertTrue(parameters[4].getType() instanceof PsiArrayType);
        assertEquals(PsiType.INT, parameters[4].getType().getDeepComponentType());
        assertFalse(parameters[4].isVarArgs());
        assertTrue(parameters[5].isVarArgs());
        assertEquals("varargs", parameters[5].getName());
        assertNotNull(parameters[5].getNameIdentifier());
        assertEquals("varargs", parameters[5].getNameIdentifier().getText());
        assertNotNull(parameters[5].getTypeElement());
        assertNotNull(method.getBody());
        assertFalse(method.isConstructor());
        assertTrue(method.isVarArgs());
        assertEquals(0, method.findSuperMethods().length);

        // Binary methods
        methods = cls.findMethodsByName("toString", true); // look in super: find in Object
        assertNotNull(methods);
        assertEquals(1, methods.length);
        method = methods[0];
        assertFalse(method instanceof PsiCompiledElement);
        assertSame(cls, method.getContainingClass());
        PsiMethod[] superMethods = method.findSuperMethods();
        assertNotNull(superMethods);
        assertEquals(1, superMethods.length);
        method = superMethods[0];
        assertTrue(method instanceof PsiCompiledElement);
        assertNotNull(method.getContainingClass());
        assertTrue(method.getContainingClass() instanceof PsiCompiledElement);
        assertEquals("java.util.AbstractCollection", method.getContainingClass().getQualifiedName());
        assertEquals(0, method.getParameterList().getParametersCount());
        assertNotNull(method.getReturnType());
        assertTrue(method.getReturnType() instanceof PsiClassType);
        assertEquals("String", ((PsiClassType)method.getReturnType()).getClassName());
        assertEquals("java.lang.String", method.getReturnType().getCanonicalText());
    }

    public void testDisjunctionTypes() throws Exception {
        PsiElement element = findElement(mJavaFile,
                "IllegalAccessException | InvocationTargetException | NoSuchMethodException e");
        assertNotNull(element);
        assertTrue(element instanceof PsiParameter);
        PsiType type = ((PsiParameter) element).getType();
        assertNotNull(type);
        assertTrue(type instanceof PsiDisjunctionType);
        PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        PsiType leastUpperBound = disjunctionType.getLeastUpperBound();
        assertNotNull(leastUpperBound);
        assertEquals("java.lang.ReflectiveOperationException", leastUpperBound.getCanonicalText());
        List<PsiType> disjunctions = disjunctionType.getDisjunctions();
        assertNotNull(disjunctions);
        assertEquals(3, disjunctions.size());
        assertEquals("java.lang.IllegalAccessException",
                disjunctions.get(0).getCanonicalText());
        assertEquals("java.lang.reflect.InvocationTargetException",
                disjunctions.get(1).getCanonicalText());
        assertEquals("java.lang.NoSuchMethodException",
                disjunctions.get(2).getCanonicalText());
    }

    @NonNull
    public static PsiElement findElement(@NonNull PsiJavaFile root, @NonNull final String source) {
        final AtomicReference<PsiElement> result = new AtomicReference<PsiElement>();
        root.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                String text = element.getText();
                assertNotNull(text);
                if (text.equals(source)) {
                    result.set(element);
                }

                if (result.get() == null) {
                    super.visitElement(element);
                }
            }
        });
        PsiElement element = result.get();
        assertNotNull("Can't find " + source, element);
        return element;
    }

    public void testValidPointers() throws Exception {
        // Make sure offsets etc are correct
        mJavaFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                assertNotNull(element.getTextRange());
                if (element != mJavaFile) {
                    assertNotNull(element.toString(), element.getParent());
                    assertTrue(element.getTextRange().getStartOffset()
                            >= element.getParent().getTextRange().getStartOffset());
                    assertTrue(element.getTextRange().getEndOffset()
                            <= element.getParent().getTextRange().getEndOffset());
                }
                PsiElement curr = element.getFirstChild();
                PsiElement prev = null;
                while (curr != null) {
                    // children in classes aren't ordered properly
                    if (prev != null && (!(element instanceof PsiClass))) {
                        assertNotNull(curr.toString(), curr.getTextRange());
                        assertTrue(curr.getTextRange().getStartOffset()
                                >= prev.getTextRange().getEndOffset());
                    }
                    assertSame(element, curr.getParent());
                    assertSame(curr.getPrevSibling(), prev);
                    prev = curr;
                    curr = curr.getNextSibling();
                }
                super.visitElement(element);
            }
        });
    }

    /** Temporarily disabled until I can track down why this happens on the server */
    public void DISABLEDtestResolve() throws Exception {
        // Make sure that all resolving works
        mJavaFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(PsiMethod method) {
                assertNotNull(method.getNameIdentifier());
                if (!method.isConstructor()) {
                    assertNotNull(method.getReturnType());
                }
                assertEquals(method.getName(), method.getNameIdentifier().getText());
                super.visitMethod(method);
            }

            @Override
            public void visitEnumConstant(PsiEnumConstant enumConstant) {
                PsiMethod[] constructors = ((PsiClass) enumConstant.getParent()).getConstructors();
                if (constructors.length > 0) {
                    assertNotNull(enumConstant.resolveConstructor());
                    assertNotNull(enumConstant.resolveMethod());
                }
                super.visitEnumConstant(enumConstant);
            }

            @Override
            public void visitAnonymousClass(PsiAnonymousClass aClass) {
                assertNotNull(aClass.getContainingClass());
                if (!aClass.getContainingClass().isEnum()) {
                    PsiJavaCodeReferenceElement baseClassReference = aClass.getBaseClassReference();
                    assertNotNull(baseClassReference);
                    assertNotNull(baseClassReference.resolve());
                }
                super.visitAnonymousClass(aClass);
            }

            @Override
            public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                assertNotNull(expression.resolve());
                super.visitMethodReferenceExpression(expression);
            }

            @Override
            public void visitField(PsiField field) {
                PsiIdentifier nameIdentifier = field.getNameIdentifier();
                assertNotNull(nameIdentifier);
                super.visitField(field);
            }

            @Override
            public void visitNewExpression(PsiNewExpression expression) {
                if (expression.getArrayDimensions().length == 0
                        && expression.getArrayInitializer() == null) {
                    PsiMethod resolvedConstructor = expression.resolveConstructor();
                    if (resolvedConstructor == null) {
                        //noinspection ConstantConditions
                        PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
                        if (anonymousClass == null) {
                            String qualifiedName = expression.getClassOrAnonymousClassReference()
                                    .getQualifiedName();
                            String call = expression.getText();
                            if ("test.pkg.LanguageTest.Local.MyTypeDeclarationStatement".equals(
                                    qualifiedName)) {
                                // OK
                                return;
                            }
                            if ("new MyTypeDeclarationStatement()".equals(call)) {
                                // OK: calls inner type which doesn't specify a constructor
                                return;
                            }
                            if ("new A()".equals(call)) {
                                // OK: just a constructor call where there isn't a constructor
                                return;
                            }
                        }
                        PsiClass[] interfaces = anonymousClass.getInterfaces();
                        if (interfaces.length == 1 &&
                                "java.lang.Runnable".equals(interfaces[0].getQualifiedName())) {
                            // OK
                            return;
                        }
                    }
                    assertNotNull(resolvedConstructor);
                }
                super.visitNewExpression(expression);
            }

            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof PsiJavaCodeReferenceElement) {
                    PsiJavaCodeReferenceElement referenceElement
                            = (PsiJavaCodeReferenceElement) element;
                    checkCodeReferenceResolve(referenceElement);
                }
                if (element instanceof PsiExpression) {
                    checkExpressions((PsiExpression)element);
                }

                if (!(element instanceof PsiJavaFile)) {
                    assertNotNull(element.getText(), element.getParent());
                }
                super.visitElement(element);
            }

            private void checkCodeReferenceResolve(PsiJavaCodeReferenceElement element) {
                PsiElement resolved = element.resolve();
                PsiImportStatementBase importStatement = PsiTreeUtil
                        .getParentOfType(element, PsiImportStatementBase.class);
                if (importStatement != null && importStatement.isOnDemand()) {
                    // Ignore package portions in imports
                    return;
                }
                if (resolved == null) {
                    String text = element.getText();
                    if (text.equals("java.lang.annotation.ElementType.TYPE_PARAMETER")
                            || text.equals("java.lang.annotation.ElementType.TYPE_USE")
                            || text.equals("TYPE_PARAMETER")
                            || text.equals("TYPE_USE")) {
                        // From Java 8: not available in android.jar
                        return;
                    }
                    if (importStatement != null
                            && (element.getParent() instanceof EcjPsiJavaCodeReferenceElement
                            || element.getFirstChild() instanceof EcjPsiJavaCodeReferenceElement)) {
                        // Import statements can't resolve package parts
                        return;
                    }

                    if (element instanceof EcjPsiSourceElement
                            && ((EcjPsiSourceElement)element).getNativeNode() == null) {
                        // For qualified references, we don't have bindings for each
                        // component part of the expression, only for the leaf
                        return;
                    }

                    if (element instanceof EcjPsiSourceElement
                            && ((EcjPsiSourceElement)element).getNativeNode() instanceof TypeReference
                            && ((TypeReference)((EcjPsiSourceElement)element).getNativeNode()).resolvedType instanceof TypeVariableBinding) {
                        // TypeVariableBindings don't have types
                        return;
                    }
                }
                assertNotNull(resolved);
            }

            private void checkExpressions(PsiExpression expression) {
                PsiType type = expression.getType();
                if (type == null) {
                    if (expression instanceof PsiParenthesizedExpression) {
                        expression = ((PsiParenthesizedExpression)expression).getExpression();
                    }
                    if (expression instanceof EcjPsiSourceElement) {
                        Object nativeNode = ((EcjPsiSourceElement) expression).getNativeNode();
                        if (nativeNode instanceof NameReference &&
                                ((NameReference)nativeNode).binding instanceof ProblemBinding) {
                            // Unresolved type, e.g. for things like Java 8 TYPE_PARAMETER field
                            // binding
                            return;
                        }
                    }
                }
                assertNotNull(type);
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                assertNotNull(expression.getArgumentList());

                PsiMethod resolved = expression.resolveMethod();
                if (resolved == null) {
                    String text = expression.getText();
                    if ("abc.super();".equals(text)) {
                        // Points to default constructor (not in code)
                        return;
                    }
                    expression.resolveMethod();
                }
                assertNotNull(resolved);
                assertNotNull(resolved.getName());
                if (!resolved.isConstructor()) {
                    assertNotNull(resolved.getReturnType());
                }
                assertNotNull(resolved.getContainingClass());

                super.visitMethodCallExpression(expression);
            }
        });
    }
}