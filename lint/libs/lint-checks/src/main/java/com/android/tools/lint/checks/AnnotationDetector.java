/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.FQCN_SUPPRESS_LINT;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPRESS_LINT;
import static com.android.tools.lint.detector.api.JavaContext.getParentOfType;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaParser.ResolvedAnnotation;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.ast.Annotation;
import lombok.ast.AnnotationDeclaration;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationValue;
import lombok.ast.ArrayInitializer;
import lombok.ast.AstVisitor;
import lombok.ast.Block;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.Select;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.TypeBody;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;

/**
 * Checks annotations to make sure they are valid
 */
public class AnnotationDetector extends Detector implements Detector.JavaScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
              AnnotationDetector.class,
              Scope.JAVA_FILE_SCOPE);

    /** Placing SuppressLint on a local variable doesn't work for class-file based checks */
    public static final Issue INSIDE_METHOD = Issue.create(
            "LocalSuppress", //$NON-NLS-1$
            "@SuppressLint on invalid element",

            "The `@SuppressAnnotation` is used to suppress Lint warnings in Java files. However, " +
            "while many lint checks analyzes the Java source code, where they can find " +
            "annotations on (for example) local variables, some checks are analyzing the " +
            "`.class` files. And in class files, annotations only appear on classes, fields " +
            "and methods. Annotations placed on local variables disappear. If you attempt " +
            "to suppress a lint error for a class-file based lint check, the suppress " +
            "annotation not work. You must move the annotation out to the surrounding method.",

            Category.CORRECTNESS,
            3,
            Severity.ERROR,
            IMPLEMENTATION);

    /** IntDef annotations should be unique */
    public static final Issue UNIQUE = Issue.create(
            "UniqueConstants", //$NON-NLS-1$
            "Overlapping Enumeration Constants",

            "The `@IntDef` annotation allows you to " +
            "create a light-weight \"enum\" or type definition. However, it's possible to " +
            "accidentally specify the same value for two or more of the values, which can " +
            "lead to hard-to-detect bugs. This check looks for this scenario and flags any " +
            "repeated constants.\n" +
            "\n" +
            "In some cases, the repeated constant is intentional (for example, renaming a " +
            "constant to a more intuitive name, and leaving the old name in place for " +
            "compatibility purposes.)  In that case, simply suppress this check by adding a " +
            "`@SuppressLint(\"UniqueConstants\")` annotation.",

            Category.CORRECTNESS,
            3,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Constructs a new {@link AnnotationDetector} check */
    public AnnotationDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(Annotation.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new AnnotationChecker(context);
    }

    private static class AnnotationChecker extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public AnnotationChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitAnnotation(Annotation node) {
            String type = node.astAnnotationTypeReference().getTypeName();
            if (SUPPRESS_LINT.equals(type) || FQCN_SUPPRESS_LINT.equals(type)) {
                Node parent = node.getParent();
                if (parent instanceof Modifiers) {
                    parent = parent.getParent();
                    if (parent instanceof VariableDefinition) {
                        for (AnnotationElement element : node.astElements()) {
                            AnnotationValue valueNode = element.astValue();
                            if (valueNode == null) {
                                continue;
                            }
                            if (valueNode instanceof StringLiteral) {
                                StringLiteral literal = (StringLiteral) valueNode;
                                String id = literal.astValue();
                                if (!checkId(node, id)) {
                                    return super.visitAnnotation(node);
                                }
                            } else if (valueNode instanceof ArrayInitializer) {
                                ArrayInitializer array = (ArrayInitializer) valueNode;
                                StrictListAccessor<Expression, ArrayInitializer> expressions =
                                        array.astExpressions();
                                if (expressions == null) {
                                    continue;
                                }
                                for (Expression arrayElement : expressions) {
                                    if (arrayElement instanceof StringLiteral) {
                                        String id = ((StringLiteral) arrayElement).astValue();
                                        if (!checkId(node, id)) {
                                            return super.visitAnnotation(node);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (INT_DEF_ANNOTATION.equals(type) || "IntDef".equals(type)) {
                // Make sure that all the constants are unique
                ResolvedNode resolved = mContext.resolve(node);
                if (resolved instanceof ResolvedAnnotation) {
                    ensureUniqueValues(((ResolvedAnnotation)resolved), node);
                }
            }

            return super.visitAnnotation(node);
        }

        private void ensureUniqueValues(@NonNull ResolvedAnnotation annotation,
                @NonNull Annotation node) {
            Object allowed = annotation.getValue();
            if (allowed instanceof Object[]) {
                Object[] allowedValues = (Object[]) allowed;
                Map<Integer,Integer> valueToIndex =
                        Maps.newHashMapWithExpectedSize(allowedValues.length);

                List<Node> constants = null;
                for (AnnotationElement element : node.astElements()) {
                    if (element.astName() == null
                            || ATTR_VALUE.equals(element.astName().astValue())) {
                        AnnotationValue value = element.astValue();
                        if (value instanceof ArrayInitializer) {
                            ArrayInitializer initializer = (ArrayInitializer)value;
                            constants = Lists.newArrayListWithExpectedSize(allowedValues.length);
                            for (Expression expression : initializer.astExpressions()) {
                                constants.add(expression);
                            }
                        }
                        break;
                    }
                }
                if (constants != null && constants.size() != allowedValues.length) {
                    constants = null;
                }

                for (int index = 0; index < allowedValues.length; index++) {
                    Object o = allowedValues[index];
                    if (o instanceof Integer) {
                        Integer integer = (Integer)o;
                        if (valueToIndex.containsKey(integer)) {
                            int repeatedValue = integer;

                            Location location;
                            String message;
                            if (constants != null) {
                                Node constant = constants.get(index);
                                int prevIndex = valueToIndex.get(integer);
                                Node prevConstant = constants.get(prevIndex);
                                message = String.format(
                                        "Constants `%1$s` and `%2$s` specify the same exact "
                                                + "value (%3$d); this is usually a cut & paste or "
                                                + "merge error",
                                        constant.toString(), prevConstant.toString(),
                                        repeatedValue);
                                location = mContext.getLocation(constant);
                                Location secondary = mContext.getLocation(prevConstant);
                                secondary.setMessage("Previous same value");
                                location.setSecondary(secondary);
                            } else {
                                message = String.format(
                                        "More than one constant specifies the same exact "
                                                + "value (%1$d); this is usually a cut & paste or"
                                                + "merge error",
                                        repeatedValue);
                                location = mContext.getLocation(node);
                            }
                            Node scope = getAnnotationScope(node);
                            mContext.report(UNIQUE, scope, location, message);
                            break;
                        }
                        valueToIndex.put(integer, index);
                    }
                }
            }
        }

        private boolean checkId(Annotation node, String id) {
            IssueRegistry registry = mContext.getDriver().getRegistry();
            Issue issue = registry.getIssue(id);
            // Special-case the ApiDetector issue, since it does both source file analysis
            // only on field references, and class file analysis on the rest, so we allow
            // annotations outside of methods only on fields
            if (issue != null && !issue.getImplementation().getScope().contains(Scope.JAVA_FILE)
                    || issue == ApiDetector.UNSUPPORTED) {
                // Ensure that this isn't a field
                Node parent = node.getParent();
                while (parent != null) {
                    if (parent instanceof MethodDeclaration
                            || parent instanceof ConstructorDeclaration
                            || parent instanceof Block) {
                        break;
                    } else if (parent instanceof TypeBody) { // It's a field
                        return true;
                    } else if (issue == ApiDetector.UNSUPPORTED
                            && parent instanceof VariableDefinition) {
                        VariableDefinition definition = (VariableDefinition) parent;
                        for (VariableDefinitionEntry entry : definition.astVariables()) {
                            Expression initializer = entry.astInitializer();
                            if (initializer instanceof Select) {
                                return true;
                            }
                        }
                    }
                    parent = parent.getParent();
                    if (parent == null) {
                        return true;
                    }
                }

                // This issue doesn't have AST access: annotations are not
                // available for local variables or parameters
                Node scope = getAnnotationScope(node);
                mContext.report(INSIDE_METHOD, scope, mContext.getLocation(node), String.format(
                    "The `@SuppressLint` annotation cannot be used on a local " +
                    "variable with the lint check '%1$s': move out to the " +
                    "surrounding method", id));
                return false;
            }

            return true;
        }
    }

    /**
     * Returns the node to use as the scope for the given annotation node.
     * You can't annotate an annotation itself (with {@code @SuppressLint}), but
     * you should be able to place an annotation next to it, as a sibling, to only
     * suppress the error on this annotated element, not the whole surrounding class.
     */
    @NonNull
    private static Node getAnnotationScope(@NonNull Annotation node) {
        //
        Node scope = getParentOfType(node,
              AnnotationDeclaration.class, true);
        if (scope == null) {
            scope = node;
        }
        return scope;
    }
}
