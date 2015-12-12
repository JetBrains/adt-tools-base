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
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.SUPPRESS_LINT;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.tools.lint.checks.PermissionRequirement.getAnnotationStrings;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_ALL_OF;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_ANY_OF;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_FROM;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_FROM_INCLUSIVE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_MAX;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_MIN;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_MULTIPLE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_TO;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_TO_INCLUSIVE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.CHECK_RESULT_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.COLOR_INT_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.FLOAT_RANGE_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.INT_RANGE_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION_READ;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION_WRITE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.RES_SUFFIX;
import static com.android.tools.lint.checks.SupportAnnotationDetector.SIZE_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.filterRelevantAnnotations;
import static com.android.tools.lint.checks.SupportAnnotationDetector.getBoolean;
import static com.android.tools.lint.checks.SupportAnnotationDetector.getDoubleAttribute;
import static com.android.tools.lint.checks.SupportAnnotationDetector.getLongAttribute;
import static com.android.tools.lint.client.api.JavaParser.TYPE_DOUBLE;
import static com.android.tools.lint.client.api.JavaParser.TYPE_FLOAT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_INT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_LONG;
import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;
import static com.android.tools.lint.detector.api.JavaContext.findSurroundingClass;
import static com.android.tools.lint.detector.api.JavaContext.getParentOfType;
import static com.android.tools.lint.detector.api.LintUtils.findSubstring;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedAnnotation;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedField;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.TypeDescriptor;
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
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import lombok.ast.Annotation;
import lombok.ast.AnnotationDeclaration;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationValue;
import lombok.ast.ArrayInitializer;
import lombok.ast.AstVisitor;
import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.Block;
import lombok.ast.Case;
import lombok.ast.Cast;
import lombok.ast.ClassDeclaration;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.Expression;
import lombok.ast.ExpressionStatement;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.InlineIfExpression;
import lombok.ast.IntegralLiteral;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.Select;
import lombok.ast.Statement;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.Switch;
import lombok.ast.TypeBody;
import lombok.ast.TypeMember;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;
import lombok.ast.VariableReference;

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

    /** Incorrectly using a support annotation */
    public static final Issue ANNOTATION_USAGE = Issue.create(
            "SupportAnnotationUsage", //$NON-NLS-1$
            "Incorrect support annotation usage",

            "This lint check makes sure that the support annotations (such as " +
            "`@IntDef` and `@ColorInt`) are used correctly. For example, it's an " +
            "error to specify an `@IntRange` where the `from` value is higher than " +
            "the `to` value.",

            Category.CORRECTNESS,
            2,
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

    /** Flags should typically be specified as bit shifts */
    public static final Issue FLAG_STYLE = Issue.create(
            "ShiftFlags", //$NON-NLS-1$
            "Dangerous Flag Constant Declaration",

            "When defining multiple constants for use in flags, the recommended style is " +
            "to use the form `1 << 2`, `1 << 3`, `1 << 4` and so on to ensure that the " +
            "constants are unique and non-overlapping.",

            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    /** All IntDef constants should be included in switch */
    public static final Issue SWITCH_TYPE_DEF = Issue.create(
            "SwitchIntDef", //$NON-NLS-1$
            "Missing @IntDef in Switch",

            "This check warns if a `switch` statement does not explicitly include all " +
            "the values declared by the typedef `@IntDef` declaration.",

            Category.CORRECTNESS,
            3,
            Severity.WARNING,
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
        //noinspection unchecked
        return Arrays.<Class<? extends Node>>asList(Annotation.class, Switch.class);
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
            String typeName = node.astAnnotationTypeReference().getTypeName();
            if (typeName.equals("Override")) {
                return super.visitAnnotation(node);
            }
            TypeDescriptor typeDescriptor = mContext.getType(node);
            if (typeDescriptor == null) {
                return super.visitAnnotation(node);
            }
            String type = typeDescriptor.getName();
            if (type.startsWith("java.lang.")){
                return super.visitAnnotation(node);
            }

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
                                if (!checkSuppressLint(node, id)) {
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
                                        if (!checkSuppressLint(node, id)) {
                                            return super.visitAnnotation(node);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (type.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
                if (CHECK_RESULT_ANNOTATION.equals(type)) {
                    // Check that the return type of this method is not void!
                } else if (INT_RANGE_ANNOTATION.equals(type)
                        || FLOAT_RANGE_ANNOTATION.equals(type)) {
                    // Check that the annotated element's type is int or long.
                    // Also make sure that from <= to.
                    ResolvedNode resolved = mContext.resolve(node);
                    if (resolved instanceof ResolvedAnnotation) {
                        ResolvedAnnotation annotation = (ResolvedAnnotation) resolved;
                        boolean invalid;
                        if (INT_RANGE_ANNOTATION.equals(type)) {
                            checkTargetType(node, TYPE_INT, TYPE_LONG);

                            long from = getLongAttribute(annotation, ATTR_FROM, Long.MIN_VALUE);
                            long to = getLongAttribute(annotation, ATTR_TO, Long.MAX_VALUE);
                            invalid = from > to;
                        } else {
                            checkTargetType(node, TYPE_FLOAT, TYPE_DOUBLE);

                            double from = getDoubleAttribute(annotation, ATTR_FROM,
                                    Double.NEGATIVE_INFINITY);
                            double to = getDoubleAttribute(annotation, ATTR_TO,
                                    Double.POSITIVE_INFINITY);
                            invalid = from > to;
                        }
                        if (invalid) {
                            mContext.report(ANNOTATION_USAGE, node, mContext.getLocation(node),
                                    "Invalid range: the `from` attribute must be less than "
                                            + "the `to` attribute");
                        }
                    }
                } else if (SIZE_ANNOTATION.equals(type)) {
                    // Check that the annotated element's type is an array, or a collection
                    // (or at least not an int or long; if so, suggest IntRange)
                    // Make sure the size and the modulo is not negative.

                    ResolvedNode resolved = mContext.resolve(node);
                    if (resolved instanceof ResolvedAnnotation) {
                        ResolvedAnnotation annotation = (ResolvedAnnotation) resolved;
                        int unset = -42;
                        long exact = getLongAttribute(annotation, ATTR_VALUE, unset);
                        long min = getLongAttribute(annotation, ATTR_MIN, Long.MIN_VALUE);
                        long max = getLongAttribute(annotation, ATTR_MAX, Long.MAX_VALUE);
                        long multiple = getLongAttribute(annotation, ATTR_MULTIPLE, 1);
                        if (min > max) {
                            mContext.report(ANNOTATION_USAGE, node, mContext.getLocation(node),
                                    "Invalid size range: the `min` attribute must be less than "
                                            + "the `max` attribute");
                        } else if (multiple < 1) {
                            mContext.report(ANNOTATION_USAGE, node, mContext.getLocation(node),
                                    "The size multiple must be at least 1");

                        } else if (exact < 0 && exact != unset) {
                            mContext.report(ANNOTATION_USAGE, node, mContext.getLocation(node),
                                    "The size can't be negative");
                        }
                    }
                } else if (COLOR_INT_ANNOTATION.equals(type)) {
                    // Check that ColorInt applies to the right type
                    checkTargetType(node, TYPE_INT, TYPE_LONG);
                } else if (INT_DEF_ANNOTATION.equals(type)) {
                    // Make sure IntDef constants are unique
                    ResolvedNode resolved = mContext.resolve(node);
                    if (resolved != null) {
                        ensureUniqueValues(((ResolvedAnnotation) resolved), node);
                    }
                } else if (PERMISSION_ANNOTATION.equals(type) ||
                        PERMISSION_ANNOTATION_READ.equals(type) ||
                        PERMISSION_ANNOTATION_WRITE.equals(type)) {
                    // Check that if there are no arguments, this is specified on a parameter,
                    // and conversely, on methods and fields there is a valid argument.
                    if (node.getParent() instanceof Modifiers &&
                            node.getParent().getParent() instanceof MethodDeclaration) {
                        ResolvedNode resolved = mContext.resolve(node);
                        if (resolved != null) {
                            ResolvedAnnotation annotation = (ResolvedAnnotation) resolved;
                            String value = (String)annotation.getValue(ATTR_VALUE);
                            String[] anyOf = getAnnotationStrings(annotation.getValue(ATTR_ANY_OF));
                            String[] allOf = getAnnotationStrings(annotation.getValue(ATTR_ALL_OF));

                            int set = 0;
                            //noinspection VariableNotUsedInsideIf
                            if (value != null) {
                                set++;
                            }
                            //noinspection VariableNotUsedInsideIf
                            if (allOf != null) {
                                set++;
                            }
                            //noinspection VariableNotUsedInsideIf
                            if (anyOf != null) {
                                set++;
                            }

                            if (set == 0) {
                                mContext.report(ANNOTATION_USAGE, node,
                                        mContext.getLocation(node),
                                        "For methods, permission annotation should specify one "
                                                + "of `value`, `anyOf` or `allOf`");
                            } else if (set > 1) {
                                mContext.report(ANNOTATION_USAGE, node,
                                        mContext.getLocation(node),
                                        "Only specify on of `value`, `anyOf` or `allOf`");
                            }
                        }
                    }

                    // Also make sure you set only one of value, anyOf, allOf

                } else if (type.endsWith(RES_SUFFIX)) {
                    // Check that resource type annotations are on ints
                    checkTargetType(node, TYPE_INT, TYPE_LONG);
                }
            } else {
                // Look for typedefs (and make sure they're specified on the right type)
                ResolvedNode resolved = mContext.resolve(node);
                if (resolved != null) {
                    for (ResolvedAnnotation annotation : resolved.getAnnotations()) {
                        String name = annotation.getName();
                        if (INT_DEF_ANNOTATION.equals(name)) {
                            checkTargetType(node, TYPE_INT, TYPE_LONG);
                        } else if (STRING_DEF_ANNOTATION.equals(type)) {
                            checkTargetType(node, TYPE_STRING, null);
                        }
                    }
                }
            }

            return super.visitAnnotation(node);
        }

        private void checkTargetType(@NonNull Annotation node, @NonNull String type1,
                @Nullable String type2) {
            Node parent = node.getParent();
            if (parent != null) {
                if (parent instanceof Modifiers) {
                    Node parentParent = parent.getParent();
                    if (parentParent != null) {
                        TypeDescriptor typeDescriptor = mContext.getType(parentParent);
                        if (typeDescriptor != null
                                && !typeDescriptor.matchesName(type1)
                                && (type2 == null || !typeDescriptor.matchesName(type2))) {
                            String expectedTypes = type2 == null ? type1 : type1 + " or " + type2;
                            String typeName = typeDescriptor.getName();
                            if (typeName.equals(TYPE_STRING)) {
                                typeName = "String";
                            }
                            String message = String.format(
                                    "This annotation does not apply for type %1$s; expected %2$s",
                                    typeName, expectedTypes);
                            Location location = mContext.getLocation(node);
                            mContext.report(ANNOTATION_USAGE, node, location, message);
                        }
                    }
                }
            }
        }

        @Override
        public boolean visitSwitch(Switch node) {
            Expression condition = node.astCondition();
            TypeDescriptor type = mContext.getType(condition);
            if (type != null && type.matchesName(TYPE_INT)) {
                ResolvedAnnotation annotation = findIntDef(condition);
                if (annotation != null) {
                    checkSwitch(node, annotation);
                }
            }

            return super.visitSwitch(node);
        }

        /**
         * Searches for the corresponding @IntDef annotation definition associated
         * with a given node
         */
        @Nullable
        private ResolvedAnnotation findIntDef(@NonNull Node node) {
            if ((node instanceof VariableReference || node instanceof Select)) {
                ResolvedNode resolved = mContext.resolve(node);
                if (resolved == null) {
                    return null;
                }

                ResolvedAnnotation annotation = SupportAnnotationDetector.findIntDef(
                        filterRelevantAnnotations(resolved.getAnnotations()));
                if (annotation != null) {
                    return annotation;
                }

                if (node instanceof VariableReference) {
                    Statement statement = getParentOfType(node, Statement.class, false);
                    if (statement != null) {
                        ListIterator<Node> iterator =
                                statement.getParent().getChildren().listIterator();
                        while (iterator.hasNext()) {
                            if (iterator.next() == statement) {
                                if (iterator.hasPrevious()) { // should always be true
                                    iterator.previous();
                                }
                                break;
                            }
                        }

                        String targetName = ((VariableReference) node).astIdentifier().astValue();
                        while (iterator.hasPrevious()) {
                            Node previous = iterator.previous();
                            if (previous instanceof VariableDeclaration) {
                                VariableDeclaration declaration = (VariableDeclaration) previous;
                                VariableDefinition definition = declaration.astDefinition();
                                for (VariableDefinitionEntry entry : definition
                                        .astVariables()) {
                                    if (entry.astInitializer() != null
                                            && entry.astName().astValue().equals(targetName)) {
                                        return findIntDef(entry.astInitializer());
                                    }
                                }
                            } else if (previous instanceof ExpressionStatement) {
                                ExpressionStatement expressionStatement =
                                        (ExpressionStatement) previous;
                                Expression expression = expressionStatement.astExpression();
                                if (expression instanceof BinaryExpression &&
                                        ((BinaryExpression) expression).astOperator()
                                                == BinaryOperator.ASSIGN) {
                                    BinaryExpression binaryExpression
                                            = (BinaryExpression) expression;
                                    if (targetName.equals(binaryExpression.astLeft().toString())) {
                                        return findIntDef(binaryExpression.astRight());
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (node instanceof MethodInvocation) {
                ResolvedNode resolved = mContext.resolve(node);
                if (resolved != null) {
                    ResolvedAnnotation annotation = SupportAnnotationDetector
                            .findIntDef(filterRelevantAnnotations(resolved.getAnnotations()));
                    if (annotation != null) {
                        return annotation;
                    }
                }
            } else if (node instanceof InlineIfExpression) {
                InlineIfExpression expression = (InlineIfExpression) node;
                if (expression.astIfTrue() != null) {
                    ResolvedAnnotation result = findIntDef(expression.astIfTrue());
                    if (result != null) {
                        return result;
                    }
                }
                if (expression.astIfFalse() != null) {
                    ResolvedAnnotation result = findIntDef(expression.astIfFalse());
                    if (result != null) {
                        return result;
                    }
                }
            } else if (node instanceof Cast) {
                Cast cast = (Cast) node;
                return findIntDef(cast.astOperand());
            }

            return null;
        }

        private void checkSwitch(@NonNull Switch node, @NonNull ResolvedAnnotation annotation) {
            Block block = node.astBody();
            if (block == null) {
                return;
            }

            Object allowed = annotation.getValue();
            if (!(allowed instanceof Object[])) {
                return;
            }
            Object[] allowedValues = (Object[]) allowed;
            List<ResolvedField> fields = Lists.newArrayListWithCapacity(allowedValues.length);
            for (Object o : allowedValues) {
                if (o instanceof ResolvedField) {
                    fields.add((ResolvedField) o);
                }
            }

            // Empty switch: arguably we could skip these (since the IDE already warns about
            // empty switches) but it's useful since the quickfix will kick in and offer all
            // the missing ones when you're editing.
            //   if (block.astContents().isEmpty()) { return; }

            for (Statement statement : block.astContents()) {
                if (statement instanceof Case) {
                    Case caseStatement = (Case) statement;
                    Expression expression = caseStatement.astCondition();
                    if (expression instanceof IntegralLiteral) {
                        // Report warnings if you specify hardcoded constants.
                        // It's the wrong thing to do.
                        List<String> list = computeFieldNames(node, Arrays.asList(allowedValues));
                        // Keep error message in sync with {@link #getMissingCases}
                        String message = "Don't use a constant here; expected one of: " + Joiner
                                .on(", ").join(list);
                        mContext.report(SWITCH_TYPE_DEF, expression,
                                mContext.getLocation(expression), message);
                        return; // Don't look for other missing typedef constants since you might
                        // have aliased with value
                    } else if (expression != null) { // default case can have null expression
                        ResolvedNode resolved = mContext.resolve(expression);
                        if (resolved == null) {
                            // If there are compilation issues (e.g. user is editing code) we
                            // can't be certain, so don't flag anything.
                            return;
                        }
                        if (resolved instanceof ResolvedField) {
                            // We can't just do
                            //    fields.remove(resolved);
                            // since the fields list contains instances of potentially
                            // different types with different hash codes. The
                            // equals method on ResolvedExternalField deliberately handles
                            // this (but it can't make its hash code match what
                            // the ECJ fields do, which is tied to the ECJ binding hash code.)
                            // So instead, manually check for equals. These lists tend to
                            // be very short anyway.
                            ListIterator<ResolvedField> iterator = fields.listIterator();
                            while (iterator.hasNext()) {
                                ResolvedField field = iterator.next();
                                if (field.equals(resolved)) {
                                    iterator.remove();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (!fields.isEmpty()) {
                List<String> list = computeFieldNames(node, fields);
                // Keep error message in sync with {@link #getMissingCases}
                String message = "Switch statement on an `int` with known associated constant "
                        + "missing case " + Joiner.on(", ").join(list);
                Location location = mContext.getNameLocation(node);
                mContext.report(SWITCH_TYPE_DEF, node, location, message);
            }
        }

        private void ensureUniqueValues(@NonNull ResolvedAnnotation annotation,
                                        @NonNull Annotation node) {
            Object allowed = annotation.getValue();
            if (allowed instanceof Object[]) {
                Object[] allowedValues = (Object[]) allowed;
                Map<Number,Integer> valueToIndex =
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
                if (constants != null) {
                    if (constants.size() != allowedValues.length) {
                        constants = null;
                    } else {
                        boolean flag = annotation.getValue(TYPE_DEF_FLAG_ATTRIBUTE) == Boolean.TRUE;
                        if (flag) {
                            ensureUsingFlagStyle(constants);
                        }
                    }
                }

                for (int index = 0; index < allowedValues.length; index++) {
                    Object o = allowedValues[index];
                    if (o instanceof Number) {
                        Number number = (Number)o;
                        if (valueToIndex.containsKey(number)) {
                            @SuppressWarnings("UnnecessaryLocalVariable")
                            Number repeatedValue = number;

                            Location location;
                            String message;
                            if (constants != null) {
                                Node constant = constants.get(index);
                                int prevIndex = valueToIndex.get(number);
                                Node prevConstant = constants.get(prevIndex);
                                message = String.format(
                                        "Constants `%1$s` and `%2$s` specify the same exact "
                                                + "value (%3$s); this is usually a cut & paste or "
                                                + "merge error",
                                        constant.toString(), prevConstant.toString(),
                                        repeatedValue.toString());
                                location = mContext.getLocation(constant);
                                Location secondary = mContext.getLocation(prevConstant);
                                secondary.setMessage("Previous same value");
                                location.setSecondary(secondary);
                            } else {
                                message = String.format(
                                        "More than one constant specifies the same exact "
                                                + "value (%1$s); this is usually a cut & paste or"
                                                + "merge error",
                                        repeatedValue.toString());
                                location = mContext.getLocation(node);
                            }
                            Node scope = getAnnotationScope(node);
                            mContext.report(UNIQUE, scope, location, message);
                            break;
                        }
                        valueToIndex.put(number, index);
                    }
                }
            }
        }

        @NonNull
        private static List<VariableDefinitionEntry> findDeclarations(
                @Nullable ClassDeclaration cls,
                @NonNull List<VariableReference> references) {
            if (cls == null) {
                return Collections.emptyList();
            }
            Map<String, VariableReference> referenceMap = Maps.newHashMap();
            for (VariableReference reference : references) {
                String name = reference.astIdentifier().astValue();
                referenceMap.put(name, reference);
            }
            List<VariableDefinitionEntry> declarations = Lists.newArrayList();
            for (TypeMember member : cls.astBody().astMembers()) {
                if (member instanceof VariableDeclaration) {
                    VariableDeclaration declaration = (VariableDeclaration)member;
                    VariableDefinitionEntry field = declaration.astDefinition().astVariables()
                            .first();
                    String name = field.astName().astValue();
                    if (referenceMap.containsKey(name)) {
                        // TODO: When the Lombok ECJ bridge properly handles resolving variable
                        // definitions into ECJ bindings this code should check that
                        // mContext.resolve(field) == mContext.resolve(referenceMap.get(name)) !
                        declarations.add(field);
                    }
                }
            }

            return declarations;
        }

        private void ensureUsingFlagStyle(@NonNull List<Node> constants) {
            if (constants.size() < 3) {
                return;
            }

            List<VariableReference> references =
                    Lists.newArrayListWithExpectedSize(constants.size());
            for (Node constant : constants) {
                if (constant instanceof VariableReference) {
                    references.add((VariableReference) constant);
                }
            }
            List<VariableDefinitionEntry> entries = findDeclarations(
                    findSurroundingClass(constants.get(0)), references);
            for (VariableDefinitionEntry entry : entries) {
                Expression declaration = entry.astInitializer();
                if (declaration == null) {
                    continue;
                }
                if (declaration instanceof IntegralLiteral) {
                    IntegralLiteral literal = (IntegralLiteral) declaration;
                    // Allow -1, 0 and 1. You can write 1 as "1 << 0" but IntelliJ for
                    // example warns that that's a redundant shift.
                    long value = literal.astLongValue();
                    if (Math.abs(value) <= 1) {
                        continue;
                    }
                    // Only warn if we're setting a specific bit
                    if (Long.bitCount(value) != 1) {
                        continue;
                    }
                    int shift = Long.numberOfTrailingZeros(value);
                    String message = String.format(
                            "Consider declaring this constant using 1 << %1$d instead",
                            shift);
                    mContext.report(FLAG_STYLE, declaration, mContext.getLocation(declaration),
                            message);
                }
            }
        }

        private boolean checkSuppressLint(@NonNull Annotation node, @NonNull String id) {
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

        @NonNull
        private List<String> computeFieldNames(@NonNull Switch node, Iterable allowedValues) {
            List<String> list = Lists.newArrayList();
            for (Object o : allowedValues) {
                if (o instanceof ResolvedField) {
                    ResolvedField field = (ResolvedField) o;
                    // Only include class name if necessary
                    String name = field.getName();
                    ClassDeclaration clz = findSurroundingClass(node);
                    if (clz != null) {
                        ResolvedNode resolved = mContext.resolve(clz);
                        ResolvedClass containingClass = field.getContainingClass();
                        if (containingClass != null && !containingClass.equals(resolved)
                                && resolved instanceof ResolvedClass) {
                            if (Objects.equal(containingClass.getPackage(),
                                    ((ResolvedClass) resolved).getPackage())) {
                                name = containingClass.getSimpleName() + '.' + field.getName();
                            } else {
                                name = containingClass.getName() + '.' + field.getName();
                            }
                        }
                    }
                    list.add('`' + name + '`');
                }
            }
            Collections.sort(list);
            return list;
        }
    }

    /**
     * Given an error message produced by this lint detector for the {@link #SWITCH_TYPE_DEF} issue
     * type, returns the list of missing enum cases. <p> Intended for IDE quickfix implementations.
     *
     * @param errorMessage the error message associated with the error
     * @param format       the format of the error message
     * @return the list of enum cases, or null if not recognized
     */
    @Nullable
    public static List<String> getMissingCases(@NonNull String errorMessage,
            @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);

        String substring = findSubstring(errorMessage, " missing case ", null);
        if (substring == null) {
            substring = findSubstring(errorMessage, "expected one of: ", null);
        }
        if (substring != null) {
            return Splitter.on(",").trimResults().splitToList(substring);
        }

        return null;
    }

    /**
     * Returns the node to use as the scope for the given annotation node.
     * You can't annotate an annotation itself (with {@code @SuppressLint}), but
     * you should be able to place an annotation next to it, as a sibling, to only
     * suppress the error on this annotated element, not the whole surrounding class.
     */
    @NonNull
    private static Node getAnnotationScope(@NonNull Annotation node) {
        Node scope = getParentOfType(node,
              AnnotationDeclaration.class, true);
        if (scope == null) {
            scope = node;
        }
        return scope;
    }
}
