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

package com.android.builder.shrinker.parser;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Grammar actions for the ProGuard config files parser, forked from Jack.
 */
@SuppressWarnings("unused") // These methods are called by the ANTLR-generated parser.
public class GrammarActions {

    public static void parse(
            @NonNull String proguardFileName,
            @NonNull String baseDir,
            @NonNull Flags flags) throws RecognitionException {
        File proguardFile = getFileFromBaseDir(baseDir, proguardFileName);
        ProguardParser parser = createParserFromFile(proguardFile);
        if (parser != null) {
            parser.prog(flags, proguardFile.getParentFile().getPath());
        }
    }

    static void addKeepClassMembers(
            @NonNull Flags flags,
            @NonNull ClassSpecification classSpecification,
            @Nullable KeepModifier keepModifier) {
        if (keepModifier == null) {
            keepModifier = KeepModifier.NONE;
        }
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassMembers(classSpecification);
    }

    static void addKeepClassSpecification(
            @NonNull Flags flags,
            @NonNull ClassSpecification classSpecification,
            @Nullable KeepModifier keepModifier) {
        if (keepModifier == null) {
            keepModifier = KeepModifier.NONE;
        }
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassSpecification(classSpecification);
    }

    static void addKeepClassesWithMembers(
            @NonNull Flags flags,
            @NonNull ClassSpecification classSpecification,
            @Nullable KeepModifier keepModifier) {
        if (keepModifier == null) {
            keepModifier = KeepModifier.NONE;
        }
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassesWithMembers(classSpecification);
    }

    static void addModifier(
            @NonNull ModifierSpecification modSpec,
            int modifier,
            boolean hasNegator) {
        modSpec.addModifier(modifier, hasNegator);
    }

    @NonNull
    static AnnotationSpecification annotation(
            @NonNull String annotationName,
            boolean hasNameNegator) {
        NameSpecification name = name(annotationName);
        name.setNegator(hasNameNegator);
        return new AnnotationSpecification(name);
    }

    @NonNull
    static ClassSpecification classSpec(
            @NonNull String name,
            boolean hasNameNegator,
            @NonNull ClassTypeSpecification classType,
            @Nullable AnnotationSpecification annotation,
            @NonNull ModifierSpecification modifier) {
        NameSpecification nameSpec;
        if (name.equals("*")) {
            nameSpec = name("**");
        } else {
            nameSpec = name(name);
        }
        nameSpec.setNegator(hasNameNegator);
        ClassSpecification classSpec = new ClassSpecification(nameSpec, classType, annotation);
        classSpec.setModifier(modifier);
        return classSpec;
    }

    @NonNull
    static ClassTypeSpecification classType(int type, boolean hasNegator) {
        ClassTypeSpecification classSpec = new ClassTypeSpecification(type);
        classSpec.setNegator(hasNegator);
        return classSpec;
    }

    @NonNull
    static InheritanceSpecification createInheritance(
      /*@NonNull*/ String className, boolean hasNameNegator,
            @NonNull AnnotationSpecification annotationType) {
        NameSpecification nameSpec = name(className);
        nameSpec.setNegator(hasNameNegator);
        return new InheritanceSpecification(nameSpec, annotationType);
    }

    static void field(
            @NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType,
            @Nullable String typeSignature,
            @NonNull String name,
            @NonNull ModifierSpecification modifier) {
        NameSpecification typeSignatureSpec = null;
        if (typeSignature != null) {
            typeSignatureSpec = name(typeSignature);
        } else {
            checkState(name.equals("*"), "No type signature, but name is not <fields> or *.");
            name = "*";
        }
        classSpec.add(new FieldSpecification(name(name), modifier, typeSignatureSpec, annotationType));
    }

