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

import static com.android.SdkConstants.RESOURCE_CLZ_ID;
import static com.android.tools.lint.detector.api.LintUtils.nextNonWhitespace;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Maps;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detector looking for cut & paste issues
 */
public class CutPasteDetector extends Detector implements Detector.JavaPsiScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "CutPasteId", //$NON-NLS-1$
            "Likely cut & paste mistakes",

            "This lint check looks for cases where you have cut & pasted calls to " +
            "`findViewById` but have forgotten to update the R.id field. It's possible " +
            "that your code is simply (redundantly) looking up the field repeatedly, " +
            "but lint cannot distinguish that from a case where you for example want to " +
            "initialize fields `prev` and `next` and you cut & pasted `findViewById(R.id.prev)` " +
            "and forgot to update the second initialization to `R.id.next`.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    CutPasteDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private PsiMethod mLastMethod;
    private Map<String, PsiMethodCallExpression> mIds;
    private Map<String, String> mLhs;
    private Map<String, String> mCallOperands;

    /** Constructs a new {@link CutPasteDetector} check */
    public CutPasteDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("findViewById"); //$NON-NLS-1$
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod calledMethod) {
        String lhs = getLhs(call);
        if (lhs == null) {
            return;
        }

        PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, false);
        if (method == null) {
            return; // prevent doing the same work for multiple findViewById calls in same method
        } else if (method != mLastMethod) {
            mIds = Maps.newHashMap();
            mLhs = Maps.newHashMap();
            mCallOperands = Maps.newHashMap();
            mLastMethod = method;
        }

        PsiReferenceExpression methodExpression = call.getMethodExpression();
        String callOperand = methodExpression.getQualifier() != null
                ? methodExpression.getQualifier().getText() : "";

        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length == 0) {
            return;
        }
        PsiExpression first = arguments[0];
        if (first instanceof PsiReferenceExpression) {
            PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression) first;
            String id = psiReferenceExpression.getReferenceName();
            PsiElement operand = psiReferenceExpression.getQualifier();
            if (operand instanceof PsiReferenceExpression) {
                PsiReferenceExpression type = (PsiReferenceExpression) operand;
                if (RESOURCE_CLZ_ID.equals(type.getReferenceName())) {
                    if (mIds.containsKey(id)) {
                        if (lhs.equals(mLhs.get(id))) {
                            return;
                        }
                        if (!callOperand.equals(mCallOperands.get(id))) {
                            return;
                        }
                        PsiMethodCallExpression earlierCall = mIds.get(id);
                        if (!isReachableFrom(method, earlierCall, call)) {
                            return;
                        }
                        Location location = context.getLocation(call);
                        Location secondary = context.getLocation(earlierCall);
                        secondary.setMessage("First usage here");
                        location.setSecondary(secondary);
                        context.report(ISSUE, call, location, String.format(
                                "The id `%1$s` has already been looked up in this method; possible "
                                        +
                                        "cut & paste error?", first.getText()));
                    } else {
                        mIds.put(id, call);
                        mLhs.put(id, lhs);
                        mCallOperands.put(id, callOperand);
                    }
                }

            }
        }
    }

    @Nullable
    private static String getLhs(@NonNull PsiMethodCallExpression call) {
        PsiElement parent = skipParentheses(call.getParent());
        if (parent instanceof PsiTypeCastExpression) {
            parent = parent.getParent();
        }

        if (parent instanceof PsiLocalVariable) {
            return ((PsiLocalVariable)parent).getName();
        } else if (parent instanceof PsiBinaryExpression) {
            PsiBinaryExpression be = (PsiBinaryExpression) parent;
            PsiExpression left = be.getLOperand();
            if (left instanceof PsiReference) {
                return left.getText();
            } else if (left instanceof PsiArrayAccessExpression) {
                PsiArrayAccessExpression aa = (PsiArrayAccessExpression) left;
                return aa.getArrayExpression().getText();
            }
        } else if (parent instanceof PsiAssignmentExpression) {
            PsiExpression left = ((PsiAssignmentExpression) parent).getLExpression();
            if (left instanceof PsiReference) {
                return left.getText();
            } else if (left instanceof PsiArrayAccessExpression) {
                PsiArrayAccessExpression aa = (PsiArrayAccessExpression) left;
                return aa.getArrayExpression().getText();
            }
        }

        return null;
    }

    private static boolean isReachableFrom(
            @NonNull PsiMethod method,
            @NonNull PsiMethodCallExpression from,
            @NonNull PsiMethodCallExpression to) {
        PsiElement current = from;
        //noinspection ConstantConditions
        while (current != null && current != method) {
            if (containsElement(current, to)) {
                return true;
            }

            if (current.getNextSibling() != null) {
                current = current.getNextSibling();
            } else {
                while (current.getParent() != null && current.getParent() != method) {
                    if (nextNonWhitespace(current.getParent()) != null) {
                        current = nextNonWhitespace(current.getParent());
                        assert current != null;
                        if (current.getParent() instanceof PsiIfStatement) {
                            // Don't want to move from then to else siblings
                            current = current.getParent();
                        } else {
                            break;
                        }
                    } else {
                        current = current.getParent();
                    }
                }

                PsiElement parent = skipParentheses(current.getParent());
                if (parent instanceof PsiForStatement
                        || parent instanceof PsiForeachStatement
                        || parent instanceof PsiWhileStatement
                        || parent instanceof PsiDoWhileStatement) {
                    if (containsElement(current, to)) {
                        return true;
                    }
                } else if (parent == method) {
                    return false;
                }
            }
        }

        return false;
    }

    private static boolean containsElement(@NonNull PsiElement root, @NonNull PsiElement element) {
        if (root.equals(element)) {
            return true;
        }

        PsiElement curr = root.getFirstChild();
        while (curr != null) {
            if (containsElement(curr, element)) {
                return true;
            }
            curr = curr.getNextSibling();
        }

        return false;
    }
}
