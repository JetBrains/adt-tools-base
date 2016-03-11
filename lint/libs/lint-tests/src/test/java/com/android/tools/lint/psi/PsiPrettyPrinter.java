/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiWildcardType;

import java.util.Map;

/**
 * Temporary usage for debugging PSI differences between the IDEA based PSI and
 * the ECJ based PSI.
 */
public class PsiPrettyPrinter {

    private static final int MAX_ID_LENGTH = 40;

    private Map<Class<?>,String> mDescriptions = Maps.newHashMapWithExpectedSize(50);
    private Map<PsiElement, String> mIds = Maps.newIdentityHashMap();
    private Map<String, Integer> mNextId = Maps.newHashMap();
    private boolean mIncludeResolves = true;
    private boolean mIncludeIds = true;

    public PsiPrettyPrinter setIncludeResolves(boolean includeResolves) {
        mIncludeResolves = includeResolves;
        return this;
    }

    public PsiPrettyPrinter setIncludeIds(boolean includeIds) {
        mIncludeIds = includeIds;
        return this;
    }

    @NonNull
    private String getPsiElementName(PsiElement element) {
        Class<? extends PsiElement> cls = element.getClass();
        String name = mDescriptions.get(cls);
        if (name == null) {
            name = cls.getSimpleName();

            if (element instanceof PsiEnumConstant &&
                    element.getParent() instanceof PsiClass &&
                    !((PsiClass)element.getParent()).isEnum()) {
                // IntelliJ seems to implement PsiEnumConstant on plain fields too
                return "PsiField";
            }
            if (name.equals("EcjPsiBinaryMethod")) {
                // IntelliJ makes all stub methods implement PsiAnnotatedMethod, whether they
                // really are or not, so try to make the outputs match
                name = "PsiAnnotationMethod";
            } else if (name.equals("EcjPsiArrayInitializerExpression")) {
                // Class implements both PsiArrayInitializerExpression and
                // PsiArrayInitializerMemberValue: usage depends on parent
                if (element.getParent() instanceof PsiNameValuePair ||
                        element.getParent() instanceof PsiAnnotationMethod) {
                    return "PsiArrayInitializerMemberValue";
                } else {
                    return "PsiArrayInitializerExpression";
                }
            } else {
                // For IntelliJ I have to try harder
                name = findPsiInterface(cls);
                if (name == null) {
                    name = cls.getSimpleName();
                }
            }

            mDescriptions.put(cls, name);
        }

        return name;
    }

    @Nullable
    private static String findPsiInterface(@Nullable Class<?> cls) {
        if (cls == null) {
            return null;
        }
        String name = cls.getName();
        if (name.startsWith("com.intellij.psi.") &&
                name.indexOf('.', "com.intellij.psi.".length()) == -1) {
            return cls.getSimpleName();
        } else {
            for (Class<?> implemented : cls.getInterfaces()) {
                name = findPsiInterface(implemented);
                if (name != null) {
                    return name;
                }
            }
            Class<?> superclass = cls.getSuperclass();
            name = findPsiInterface(superclass);
            if (name != null) {
                return name;
            }
        }

        return null;
    }

    public String print(@NonNull PsiElement root) {
        if (mIncludeResolves || mIncludeResolves) {
            mIds.clear();
            recordIds(root);
        }

        StringBuilder sb = new StringBuilder(1000);
        describeElements(0, sb, root);

        return sb.toString();
    }

    void recordIds(@NonNull PsiElement element) {
        if (!skipElement(element)) {
            String base = getPsiElementName(element);
            if (base.startsWith("Psi")) {
                base = base.substring(3);
            }
            if (base.length() > MAX_ID_LENGTH - 3) {
                base = base.substring(0, MAX_ID_LENGTH - 3);
            }
            Integer id = mNextId.get(base);
            if (id == null) {
                id = 1;
            }
            assert mIds.get(element) == null : element;
            mIds.put(element, base + Integer.toString(id));
            mNextId.put(base, id + 1);
        }

        if (!skipChildren(element)) {
            for (PsiElement child : element.getChildren()) {
                recordIds(child);
            }
        }
    }

    void describeElements(int depth, @NonNull StringBuilder sb, @NonNull PsiElement element) {
        if (!skipElement(element)) {
            if (mIncludeResolves && mIncludeIds) {
                String id = mIds.get(element);
                sb.append(String.format("%" + MAX_ID_LENGTH + "s", id));
                sb.append(':');
            }

            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            describeElement(sb, element);

            sb.append('\n');
        }

        if (!skipChildren(element)) {
            for (PsiElement child : element.getChildren()) {
                assert element == child.getParent();
                describeElements(depth + 1, sb, child);
            }
        }
    }

