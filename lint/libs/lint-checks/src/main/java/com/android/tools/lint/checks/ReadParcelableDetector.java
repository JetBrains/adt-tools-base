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
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.Identifier;
import lombok.ast.MethodInvocation;
import lombok.ast.NullLiteral;

/**
 * Looks for Parcelable classes that are missing a CREATOR field
 */
public class ReadParcelableDetector extends Detector implements Detector.JavaScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelClassLoader", //$NON-NLS-1$
            "Default Parcel Class Loader",

            "The documentation for `Parcel#readParcelable(ClassLoader)` (and its variations) " +
            "says that you can pass in `null` to pick up the default class loader. However, " +
            "that ClassLoader is a system class loader and is not able to find classes in " +
            "your own application.\n" +
            "\n" +
            "If you are writing your own classes into the `Parcel` (not just SDK classes like " +
            "`String` and so on), then you should supply a `ClassLoader` for your application " +
            "instead; a simple way to obtain one is to just call `getClass().getClassLoader()` " +
            "from your own class.",

            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(
                    ReadParcelableDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("http://developer.android.com/reference/android/os/Parcel.html");

    /** Constructs a new {@link ReadParcelableDetector} check */
    public ReadParcelableDetector() {
    }

    // ---- Implements JavaScanner ----


    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "readParcelable",
                "readParcelableArray",
                "readBundle",
                "readArray",
                "readSparseArray",
                "readValue",
                "readPersistableBundle"
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        ResolvedNode resolved = context.resolve(node);
        if (resolved instanceof ResolvedMethod) {
            ResolvedMethod method = (ResolvedMethod) resolved;
            if (method.getContainingClass().matches("android.os.Parcel")) {
                int argumentCount = method.getArgumentCount();
                if (argumentCount == 0) {
                    Identifier name = node.astName();
                    String message = String.format("Using the default class loader "
                            + "will not work if you are restoring your own classes. Consider "
                            + "using for example `%1$s(getClass().getClassLoader())` instead.",
                            name.astValue());
                    Location location = context.getRangeLocation(name, 0, name, 2);
                    context.report(ISSUE, node, location, message);
                } else if (argumentCount == 1) {
                    Expression parameter = node.astArguments().first();
                    if (parameter instanceof NullLiteral) {
                        String message = "Passing null here (to use the default class loader) "
                                + "will not work if you are restoring your own classes. Consider "
                                + "using for example `getClass().getClassLoader()` instead.";
                        Location location = context.getRangeLocation(node.astName(), 0, parameter, 1);
                        context.report(ISSUE, node, location, message);
                    }
                }
            }
        }
    }
}
