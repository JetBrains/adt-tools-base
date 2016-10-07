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

import static com.android.SdkConstants.SUPPORT_LIB_ARTIFACT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_BOOLEAN;
import static com.android.tools.lint.client.api.JavaParser.TYPE_BOOLEAN_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_BYTE_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_CHARACTER_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_DOUBLE_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_FLOAT_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_INT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_INTEGER_WRAPPER;
import static com.android.tools.lint.client.api.JavaParser.TYPE_LONG_WRAPPER;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Looks for performance issues in Java files, such as memory allocations during
 * drawing operations and using HashMap instead of SparseArray.
 */
public class JavaPerformanceDetector extends Detector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            JavaPerformanceDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Allocating objects during a paint method */
    public static final Issue PAINT_ALLOC = Issue.create(
            "DrawAllocation", //$NON-NLS-1$
            "Memory allocations within drawing code",

            "You should avoid allocating objects during a drawing or layout operation. These " +
            "are called frequently, so a smooth UI can be interrupted by garbage collection " +
            "pauses caused by the object allocations.\n" +
            "\n" +
            "The way this is generally handled is to allocate the needed objects up front " +
            "and to reuse them for each drawing operation.\n" +
            "\n" +
            "Some methods allocate memory on your behalf (such as `Bitmap.create`), and these " +
            "should be handled in the same way.",

            Category.PERFORMANCE,
            9,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Using HashMaps where SparseArray would be better */
    public static final Issue USE_SPARSE_ARRAY = Issue.create(
            "UseSparseArrays", //$NON-NLS-1$
            "HashMap can be replaced with SparseArray",

            "For maps where the keys are of type integer, it's typically more efficient to " +
            "use the Android `SparseArray` API. This check identifies scenarios where you might " +
            "want to consider using `SparseArray` instead of `HashMap` for better performance.\n" +
            "\n" +
            "This is *particularly* useful when the value types are primitives like ints, " +
            "where you can use `SparseIntArray` and avoid auto-boxing the values from `int` to " +
            "`Integer`.\n" +
            "\n" +
            "If you need to construct a `HashMap` because you need to call an API outside of " +
            "your control which requires a `Map`, you can suppress this warning using for " +
            "example the `@SuppressLint` annotation.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Using {@code new Integer()} instead of the more efficient {@code Integer.valueOf} */
    public static final Issue USE_VALUE_OF = Issue.create(
            "UseValueOf", //$NON-NLS-1$
            "Should use `valueOf` instead of `new`",

            "You should not call the constructor for wrapper classes directly, such as" +
            "`new Integer(42)`. Instead, call the `valueOf` factory method, such as " +
            "`Integer.valueOf(42)`. This will typically use less memory because common integers " +
            "such as 0 and 1 will share a single instance.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    static final String ON_MEASURE = "onMeasure";
    static final String ON_DRAW = "onDraw";
    static final String ON_LAYOUT = "onLayout";
    private static final String LAYOUT = "layout";
    private static final String HASH_MAP = "java.util.HashMap";
    private static final String SPARSE_ARRAY = "android.util.SparseArray";
    public static final String CLASS_CANVAS = "android.graphics.Canvas";

    /** Constructs a new {@link JavaPerformanceDetector} check */
    public JavaPerformanceDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<Class<? extends PsiElement>>(3);
        types.add(PsiNewExpression.class);
        types.add(PsiMethod.class);
        types.add(PsiMethodCallExpression.class);
        return types;
    }

    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new PerformanceVisitor(context);
    }

    private static class PerformanceVisitor extends JavaElementVisitor {
        private final JavaContext mContext;
        private final boolean mCheckMaps;
        private final boolean mCheckAllocations;
        private final boolean mCheckValueOf;
        /** Whether allocations should be "flagged" in the current method */
        private boolean mFlagAllocations;

        public PerformanceVisitor(JavaContext context) {
            mContext = context;

            mCheckAllocations = context.isEnabled(PAINT_ALLOC);
            mCheckMaps = context.isEnabled(USE_SPARSE_ARRAY);
            mCheckValueOf = context.isEnabled(USE_VALUE_OF);
        }

        @Override
        public void visitMethod(PsiMethod node) {
            mFlagAllocations = isBlockedAllocationMethod(node);
        }

        @Override
        public void visitNewExpression(PsiNewExpression node) {
            String typeName = null;
            PsiJavaCodeReferenceElement classReference = node.getClassReference();
            if (mCheckMaps || mCheckValueOf) {
                if (classReference != null) {
                    typeName = classReference.getQualifiedName();
                }
            }

            if (mCheckMaps) {
                // TODO: Should we handle factory method constructions of HashMaps as well,
                // e.g. via Guava? This is a bit trickier since we need to infer the type
                // arguments from the calling context.
                if (HASH_MAP.equals(typeName)) {
                    checkHashMap(node, classReference);
                } else if (SPARSE_ARRAY.equals(typeName)) {
                    checkSparseArray(node, classReference);
                }
            }

            if (mCheckValueOf) {
                if (typeName != null
                        && (typeName.equals(TYPE_INTEGER_WRAPPER)
                        || typeName.equals(TYPE_BOOLEAN_WRAPPER)
                        || typeName.equals(TYPE_FLOAT_WRAPPER)
                        || typeName.equals(TYPE_CHARACTER_WRAPPER)
                        || typeName.equals(TYPE_LONG_WRAPPER)
                        || typeName.equals(TYPE_DOUBLE_WRAPPER)
                        || typeName.equals(TYPE_BYTE_WRAPPER))
                        //&& node.astTypeReference().astParts().size() == 1
                        && node.getArgumentList() != null
                        && node.getArgumentList().getExpressions().length == 1) {
                    String argument = node.getArgumentList().getExpressions()[0].getText();
                    mContext.report(USE_VALUE_OF, node, mContext.getLocation(node), getUseValueOfErrorMessage(
                            typeName, argument));
                }
            }

            if (mFlagAllocations
                    && !(skipParentheses(node.getParent()) instanceof PsiThrowStatement)
                    && mCheckAllocations) {
                // Make sure we're still inside the method declaration that marked
                // mInDraw as true, in case we've left it and we're in a static
                // block or something:
                PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class);
                if (method != null && isBlockedAllocationMethod(method)
                        && !isLazilyInitialized(node)) {
                    reportAllocation(node);
                }
            }
        }

        private void reportAllocation(PsiElement node) {
            mContext.report(PAINT_ALLOC, node, mContext.getLocation(node),
                "Avoid object allocations during draw/layout operations (preallocate and " +
                "reuse instead)");
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression node) {
            if (!mFlagAllocations) {
                return;
            }
            PsiReferenceExpression expression = node.getMethodExpression();
            PsiElement qualifier = expression.getQualifier();
            if (qualifier == null) {
                return;
            }
            String methodName = expression.getReferenceName();
            if (methodName == null) {
                return;
            }
            // Look for forbidden methods
            if (methodName.equals("createBitmap")                              //$NON-NLS-1$
                    || methodName.equals("createScaledBitmap")) {              //$NON-NLS-1$
                PsiMethod method = node.resolveMethod();
                if (method != null && mContext.getEvaluator().isMemberInClass(method,
                        "android.graphics.Bitmap") && !isLazilyInitialized(node)) {
                    reportAllocation(node);
                }
            } else if (methodName.startsWith("decode")) {                      //$NON-NLS-1$
                // decodeFile, decodeByteArray, ...
                PsiMethod method = node.resolveMethod();
                if (method != null && mContext.getEvaluator().isMemberInClass(method,
                        "android.graphics.BitmapFactory") && !isLazilyInitialized(node)) {
                    reportAllocation(node);
                }
            } else if (methodName.equals("getClipBounds")) {                   //$NON-NLS-1$
                if (node.getArgumentList().getExpressions().length == 0) {
                    mContext.report(PAINT_ALLOC, node, mContext.getLocation(node),
                            "Avoid object allocations during draw operations: Use " +
                            "`Canvas.getClipBounds(Rect)` instead of `Canvas.getClipBounds()` " +
                            "which allocates a temporary `Rect`");
                }
            }
        }

        /**
         * Check whether the given invocation is done as a lazy initialization,
         * e.g. {@code if (foo == null) foo = new Foo();}.
         * <p>
         * This tries to also handle the scenario where the check is on some
         * <b>other</b> variable - e.g.
         * <pre>
         *    if (foo == null) {
         *        foo == init1();
         *        bar = new Bar();
         *    }
         * </pre>
         * or
         * <pre>
         *    if (!initialized) {
         *        initialized = true;
         *        bar = new Bar();
         *    }
         * </pre>
         */
        private static boolean isLazilyInitialized(PsiElement node) {
            PsiElement curr = node.getParent();
            while (curr != null) {
                if (curr instanceof PsiMethod) {
                    return false;
                } else if (curr instanceof PsiIfStatement) {
                    PsiIfStatement ifNode = (PsiIfStatement) curr;
                    // See if the if block represents a lazy initialization:
                    // compute all variable names seen in the condition
                    // (e.g. for "if (foo == null || bar != foo)" the result is "foo,bar"),
                    // and then compute all variables assigned to in the if body,
                    // and if there is an overlap, we'll consider the whole if block
                    // guarded (so lazily initialized and an allocation we won't complain
                    // about.)
                    List<String> assignments = new ArrayList<String>();
                    AssignmentTracker visitor = new AssignmentTracker(assignments);
                    if (ifNode.getThenBranch() != null) {
                        ifNode.getThenBranch().accept(visitor);
                    }
                    if (!assignments.isEmpty()) {
                        List<String> references = new ArrayList<String>();
                        addReferencedVariables(references, ifNode.getCondition());
                        if (!references.isEmpty()) {
                            SetView<String> intersection = Sets.intersection(
                                    new HashSet<String>(assignments),
                                    new HashSet<String>(references));
                            return !intersection.isEmpty();
                        }
                    }
                    return false;

                }
                curr = curr.getParent();
            }

            return false;
        }

        /** Adds any variables referenced in the given expression into the given list */
        private static void addReferencedVariables(
                @NonNull Collection<String> variables,
                @Nullable PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                PsiBinaryExpression binary = (PsiBinaryExpression) expression;
                addReferencedVariables(variables, binary.getLOperand());
                addReferencedVariables(variables, binary.getROperand());
            } else if (expression instanceof PsiPrefixExpression) {
                PsiPrefixExpression unary = (PsiPrefixExpression) expression;
                addReferencedVariables(variables, unary.getOperand());
            } else if (expression instanceof PsiParenthesizedExpression) {
                PsiParenthesizedExpression exp = (PsiParenthesizedExpression) expression;
                addReferencedVariables(variables, exp.getExpression());
            } else if (expression instanceof PsiIdentifier) {
                PsiIdentifier reference = (PsiIdentifier) expression;
                variables.add(reference.getText());
            } else if (expression instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) expression;
                PsiElement qualifier = ref.getQualifier();
                if (qualifier != null) {
                    if (qualifier instanceof PsiThisExpression ||
                            qualifier instanceof PsiSuperExpression) {
                        variables.add(ref.getReferenceName());
                    }
                } else {
                    variables.add(ref.getReferenceName());
                }
            }
        }

        /**
         * Returns whether the given method declaration represents a method
         * where allocating objects is not allowed for performance reasons
         */
        private boolean isBlockedAllocationMethod(
                @NonNull PsiMethod node) {
            JavaEvaluator evaluator = mContext.getEvaluator();
            return isOnDrawMethod(evaluator, node)
                    || isOnMeasureMethod(evaluator, node)
                    || isOnLayoutMethod(evaluator, node)
                    || isLayoutMethod(evaluator, node);
        }

        /**
         * Returns true if this method looks like it's overriding android.view.View's
         * {@code protected void onDraw(Canvas canvas)}
         */
        private static boolean isOnDrawMethod(
                @NonNull JavaEvaluator evaluator,
                @NonNull PsiMethod node) {
            return ON_DRAW.equals(node.getName()) && evaluator.parametersMatch(node, CLASS_CANVAS);
        }

        /**
         * Returns true if this method looks like it's overriding
         * android.view.View's
         * {@code protected void onLayout(boolean changed, int left, int top,
         *      int right, int bottom)}
         */
        private static boolean isOnLayoutMethod(
                @NonNull JavaEvaluator evaluator,
                @NonNull PsiMethod node) {
            return ON_LAYOUT.equals(node.getName()) && evaluator.parametersMatch(node,
                    TYPE_BOOLEAN, TYPE_INT, TYPE_INT, TYPE_INT, TYPE_INT);
        }

        /**
         * Returns true if this method looks like it's overriding android.view.View's
         * {@code protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)}
         */
        private static boolean isOnMeasureMethod(
                @NonNull JavaEvaluator evaluator,
                @NonNull PsiMethod node) {
            return ON_MEASURE.equals(node.getName()) && evaluator.parametersMatch(node,
                    TYPE_INT, TYPE_INT);
        }

        /**
         * Returns true if this method looks like it's overriding android.view.View's
         * {@code public void layout(int l, int t, int r, int b)}
         */
        private static boolean isLayoutMethod(
                @NonNull JavaEvaluator evaluator,
                @NonNull PsiMethod node) {
            return LAYOUT.equals(node.getName()) && evaluator.parametersMatch(node,
                    TYPE_INT, TYPE_INT, TYPE_INT, TYPE_INT);
        }

        /**
         * Checks whether the given constructor call and type reference refers
         * to a HashMap constructor call that is eligible for replacement by a
         * SparseArray call instead
         */
        private void checkHashMap(
                @NonNull PsiNewExpression node,
                @NonNull PsiJavaCodeReferenceElement reference) {
            PsiType[] types = reference.getTypeParameters();
            if (types.length == 2) {
                PsiType first = types[0];
                String typeName = first.getCanonicalText();
                int minSdk = mContext.getMainProject().getMinSdk();
                if (TYPE_INTEGER_WRAPPER.equals(typeName) || TYPE_BYTE_WRAPPER.equals(typeName)) {
                    String valueType = types[1].getCanonicalText();
                    if (valueType.equals(TYPE_INTEGER_WRAPPER)) {
                        mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                            "Use new `SparseIntArray(...)` instead for better performance");
                    } else if (valueType.equals(TYPE_LONG_WRAPPER) && minSdk >= 18) {
                        mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                                "Use `new SparseLongArray(...)` instead for better performance");
                    } else if (valueType.equals(TYPE_BOOLEAN_WRAPPER)) {
                        mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                                "Use `new SparseBooleanArray(...)` instead for better performance");
                    } else {
                        mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                            String.format(
                                "Use `new SparseArray<%1$s>(...)` instead for better performance",
                              valueType.substring(valueType.lastIndexOf('.') + 1)));
                    }
                } else if (TYPE_LONG_WRAPPER.equals(typeName) && (minSdk >= 16 ||
                        Boolean.TRUE == mContext.getMainProject().dependsOn(
                                SUPPORT_LIB_ARTIFACT))) {
                    boolean useBuiltin = minSdk >= 16;
                    String message = useBuiltin ?
                            "Use `new LongSparseArray(...)` instead for better performance" :
                            "Use `new android.support.v4.util.LongSparseArray(...)` instead for better performance";
                    mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                            message);
                }
            }
        }

        private void checkSparseArray(
                @NonNull PsiNewExpression node,
                @NonNull PsiJavaCodeReferenceElement reference) {
            PsiType[] types = reference.getTypeParameters();
            if (types.length == 1) {
                String valueType = types[0].getCanonicalText();
                if (valueType.equals(TYPE_INTEGER_WRAPPER)) {
                    mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                        "Use `new SparseIntArray(...)` instead for better performance");
                } else if (valueType.equals(TYPE_BOOLEAN_WRAPPER)) {
                    mContext.report(USE_SPARSE_ARRAY, node, mContext.getLocation(node),
                            "Use `new SparseBooleanArray(...)` instead for better performance");
                }
            }
        }
    }

    private static String getUseValueOfErrorMessage(String typeName, String argument) {
        // Keep in sync with {@link #getReplacedType} below
        return String.format("Use `%1$s.valueOf(%2$s)` instead",
                typeName.substring(typeName.lastIndexOf('.') + 1), argument);
    }

    /**
     * For an error message for an {@link #USE_VALUE_OF} issue reported by this detector,
     * returns the type being replaced. Intended to use for IDE quickfix implementations.
     */
    @SuppressWarnings("unused") // Used by the IDE
    @Nullable
    public static String getReplacedType(@NonNull String message, @NonNull TextFormat format) {
        message = format.toText(message);
        int index = message.indexOf('.');
        if (index != -1 && message.startsWith("Use ")) {
            return message.substring(4, index);
        }
        return null;
    }

    /** Visitor which records variable names assigned into */
    private static class AssignmentTracker extends JavaRecursiveElementVisitor {
        private final Collection<String> mVariables;

        public AssignmentTracker(Collection<String> variables) {
            mVariables = variables;
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression node) {
            super.visitAssignmentExpression(node);

            PsiExpression left = node.getLExpression();
            if (left instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) left;
                if (ref.getQualifier() instanceof PsiThisExpression ||
                        ref.getQualifier() instanceof PsiSuperExpression) {
                    mVariables.add(ref.getReferenceName());
                } else {
                    mVariables.add(ref.getText());
                }
            } else if (left instanceof PsiIdentifier) {
                mVariables.add(left.getText());
            }
        }
    }
}