    private void describeElement(@NonNull StringBuilder sb, @NonNull PsiElement element) {
        sb.append(getPsiElementName(element));

        if (element instanceof PsiIdentifier) {
            sb.append(':').append('"').append(element.getText()).append('"');
        }  else if (element instanceof PsiLiteral) {
            sb.append(':').append(element.getText());
        } else if (element instanceof PsiTypeElement) {
            PsiType type = ((PsiTypeElement) element).getType();
            if (type instanceof PsiWildcardType) {
                PsiWildcardType psiWildcardType = (PsiWildcardType)type;
                //PsiType bound = psiWildcardType.getBound();
                type = psiWildcardType.getDeepComponentType();
            } else if (type instanceof PsiClassType) {
                // Temporarily wipe out generics; not modelled in our type hierarchy
                PsiClassType psiClassType = (PsiClassType)type;
                PsiType[] parameters = psiClassType.getParameters();
                if (parameters.length > 0) {
                    type = psiClassType.rawType();
                }
            }
            sb.append(':').append(type.getCanonicalText());
        } else if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            sb.append(":").append(method.getName());
        } else if (element instanceof PsiField) {
            PsiField field = (PsiField) element;
            sb.append(":").append(field.getName());
            Object constant = field.computeConstantValue();
            if (constant != null && constant.toString().startsWith("EcjPsiEnumConstant")) {
                constant = "PsiEnumConstant:" + field.getName();
            }
            sb.append(":").append(constant);
        } else if (element instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable) element;
            sb.append(":").append(variable.getName());
        } else if (element instanceof PsiAssignmentExpression) {
            sb.append(":").append(element.getText());
        } else if (element instanceof PsiClass) {
            PsiClass cls = (PsiClass) element;
            sb.append(":").append(cls.getQualifiedName());
        } else if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression expression = (PsiReferenceExpression) element;
            sb.append(":").append(expression.getReferenceName());
        } else if (element instanceof PsiModifierList) {
            PsiModifierList modifierList = (PsiModifierList) element;
            for (String modifier : new String[]{
                    PsiModifier.PUBLIC,
                    PsiModifier.PROTECTED,
                    PsiModifier.PRIVATE,
                    PsiModifier.STATIC,
                    PsiModifier.ABSTRACT,
                    PsiModifier.FINAL,
                    PsiModifier.TRANSIENT,
                    PsiModifier.VOLATILE,
                    PsiModifier.NATIVE,
                    PsiModifier.SYNCHRONIZED,
                    PsiModifier.STRICTFP
            }) {
                if (modifierList.hasModifierProperty(modifier)) {
                    sb.append(" ").append(modifier);
                }
            }
        } else if (element instanceof PsiReferenceList) {
            PsiReferenceList psiReferenceList = (PsiReferenceList) element;
            PsiJavaCodeReferenceElement[] referenceElements = psiReferenceList
                    .getReferenceElements();
            for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                sb.append(';');
                describeElement(sb, referenceElement);
            }
        } else if (mIncludeResolves) {
            PsiElement resolved = null;
            if (element instanceof PsiCallExpression) {
                resolved = ((PsiCallExpression) element).resolveMethod();
            } else if (element instanceof PsiReference) {
                resolved = ((PsiReference) element).resolve();
            }

            if (resolved != null) {
                sb.append(" -> [");
                String id = mIds.get(resolved);
                if (id != null) {
                    sb.append(id);
                } else {
                    describeElement(sb, resolved);
                }
                sb.append("]");
            }
        }

        if (includePosition(element)) {
            TextRange textRange = element.getTextRange();
            sb.append(":").append(textRange);
        }
    }

    private static boolean includePosition(@NonNull PsiElement element) {
        return element instanceof PsiIdentifier || element instanceof PsiLiteral;
    }

    private static boolean skipElement(@NonNull PsiElement element) {
        if (element instanceof PsiLiteralExpression) {
            return false; // otherwise PsiLanguageInjectionHost will clear it
        }
        if (element instanceof PsiReferenceParameterList) {
            //PsiReferenceParameterList parameterList = (PsiReferenceParameterList)element;
            //if (parameterList.getTypeArguments().length == 0) {
            //    return true;
            //}
            return true;
        }

        if (element instanceof PsiReferenceList) {
            PsiReferenceList parameterList = (PsiReferenceList)element;
            if (parameterList.getReferenceElements().length == 0) {
                return true;
            }
        }
        if (element instanceof PsiTypeParameterList) {
            PsiTypeParameterList parameterList = (PsiTypeParameterList)element;
            if (parameterList.getTypeParameters().length == 0) {
                return true;
            }
        }
        if (element instanceof PsiAnnotationParameterList) {
            PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList) element;
            if (parameterList.getAttributes().length == 0) {
                return true;
            }
        }

        return element instanceof PsiWhiteSpace
               || element instanceof PsiComment
               || element instanceof PsiParenthesizedExpression
               || element instanceof PsiKeyword
               || element instanceof PsiJavaToken
               || element instanceof PsiLanguageInjectionHost;
    }

    private static boolean skipChildren(@NonNull PsiElement element) {
        // We don't include resolving for package and import statement elements
        if (element instanceof PsiPackageStatement || element instanceof PsiImportStatementBase) {
            return true;
        }

        if (element instanceof PsiComment) {
            return true;
        }

        if (element instanceof PsiTypeElement) {
            // We don't create nested type elements for int[]
            // (int root type element inside array type element etc)
            return true;
        }

        return false;
    }
}
