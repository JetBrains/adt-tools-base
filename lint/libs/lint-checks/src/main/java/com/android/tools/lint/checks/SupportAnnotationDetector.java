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

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.resources.ResourceType.COLOR;
import static com.android.resources.ResourceType.DRAWABLE;
import static com.android.resources.ResourceType.MIPMAP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedAnnotation;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import lombok.ast.ArrayCreation;
import lombok.ast.ArrayInitializer;
import lombok.ast.AstVisitor;
import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.Expression;
import lombok.ast.ExpressionStatement;
import lombok.ast.FloatingPointLiteral;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.InlineIfExpression;
import lombok.ast.IntegralLiteral;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.NullLiteral;
import lombok.ast.Select;
import lombok.ast.StringLiteral;
import lombok.ast.UnaryExpression;
import lombok.ast.UnaryOperator;
import lombok.ast.VariableReference;

/**
 * Looks up annotations on method calls and enforces the various things they
 * express, e.g. for {@code @CheckReturn} it makes sure the return value is used,
 * for {@code ColorInt} it ensures that a proper color integer is passed in, etc.
 *
 * TODO: Throw in some annotation usage checks here too; e.g. specifying @Size without parameters,
 * specifying toInclusive without setting to, combining @ColorInt with any @ResourceTypeRes,
 * using @CheckResult on a void method, etc.
 */
public class SupportAnnotationDetector extends Detector implements Detector.JavaScanner {

