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

import static com.android.SdkConstants.CLASS_CONTENTPROVIDER;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for missing {@code recycle} calls on resources that encourage it, and
 * for missing {@code commit} calls on FragmentTransactions, etc.
 */
public class CleanupDetector extends Detector implements JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            CleanupDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Problems with missing recycle calls */
    public static final Issue RECYCLE_RESOURCE = Issue.create(
        "Recycle", //$NON-NLS-1$
        "Missing `recycle()` calls",

        "Many resources, such as TypedArrays, VelocityTrackers, etc., " +
        "should be recycled (with a `recycle()` call) after use. This lint check looks " +
        "for missing `recycle()` calls.",

        Category.PERFORMANCE,
        7,
        Severity.WARNING,
            IMPLEMENTATION);

    /** Problems with missing commit calls. */
    public static final Issue COMMIT_FRAGMENT = Issue.create(
            "CommitTransaction", //$NON-NLS-1$
            "Missing `commit()` calls",

            "After creating a `FragmentTransaction`, you typically need to commit it as well",

            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            IMPLEMENTATION);

    /** The main issue discovered by this detector */
    public static final Issue SHARED_PREF = Issue.create(
            "CommitPrefEdits", //$NON-NLS-1$
            "Missing `commit()` on `SharedPreference` editor",

            "After calling `edit()` on a `SharedPreference`, you must call `commit()` " +
            "or `apply()` on the editor to save the results.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    CleanupDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    // Target method names
    private static final String RECYCLE = "recycle";                                  //$NON-NLS-1$
    private static final String RELEASE = "release";                                  //$NON-NLS-1$
    private static final String OBTAIN = "obtain";                                    //$NON-NLS-1$
    private static final String SHOW = "show";                                        //$NON-NLS-1$
    private static final String ACQUIRE_CPC = "acquireContentProviderClient";         //$NON-NLS-1$
    private static final String OBTAIN_NO_HISTORY = "obtainNoHistory";                //$NON-NLS-1$
    private static final String OBTAIN_ATTRIBUTES = "obtainAttributes";               //$NON-NLS-1$
    private static final String OBTAIN_TYPED_ARRAY = "obtainTypedArray";              //$NON-NLS-1$
    private static final String OBTAIN_STYLED_ATTRIBUTES = "obtainStyledAttributes";  //$NON-NLS-1$
    private static final String BEGIN_TRANSACTION = "beginTransaction";               //$NON-NLS-1$
    private static final String COMMIT = "commit";                                    //$NON-NLS-1$
    private static final String COMMIT_NOW = "commitNow";                             //$NON-NLS-1$
    private static final String APPLY = "apply";                                      //$NON-NLS-1$
    private static final String COMMIT_ALLOWING_LOSS = "commitAllowingStateLoss";     //$NON-NLS-1$
    private static final String QUERY = "query";                                      //$NON-NLS-1$
    private static final String RAW_QUERY = "rawQuery";                               //$NON-NLS-1$
    private static final String QUERY_WITH_FACTORY = "queryWithFactory";              //$NON-NLS-1$
    private static final String RAW_QUERY_WITH_FACTORY = "rawQueryWithFactory";       //$NON-NLS-1$
    private static final String CLOSE = "close";                                      //$NON-NLS-1$
    private static final String EDIT = "edit";                                        //$NON-NLS-1$

    private static final String MOTION_EVENT_CLS = "android.view.MotionEvent";        //$NON-NLS-1$
    private static final String PARCEL_CLS = "android.os.Parcel";                     //$NON-NLS-1$
    private static final String VELOCITY_TRACKER_CLS = "android.view.VelocityTracker";//$NON-NLS-1$
    private static final String DIALOG_FRAGMENT = "android.app.DialogFragment";       //$NON-NLS-1$
    private static final String DIALOG_V4_FRAGMENT =
            "android.support.v4.app.DialogFragment";                                  //$NON-NLS-1$
    private static final String FRAGMENT_MANAGER_CLS = "android.app.FragmentManager"; //$NON-NLS-1$
    private static final String FRAGMENT_MANAGER_V4_CLS =
            "android.support.v4.app.FragmentManager";                                 //$NON-NLS-1$
    private static final String FRAGMENT_TRANSACTION_CLS =
            "android.app.FragmentTransaction";                                        //$NON-NLS-1$
    private static final String FRAGMENT_TRANSACTION_V4_CLS =
            "android.support.v4.app.FragmentTransaction";                             //$NON-NLS-1$

    public static final String SURFACE_CLS = "android.view.Surface";
    public static final String SURFACE_TEXTURE_CLS = "android.graphics.SurfaceTexture";

    public static final String CONTENT_PROVIDER_CLIENT_CLS
            = "android.content.ContentProviderClient";

    public static final String CONTENT_RESOLVER_CLS = "android.content.ContentResolver";

    @SuppressWarnings("SpellCheckingInspection")
    public static final String SQLITE_DATABASE_CLS = "android.database.sqlite.SQLiteDatabase";
    public static final String CURSOR_CLS = "android.database.Cursor";

    public static final String ANDROID_CONTENT_SHARED_PREFERENCES =
            "android.content.SharedPreferences"; //$NON-NLS-1$
    private static final String ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR =
            "android.content.SharedPreferences.Editor"; //$NON-NLS-1$

    /** Constructs a new {@link CleanupDetector} */
    public CleanupDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                // FragmentManager commit check
                BEGIN_TRANSACTION,

                // Recycle check
                OBTAIN, OBTAIN_NO_HISTORY,
                OBTAIN_STYLED_ATTRIBUTES,
                OBTAIN_ATTRIBUTES,
                OBTAIN_TYPED_ARRAY,

                // Release check
                ACQUIRE_CPC,

                // Cursor close check
                QUERY, RAW_QUERY, QUERY_WITH_FACTORY, RAW_QUERY_WITH_FACTORY,

                // SharedPreferences check
                EDIT
        );
    }

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(SURFACE_TEXTURE_CLS, SURFACE_CLS);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        String name = method.getName();
        if (BEGIN_TRANSACTION.equals(name)) {
            checkTransactionCommits(context, call, method);
        } else if (EDIT.equals(name)) {
            checkEditorApplied(context, call, method);
        } else {
            checkResourceRecycled(context, call, method);
        }
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiNewExpression node, @NonNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass != null) {
            String type = containingClass.getQualifiedName();
            if (type != null) {
                checkRecycled(context, node, type, RELEASE);
            }
        }
    }

    private static void checkResourceRecycled(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        String name = method.getName();
        // Recycle detector
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        if ((OBTAIN.equals(name) || OBTAIN_NO_HISTORY.equals(name)) &&
                evaluator.extendsClass(containingClass, MOTION_EVENT_CLS, false)) {
            checkRecycled(context, node, MOTION_EVENT_CLS, RECYCLE);
        } else if (OBTAIN.equals(name) && evaluator.extendsClass(containingClass, PARCEL_CLS, false)) {
            checkRecycled(context, node, PARCEL_CLS, RECYCLE);
        } else if (OBTAIN.equals(name) &&
                evaluator.extendsClass(containingClass, VELOCITY_TRACKER_CLS, false)) {
            checkRecycled(context, node, VELOCITY_TRACKER_CLS, RECYCLE);
        } else if ((OBTAIN_STYLED_ATTRIBUTES.equals(name)
                || OBTAIN_ATTRIBUTES.equals(name)
                || OBTAIN_TYPED_ARRAY.equals(name)) &&
                (evaluator.extendsClass(containingClass, CLASS_CONTEXT, false) ||
                        evaluator.extendsClass(containingClass, SdkConstants.CLASS_RESOURCES, false))) {
            PsiType returnType = method.getReturnType();
            if (returnType instanceof PsiClassType) {
                PsiClass cls = ((PsiClassType)returnType).resolve();
                if (cls != null && SdkConstants.CLS_TYPED_ARRAY.equals(cls.getQualifiedName())) {
                    checkRecycled(context, node, SdkConstants.CLS_TYPED_ARRAY, RECYCLE);
                }
            }
        } else if (ACQUIRE_CPC.equals(name) && evaluator.extendsClass(containingClass,
                CONTENT_RESOLVER_CLS, false)) {
            checkRecycled(context, node, CONTENT_PROVIDER_CLIENT_CLS, RELEASE);
        } else if ((QUERY.equals(name)
                || RAW_QUERY.equals(name)
                || QUERY_WITH_FACTORY.equals(name)
                || RAW_QUERY_WITH_FACTORY.equals(name))
                && (evaluator.extendsClass(containingClass, SQLITE_DATABASE_CLS, false) ||
                    evaluator.extendsClass(containingClass, CONTENT_RESOLVER_CLS, false) ||
                    evaluator.extendsClass(containingClass, CLASS_CONTENTPROVIDER, false) ||
                    evaluator.extendsClass(containingClass, CONTENT_PROVIDER_CLIENT_CLS, false))) {
            // Other potential cursors-returning methods that should be tracked:
            //    android.app.DownloadManager#query
            //    android.content.ContentProviderClient#query
            //    android.content.ContentResolver#query
            //    android.database.sqlite.SQLiteQueryBuilder#query
            //    android.provider.Browser#getAllBookmarks
            //    android.provider.Browser#getAllVisitedUrls
            //    android.provider.DocumentsProvider#queryChildDocuments
            //    android.provider.DocumentsProvider#qqueryDocument
            //    android.provider.DocumentsProvider#queryRecentDocuments
            //    android.provider.DocumentsProvider#queryRoots
            //    android.provider.DocumentsProvider#querySearchDocuments
            //    android.provider.MediaStore$Images$Media#query
            //    android.widget.FilterQueryProvider#runQuery
            checkRecycled(context, node, CURSOR_CLS, CLOSE);
        }
    }

    private static void checkRecycled(@NonNull final JavaContext context, @NonNull PsiElement node,
            @NonNull final String recycleType, @NonNull final String recycleName) {
        PsiVariable boundVariable = getVariableElement(node);
        if (boundVariable == null) {
            return;
        }

        PsiMethod method = getParentOfType(node, PsiMethod.class, true);
        if (method == null) {
            return;
        }
        FinishVisitor visitor = new FinishVisitor(context, boundVariable) {
            @Override
            protected boolean isCleanupCall(@NonNull PsiMethodCallExpression call) {
                PsiReferenceExpression methodExpression = call.getMethodExpression();
                String methodName = methodExpression.getReferenceName();
                if (!recycleName.equals(methodName)) {
                    return false;
                }
                PsiMethod method = call.resolveMethod();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (mContext.getEvaluator().extendsClass(containingClass, recycleType, false)) {
                        // Yes, called the right recycle() method; now make sure
                        // we're calling it on the right variable
                        PsiExpression operand = methodExpression.getQualifierExpression();
                        if (operand instanceof PsiReferenceExpression) {
                            PsiElement resolved = ((PsiReferenceExpression) operand).resolve();
                            //noinspection SuspiciousMethodCalls
                            if (resolved != null && mVariables.contains(resolved)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        };

        method.accept(visitor);
        if (visitor.isCleanedUp() || visitor.variableEscapes()) {
            return;
        }

        String className = recycleType.substring(recycleType.lastIndexOf('.') + 1);
        String message;
        if (RECYCLE.equals(recycleName)) {
            message = String.format(
                    "This `%1$s` should be recycled after use with `#recycle()`", className);
        } else {
            message = String.format(
                    "This `%1$s` should be freed up after use with `#%2$s()`", className,
                    recycleName);
        }

        PsiElement locationNode = node instanceof PsiMethodCallExpression ?
                ((PsiMethodCallExpression)node).getMethodExpression().getReferenceNameElement() : node;
        if (locationNode == null) {
            locationNode = node;
        }
        Location location = context.getLocation(locationNode);
        context.report(RECYCLE_RESOURCE, node, location, message);
    }

    private static void checkTransactionCommits(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod calledMethod) {
        if (isBeginTransaction(context, calledMethod)) {
            PsiVariable boundVariable = getVariableElement(node, true);
            if (boundVariable == null && isCommittedInChainedCalls(context, node)) {
                return;
            }

            if (boundVariable != null) {
                PsiMethod method = getParentOfType(node, PsiMethod.class, true);
                if (method == null) {
                    return;
                }

                FinishVisitor commitVisitor = new FinishVisitor(context, boundVariable) {
                    @Override
                    protected boolean isCleanupCall(@NonNull PsiMethodCallExpression call) {
                        if (isTransactionCommitMethodCall(mContext, call)) {
                            PsiExpression operand = call.getMethodExpression().getQualifierExpression();
                            if (operand != null) {
                                PsiElement resolved = mContext.getEvaluator().resolve(operand);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                } else if (resolved instanceof PsiMethod
                                        && operand instanceof PsiMethodCallExpression
                                        && isCommittedInChainedCalls(mContext,
                                            (PsiMethodCallExpression) operand)) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand instanceof PsiMethodCallExpression) {
                                        operand = ((PsiMethodCallExpression)operand).getMethodExpression().getQualifierExpression();
                                    }
                                    if (operand instanceof PsiReferenceExpression) {
                                        resolved = ((PsiReferenceExpression)operand).resolve();
                                        //noinspection SuspiciousMethodCalls
                                        if (resolved != null && mVariables.contains(resolved)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        } else if (isShowFragmentMethodCall(mContext, call)) {
                            PsiExpression[] arguments = call.getArgumentList().getExpressions();
                            if (arguments.length == 2) {
                                PsiExpression first = arguments[0];
                                PsiElement resolved = mContext.getEvaluator().resolve(first);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                };

                method.accept(commitVisitor);
                if (commitVisitor.isCleanedUp() || commitVisitor.variableEscapes()) {
                    return;
                }
            }

            String message = "This transaction should be completed with a `commit()` call";
            context.report(COMMIT_FRAGMENT, node, context.getNameLocation(node), message);
        }
    }

    private static boolean isCommittedInChainedCalls(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node) {
        // Look for chained calls since the FragmentManager methods all return "this"
        // to allow constructor chaining, e.g.
        //    getFragmentManager().beginTransaction().addToBackStack("test")
        //            .disallowAddToBackStack().hide(mFragment2).setBreadCrumbShortTitle("test")
        //            .show(mFragment2).setCustomAnimations(0, 0).commit();
        PsiElement parent = skipParentheses(node.getParent());
        while (parent != null) {
            if (parent instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodInvocation = (PsiMethodCallExpression) parent;
                if (isTransactionCommitMethodCall(context, methodInvocation)
                        || isShowFragmentMethodCall(context, methodInvocation)) {
                    return true;
                }
            } else if (!(parent instanceof PsiReferenceExpression)) {
                // reference expressions are method references
                return false;
            }

            parent = skipParentheses(parent.getParent());
        }

        return false;
    }

    private static boolean isTransactionCommitMethodCall(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {

        String methodName = call.getMethodExpression().getReferenceName();
        return (COMMIT.equals(methodName)
                    || COMMIT_ALLOWING_LOSS.equals(methodName)
                    || COMMIT_NOW.equals(methodName)) &&
                isMethodOnFragmentClass(context, call,
                        FRAGMENT_TRANSACTION_CLS,
                        FRAGMENT_TRANSACTION_V4_CLS,
                        true);
    }

    private static boolean isShowFragmentMethodCall(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        return SHOW.equals(methodName)
                && isMethodOnFragmentClass(context, call,
                DIALOG_FRAGMENT, DIALOG_V4_FRAGMENT, true);
    }

    private static boolean isMethodOnFragmentClass(
            @NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call,
            @NonNull String fragmentClass,
            @NonNull String v4FragmentClass,
            boolean returnForUnresolved) {
        PsiMethod method = call.resolveMethod();
        if (method != null) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            return evaluator.extendsClass(containingClass, fragmentClass, false) ||
                    evaluator.extendsClass(containingClass, v4FragmentClass, false);
        } else {
            // If we *can't* resolve the method call, caller can decide
            // whether to consider the method called or not
            return returnForUnresolved;
        }
    }

    private static void checkEditorApplied(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod calledMethod) {
        if (isSharedEditorCreation(context, calledMethod)) {
            PsiVariable boundVariable = getVariableElement(node, true);
            if (isEditorCommittedInChainedCalls(context, node)) {
                return;
            }

            if (boundVariable != null) {
                PsiMethod method = getParentOfType(node, PsiMethod.class, true);
                if (method == null) {
                    return;
                }

                FinishVisitor commitVisitor = new FinishVisitor(context, boundVariable) {
                    @Override
                    protected boolean isCleanupCall(@NonNull PsiMethodCallExpression call) {
                        if (isEditorApplyMethodCall(mContext, call)
                                || isEditorCommitMethodCall(mContext, call)) {
                            PsiExpression operand = call.getMethodExpression().getQualifierExpression();
                            if (operand != null) {
                                PsiElement resolved = mContext.getEvaluator().resolve(operand);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                } else if (resolved instanceof PsiMethod
                                        && operand instanceof PsiMethodCallExpression
                                        && isCommittedInChainedCalls(mContext,
                                        (PsiMethodCallExpression) operand)) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand instanceof PsiMethodCallExpression) {
                                        operand = ((PsiMethodCallExpression)operand).
                                                getMethodExpression().getQualifierExpression();
                                    }
                                    if (operand instanceof PsiReferenceExpression) {
                                        resolved = ((PsiReferenceExpression)operand).resolve();
                                        //noinspection SuspiciousMethodCalls
                                        if (resolved != null && mVariables.contains(resolved)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                        return false;
                    }
                };

                method.accept(commitVisitor);
                if (commitVisitor.isCleanedUp() || commitVisitor.variableEscapes()) {
                    return;
                }
            } else if (PsiTreeUtil.getParentOfType(node, PsiReturnStatement.class) != null) {
                // Allocation is in a return statement
                return;
            }

            String message = "`SharedPreferences.edit()` without a corresponding `commit()` or "
                    + "`apply()` call";
            context.report(SHARED_PREF, node, context.getLocation(node), message);
        }
    }

    private static boolean isSharedEditorCreation(@NonNull JavaContext context,
            @NonNull PsiMethod method) {
        String methodName = method.getName();
        if (EDIT.equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            return evaluator.extendsClass(containingClass, ANDROID_CONTENT_SHARED_PREFERENCES,
                    false);
        }

        return false;
    }

    private static boolean isEditorCommittedInChainedCalls(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node) {
        PsiElement parent = skipParentheses(node.getParent());
        while (parent != null) {
            if (parent instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodInvocation = (PsiMethodCallExpression) parent;
                if (isEditorCommitMethodCall(context, methodInvocation)
                        || isEditorApplyMethodCall(context, methodInvocation)) {
                    return true;
                }
            } else if (!(parent instanceof PsiReferenceExpression)) {
                // reference expressions are method references
                return false;
            }

            parent = skipParentheses(parent.getParent());
        }

        return false;
    }

    private static boolean isEditorCommitMethodCall(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (COMMIT.equals(methodName)) {
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                JavaEvaluator evaluator = context.getEvaluator();
                if (evaluator.extendsClass(containingClass,
                        ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false)) {
                    suggestApplyIfApplicable(context, call);
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isEditorApplyMethodCall(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (APPLY.equals(methodName)) {
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                JavaEvaluator evaluator = context.getEvaluator();
                return evaluator.extendsClass(containingClass,
                        ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false);
            }
        }

        return false;
    }

    private static void suggestApplyIfApplicable(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node) {
        if (context.getProject().getMinSdkVersion().getApiLevel() >= 9) {
            // See if the return value is read: can only replace commit with
            // apply if the return value is not considered
            PsiElement parent = skipParentheses(node.getParent());
            while (parent instanceof PsiReferenceExpression) {
                parent = skipParentheses(parent.getParent());
            }
            boolean returnValueIgnored = false;
            if (parent instanceof PsiMethodCallExpression ||
                    parent instanceof PsiNewExpression ||
                    parent instanceof PsiClass ||
                    parent instanceof PsiCodeBlock ||
                    parent instanceof PsiExpressionStatement) {
                returnValueIgnored = true;
            } else if (parent instanceof PsiStatement) {
                if (parent instanceof PsiIfStatement) {
                    returnValueIgnored = ((PsiIfStatement)parent).getCondition() != node;
                } else if (parent instanceof PsiWhileStatement) {
                    returnValueIgnored = ((PsiWhileStatement)parent).getCondition() != node;
                } else if (parent instanceof PsiDoWhileStatement) {
                    returnValueIgnored = ((PsiDoWhileStatement)parent).getCondition() != node;
                } else if (parent instanceof PsiAssertStatement) {
                    returnValueIgnored = ((PsiAssertStatement)parent).getAssertCondition() != node;
                } else if (parent instanceof PsiReturnStatement
                        || parent instanceof PsiDeclarationStatement) {
                    returnValueIgnored = false;
                } else {
                    returnValueIgnored = true;
                }
            }
            if (returnValueIgnored) {
                String message = "Consider using `apply()` instead; `commit` writes "
                        + "its data to persistent storage immediately, whereas "
                        + "`apply` will handle it in the background";
                context.report(SHARED_PREF, node, context.getLocation(node), message);
            }
        }
    }

    /** Returns the variable the expression is assigned to, if any */
    @Nullable
    public static PsiVariable getVariableElement(@NonNull PsiElement rhs) {
        return getVariableElement(rhs, false);
    }

    @Nullable
    public static PsiVariable getVariableElement(@NonNull PsiElement rhs,
            boolean allowChainedCalls) {
        PsiElement parent = skipParentheses(rhs.getParent());

        // Handle some types of chained calls; e.g. you might have
        //    var = prefs.edit().put(key,value)
        // and here we want to skip past the put call
        if (allowChainedCalls) {
            while (true) {
                if ((parent instanceof PsiReferenceExpression)) {
                    PsiElement parentParent = skipParentheses(parent.getParent());
                    if ((parentParent instanceof PsiMethodCallExpression)) {
                        parent = skipParentheses(parentParent.getParent());
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (parent instanceof PsiAssignmentExpression) {
            PsiAssignmentExpression assignment = (PsiAssignmentExpression) parent;
            PsiExpression lhs = assignment.getLExpression();
            if (lhs instanceof PsiReference) {
                PsiElement resolved = ((PsiReference)lhs).resolve();
                if (resolved instanceof PsiVariable && !(resolved instanceof PsiField)) {
                    // e.g. local variable, parameter - but not a field
                    return (PsiVariable) resolved;
                }
            }
        } else if (parent instanceof PsiVariable && !(parent instanceof PsiField)) {
            return (PsiVariable) parent;
        }

        return null;
    }

    private static boolean isBeginTransaction(@NonNull JavaContext context, @NonNull PsiMethod method) {
        String methodName = method.getName();
        if (BEGIN_TRANSACTION.equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            if (evaluator.extendsClass(containingClass, FRAGMENT_MANAGER_CLS, false)
                    || evaluator.extendsClass(containingClass, FRAGMENT_MANAGER_V4_CLS, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Visitor which checks whether an operation is "finished"; in the case
     * of a FragmentTransaction we're looking for a "commit" call; in the
     * case of a TypedArray we're looking for a "recycle", call, in the
     * case of a database cursor we're looking for a "close" call, etc.
     */
    private abstract static class FinishVisitor extends JavaRecursiveElementVisitor {
        protected final JavaContext mContext;
        protected final List<PsiVariable> mVariables;
        private final PsiVariable mOriginalVariableNode;

        private boolean mContainsCleanup;
        private boolean mEscapes;

        public FinishVisitor(JavaContext context, @NonNull PsiVariable variableNode) {
            mContext = context;
            mOriginalVariableNode = variableNode;
            mVariables = Lists.newArrayList(variableNode);
        }

        public boolean isCleanedUp() {
            return mContainsCleanup;
        }

        public boolean variableEscapes() {
            return mEscapes;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (!mContainsCleanup) {
                super.visitElement(element);
            }
        }

        protected abstract boolean isCleanupCall(@NonNull PsiMethodCallExpression call);

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            if (mContainsCleanup) {
                return;
            }

            super.visitMethodCallExpression(call);

            // Look for escapes
            if (!mEscapes) {
                for (PsiExpression expression : call.getArgumentList().getExpressions()) {
                    if (expression instanceof PsiReferenceExpression) {
                        PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
                        //noinspection SuspiciousMethodCalls
                        if (resolved != null && mVariables.contains(resolved)) {
                            boolean wasEscaped = mEscapes;
                            mEscapes = true;

                            // Special case: MotionEvent.obtain(MotionEvent): passing in an
                            // event here does not recycle the event, and we also know it
                            // doesn't escape
                            if (OBTAIN.equals(call.getMethodExpression().getReferenceName())) {
                                PsiMethod method = call.resolveMethod();
                                if (mContext.getEvaluator()
                                        .isMemberInClass(method, MOTION_EVENT_CLS)) {
                                    mEscapes = wasEscaped;
                                }
                            }
                        }
                    }
                }
            }

            if (isCleanupCall(call)) {
                mContainsCleanup = true;
            }
        }

        @Override
        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);

            PsiExpression initializer = variable.getInitializer();
            if (initializer instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) initializer).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    mVariables.add(variable);
                }
            }
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);

            // TEMPORARILY DISABLED; see testDatabaseCursorReassignment
            // This can result in some false positives right now. Play it
            // safe instead.
            boolean clearLhs = false;

            PsiExpression rhs = expression.getRExpression();
            if (rhs instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) rhs).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    clearLhs = false;
                    PsiElement lhs = mContext.getEvaluator().resolve(expression.getLExpression());
                    if (lhs instanceof PsiLocalVariable) {
                        mVariables.add((PsiLocalVariable)lhs);
                    } else if (lhs instanceof PsiField) {
                        mEscapes = true;
                    }
                }
            }

            //noinspection ConstantConditions
            if (clearLhs) {
                // If we reassign one of the variables, clear it out
                PsiElement lhs = mContext.getEvaluator().resolve(expression.getLExpression());
                //noinspection SuspiciousMethodCalls
                if (lhs != null && !lhs.equals(mOriginalVariableNode)
                        && mVariables.contains(lhs)) {
                    //noinspection SuspiciousMethodCalls
                    mVariables.remove(lhs);
                }
            }
        }

        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
            PsiExpression returnValue = statement.getReturnValue();
            if (returnValue instanceof PsiReference) {
                PsiElement resolved = ((PsiReference) returnValue).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    mEscapes = true;
                }
            }

            super.visitReturnStatement(statement);
        }
    }
}
