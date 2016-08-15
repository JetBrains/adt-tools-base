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

import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class EcjPsiClass extends EcjPsiSourceElement implements PsiClass {

    private final int mEcjModifiers;

    private final String mName;

    private String mQualifiedName;

    private EcjPsiModifierList mModifierList;

    private TypeReference mSuperClassReference;

    private PsiClass mSuperClass;

    private TypeReference[] mSuperInterfaceReferences;

    private PsiClass[] mSuperInterfaces;

    private PsiIdentifier mIdentifier;

    private PsiTypeParameterList mTypeParameterList;

    private List<PsiClassInitializer> mInitializers;

    private List<EcjPsiMethod> mMethods;

    private EcjPsiClass[] mInnerClasses;

    private List<EcjPsiField> mFields;

    private PsiReferenceList mExtendsList;
    private PsiReferenceList mImplementsList;

    EcjPsiClass(@NonNull EcjPsiManager manager, @NonNull TypeDeclaration declaration,
            @Nullable String name) {
        super(manager, declaration);
        mEcjModifiers = declaration.modifiers;
        mName = name;
        if (declaration.binding != null && declaration.binding.compoundName != null) {
            mQualifiedName = getTypeName(declaration.binding);
        }

        mManager.registerElement(declaration.binding, this);
    }

    void setNameIdentifier(@Nullable PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    void setSuperClass(@Nullable TypeReference superclass) {
        mSuperClassReference = superclass;
    }

    void setSuperInterfaces(@Nullable TypeReference[] superInterfaces) {
        mSuperInterfaceReferences = superInterfaces;
    }

    void setFields(List<EcjPsiField> fields) {
        mFields = fields;
    }

    void setInitializers(List<PsiClassInitializer> initializers) {
        mInitializers = initializers;
    }

    void setMethods(@NonNull List<EcjPsiMethod> methods) {
        mMethods = methods;
    }

    void setTypeParameterList(@Nullable PsiTypeParameterList typeParameterList) {
        mTypeParameterList = typeParameterList;
    }

    void setInnerClasses(@NonNull EcjPsiClass[] innerClasses) {
        mInnerClasses = innerClasses;
    }

    void setExtendsList(PsiReferenceList extendsList) {
        mExtendsList = extendsList;
    }

    void setImplementsList(PsiReferenceList implementsList) {
        mImplementsList = implementsList;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return mQualifiedName;
    }

    @Override
    public boolean isInterface() {
        return TypeDeclaration.kind(mEcjModifiers) == TypeDeclaration.INTERFACE_DECL;
    }

    @Override
    public boolean isAnnotationType() {
        return TypeDeclaration.kind(mEcjModifiers) == TypeDeclaration.ANNOTATION_TYPE_DECL;
    }

    @Override
    public boolean isEnum() {
        return TypeDeclaration.kind(mEcjModifiers) == TypeDeclaration.ENUM_DECL;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        return mParent instanceof EcjPsiClass ? (EcjPsiClass) mParent : null;
    }

    @Nullable
    @Override
    public PsiModifierList getModifierList() {
        return mModifierList;
    }

    public void setModifierList(@Nullable EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    @Override
    public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNull String s) {
        return mModifierList != null && mModifierList.hasModifierProperty(s);
    }

    @Override
    public boolean hasTypeParameters() {
        return mTypeParameterList != null;
    }

    @Nullable
    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return mTypeParameterList;
    }

    @NonNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return mTypeParameterList != null ?
                mTypeParameterList.getTypeParameters() : PsiTypeParameter.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiReferenceList getExtendsList() {
        return mExtendsList;
    }

    @Nullable
    @Override
    public PsiReferenceList getImplementsList() {
        return mImplementsList;
    }

    @NonNull
    @Override
    public PsiClassType[] getExtendsListTypes() {
        return mExtendsList != null ? mExtendsList.getReferencedTypes() : PsiClassType.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiClassType[] getImplementsListTypes() {
        return mImplementsList != null
                ? mImplementsList.getReferencedTypes() : PsiClassType.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiClass getSuperClass() {
        if (mSuperClass == null && mSuperClassReference != null) {
            mSuperClass = mManager.findClass(mSuperClassReference);
        }
        return mSuperClass;
    }

    @Override
    public PsiClass[] getInterfaces() {
        if (mSuperInterfaces == null) {
            if (mSuperInterfaceReferences != null && mSuperInterfaceReferences.length > 0) {
                List<PsiClass> interfaces =
                        Lists.newArrayListWithCapacity(mSuperInterfaceReferences.length);
                for (TypeReference ref : mSuperInterfaceReferences) {
                    PsiClass cls = mManager.findClass(ref);
                    if (cls != null) {
                        interfaces.add(cls);
                    } // else: if we couldn't resolve it, create a problem binding?
                }
                mSuperInterfaces = interfaces.toArray(PsiClass.EMPTY_ARRAY);
            } else {
                mSuperInterfaces = PsiClass.EMPTY_ARRAY;
            }
        }
        return mSuperInterfaces;
    }

    @NonNull
    @Override
    public PsiClass[] getSupers() {
        PsiClass superClass = getSuperClass();
        PsiClass[] interfaces = getInterfaces();
        if (superClass == null) {
            return interfaces;
        } else if (interfaces == null) {
            return new PsiClass[] { superClass };
        } else {
            PsiClass[] result = new PsiClass[interfaces.length+1];
            System.arraycopy(interfaces, 1, result, 0, interfaces.length);
            result[0] = superClass;
            return result;
        }
    }

    @NonNull
    @Override
    public PsiClassType[] getSuperTypes() {
        return mManager.getClassTypes(getSupers());
    }

    @NonNull
    @Override
    public PsiField[] getFields() {
        return mFields != null ? mFields.toArray(PsiField.EMPTY_ARRAY) : PsiField.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiMethod[] getMethods() {
        return mMethods != null ? mMethods.toArray(PsiMethod.EMPTY_ARRAY) : PsiMethod.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiMethod[] getConstructors() {
        if (mMethods != null) {
            List<PsiMethod> constructors = Lists.newArrayList();
            for (PsiMethod method : mMethods) {
                if (method.isConstructor()) {
                    constructors.add(method);
                }
            }
            return constructors.toArray(PsiMethod.EMPTY_ARRAY);
        } else {
            return PsiMethod.EMPTY_ARRAY;
        }
    }

    @NonNull
    @Override
    public PsiClass[] getInnerClasses() {
        return mInnerClasses != null ? mInnerClasses : PsiClass.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiClassInitializer[] getInitializers() {
        return mInitializers != null
                ? mInitializers.toArray(new PsiClassInitializer[0])
                : PsiClassInitializer.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiField[] getAllFields() {
        PsiField[] fields = getFields();
        PsiField[] superFields = PsiField.EMPTY_ARRAY;
        PsiClass superClass = getSuperClass();
        if (superClass != null) {
            superFields = superClass.getAllFields();
        }
        if (superFields.length == 0) {
            return fields;
        } else if (fields.length == 0) {
            return superFields;
        } else {
            List<PsiField> merged = Lists.newArrayListWithCapacity(
                    fields.length + superFields.length);
            Collections.addAll(merged, fields);
            // Not a very good algorithm but lint isn't really using this method
        loop:
            for (PsiField superField : superFields) {
                String superName = superField.getName();
                if (superName != null) {
                    for (PsiField field : fields) {
                        if (superName.equals(field.getName())) {
                            continue loop;
                        }
                    }
                }

                merged.add(superField);
            }

            return merged.toArray(PsiField.EMPTY_ARRAY);
        }
    }

    @NonNull
    @Override
    public PsiMethod[] getAllMethods() {
        PsiMethod[] methods = getMethods();
        PsiMethod[] superMethods = PsiMethod.EMPTY_ARRAY;
        PsiClass superClass = getSuperClass();
        if (superClass != null) {
            superMethods = superClass.getAllMethods();
        }
        if (superMethods.length == 0) {
            return methods;
        } else if (methods.length == 0) {
            return superMethods;
        } else {
            List<PsiMethod> merged = Lists.newArrayListWithCapacity(
                    methods.length + superMethods.length);
            Collections.addAll(merged, methods);
            // Not a very good algorithm but lint isn't really using this method
        superMethodLoop:
            for (PsiMethod superMethod : superMethods) {
                String superName = superMethod.getName();
                PsiParameterList superParameterList = superMethod.getParameterList();
                int superParameterCount = superParameterList.getParametersCount();
                PsiParameter[] superParameters = superParameterList.getParameters();
            methodCheck:
                for (PsiMethod method : methods) {
                    if (method.getName().equals(superName)) {
                        continue;
                    }
                    PsiParameterList parameterList = method.getParameterList();
                    if (parameterList.getParametersCount() != superParameterCount) {
                        continue;
                    }
                    PsiParameter[] parameters = parameterList.getParameters();
                    for (int i = 0; i < superParameters.length; i++) {
                        PsiParameter superParameter = superParameters[i];
                        PsiParameter parameter = parameters[i];
                        if (!(superParameter.getType().equals(parameter.getType()))) {
                            // Parameter mismatch: check next candidate
                            continue methodCheck;
                        }
                    }
                    // Signature matches: can't add this one
                    continue superMethodLoop;
                }

                merged.add(superMethod);
            }

            return merged.toArray(PsiMethod.EMPTY_ARRAY);
        }
    }

    @NonNull
    @Override
    public PsiClass[] getAllInnerClasses() {
        PsiClass[] innerClasses = getInnerClasses();
        PsiClass superClass = getSuperClass();
        PsiClass[] superInnerClasses;
        if (superClass != null) {
            superInnerClasses = superClass.getAllInnerClasses();
        } else {
            return innerClasses;
        }
        if (innerClasses.length == 0) {
            return superInnerClasses;
        }
        if (superInnerClasses.length == 0) {
            return innerClasses;
        }
        PsiClass[] all = new PsiClass[innerClasses.length + superInnerClasses.length];
        System.arraycopy(innerClasses, 0, all, 0, innerClasses.length);
        System.arraycopy(superInnerClasses, 0, all, innerClasses.length, superInnerClasses.length);
        return all;
    }

    @Nullable
    @Override
    public PsiField findFieldByName(String name, boolean checkBases) {
        if (mFields != null) {
            for (EcjPsiField field : mFields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
        }
        if (checkBases) {
            PsiClass superClass = getSuperClass();
            if (superClass != null) {
                return superClass.findFieldByName(name, true);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public PsiMethod findMethodBySignature(PsiMethod psiMethod, boolean checkBases) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiMethod[] findMethodsBySignature(PsiMethod psiMethod, boolean checkBases) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
        PsiMethod match = null;
        List<PsiMethod> all = null;
        for (PsiMethod method : getMethods()) {
            if (name.equals(method.getName())) {
                if (match == null) {
                    match = method;
                } else {
                    if (all == null) {
                        all = Lists.newArrayList();
                        all.add(match);
                    }
                    all.add(method);
                }
            }
        }
        if (checkBases) {
            PsiClass superClass = getSuperClass();
            if (superClass != null) {
                PsiMethod[] superMatches = superClass.findMethodsByName(name, true);
                if (match == null) {
                    return superMatches;
                } else if (superMatches.length == 0) {
                    if (all != null) {
                        return all.toArray(PsiMethod.EMPTY_ARRAY);
                    } else {
                        return new PsiMethod[]{match};
                    }
                } else {
                    List<PsiMethod> methods = Lists.newArrayList();
                    if (all == null) {
                        all = Collections.singletonList(match);
                    }

                    methods.addAll(all);

                    // Check for masking
                    for (PsiMethod fromSuper : superMatches) {
                        PsiParameterList parameterList = fromSuper.getParameterList();
                        PsiParameter[] parameters = parameterList.getParameters();
                        boolean masked = false;
                        for (PsiMethod local : all) {
                            PsiParameterList superParameterList = local.getParameterList();
                            if (parameterList.getParametersCount() ==
                                    superParameterList.getParametersCount()) {
                                boolean matches = true;
                                PsiParameter[] superParams = superParameterList.getParameters();
                                for (int i = 0; i < parameters.length; i++) {
                                    PsiParameter parameter = parameters[i];
                                    PsiParameter superParam = superParams[i];
                                    if (!Objects.equal(parameter.getType(),
                                            superParam.getType())) {
                                        matches = false;
                                        break;
                                    }
                                }
                                if (!matches) {
                                    masked = true;
                                    break;
                                }
                            }
                        }
                        if (!masked) {
                            methods.add(fromSuper);
                        }
                    }
                }
            }
        }
        if (all != null) {
            return all.toArray(PsiMethod.EMPTY_ARRAY);
        } else if (match != null) {
            return new PsiMethod[] { match };
        } else {
            return PsiMethod.EMPTY_ARRAY;
        }
    }

    @NonNull
    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(
            String s, boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiClass findInnerClassByName(String name, boolean checkBases) {
        for (PsiClass cls : getInnerClasses()) {
            if (name.equals(cls.getName())) {
                return cls;
            }
        }
        if (checkBases) {
            PsiClass superClass = getSuperClass();
            if (superClass != null) {
                return superClass.findInnerClassByName(name, true);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public PsiElement getLBrace() {
        // Position at TypeDeclaration.bodyStart - 1
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiElement getRBrace() {
        // Position at TypeDeclaration.bodyEnd + 1
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getScope() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isInheritor(@NonNull PsiClass psiClass, boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isInheritorDeep(PsiClass psiClass, PsiClass psiClass1) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiDocComment getDocComment() {
        // TODO: populate from TypeDeclaration.javadoc
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isDeprecated() {
        SourceTypeBinding binding = ((TypeDeclaration)mNativeNode).binding;
        return binding != null
                && (binding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitClass(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void sortChildren() {
        // We're initializing the class members separated by fields and methods,
        // but as children they should appear in source order, so reorder the children
        // now
        if (mFirstChild == null) {
            return;
        }
        PsiElement[] children = getChildren();
        Arrays.sort(children, new Comparator<PsiElement>() {
            @Override
            public int compare(PsiElement o1, PsiElement o2) {
                int delta = o1.getTextRange().getStartOffset() - o2.getTextRange().getStartOffset();
                if (delta == 0) {
                    delta = o1.getTextRange().getEndOffset() - o2.getTextRange().getEndOffset();
                }
                return delta;
            }
        });
        EcjPsiSourceElement last = null;
        for (PsiElement child : children) {
            EcjPsiSourceElement element = (EcjPsiSourceElement)child;
            element.mPrevSibling = last;
            if (last == null) {
                mFirstChild = element;
                element.mPrevSibling = null;
            } else {
                last.mNextSibling = element;
            }
            mLastChild = element;
            last = element;
        }
        mLastChild.mNextSibling = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        TypeDeclaration typeDeclaration = (TypeDeclaration) mNativeNode;
        SourceTypeBinding binding = typeDeclaration.binding;
        TypeBinding otherBinding;
        if (o instanceof EcjPsiClass) {
            TypeDeclaration otherTypeDeclaration =
                    (TypeDeclaration) (((EcjPsiClass) o).getNativeNode());
            assert otherTypeDeclaration != null;
            otherBinding = otherTypeDeclaration.binding;
            if (binding == null || otherBinding == null) {
                return typeDeclaration.equals(otherTypeDeclaration);
            }
            return binding.equals(otherBinding);
        } else if (o instanceof EcjPsiBinaryClass) {
            otherBinding = (ReferenceBinding) (((EcjPsiBinaryClass) o).getBinding());
            return binding != null && otherBinding != null && binding.equals(otherBinding);
        }

        return false;
    }

    @Override
    public int hashCode() {
        SourceTypeBinding binding = ((TypeDeclaration) mNativeNode).binding;
        return binding != null ? binding.hashCode() : 0;
    }

    @Nullable
    public ReferenceBinding getBinding() {
        return ((TypeDeclaration) mNativeNode).binding;
    }
}