    static void fieldOrAnyMember(@NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType, @Nullable String typeSig,
            @NonNull String name, @NonNull ModifierSpecification modifier) {
        if (typeSig == null) {
            assert name.equals("*");
            // This is the "any member" case, we have to handle methods as well.
            method(classSpec,
                    annotationType,
                    getSignature("***", 0),
                    "*",
                    "(" + getSignature("...", 0) + ")",
                    modifier);
        }
        field(classSpec, annotationType, typeSig, name, modifier);
    }

    static void filter(
            @NonNull FilterSpecification filter,
            boolean negator,
            @NonNull String filterName) {
        filter.addElement(name(filterName), negator);
    }

    @NonNull
    static String getSignature(@NonNull String name, int dim) {
        StringBuilder sig = new StringBuilder();

        for (int i = 0; i < dim; i++) {
            sig.append("\\[");
        }

        // ... matches any number of arguments of any type
        if (name.equals("...")) {
            sig.append(".*");
        } else if (name.equals("***")) {
            // *** matches any type (primitive or non-primitive, array or non-array)
            sig.append(".*");
        } else if (name.equals("%")) {
            // % matches any primitive type ("boolean", "int", etc, but not "void")
            sig.append("(Z|B|C|S|I|F|D|L)");
        } else if (name.equals("boolean")) {
            sig.append("Z");
        } else if (name.equals("byte")) {
            sig.append("B");
        } else if (name.equals("char")) {
            sig.append("C");
        } else if (name.equals("short")) {
            sig.append("S");
        } else if (name.equals("int")) {
            sig.append("I");
        } else if (name.equals("float")) {
            sig.append("F");
        } else if (name.equals("double")) {
            sig.append("D");
        } else if (name.equals("long")) {
            sig.append("J");
        } else if (name.equals("void")) {
            sig.append("V");
        } else {
            sig.append("L").append(convertNameToPattern(name)).append(";");
        }

        return sig.toString();
    }

    static void method(
            @NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType,
            @Nullable String typeSig,
            @NonNull String name,
            @NonNull String signature,
            @Nullable ModifierSpecification modifier) {
        String fullName = "^" + convertNameToPattern(name);
        fullName += ":";
        fullName += signature.replace("(", "\\(").replace(")", "\\)");
        if (typeSig != null) {
            fullName += typeSig;
        } else {
            fullName += "V";
        }
        fullName += "$";
        Pattern pattern = Pattern.compile(fullName);
        classSpec.add(
                new MethodSpecification(
                        new NameSpecification(pattern),
                        modifier,
                        annotationType));
    }

    @NonNull
    static NameSpecification name(@NonNull String name) {
        String transformedName = "^" +
                convertNameToPattern(name) + "$";

        Pattern pattern = Pattern.compile(transformedName);
        return new NameSpecification(pattern);
    }

    static void unsupportedFlag(String flag) {
        throw new IllegalArgumentException(
                String.format(
                        "Flag %s is not supported by the custom shrinker.",
                        flag));
    }

    @NonNull
    private static String convertNameToPattern(@NonNull String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '.':
                    sb.append('/');
                    break;
                case '?':
                    // ? matches any single character in a name
                    // but not the package separator
                    sb.append("[^/]");
                    break;
                case '*':
                    int j = i + 1;
                    if (j < name.length() && name.charAt(j) == '*') {
                        // ** matches any part of a name, possibly containing
                        // any number of package separators or directory separators
                        sb.append(".*");
                        i++;
                    } else {
                        // * matches any part of a name not containing
                        // the package separator or directory separator
                        sb.append("[^/]*");
                    }
                    break;
                case '$':
                    sb.append("\\$");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    @NonNull
    private static ProguardParser createParserCommon(@NonNull CharStream stream) {
        ProguardLexer lexer = new ProguardLexer(stream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new ProguardParser(tokens);
    }

    @Nullable
    private static ProguardParser createParserFromFile(@NonNull File file) {
        try {
            return createParserCommon(new ANTLRFileStream(file.getPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static File getFileFromBaseDir(@NonNull String baseDir, @NonNull String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(baseDir, path);
        }
        return file;
    }
}
