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


import static com.android.tools.lint.detector.api.JavaContext.findSurroundingClass;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedField;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.ResolvedVariable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.ClassDeclaration;
import lombok.ast.ClassLiteral;
import lombok.ast.ConstructorInvocation;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodDeclaration;
import lombok.ast.Node;
import lombok.ast.NormalTypeBody;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;
import lombok.ast.VariableReference;

/**
 * Checks related to RecyclerView usage.
 * // https://code.google.com/p/android/issues/detail?id=172335
 */
public class RecyclerViewDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "RecyclerView", //$NON-NLS-1$
            "RecyclerView Problems",
            "`RecyclerView` will *not* call `onBindViewHolder` again when the position of " +
            "the item changes in the data set unless the item itself is " +
            "invalidated or the new position cannot be determined.\n" +
            "\n" +
            "For this reason, you should *only* use the position parameter " +
            "while acquiring the related data item inside this method, and " +
            "should *not* keep a copy of it.\n" +
            "\n" +
            "If you need the position of an item later on (e.g. in a click " +
            "listener), use `getAdapterPosition()` which will have the updated " +
            "adapter position.",
            Category.CORRECTNESS,
            8,
            Severity.WARNING,
            new Implementation(
                    RecyclerViewDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String VIEW_ADAPTER = "android.support.v7.widget.RecyclerView.Adapter"; //$NON-NLS-1$
    private static final String ON_BIND_VIEW_HOLDER = "onBindViewHolder"; //$NON-NLS-1$

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_ADAPTER);
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @Nullable ClassDeclaration declaration,
            @NonNull Node node, @NonNull ResolvedClass resolvedClass) {
        NormalTypeBody body;
        if (declaration != null) {
            body = declaration.astBody();
        } else if (node instanceof NormalTypeBody) {
            // anonymous inner class
            body = (NormalTypeBody) node;
        } else {
            return;
        }

        for (Node child : body.astMembers()) {
            if (child instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) child;
                if (method.astMethodName().astValue().equals(ON_BIND_VIEW_HOLDER)) {
                    int size = method.astParameters().size();
                    if (size == 2 || size == 3) {
                        checkMethod(context, method);
                    }
                }
            }
        }
    }

    private static void checkMethod(@NonNull JavaContext context,
            @NonNull MethodDeclaration declaration) {
        Iterator<VariableDefinition> iterator = declaration.astParameters().iterator();
        if (!iterator.hasNext()) {
            return;
        }
        VariableDefinition viewHolder = iterator.next();
        if (!iterator.hasNext()) {
            return;
        }
        VariableDefinition parameter = iterator.next();
        ResolvedNode reference = context.resolve(parameter);

        if (reference instanceof ResolvedVariable) {
            ParameterEscapesVisitor visitor = new ParameterEscapesVisitor(context, declaration,
                    (ResolvedVariable) reference);
            declaration.accept(visitor);
            if (visitor.variableEscapes()) {
                reportError(context, viewHolder, parameter);
            }
        } else if (parameter.astModifiers().isFinal()) {
            reportError(context, viewHolder, parameter);
        }
    }

    private static void reportError(@NonNull JavaContext context, VariableDefinition viewHolder,
            VariableDefinition parameter) {
        String variablePrefix;
        VariableDefinitionEntry first = viewHolder.astVariables().first();
        if (first != null) {
            variablePrefix = first.astName().astValue();
        } else {
            variablePrefix = "ViewHolder";
        }
        String message = String.format("Do not treat position as fixed; only use immediately "
                + "and call `%1$s.getAdapterPosition()` to look it up later",
                variablePrefix);
        context.report(ISSUE, parameter, context.getLocation(parameter),
                message);
    }

    /**
     * Determines whether a given variable "escapes" either to a field or to a nested
     * runnable. (We deliberately ignore variables that escape via method calls.)
     */
    private static class ParameterEscapesVisitor extends ForwardingAstVisitor {
        protected final JavaContext mContext;
        protected final List<ResolvedVariable> mVariables;
        private final ClassDeclaration mBindClass;
        private boolean mEscapes;
        private boolean mFoundInnerClass;

        public ParameterEscapesVisitor(JavaContext context,
                @NonNull MethodDeclaration onBindMethod,
                @NonNull ResolvedVariable variable) {
            mContext = context;
            mVariables = Lists.newArrayList(variable);
            mBindClass = findSurroundingClass(onBindMethod);
        }

        public boolean variableEscapes() {
            return mEscapes;
        }

        @Override
        public boolean visitNode(Node node) {
            return mEscapes || super.visitNode(node);
        }

        @Override
        public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
            Expression initializer = node.astInitializer();
            if (initializer instanceof VariableReference) {
                ResolvedNode resolved = mContext.resolve(initializer);
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    ResolvedNode resolvedVariable = mContext.resolve(node);
                    if (resolvedVariable instanceof ResolvedVariable) {
                        ResolvedVariable variable = (ResolvedVariable) resolvedVariable;
                        mVariables.add(variable);
                    } else if (resolvedVariable instanceof ResolvedField) {
                        mEscapes = true;
                    }
                }
            }
            return super.visitVariableDefinitionEntry(node);
        }

        @Override
        public boolean visitBinaryExpression(BinaryExpression node) {
            if (node.astOperator() == BinaryOperator.ASSIGN) {
                Expression rhs = node.astRight();
                boolean clearLhs = true;
                if (rhs instanceof VariableReference) {
                    ResolvedNode resolved = mContext.resolve(rhs);
                    //noinspection SuspiciousMethodCalls
                    if (resolved != null && mVariables.contains(resolved)) {
                        clearLhs = false;
                        ResolvedNode resolvedLhs = mContext.resolve(node.astLeft());
                        if (resolvedLhs instanceof ResolvedVariable) {
                            ResolvedVariable variable = (ResolvedVariable) resolvedLhs;
                            mVariables.add(variable);
                        } else if (resolvedLhs instanceof ResolvedField) {
                            mEscapes = true;
                        }
                    }
                }
                if (clearLhs) {
                    // If we reassign one of the variables, clear it out
                    ResolvedNode resolved = mContext.resolve(node.astLeft());
                    //noinspection SuspiciousMethodCalls
                    if (resolved != null && mVariables.contains(resolved)) {
                        //noinspection SuspiciousMethodCalls
                        mVariables.remove(resolved);
                    }
                }
            }
            return super.visitBinaryExpression(node);
        }

        @Override
        public boolean visitVariableReference(VariableReference node) {
            if (mFoundInnerClass) {
                // Check to see if this reference is inside the same class as the original
                // onBind (e.g. is this a reference from an inner class, or a reference
                // to a variable assigned from there)
                ResolvedNode resolved = mContext.resolve(node);
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    Node scope = node.getParent();
                    while (scope != null) {
                        if (scope instanceof NormalTypeBody) {
                            if (scope != mBindClass.astBody()) {
                                mEscapes = true;
                            }
                            break;
                        }
                        scope = scope.getParent();
                    }
                }
            }
            return super.visitVariableReference(node);
        }

        @Override
        public boolean visitClassLiteral(ClassLiteral node) {
            mFoundInnerClass = true;

            return super.visitClassLiteral(node);
        }

        @Override
        public boolean visitConstructorInvocation(ConstructorInvocation node) {
            NormalTypeBody anonymous = node.astAnonymousClassBody();
            if (anonymous != null) {
                mFoundInnerClass = true;
            }

            return super.visitConstructorInvocation(node);
        }
    }
}
