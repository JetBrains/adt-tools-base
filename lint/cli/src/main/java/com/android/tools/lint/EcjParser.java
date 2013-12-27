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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;

import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

import java.io.File;
import java.util.List;

import lombok.ast.Node;
import lombok.ast.TypeReference;
import lombok.ast.ecj.EcjTreeConverter;

/**
 * Java parser which uses ECJ for parsing.
 */
public class EcjParser implements IJavaParser {
    private final Parser mParser;

    private final LintClient mClient;

    public EcjParser(LintClient client) {
        mClient = client;
        CompilerOptions options = new CompilerOptions();
        // Always using JDK 7 rather than basing it on project metadata since we
        // don't do compilation error validation in lint (we leave that to the IDE's
        // error parser or the command line build's compilation step); we want an
        // AST that is as tolerant as possible.
        options.complianceLevel = ClassFileConstants.JDK1_7;
        options.sourceLevel = ClassFileConstants.JDK1_7;
        options.targetJDK = ClassFileConstants.JDK1_7;
        options.parseLiteralExpressionsAsConstants = true;
        ProblemReporter problemReporter = new ProblemReporter(
                DefaultErrorHandlingPolicies.exitOnFirstError(),
                options,
                new DefaultProblemFactory());
        mParser = new Parser(problemReporter, options.parseLiteralExpressionsAsConstants);
        mParser.javadocParser.checkDocComment = false;
    }

    @Override
    public lombok.ast.Node parseJava(@NonNull JavaContext context) {
        // Use Eclipse's compiler
        EcjTreeConverter converter = new EcjTreeConverter();
        String code = context.getContents();
        if (code == null) {
            return null;
        }

        CompilationUnit sourceUnit = new CompilationUnit(code.toCharArray(),
                context.file.getName(), "UTF-8"); //$NON-NLS-1$
        CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 0, 0);
        CompilationUnitDeclaration unit;
        try {
            unit = mParser.parse(sourceUnit, compilationResult);
        } catch (AbortCompilation e) {
            // No need to report Java parsing errors while running in Eclipse.
            // Eclipse itself will already provide problem markers for these files,
            // so all this achieves is creating "multiple annotations on this line"
            // tooltips instead.
            return null;
        }
        if (unit == null) {
            return null;
        }

        try {
            converter.visit(code, unit);
            List<? extends Node> nodes = converter.getAll();

            // There could be more than one node when there are errors; pick out the
            // compilation unit node
            for (lombok.ast.Node node : nodes) {
                if (node instanceof lombok.ast.CompilationUnit) {
                    return node;
                }
            }

            return null;
        } catch (Throwable t) {
            mClient.log(t, "Failed converting ECJ parse tree to Lombok for file %1$s",
                    context.file.getPath());
            return null;
        }
    }

    @NonNull
    @Override
    public Location getLocation(@NonNull JavaContext context,
            @NonNull lombok.ast.Node node) {
        lombok.ast.Position position = node.getPosition();
        return Location.create(context.file, context.getContents(),
                position.getStart(), position.getEnd());
    }

    @NonNull
    @Override
    public
    Location.Handle createLocationHandle(@NonNull JavaContext context,
            @NonNull lombok.ast.Node node) {
        return new LocationHandle(context.file, node);
    }

    @Override
    public void dispose(@NonNull JavaContext context,
            @NonNull lombok.ast.Node compilationUnit) {
    }

    @Override
    @Nullable
    public lombok.ast.Node resolve(@NonNull JavaContext context,
            @NonNull lombok.ast.Node node) {
        return null;
    }

    @Override
    @Nullable
    public TypeReference getType(@NonNull JavaContext context, @NonNull lombok.ast.Node node) {
        return null;
    }

    /* Handle for creating positions cheaply and returning full fledged locations later */
    private static class LocationHandle implements Location.Handle {
        private File mFile;
        private lombok.ast.Node mNode;
        private Object mClientData;

        public LocationHandle(File file, lombok.ast.Node node) {
            mFile = file;
            mNode = node;
        }

        @NonNull
        @Override
        public Location resolve() {
            lombok.ast.Position pos = mNode.getPosition();
            return Location.create(mFile, null /*contents*/, pos.getStart(), pos.getEnd());
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
            mClientData = clientData;
        }

        @Override
        @Nullable
        public Object getClientData() {
            return mClientData;
        }
    }
}
