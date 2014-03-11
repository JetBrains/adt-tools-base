/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.utils.Pair;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;

import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link GradleDetector} using a real Groovy AST,
 * which the Gradle plugin has access to
 */
public class GroovyGradleDetector extends GradleDetector {
    static final Implementation IMPLEMENTATION = new Implementation(
            GroovyGradleDetector.class,
            Scope.GRADLE_SCOPE);

    @Override
    public void visitBuildScript(@NonNull final Context context, Map<String, Object> sharedData) {
        try {
            visitQuietly(context, sharedData);
        } catch (Throwable t) {
            // ignore
            // Parsing the build script can involve class loading that we sometimes can't
            // handle. This happens for example when running lint in build-system/tests/api/.
            // This is a lint limitation rather than a user error, so don't complain
            // about these. Consider reporting a Issue#LINT_ERROR.
        }
    }

    private void visitQuietly(@NonNull final Context context, Map<String, Object> sharedData) {
        String source = context.getContents();
        if (source == null) {
            return;
        }

        List<ASTNode> astNodes = new AstBuilder().buildFromString(source);
        GroovyCodeVisitor visitor = new CodeVisitorSupport() {
            @Override
            public void visitMethodCallExpression(MethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                Expression arguments = expression.getArguments();
                if (arguments instanceof ArgumentListExpression) {
                    ArgumentListExpression ale = (ArgumentListExpression)arguments;
                    List<Expression> expressions = ale.getExpressions();
                    if (expressions.size() == 1 &&
                            expressions.get(0) instanceof ClosureExpression) {
                        String parent = expression.getMethodAsString();
                        if (isInterestingBlock(parent)) {
                            ClosureExpression closureExpression =
                                    (ClosureExpression)expressions.get(0);
                            Statement block = closureExpression.getCode();
                            if (block instanceof BlockStatement) {
                                BlockStatement bs = (BlockStatement)block;
                                for (Statement statement : bs.getStatements()) {
                                    if (statement instanceof ExpressionStatement) {
                                        ExpressionStatement e = (ExpressionStatement)statement;
                                        if (e.getExpression() instanceof MethodCallExpression) {
                                            checkDslProperty(parent,
                                                    (MethodCallExpression)e.getExpression());
                                        }
                                    } else if (statement instanceof ReturnStatement) {
                                        // Single item in block
                                        ReturnStatement e = (ReturnStatement)statement;
                                        if (e.getExpression() instanceof MethodCallExpression) {
                                            checkDslProperty(parent,
                                                    (MethodCallExpression)e.getExpression());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            private void checkDslProperty(String parent, MethodCallExpression c) {
                String property = c.getMethodAsString();
                if (isInterestingProperty(property, parent)) {
                    String value = getText(c.getArguments());
                    checkDslPropertyAssignment(context, property, value, parent, c);
                }
            }

            private String getText(ASTNode node) {
                String source = context.getContents();
                Pair<Integer, Integer> offsets = getOffsets(node, context);
                return source.substring(offsets.getFirst(), offsets.getSecond());
            }
        };

        for (ASTNode node : astNodes) {
            node.visit(visitor);
        }
    }

    @NonNull
    private static Pair<Integer, Integer> getOffsets(ASTNode node, Context context) {
        String source = context.getContents();
        assert source != null; // because we successfully parsed
        int start = 0;
        int end = source.length();
        int line = 1;
        int startLine = node.getLineNumber();
        int startColumn = node.getColumnNumber();
        int endLine = node.getLastLineNumber();
        int endColumn = node.getLastColumnNumber();
        int column = 1;
        for (int index = 0, len = end; index < len; index++) {
            if (line == startLine && column == startColumn) {
                start = index;
            }
            if (line == endLine && column == endColumn) {
                end = index;
                break;
            }

            char c = source.charAt(index);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        return Pair.of(start, end);
    }

    @Override
    protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<Integer, Integer> offsets = getOffsets(node, context);
        return offsets.getFirst();
    }

    @Override
    protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<Integer, Integer> offsets = getOffsets(node, context);
        int fromLine = node.getLineNumber() - 1;
        int fromColumn = node.getColumnNumber() - 1;
        int toLine = node.getLastLineNumber() - 1;
        int toColumn = node.getLastColumnNumber() - 1;
        return Location.create(context.file,
                new DefaultPosition(fromLine, fromColumn, offsets.getFirst()),
                new DefaultPosition(toLine, toColumn, offsets.getSecond()));
    }
}