    public static final Implementation IMPLEMENTATION
            = new Implementation(SupportAnnotationDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Method result should be used */
    public static final Issue RANGE = Issue.create(
        "Range", //$NON-NLS-1$
        "Outside Range",

        "Some parameters are required to in a particular numerical range; this check " +
        "makes sure that arguments passed fall within the range. For arrays, Strings " +
        "and collections this refers to the size or length.",

        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        IMPLEMENTATION);

    /**
     * Attempting to set a resource id as a color
     */
    public static final Issue RESOURCE_TYPE = Issue.create(
        "ResourceType", //$NON-NLS-1$
        "Wrong Resource Type",

        "Ensures that resource id's passed to APIs are of the right type; for example, " +
        "calling `Resources.getColor(R.string.name)` is wrong.",

        Category.CORRECTNESS,
        7,
        Severity.FATAL,
        IMPLEMENTATION);

    /** Attempting to set a resource id as a color */
    public static final Issue COLOR_USAGE = Issue.create(
        "ResourceAsColor", //$NON-NLS-1$
        "Should pass resolved color instead of resource id",

        "Methods that take a color in the form of an integer should be passed " +
        "an RGB triple, not the actual color resource id. You must call " +
        "`getResources().getColor(resource)` to resolve the actual color value first.",

        Category.CORRECTNESS,
        7,
        Severity.ERROR,
        IMPLEMENTATION);

    /** Passing the wrong constant to an int or String method */
    public static final Issue TYPE_DEF = Issue.create(
        "WrongConstant", //$NON-NLS-1$
        "Incorrect constant",

        "Ensures that when parameter in a method only allows a specific set " +
        "of constants, calls obey those rules.",

        Category.SECURITY,
        6,
        Severity.ERROR,
        IMPLEMENTATION);

    /** Method result should be used */
    public static final Issue CHECK_RESULT = Issue.create(
        "CheckResult", //$NON-NLS-1$
        "Ignoring results",

        "Some methods have no side effects, an calling them without doing something " +
        "without the result is suspicious. ",

        Category.CORRECTNESS,
        6,
        Severity.WARNING,
            IMPLEMENTATION);

    /** Failing to enforce security by just calling check permission */
    public static final Issue CHECK_PERMISSION = Issue.create(
        "UseCheckPermission", //$NON-NLS-1$
        "Using the result of check permission calls",

        "You normally want to use the result of checking a permission; these methods " +
        "return whether the permission is held; they do not throw an error if the permission " +
        "is not granted. Code which does not do anything with the return value probably " +
        "meant to be calling the enforce methods instead, e.g. rather than " +
        "`Context#checkCallingPermission` it should call `Context#enforceCallingPermission`.",

        Category.SECURITY,
        6,
        Severity.WARNING,
        IMPLEMENTATION);

    public static final String CHECK_RESULT_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "CheckResult"; //$NON-NLS-1$
    public static final String COLOR_INT_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "ColorInt"; //$NON-NLS-1$
    public static final String INT_RANGE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "IntRange"; //$NON-NLS-1$
    public static final String FLOAT_RANGE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "FloatRange"; //$NON-NLS-1$
    public static final String SIZE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "Size"; //$NON-NLS-1$

    public static final String RES_SUFFIX = "Res";       //$NON-NLS-1$
    public static final String ATTR_SUGGEST = "suggest"; //$NON-NLS-1$
    public static final String ATTR_TO = "to";
    public static final String ATTR_FROM = "from";
    public static final String ATTR_FROM_INCLUSIVE = "fromInclusive";
    public static final String ATTR_TO_INCLUSIVE = "toInclusive";
    public static final String ATTR_MULTIPLE = "multiple";
    public static final String ATTR_MIN = "min";
    public static final String ATTR_MAX = "max";

    /**
     * Constructs a new {@link SupportAnnotationDetector} check
     */
    public SupportAnnotationDetector() {
    }

    private static void checkMethodAnnotation(
            @NonNull JavaContext context,
            @NonNull MethodInvocation node,
            @NonNull ResolvedAnnotation annotation) {
        String signature = annotation.getSignature();
        if (CHECK_RESULT_ANNOTATION.equals(signature)
                || signature.endsWith(".CheckReturnValue")) { // support findbugs annotation too
            checkResult(context, node, annotation);
        }
    }

    private static void checkParameterAnnotation(
            @NonNull JavaContext context,
            @NonNull Node argument,
            @NonNull ResolvedAnnotation annotation) {
        String signature = annotation.getSignature();

        if (COLOR_INT_ANNOTATION.equals(signature)) {
            checkColor(context, argument);
        } else if (signature.equals(INT_RANGE_ANNOTATION)) {
            checkIntRange(context, annotation, argument);
        } else if (signature.equals(FLOAT_RANGE_ANNOTATION)) {
            checkFloatRange(context, annotation, argument);
        } else if (signature.equals(SIZE_ANNOTATION)) {
            checkSize(context, annotation, argument);
        } else {
            // We only run @IntDef, @StringDef and @<Type>Res checks if we're not
            // running inside Android Studio / IntelliJ where there are already inspections
            // covering the same warnings (using IntelliJ's own data flow analysis); we
            // don't want to (a) create redundant warnings or (b) work harder than we
            // have to
            if (signature.equals(INT_DEF_ANNOTATION)) {
                boolean flag = annotation.getValue(TYPE_DEF_FLAG_ATTRIBUTE) == Boolean.TRUE;
                checkTypeDefConstant(context, annotation, argument, flag);
            } else if (signature.equals(STRING_DEF_ANNOTATION)) {
                checkTypeDefConstant(context, annotation, argument, false);
            } else if (signature.endsWith(RES_SUFFIX)) {
                String typeString = signature.substring(SUPPORT_ANNOTATIONS_PREFIX.length(),
                        signature.length() - RES_SUFFIX.length()).toLowerCase(Locale.US);
                ResourceType type = ResourceType.getEnum(typeString);
                if (type != null) {
                    checkResourceType(context, argument, type);
                } else if (typeString.equals("any")) { // @AnyRes
                    checkResourceType(context, argument, null);
                }
            }
        }
    }

    private static void checkColor(@NonNull JavaContext context, @NonNull Node argument) {
        if (argument instanceof InlineIfExpression) {
            InlineIfExpression expression = (InlineIfExpression) argument;
            checkColor(context, expression.astIfTrue());
            checkColor(context, expression.astIfFalse());
            return;
        }

        ResourceType type = getResourceType(argument);

        if (type == ResourceType.COLOR) {
            String message = String.format(
                    "Should pass resolved color instead of resource id here: " +
                            "`getResources().getColor(%1$s)`", argument.toString());
            context.report(COLOR_USAGE, argument, context.getLocation(argument), message);
        }
    }

    private static void checkResult(@NonNull JavaContext context, @NonNull MethodInvocation node,
            @NonNull ResolvedAnnotation annotation) {
        if (node.getParent() instanceof ExpressionStatement) {
            String methodName = node.astName().astValue();
            Object suggested = annotation.getValue(ATTR_SUGGEST);

            // Failing to check permissions is a potential security issue (and had an existing
            // dedicated issue id before which people may already have configured with a
            // custom severity in their LintOptions etc) so continue to use that issue
            // (which also has category Security rather than Correctness) for these:
            Issue issue = CHECK_RESULT;
            if (methodName.startsWith("check") && methodName.contains("Permission")) {
                issue = CHECK_PERMISSION;
            }

            String message = String.format("The result of `%1$s` is not used",
                    methodName);
            if (suggested != null) {
                // TODO: Resolve suggest attribute (e.g. prefix annotation class if it starts
                // with "#" etc?
                message = String.format(
                        "The result of `%1$s` is not used; did you mean to call `%2$s`?",
                        methodName, suggested.toString());
            }
            context.report(issue, node, context.getLocation(node), message);
        }
    }


    private static boolean isNumber(@NonNull Node argument) {
        return argument instanceof IntegralLiteral || argument instanceof UnaryExpression
                && ((UnaryExpression) argument).astOperator() == UnaryOperator.UNARY_MINUS
                && ((UnaryExpression) argument).astOperand() instanceof IntegralLiteral;
    }

    private static boolean isZero(@NonNull Node argument) {
        return argument instanceof IntegralLiteral
                && ((IntegralLiteral) argument).astIntValue() == 0;
    }

    private static boolean isMinusOne(@NonNull Node argument) {
        return argument instanceof UnaryExpression
                && ((UnaryExpression) argument).astOperator() == UnaryOperator.UNARY_MINUS
                && ((UnaryExpression) argument).astOperand() instanceof IntegralLiteral
                && ((IntegralLiteral) ((UnaryExpression) argument).astOperand()).astIntValue()
                == 1;
    }

    private static void checkResourceType(
            @NonNull JavaContext context,
            @NonNull Node argument,
            @Nullable ResourceType expectedType) {
        ResourceType actual = getResourceType(argument);
        if (actual == null && (!isNumber(argument) || isZero(argument) || isMinusOne(argument)) ) {
            // Unknown type: perform flow analysis later
            return;
        } else if (actual != null && (expectedType == null
                || expectedType == actual
                || expectedType == DRAWABLE && (actual == COLOR || actual == MIPMAP))) {
            return;
        }

        String message;
        if (expectedType != null) {
            message = String.format(
                    "Expected resource of type %1$s", expectedType.getName());
        } else {
            message = "Expected resource identifier (`R`.type.`name`)";
        }
        context.report(RESOURCE_TYPE, argument, context.getLocation(argument), message);
    }

    @Nullable
    private static ResourceType getResourceType(@NonNull Node argument) {
        if (argument instanceof Select) {
            Select node = (Select)argument;
            if (node.astOperand() instanceof Select) {
                Select select = (Select) node.astOperand();
                if (select.astOperand() instanceof Select) { // android.R....
                    Select innerSelect = (Select) select.astOperand();
                    if (innerSelect.astIdentifier().astValue().equals(R_CLASS)) {
                        String type = select.astIdentifier().astValue();
                        return ResourceType.getEnum(type);
                    }
                }
                if (select.astOperand() instanceof VariableReference) {
                    VariableReference reference = (VariableReference) select.astOperand();
                    if (reference.astIdentifier().astValue().equals(R_CLASS)) {
                        String type = select.astIdentifier().astValue();
                        return ResourceType.getEnum(type);
                    }
                }
            }

            // Arbitrary packages -- android.R.type.name, foo.bar.R.type.name
            if (node.astIdentifier().astValue().equals(R_CLASS)) {
                Node parent = node.getParent();
                if (parent instanceof Select) {
                    Node grandParent = parent.getParent();
                    if (grandParent instanceof Select) {
                        Select select = (Select) grandParent;
                        Expression typeOperand = select.astOperand();
                        if (typeOperand instanceof Select) {
                            Select typeSelect = (Select) typeOperand;
                            String type = typeSelect.astIdentifier().astValue();
                            return ResourceType.getEnum(type);
                        }
                    }
                }
            }
        }

        return null;
    }

    private static void checkIntRange(
            @NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation,
            @NonNull Node argument) {
        long value;
        if (argument instanceof IntegralLiteral) {
            IntegralLiteral literal = (IntegralLiteral) argument;
            value = literal.astLongValue();
        } else if (argument instanceof FloatingPointLiteral) {
            FloatingPointLiteral literal = (FloatingPointLiteral) argument;
            value = (int)literal.astFloatValue();
        } else {
            // No flow analysis for this check yet, only checking literals passed in as parameters
            return;
        }
        long from = getLongAttribute(annotation, ATTR_FROM, Long.MIN_VALUE);
        long to = getLongAttribute(annotation, ATTR_TO, Long.MAX_VALUE);

        String message = getIntRangeError(value, from, to);
        if (message != null) {
            context.report(RANGE, argument, context.getLocation(argument), message);
        }
    }

    /**
     * Checks whether a given integer value is in the allowed range, and if so returns
     * null; otherwise returns a suitable error message.
     */
    private static String getIntRangeError(long value, long from, long to) {
        String message = null;
        if (value < from || value > to) {
            StringBuilder sb = new StringBuilder(20);
            if (value < from) {
                sb.append("Value must be \u2265 ");
                sb.append(Long.toString(from));
            } else {
                assert value > to;
                sb.append("Value must be \u2264 ");
                sb.append(Long.toString(to));
            }
            sb.append(" (was ").append(value).append(')');
            message = sb.toString();
        }
        return message;
    }

    private static void checkFloatRange(
            @NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation,
            @NonNull Node argument) {
        double value;
        if (argument instanceof IntegralLiteral) {
            IntegralLiteral literal = (IntegralLiteral) argument;
            value = literal.astIntValue();
        } else if (argument instanceof FloatingPointLiteral) {
            FloatingPointLiteral literal = (FloatingPointLiteral) argument;
            value = literal.astDoubleValue();
        } else {
            // No flow analysis for this check yet, only checking literals passed in as parameters
            return;
        }
        double from = getDoubleAttribute(annotation, ATTR_FROM, Double.NEGATIVE_INFINITY);
        double to = getDoubleAttribute(annotation, ATTR_TO, Double.POSITIVE_INFINITY);
        boolean fromInclusive = getBoolean(annotation, ATTR_FROM_INCLUSIVE, true);
        boolean toInclusive = getBoolean(annotation, ATTR_TO_INCLUSIVE, true);

        String message = getFloatRangeError(value, from, to, fromInclusive, toInclusive);
        if (message != null) {
            context.report(RANGE, argument, context.getLocation(argument), message);
        }
    }

    /**
     * Checks whether a given floating point value is in the allowed range, and if so returns
     * null; otherwise returns a suitable error message.
     */
    @Nullable
    private static String getFloatRangeError(double value, double from, double to,
            boolean fromInclusive, boolean toInclusive) {
        if (!((fromInclusive && value >= from || !fromInclusive && value > from) &&
                (toInclusive && value <= to || !toInclusive && value < to))) {
            StringBuilder sb = new StringBuilder(20);
            if (from != Double.NEGATIVE_INFINITY) {
                if (to != Double.POSITIVE_INFINITY) {
                    if (fromInclusive && value < from || !fromInclusive && value <= from) {
                        sb.append("Value must be ");
                        if (fromInclusive) {
                            sb.append('\u2265'); // >= sign
                        } else {
                            sb.append('>');
                        }
                        sb.append(' ');
                        sb.append(Double.toString(from));
                    } else {
                        assert toInclusive && value > to || !toInclusive && value >= to;
                        sb.append("Value must be ");
                        if (toInclusive) {
                            sb.append('\u2264'); // <= sign
                        } else {
                            sb.append('<');
                        }
                        sb.append(' ');
                        sb.append(Double.toString(to));
                    }
                } else {
                    sb.append("Value must be ");
                    if (fromInclusive) {
                        sb.append('\u2265'); // >= sign
                    } else {
                        sb.append('>');
                    }
                    sb.append(' ');
                    sb.append(Double.toString(from));
                }
            } else if (to != Double.POSITIVE_INFINITY) {
                sb.append("Value must be ");
                if (toInclusive) {
                    sb.append('\u2264'); // <= sign
                } else {
                    sb.append('<');
                }
                sb.append(' ');
                sb.append(Double.toString(to));
            }
            sb.append(" (was ").append(value).append(')');
            return sb.toString();
        }
        return null;
    }

    private static void checkSize(
            @NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation,
            @NonNull Node argument) {
        int actual;
        if (argument instanceof StringLiteral) {
            // Check string length
            StringLiteral literal = (StringLiteral) argument;
            String s = literal.astValue();
            actual = s.length();
        } else if (argument instanceof ArrayCreation) {
            ArrayCreation literal = (ArrayCreation) argument;
            ArrayInitializer initializer = literal.astInitializer();
            if (initializer == null) {
                return;
            }
            actual = initializer.astExpressions().size();
        } else {
            // TODO: Collections syntax, e.g. Arrays.asList => param count, emptyList=0, singleton=1, etc
            // TODO: Flow analysis
            // No flow analysis for this check yet, only checking literals passed in as parameters
            return;
        }
        long exact = getLongAttribute(annotation, ATTR_VALUE, -1);
        long min = getLongAttribute(annotation, ATTR_MIN, Long.MIN_VALUE);
        long max = getLongAttribute(annotation, ATTR_MAX, Long.MAX_VALUE);
        long multiple = getLongAttribute(annotation, ATTR_MULTIPLE, 1);

        String unit;
        boolean isString = argument instanceof StringLiteral;
        if (isString) {
            unit = "length";
        } else {
            unit = "size";
        }
        String message = getSizeError(actual, exact, min, max, multiple, unit);
        if (message != null) {
            context.report(RANGE, argument, context.getLocation(argument), message);
        }
    }

    /**
     * Checks whether a given size follows the given constraints, and if so returns
     * null; otherwise returns a suitable error message.
     */
    private static String getSizeError(long actual, long exact, long min, long max, long multiple,
            @NonNull String unit) {
        String message = null;
        if (exact != -1) {
            if (exact != actual) {
                message = String.format("Expected %1$s %2$d (was %3$d)",
                        unit, exact, actual);
            }
        } else if (actual < min || actual > max) {
            StringBuilder sb = new StringBuilder(20);
            if (actual < min) {
                sb.append("Expected ").append(unit).append(" \u2265 ");
                sb.append(Long.toString(min));
            } else {
                assert actual > max;
                sb.append("Expected ").append(unit).append(" \u2264 ");
                sb.append(Long.toString(max));
            }
            sb.append(" (was ").append(actual).append(')');
            message = sb.toString();
        } else if (actual % multiple != 0) {
            message = String.format("Expected %1$s to be a multiple of %2$d (was %3$d "
                            + "and should be either %4$d or %5$d)",
                    unit, multiple, actual, (actual / multiple) * multiple,
                    (actual / multiple + 1) * multiple);
        }
        return message;
    }

    private static void checkTypeDefConstant(
            @NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation,
            @NonNull Node argument,
            boolean flag) {
        if (argument instanceof NullLiteral) {
            // Accepted for @StringDef
            return;
        }

        if (argument instanceof StringLiteral) {
            StringLiteral string = (StringLiteral) argument;
            checkTypeDefConstant(context, annotation, argument, false, string.astValue());
        } else if (argument instanceof IntegralLiteral) {
            IntegralLiteral literal = (IntegralLiteral) argument;
            int value = literal.astIntValue();
            if (flag && value == 0) {
                // Accepted for a flag @IntDef
                return;
            }
            checkTypeDefConstant(context, annotation, argument, flag, value);
        } else if (isMinusOne(argument)) {
            // -1 is accepted unconditionally for flags
            if (!flag) {
                reportTypeDef(context, annotation, argument);
            }
        } else if (argument instanceof InlineIfExpression) {
            InlineIfExpression expression = (InlineIfExpression) argument;
            if (expression.astIfTrue() != null) {
                checkTypeDefConstant(context, annotation, expression.astIfTrue(), flag);
            }
            if (expression.astIfFalse() != null) {
                checkTypeDefConstant(context, annotation, expression.astIfFalse(), flag);
            }
        } else if (argument instanceof UnaryExpression) {
            UnaryExpression expression = (UnaryExpression) argument;
            UnaryOperator operator = expression.astOperator();
            if (flag) {
                checkTypeDefConstant(context, annotation, expression.astOperand(), true);
            } else if (operator == UnaryOperator.BINARY_NOT) {
                context.report(TYPE_DEF, expression, context.getLocation(expression),
                        "Flag not allowed here");
            }
        } else if (argument instanceof BinaryExpression) {
            // If it's ?: then check both the if and else clauses
            BinaryExpression expression = (BinaryExpression) argument;
            if (flag) {
                checkTypeDefConstant(context, annotation, expression.astLeft(), true);
                checkTypeDefConstant(context, annotation, expression.astRight(), true);
            } else {
                BinaryOperator operator = expression.astOperator();
                if (operator == BinaryOperator.BITWISE_AND
                        || operator == BinaryOperator.BITWISE_OR
                        || operator == BinaryOperator.BITWISE_XOR) {
                    context.report(TYPE_DEF, expression, context.getLocation(expression),
                            "Flag not allowed here");
                }
            }
        } else {
            ResolvedNode resolved = context.resolve(argument);
            if (resolved instanceof JavaParser.ResolvedField) {
                checkTypeDefConstant(context, annotation, argument, flag, resolved);
            } else if (resolved instanceof JavaParser.ResolvedVariable) {
                // Perform value flow analysis to see what values have flown to this
                // variable
                // TODO:
            }
        }
    }

    private static void checkTypeDefConstant(@NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation, @NonNull Node argument,
            boolean flag, Object value) {
        Object allowed = annotation.getValue();
        if (allowed instanceof Object[]) {
            Object[] allowedValues = (Object[]) allowed;
            for (Object o : allowedValues) {
                if (o.equals(value)) {
                    return;
                }
            }
            reportTypeDef(context, argument, flag, allowedValues);
        }
    }

    private static void reportTypeDef(@NonNull JavaContext context,
            @NonNull ResolvedAnnotation annotation, @NonNull Node argument) {
        Object allowed = annotation.getValue();
        if (allowed instanceof Object[]) {
            Object[] allowedValues = (Object[]) allowed;
            reportTypeDef(context, argument, false, allowedValues);
        }
    }

    private static void reportTypeDef(@NonNull JavaContext context, @NonNull Node node,
            boolean flag, @NonNull Object[] allowedValues) {
        String values = listAllowedValues(allowedValues);
        String message;
        if (flag) {
            message = "Must be one or more of: " + values;
        } else {
            message = "Must be one of: " + values;
        }
        context.report(TYPE_DEF, node, context.getLocation(node), message);
    }

    private static String listAllowedValues(@NonNull Object[] allowedValues) {
        StringBuilder sb = new StringBuilder();
        for (Object allowedValue : allowedValues) {
            String s;
            if (allowedValue instanceof Integer) {
                s = allowedValue.toString();
            } else if (allowedValue instanceof ResolvedNode) {
                ResolvedNode node = (ResolvedNode) allowedValue;
                if (node instanceof JavaParser.ResolvedField) {
                    JavaParser.ResolvedField field = (JavaParser.ResolvedField) node;
                    String containingClassName = field.getContainingClassName();
                    containingClassName = containingClassName.substring(containingClassName.lastIndexOf('.') + 1);
                    s = containingClassName + "." + field.getName();
                } else {
                    s = node.getSignature();
                }
            } else {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static double getDoubleAttribute(@NonNull ResolvedAnnotation annotation,
            @NonNull String name, double defaultValue) {
        Object value = annotation.getValue(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return defaultValue;
    }

    private static long getLongAttribute(@NonNull ResolvedAnnotation annotation,
            @NonNull String name, long defaultValue) {
        Object value = annotation.getValue(name);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return defaultValue;
    }

    private static boolean getBoolean(@NonNull ResolvedAnnotation annotation,
            @NonNull String name, boolean defaultValue) {
        Object value = annotation.getValue(name);
        if (value instanceof Boolean) {
            return ((Boolean) value);
        }

        return defaultValue;
    }

    @Nullable
    static ResolvedAnnotation getRelevantAnnotation(@NonNull ResolvedAnnotation annotation) {
        String signature = annotation.getSignature();
        if (signature.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            // Bail on the nullness annotations early since they're the most commonly
            // defined ones. They're not analyzed in lint yet.
            if (signature.endsWith(".Nullable") || signature.endsWith(".NonNull")) {
                return null;
            }


            return annotation;
        }

        if (signature.startsWith("java.")) {
            // @Override, @SuppressWarnings etc. Ignore
            return null;
        }

        // Special case @IntDef and @StringDef: These are used on annotations
        // themselves. For example, you create a new annotation named @foo.bar.Baz,
        // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
        // Here we want to map from @foo.bar.Baz to the corresponding int def.
        // Don't need to compute this if performing @IntDef or @StringDef lookup
        ResolvedClass type = annotation.getClassType();
        if (type != null) {
            for (ResolvedAnnotation inner : type.getAnnotations()) {
                if (inner.matches(INT_DEF_ANNOTATION)
                        || inner.matches(STRING_DEF_ANNOTATION)) {
                    return inner;
                }
            }
        }

        return null;
    }

    // ---- Implements JavaScanner ----

    @Override
    public
    List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(MethodInvocation.class);
    }

    @Nullable
    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new CallVisitor(context);
    }

    private static class CallVisitor extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public CallVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitMethodInvocation(@NonNull MethodInvocation call) {
            ResolvedNode resolved = mContext.resolve(call);
            if (resolved instanceof ResolvedMethod) {
                ResolvedMethod method = (ResolvedMethod) resolved;
                Iterable<ResolvedAnnotation> annotations = method.getAnnotations();
                for (ResolvedAnnotation annotation : annotations) {
                    annotation = getRelevantAnnotation(annotation);
                    if (annotation != null) {
                        checkMethodAnnotation(mContext, call, annotation);
                    }
                }

                Iterator<Expression> arguments = call.astArguments().iterator();
                for (int i = 0, n = method.getArgumentCount();
                        i < n && arguments.hasNext();
                        i++) {
                    Expression argument = arguments.next();

                    annotations = method.getParameterAnnotations(i);
                    for (ResolvedAnnotation annotation : annotations) {
                        annotation = getRelevantAnnotation(annotation);
                        if (annotation != null) {
                            checkParameterAnnotation(mContext, argument, annotation);
                        }
                    }
                }
            }

            return false;
        }
    }
}
