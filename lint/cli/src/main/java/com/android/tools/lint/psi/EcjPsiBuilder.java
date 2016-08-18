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

package com.android.tools.lint.psi;

import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.EcjParser;
import com.android.tools.lint.EcjSourceFile;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceList.Role;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.AnnotationMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.eclipse.jdt.internal.compiler.ast.ArrayReference;
import org.eclipse.jdt.internal.compiler.ast.AssertStatement;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression;
import org.eclipse.jdt.internal.compiler.ast.Block;
import org.eclipse.jdt.internal.compiler.ast.BreakStatement;
import org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.eclipse.jdt.internal.compiler.ast.CastExpression;
import org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess;
import org.eclipse.jdt.internal.compiler.ast.Clinit;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.CompoundAssignment;
import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ContinueStatement;
import org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.eclipse.jdt.internal.compiler.ast.EmptyStatement;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.eclipse.jdt.internal.compiler.ast.ForeachStatement;
import org.eclipse.jdt.internal.compiler.ast.FunctionalExpression;
import org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.Initializer;
import org.eclipse.jdt.internal.compiler.ast.InstanceOfExpression;
import org.eclipse.jdt.internal.compiler.ast.LabeledStatement;
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation;
import org.eclipse.jdt.internal.compiler.ast.OperatorExpression;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.PostfixExpression;
import org.eclipse.jdt.internal.compiler.ast.PrefixExpression;
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedSuperReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedThisReference;
import org.eclipse.jdt.internal.compiler.ast.Reference;
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.SuperReference;
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.eclipse.jdt.internal.compiler.ast.SynchronizedStatement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.ThrowStatement;
import org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.UnaryExpression;
import org.eclipse.jdt.internal.compiler.ast.UnionTypeReference;
import org.eclipse.jdt.internal.compiler.ast.WhileStatement;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

import java.lang.reflect.Modifier;
import java.util.List;

@SuppressWarnings({"Duplicates", "MethodMayBeStatic"})
public class EcjPsiBuilder {

    /**
     * Consistency checking; only enabled when true (and compiled out of the binary
     * when false)
     */
    static final boolean DEBUG = false;

    private static boolean sAddWhitespaceNodes = false;
    private static boolean sAddParentheses = false;

    private static final char[] PACKAGE_INFO = "package-info".toCharArray();

    @NonNull
    private final EcjPsiManager mManager;

    EcjPsiBuilder(@NonNull EcjPsiManager manager) {
        mManager = manager;
    }

    // Debugging only
    @Nullable
    static List<EcjPsiSourceElement> sElements;

    /** Unit test options: enable AST mutation where we insert extra nodes. */
    public static void setDebugOptions(boolean addWhitespace, boolean addParens) {
        sAddWhitespaceNodes = addWhitespace;
        sAddParentheses = addParens;
    }

    @SuppressWarnings("ConstantConditions")
    private static void checkElements(@NonNull EcjPsiSourceElement root) {
        if (DEBUG) {
            // All elements should be below the given root (finds bugs where we construct
            // AST nodes without adding them into the hierarchy)

            for (EcjPsiSourceElement element : sElements) {
                EcjPsiSourceElement current = element;
                while (current != null) {
                    if (current == root) {
                        break;
                    }
                    current = current.getParent();
                    assert current != null : element;
                }
            }

            // Make sure all children are in consecutive offset order
            checkChildOrder(root);
        }
    }

    @SuppressWarnings({"ConstantConditions", "AssertWithSideEffects"})
    private static void checkChildOrder(@NonNull EcjPsiSourceElement element) {
        if (DEBUG) {
            if (element.mFirstChild == null) {
                return;
            }

            // Recurse
            EcjPsiSourceElement current = element.mFirstChild;
            while (current != null) {
                checkChildOrder(current);
                current = current.mNextSibling;
            }

            // Check this node
            assert element.getTextRange() != null;
            if (!(element instanceof PsiJavaFile)) {
                assert element.getParent() != null : element.toString();
                assert element.getTextRange().getStartOffset()
                        >= element.getParent().getTextRange().getStartOffset()
                        : "Element " + element + " overlaps parent " + element.getParent();
                assert element.getTextRange().getEndOffset()
                        <= element.getParent().getTextRange().getEndOffset()
                        : "Element " + element + " overlaps parent " + element.getParent();
            }
            PsiElement curr = element.getFirstChild();
            PsiElement prev = null;
            while (curr != null) {
                // children in classes aren't ordered properly
                if (prev != null && (!(element instanceof PsiClass))) {
                    assert curr.getTextRange() != null : curr.toString();
                    assert curr.getTextRange().getStartOffset()
                            >= prev.getTextRange().getEndOffset()
                            : "Siblings overlap: " + prev + " vs " + curr;
                }
                assert element == curr.getParent();
                assert curr.getPrevSibling() == prev;
                prev = curr;
                curr = curr.getNextSibling();
            }
        }
    }

    static void registerElement(@NonNull EcjPsiSourceElement element) {
        if (DEBUG) {
            if (sElements == null) {
                sElements = Lists.newArrayListWithCapacity(1000);
            }
            sElements.add(element);
        }
    }

    // For debugging purposes, insert whitespaces in all the valid places to make sure
    // that lint checks properly handle this scenario (and don't do incorrect things
    // like only look at the immediate siblings rather than skipping whitespace nodes
    // if necessary)
    private void insertWhitespace(
            @NonNull EcjPsiSourceElement root) {
        EcjPsiSourceElement current = root.mFirstChild;
        while (current != null) {
            insertWhitespace(current);
            current = current.mNextSibling;
        }

        current = root.mFirstChild;
        if (root.mFirstChild == null) {
            // Don't insert whitespace nodes into empty nodes (such as literal nodes etc)
            return;
        }

        while (current != null) {
            EcjPsiSourceElement next = current.mNextSibling;
            TestWhitespace inserted = new TestWhitespace();
            int offset = current.getTextRange().getStartOffset();
            inserted.setRange(offset, offset);

            inserted.mParent = root;
            if (root.mFirstChild == current) {
                root.mFirstChild = inserted;
            }
            if (current.mPrevSibling != null) {
                inserted.mPrevSibling = current.mPrevSibling;
                current.mPrevSibling.mNextSibling = inserted;
                current.mPrevSibling = null;
            }
            inserted.mNextSibling = current;
            current.mPrevSibling = inserted;

            current = next;
        }

        // Also insert a whitespace node at the end
        TestWhitespace inserted = new TestWhitespace();
        inserted.mParent = root;
        root.mLastChild.mNextSibling = inserted;
        inserted.mPrevSibling = root.mLastChild;
        root.mLastChild = inserted;
    }

    // For debugging purposes, insert parentheses in all the valid places to make sure
    // that lint checks properly handle this scenario (e.g. by not hardcoding checks
    // for the direct parent or properly looking inside parenthesized expressions)
    private void insertParentheses(
            @NonNull EcjPsiSourceElement root) {
        EcjPsiSourceElement current = root.mFirstChild;
        while (current != null) {
            insertParentheses(current);
            current = current.mNextSibling;
        }

        current = root.mFirstChild;
        while (current != null) {
            EcjPsiSourceElement next = current.mNextSibling;

            if (current instanceof PsiExpression) {
                TestParentheses inserted = new TestParentheses((PsiExpression) current);
                inserted.mRange = current.mRange;
                inserted.mFirstChild = inserted.mLastChild = current;
                current.mParent = inserted;
                inserted.mParent = root;
                if (root.mFirstChild == current) {
                    root.mFirstChild = inserted;
                }
                if (root.mLastChild == current) {
                    root.mLastChild = inserted;
                }
                if (current.mPrevSibling != null) {
                    inserted.mPrevSibling = current.mPrevSibling;
                    current.mPrevSibling.mNextSibling = inserted;
                    current.mPrevSibling = null;
                }
                if (current.mNextSibling != null) {
                    inserted.mNextSibling = current.mNextSibling;
                    current.mNextSibling.mPrevSibling = inserted;
                    current.mNextSibling = null;
                }
            }

            current = next;
        }
    }

    private class TestWhitespace extends EcjPsiSourceElement implements PsiWhiteSpace {
        public TestWhitespace() {
            super(EcjPsiBuilder.this.mManager, null);
        }

        @Override
        public void accept(@NonNull PsiElementVisitor visitor) {
            visitor.visitWhiteSpace(this);
        }
    }

    private class TestParentheses extends EcjPsiExpression implements PsiParenthesizedExpression {
        private final PsiExpression mExpression;

        public TestParentheses(@NonNull PsiExpression expression) {
            super(EcjPsiBuilder.this.mManager, null);
            mExpression = expression;
        }

        @Override
        public void accept(@NonNull PsiElementVisitor visitor) {
            if (visitor instanceof JavaElementVisitor) {
                ((JavaElementVisitor)visitor).visitParenthesizedExpression(this);
            }
            else {
                visitor.visitElement(this);
            }
        }

        @Nullable
        @Override
        public PsiExpression getExpression() {
            return mExpression;
        }

        @Nullable
        @Override
        public PsiType getType() {
            return mExpression.getType();
        }
    }

    @NonNull
    public static EcjPsiJavaFile create(
            @NonNull EcjPsiManager manager,
            @NonNull CompilationUnitDeclaration unit,
            @NonNull EcjSourceFile source) {

        EcjPsiBuilder builder = new EcjPsiBuilder(manager);

        if (DEBUG) {
            sElements = null;
        }

        EcjPsiJavaFile file = builder.toFile(unit, source);

        if (DEBUG) {
            checkElements(file);
        }

        if (sAddParentheses) {
            builder.insertParentheses(file);
        }
        if (sAddWhitespaceNodes) {
            builder.insertWhitespace(file);
        }

        return file;
    }

    @NonNull
    static TextRange toRange(long ecjPos) {
        int start = (int) (ecjPos >> 32);
        int end = (int) (ecjPos & 0xFFFFFFFFL) + 1;
        return new TextRange(start, end);
    }

    @NonNull
    static TextRange toRange(int start, int end) {
        return new TextRange(start, end);
    }

    @NonNull
    static TextRange toRange(@NonNull ASTNode node) {
        int sourceStart = node.sourceStart;
        int endOffset = node.sourceEnd + 1;

        // The source offsets include parentheses, but we don't currently create
        // PsiParenthesizedExpression's. We do however need to fix the source
        // offsets such that they point to the correct region excluding the parenthesis
        // range.
        int parens = (node.bits & ASTNode.ParenthesizedMASK) >> ASTNode.ParenthesizedSHIFT;
        if (parens > 0) {
            sourceStart += parens;
            endOffset -= parens;
            if (endOffset < sourceStart) {
                // This shouldn't happen, but has for some source files discovered by
                // the unit tests: Give up on parenthesis adjustment if it leads to impossible
                // offsets
                sourceStart = node.sourceStart;
                endOffset = node.sourceEnd + 1;
            }
        }

        return new TextRange(sourceStart, endOffset);
    }

    @NonNull
    EcjPsiPackageStatement toPackageStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull ImportReference node) {
        EcjPsiPackageStatement statement = new EcjPsiPackageStatement(mManager, node);
        // Is this necessary?
        statement.setRange(toRange(node.declarationSourceStart, node.declarationSourceEnd + 1));
        parent.adoptChild(statement);
        statement.setPackageName(getTypeName(node.tokens));
        // Only needed for annotations
        //noinspection VariableNotUsedInsideIf
        if (node.annotations != null) {
            EcjPsiModifierList modifierList = toModifierList(statement, node);
            statement.setModifierList(modifierList);
        }
        return statement;
    }

    private EcjPsiModifierList toModifierList(@NonNull EcjPsiSourceElement parent,
            @NonNull ImportReference node) {
        EcjPsiModifierList modifierList = toModifierList(parent, node.modifiers, node.annotations);
        modifierList.setRange(node.declarationSourceStart, node.declarationSourceStart);
        return modifierList;
    }

    private EcjPsiModifierList toModifierList(@NonNull EcjPsiSourceElement parent,
            @NonNull AbstractMethodDeclaration node) {
        EcjPsiModifierList modifierList = toModifierList(parent, node.modifiers, node.annotations);
        int start = node.modifiersSourceStart > 0
                ? node.modifiersSourceStart : node.declarationSourceStart;
        int end = start;
        if (node.javadoc != null) {
            start = Math.max(start, node.javadoc.sourceEnd+1);
            end = Math.max(end, start);
        }
        if (node.annotations != null && node.annotations.length > 0) {
            // TypeDeclaration range *includes* modifier keywords and annotations
            Annotation last = node.annotations[node.annotations.length - 1];
            end = Math.max(end, last.declarationSourceEnd + 1);
        }
        modifierList.setRange(start, end);
        return modifierList;
    }

    private EcjPsiModifierList toModifierList(@NonNull EcjPsiSourceElement parent,
            @NonNull TypeDeclaration node) {
        EcjPsiModifierList modifierList = toModifierList(parent, node.modifiers, node.annotations);
        int start = node.modifiersSourceStart > 0
                ? node.modifiersSourceStart : node.declarationSourceStart;
        int end = start;
        if (node.javadoc != null) {
            start = Math.max(start, node.javadoc.sourceEnd+1);
            end = Math.max(end, start);
        }
        if (node.annotations != null && node.annotations.length > 0) {
            // TypeDeclaration range *includes* modifier keywords and annotations
            Annotation last = node.annotations[node.annotations.length - 1];
            end = Math.max(end, last.declarationSourceEnd + 1);
        }
        modifierList.setRange(start, end);
        return modifierList;
    }

    private EcjPsiModifierList toModifierList(@NonNull EcjPsiSourceElement parent,
            @NonNull AbstractVariableDeclaration node) {
        EcjPsiModifierList modifierList = toModifierList(parent, node.modifiers, node.annotations);
        int start = node.modifiersSourceStart > 0 ? node.modifiersSourceStart
                : node.declarationSourceStart;
        int end = start;
        if (node.annotations != null && node.annotations.length > 0) {
            // TypeDeclaration range *includes* modifier keywords and annotations
            Annotation last = node.annotations[node.annotations.length - 1];
            end = Math.max(end, last.declarationSourceEnd + 1);
        }
        modifierList.setRange(start, end);
        return modifierList;
    }

    private EcjPsiModifierList toModifierList(@NonNull EcjPsiSourceElement parent,
            int modifiers, Annotation[] annotations) {
        int flags = 0;
        if ((modifiers & ClassFileConstants.AccStatic) != 0) {
            flags |= Modifier.STATIC;
        }
        if ((modifiers & ClassFileConstants.AccFinal) != 0) {
            flags |= Modifier.FINAL;
        }
        if ((modifiers & ClassFileConstants.AccAbstract) != 0) {
            flags |= Modifier.ABSTRACT;
        }
        if ((modifiers & ClassFileConstants.AccPrivate) != 0) {
            flags |= Modifier.PRIVATE;
        }
        if ((modifiers & ClassFileConstants.AccProtected) != 0) {
            flags |= Modifier.PROTECTED;
        }
        if ((modifiers & ClassFileConstants.AccPublic) != 0) {
            flags |= Modifier.PUBLIC;
        }
        if ((modifiers & ClassFileConstants.AccSynchronized) != 0) {
            flags |= Modifier.SYNCHRONIZED;
        }
        if ((modifiers & ClassFileConstants.AccVolatile) != 0) {
            flags |= Modifier.VOLATILE;
        }
        if ((modifiers & ExtraCompilerModifiers.AccDefaultMethod) != 0) {
            flags |= EcjPsiModifierList.DEFAULT_MASK;
        }

        EcjPsiModifierList modifierList = new EcjPsiModifierList(mManager, flags);
        parent.adoptChild(modifierList);
        EcjPsiAnnotation[] psiAnnotations = toAnnotations(modifierList, annotations);
        modifierList.setAnnotations(psiAnnotations);
        return modifierList;
    }

    @NonNull
    private EcjPsiAnnotation[] toAnnotations(
            @NonNull EcjPsiSourceElement parent,
            @Nullable Annotation[] annotations) {
        List<EcjPsiAnnotation> list = Lists.newArrayList();
        if (annotations != null) {
            for (Annotation ecjAnnotation : annotations) {
                EcjPsiAnnotation psiAnnotation = toAnnotation(parent, ecjAnnotation);
                if (psiAnnotation != null) {
                    list.add(psiAnnotation);
                }
            }
        }
        return list.toArray(new EcjPsiAnnotation[list.size()]);
    }

    @Nullable
    private EcjPsiAnnotation toAnnotation(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Annotation ecjAnnotation) {
        EcjPsiAnnotation psiAnnotation = new EcjPsiAnnotation(mManager, ecjAnnotation);
        parent.adoptChild(psiAnnotation);
        EcjPsiJavaCodeReferenceElement nameElement = toTypeReference(
                psiAnnotation, ecjAnnotation.type);
        psiAnnotation.setNameElement(nameElement);

        //noinspection StatementWithEmptyBody
        if (ecjAnnotation instanceof MarkerAnnotation) {
            // Nothing to do
        } else if (ecjAnnotation instanceof NormalAnnotation) {
            NormalAnnotation na = (NormalAnnotation) ecjAnnotation;
            psiAnnotation.setParameterList(toAnnotationParameterList(psiAnnotation,
                    na.memberValuePairs));
        } else if (ecjAnnotation instanceof SingleMemberAnnotation) {
            SingleMemberAnnotation na = (SingleMemberAnnotation) ecjAnnotation;
            psiAnnotation.setParameterList(toAnnotationParameterList(psiAnnotation,
                    na.memberValuePairs()));
        }

        return psiAnnotation;
    }

    @NonNull
    private EcjPsiNameValuePair toMemberValuePair(
            @NonNull EcjPsiSourceElement parent,
            @NonNull MemberValuePair memberValuePair) {
        EcjPsiNameValuePair pair = new EcjPsiNameValuePair(mManager, memberValuePair);
        parent.adoptChild(pair);
        if (memberValuePair.name != null
                // Only add a name identifier if the user specified one. ECJ
                // pretends the value is there even if it hasn't been specified.
                && memberValuePair.sourceStart < memberValuePair.value.sourceStart) {
            pair.setNameIdentifier(toIdentifier(pair, memberValuePair.name,
                    toRange(memberValuePair.sourceStart, memberValuePair.sourceStart
                            + memberValuePair.name.length)));
        }

        Expression value = memberValuePair.value;
        if (value != null) {
            if (value instanceof Annotation) {
                EcjPsiAnnotation psiAnnotation = toAnnotation(pair, ((Annotation) value));
                if (psiAnnotation != null) {
                    pair.setMemberValue(psiAnnotation);
                    // Annotations have incorrect source offsets on the expression
                    // node; incorporate the actual offset found on the annotation
                    pair.setRange(memberValuePair.sourceStart,
                            psiAnnotation.getTextRange().getEndOffset());

                } else {
                    pair.setRange(memberValuePair.sourceStart, value.sourceEnd + 1);
                }
            } else {
                pair.setMemberValue(toMemberValue(pair, value));
                pair.setRange(memberValuePair.sourceStart, value.sourceEnd + 1);
            }
        } else {
            pair.setRange(memberValuePair.sourceStart, memberValuePair.sourceEnd + 1);
        }

        return pair;
    }

    @NonNull
    private EcjPsiImportList toImportList(
            @NonNull EcjPsiSourceElement parent,
            @Nullable ImportReference[] references) {
        EcjPsiImportList importList = new EcjPsiImportList(mManager);
        parent.adoptChild(importList);

        if (references != null) {
            List<EcjPsiImport> list = Lists.newArrayListWithCapacity(references.length);
            for (ImportReference node : references) {
                list.add(toImport(importList, node));
            }
            importList.setImports(list);

            // Update offset range
            PsiElement firstChild = importList.getFirstChild();
            if (firstChild != null) {
                PsiElement lastChild = importList.getLastChild();
                importList.setRange(firstChild.getTextOffset(),
                        lastChild.getTextOffset() + lastChild.getTextLength());
            }
        }

        return importList;
    }

    private EcjPsiImport toImport(EcjPsiImportList parent, ImportReference importReference) {
        String typeName = getTypeName(importReference.getImportName());
        boolean onDemand = (importReference.bits & ASTNode.OnDemand) != 0;
        EcjPsiImport ref;
        if ((importReference.modifiers & ClassFileConstants.AccStatic) != 0) {
            ref = new EcjPsiStaticImport(mManager, importReference, typeName, onDemand);
        } else {
            ref = new EcjPsiImport(mManager, importReference, typeName, onDemand);
        }
        ref.setReference(toImportReference(ref, importReference));
        parent.adoptChild(ref);
        return ref;
    }

    @NonNull
    private EcjPsiClass toClass(
            @NonNull EcjPsiSourceElement parent,
            @NonNull TypeDeclaration declaration) {
        EcjPsiClass cls = new EcjPsiClass(mManager, declaration,
                new String(declaration.name));
        parent.adoptChild(cls);
        cls.setRange(declaration.declarationSourceStart, declaration.declarationSourceEnd + 1);
        EcjPsiModifierList modifierList = toModifierList(cls, declaration);
        cls.setNameIdentifier(toIdentifier(cls, declaration.name, toRange(declaration)));
        cls.setModifierList(modifierList);

        int kind = TypeDeclaration.kind(declaration.modifiers);
        switch (kind) {
            case TypeDeclaration.CLASS_DECL: {
                // Class: set body, super class, extended interfaces, and type variables
                cls.setSuperClass(declaration.superclass);
                cls.setSuperInterfaces(declaration.superInterfaces);
                cls.setTypeParameterList(toTypeParameterList(cls, declaration.typeParameters));
                cls.setExtendsList(toTypeReferenceList(cls, declaration.superclass,
                        Role.EXTENDS_LIST));
                cls.setImplementsList(toTypeReferenceList(cls, declaration.superInterfaces,
                        Role.IMPLEMENTS_LIST));
                initializeClassBody(cls, declaration);
                break;
            }
            case TypeDeclaration.INTERFACE_DECL: {
                // Interface: set body, super interface, and type variables
                modifierList.setModifiers(modifierList.getModifiers()
                        | Modifier.STATIC | Modifier.ABSTRACT);
                cls.setSuperInterfaces(declaration.superInterfaces);
                cls.setTypeParameterList(toTypeParameterList(cls, declaration.typeParameters));
                cls.setImplementsList(toTypeReferenceList(cls, declaration.superInterfaces,
                        Role.EXTENDS_LIST));
                initializeClassBody(cls, declaration);
                break;
            }
            case TypeDeclaration.ENUM_DECL: {
                // Enum: set enum body, and implemented interfaces
                cls.setSuperInterfaces(declaration.superInterfaces);
                cls.setImplementsList(toTypeReferenceList(cls, declaration.superInterfaces,
                        Role.IMPLEMENTS_LIST));
                modifierList.setModifiers(modifierList.getModifiers()
                        | Modifier.STATIC | Modifier.FINAL);
                initializeClassBody(cls, declaration);
                break;
            }
            case TypeDeclaration.ANNOTATION_TYPE_DECL: {
                // Annotation: set body
                modifierList.setModifiers(modifierList.getModifiers()
                        | Modifier.STATIC | Modifier.ABSTRACT);
                initializeClassBody(cls, declaration);
                break;
            }
        }

        return cls;
    }

    @Nullable
    private EcjPsiEnumConstantInitializer toEnumInitializer(
            @NonNull EcjPsiEnumConstant parent,
            @NonNull Expression expression) {
        if (!(expression instanceof QualifiedAllocationExpression)) {
            return null;
        }
        QualifiedAllocationExpression node = (QualifiedAllocationExpression) expression;
        TypeDeclaration declaration = node.anonymousType;
        if (declaration == null) {
            return null;
        }
        EcjPsiEnumConstantInitializer cls = new EcjPsiEnumConstantInitializer(mManager,
                declaration);
        parent.adoptChild(cls);
        cls.setRange(declaration.declarationSourceStart, declaration.declarationSourceEnd + 1);
        cls.setSuperClass(declaration.superclass);
        initializeClassBody(cls, declaration);

        PsiClass containingClass = parent.getContainingClass();
        if (containingClass != null) {
            EcjPsiModifierList modifierList = (EcjPsiModifierList) containingClass.getModifierList();
            if (modifierList != null) {
                modifierList.setModifiers(modifierList.getModifiers() & ~Modifier.FINAL);
            }
        }
        return cls;
    }

    private void initializeClassBody(@NonNull EcjPsiClass cls,
            @NonNull TypeDeclaration declaration) {
        List<PsiClassInitializer> initializers = null;
        if (declaration.fields != null) {
            List<EcjPsiField> fields = Lists.newArrayList();
            for (FieldDeclaration field : declaration.fields) {
                if (field instanceof Initializer) {
                    if (initializers == null) {
                        initializers = Lists.newArrayListWithExpectedSize(4);
                    }
                    initializers.add(toClassInitializer(cls, (Initializer)field));
                } else //noinspection VariableNotUsedInsideIf
                    if (field.type == null) {
                    fields.add(toEnumConstant(cls, field));
                } else {
                    fields.add(toField(cls, field));
                }
            }
            cls.setFields(fields);
        }
        if (declaration.methods != null) {
            List<EcjPsiMethod> methods = Lists.newArrayList();
            for (AbstractMethodDeclaration method : declaration.methods) {
                if (method.isClinit()) {
                    Clinit clinit = (Clinit) method;
                    if (clinit.statements == null) {
                        continue;
                    }
                    if (initializers == null) {
                        initializers = Lists.newArrayListWithExpectedSize(4);
                    }
                    initializers.add(toClassInitializer(cls, clinit));
                } else {
                    EcjPsiMethod psiMethod = toMethod(cls, method);
                    if (psiMethod != null) {
                        methods.add(psiMethod);
                    } // else: default constructor not present in source; we skip those
                }
            }
            cls.setMethods(methods);
        }
        if (initializers != null) {
            cls.setInitializers(initializers);
        }
        if (declaration.memberTypes != null && declaration.memberTypes.length > 0) {
            List<EcjPsiClass> methods = Lists.newArrayList();
            for (TypeDeclaration method : declaration.memberTypes) {
                methods.add(toClass(cls, method));
            }
            cls.setInnerClasses(methods.toArray(new EcjPsiClass[0]));
        }
        cls.sortChildren();
    }

    @NonNull
    private PsiClassInitializer toClassInitializer(@NonNull EcjPsiClass parent, Clinit clinit) {
        EcjPsiClassInitializer initializer = new EcjPsiClassInitializer(mManager, parent, clinit);
        parent.adoptChild(initializer);
        if ((clinit.modifiers & ClassFileConstants.AccStatic) != 0) {
            initializer.setRange(clinit.declarationSourceStart, clinit.sourceEnd + 1);
        }
        initializer.setModifierList(toModifierList(initializer, clinit.modifiers,
                clinit.annotations));

        EcjPsiCodeBlock body = toBlock(initializer, clinit.statements, null,
                clinit.declarationSourceStart, clinit.declarationSourceEnd + 1);
        initializer.setBody(body);

        return initializer;
    }

    @NonNull
    private PsiClassInitializer toClassInitializer(@NonNull EcjPsiClass parent, Initializer init) {
        EcjPsiClassInitializer initializer = new EcjPsiClassInitializer(mManager, parent, init);
        parent.adoptChild(initializer);
        initializer.setModifierList(toModifierList(initializer, init.modifiers, init.annotations));
        EcjPsiCodeBlock body = toBlock(initializer, init.block);
        initializer.setBody(body);
        return initializer;
    }

    @Nullable
    private EcjPsiMethod toMethod(EcjPsiClass cls, AbstractMethodDeclaration method) {
        if (method instanceof ConstructorDeclaration) {
            if ((method.bits & ASTNode.IsDefaultConstructor) != 0) {
                return null;
            }
        }

        boolean isAnnotation = method instanceof AnnotationMethodDeclaration;
        EcjPsiMethod psiMethod;
        if (!isAnnotation) {
            psiMethod = new EcjPsiMethod(mManager, cls, method);
        } else {
            psiMethod = new EcjPsiAnnotationMethod(mManager, cls, method);
        }

        cls.adoptChild(psiMethod);
        EcjPsiModifierList modifierList = toModifierList(psiMethod, method);
        psiMethod.setModifierList(modifierList);
        if (cls.isInterface()) {
            boolean hasDefaultMethod = method.statements != null && method.statements.length > 0;
            int modifiers = modifierList.getModifiers();
            modifiers |= (hasDefaultMethod ? 0 : Modifier.ABSTRACT) | Modifier.PUBLIC;
            modifiers &= ~(Modifier.PROTECTED | Modifier.PRIVATE);
            modifierList.setModifiers(modifiers);
        } else if (cls.isAnnotationType()) {
            int modifiers = modifierList.getModifiers();
            modifiers |= Modifier.ABSTRACT | Modifier.PUBLIC;
            modifiers &= ~(Modifier.PROTECTED | Modifier.PRIVATE);
            modifierList.setModifiers(modifiers);
        } else if (cls.isEnum() && method.isConstructor()) {
            int modifiers = modifierList.getModifiers();
            modifiers |= Modifier.PRIVATE;
            modifiers &= ~(Modifier.PUBLIC | Modifier.PROTECTED);
            modifierList.setModifiers(modifiers);
        }

        TypeParameter[] typeParameters = method.typeParameters();
        if (typeParameters != null) {
            psiMethod.setTypeParameters(toTypeParameterList(psiMethod, typeParameters));
        }

        if (method instanceof MethodDeclaration && !method.isConstructor()) {
            TypeReference returnType = ((MethodDeclaration) method).returnType;
            if (returnType != null) {
                psiMethod.setReturnTypeElement(toTypeElement(psiMethod, returnType));
            }
        }

        psiMethod.setNameIdentifier(toIdentifier(cls, method.selector,
                toRange(method.sourceStart, method.sourceStart + method.selector.length)));

        psiMethod.setArguments(toParameterList(psiMethod, method.arguments));
        PsiReferenceList psiReferenceList = toTypeReferenceList(psiMethod, method.thrownExceptions,
                Role.THROWS_LIST);
        psiMethod.setThrownExceptions(psiReferenceList);

        PsiCodeBlock body;
        if (method instanceof ConstructorDeclaration) {
            ExplicitConstructorCall constructorCall
                    = ((ConstructorDeclaration) method).constructorCall;
            body = toBlock(psiMethod, method.statements, constructorCall,
                    method.bodyStart - 1, method.bodyEnd + 1);
        } else if (method instanceof MethodDeclaration) {
            boolean semiColonBody = ((method.modifiers & ExtraCompilerModifiers.AccSemicolonBody)
                    != 0);
            if (!method.isAbstract() && !method.isNative() && !semiColonBody) {
                body = toBlock(psiMethod, method.statements, null,
                        method.bodyStart - 1, method.bodyEnd + 1);
            } else {
                body = null;
            }
            if (isAnnotation) {
                //noinspection CastConflictsWithInstanceof
                AnnotationMethodDeclaration amd = (AnnotationMethodDeclaration) method;
                if (amd.defaultValue != null) {
                    PsiExpression defaultValue = toExpression(psiMethod, amd.defaultValue);
                    ((EcjPsiAnnotationMethod) psiMethod).setValue(defaultValue);
                }
            }
        } else {
            body = toBlock(psiMethod, method.statements, null,
                    method.bodyStart - 1, method.bodyEnd + 1);
        }
        psiMethod.setBody(body);

        return psiMethod;
    }

    @NonNull
    private PsiAnnotationMemberValue toMemberValue(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ASTNode node) {
        if (node instanceof Annotation) {
            return toAnnotation(parent, (Annotation) node);
        } else if (node instanceof ArrayInitializer) {
            return toArrayInitializerMemberValue(parent, (ArrayInitializer) node);
        } else if (node instanceof Expression) {
            return toExpression(parent, (Expression)node);
        } else {
            throw new IllegalArgumentException(node.getClass().getName());
        }
    }

    @NonNull
    private EcjPsiExpression toExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Expression expression) {
        if (expression instanceof Literal) {
            return toLiteral(parent, (Literal) expression);
        } else if (expression instanceof Reference) {
            return toReferenceExpression(parent, expression);
        } else if (expression instanceof MessageSend) {
            return toCallExpression(parent, (MessageSend) expression);
        } else if (expression instanceof OperatorExpression) {
            return toOperatorExpression(parent, ((OperatorExpression) expression));
        } else if (expression instanceof Assignment) {
            return toAssignmentExpression(parent, (Assignment) expression);
        } else if (expression instanceof AllocationExpression) {
            return toNewExpression(parent, (AllocationExpression) expression);
        } else if (expression instanceof CastExpression) {
            return toCastExpression(parent, (CastExpression) expression);
        } else if (expression instanceof FunctionalExpression) {
            return toFunctionalExpression(parent, (FunctionalExpression) expression);
        } else if (expression instanceof ClassLiteralAccess) {
            return toClassObjectAccessExpression(parent, ((ClassLiteralAccess) expression));
        } else if (expression instanceof ArrayInitializer) {
            return toArrayInitializerExpression(parent, (ArrayInitializer) expression);
        } else if (expression instanceof ArrayAllocationExpression) {
            return toArrayAllocationExpression(parent, ((ArrayAllocationExpression) expression));
        } else {
            throw new IllegalArgumentException(expression.getClass().getName());
        }
    }

    @NonNull
    private EcjPsiArrayInitializerExpression toArrayInitializerExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ArrayInitializer expression) {
        EcjPsiArrayInitializerExpression e = new EcjPsiArrayInitializerExpression(mManager,
                expression);
        if (expression.expressions != null) {
            PsiExpression initializers[] = new PsiExpression[expression.expressions.length];
            for (int i = 0; i < expression.expressions.length; i++) {
                initializers[i] = toExpression(e, expression.expressions[i]);
            }
            e.setInitializers(initializers);
        } else {
            e.setInitializers(PsiExpression.EMPTY_ARRAY);
        }
        parent.adoptChild(e);
        return e;
    }

    @NonNull
    private EcjPsiArrayInitializerMemberValue toArrayInitializerMemberValue(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ArrayInitializer expression) {
        EcjPsiArrayInitializerMemberValue e = new EcjPsiArrayInitializerMemberValue(mManager,
                expression);
        if (expression.expressions != null) {
            PsiAnnotationMemberValue initializers[] =
                    new PsiAnnotationMemberValue[expression.expressions.length];
            for (int i = 0; i < expression.expressions.length; i++) {
                initializers[i] = toMemberValue(e, expression.expressions[i]);
            }
            e.setInitializers(initializers);
        } else {
            e.setInitializers(PsiExpression.EMPTY_ARRAY);
        }
        parent.adoptChild(e);
        return e;
    }

    @NonNull
    private EcjPsiExpression toFunctionalExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull FunctionalExpression expression) {
        if (expression instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression) expression;
            EcjPsiLambdaExpression e = new EcjPsiLambdaExpression(mManager, lambda);
            e.setParameterList(toParameterList(e, lambda.arguments));
            if (lambda.body instanceof Expression) {
                e.setBody(toExpression(e, (Expression) lambda.body));
            } else {
                e.setBody(toStatement(e, lambda.body));
            }
            parent.adoptChild(e);
            return e;
        } else if (expression instanceof ReferenceExpression) {
            ReferenceExpression ref = (ReferenceExpression) expression;
            EcjPsiMethodReferenceExpression e = new EcjPsiMethodReferenceExpression(mManager, ref);
            e.setQualifier(toExpression(e, ref.lhs));
            e.setParameterList(toTypeParameterList(e, ref.typeArguments));
            if (EcjParser.sameChars(CONSTRUCTOR_NAME, ref.selector)) {
                // When user specifies Class:new we end up with "<init>" instead of "new"
                // as the identifier
                e.setReferenceNameElement(toIdentifier(e, "new", toRange(ref.nameSourceStart,
                        ref.nameSourceStart + 3)));
            } else {
                e.setReferenceNameElement(toIdentifier(e, ref.selector, toRange(ref.nameSourceStart,
                        ref.nameSourceStart + ref.selector.length)));
            }
            parent.adoptChild(e);
            return e;
        }

        throw new IllegalArgumentException(expression.getClass().getName());
    }

    @NonNull
    private EcjPsiClassObjectAccessExpression toClassObjectAccessExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ClassLiteralAccess expression) {
        EcjPsiClassObjectAccessExpression accessExpression = new EcjPsiClassObjectAccessExpression(
                mManager, expression);
        accessExpression.setOperand(toTypeElement(accessExpression, expression.type));
        parent.adoptChild(accessExpression);
        return accessExpression;
    }

    @NonNull
    private EcjPsiExpression toCastExpression(@NonNull EcjPsiSourceElement parent,
            @NonNull CastExpression expression) {
        EcjPsiTypeCastExpression cast = new EcjPsiTypeCastExpression(mManager, expression);
        cast.setCastType(toTypeElement(cast, expression.type));
        cast.setOperand(toExpression(cast, expression.expression));
        parent.adoptChild(cast);
        return cast;
    }

    @NonNull
    private EcjPsiMethodCallExpression toCallExpression(@NonNull EcjPsiSourceElement parent,
            @NonNull MessageSend send) {
        EcjPsiMethodCallExpression call = new EcjPsiMethodCallExpression(mManager, send);
        parent.adoptChild(call);
        EcjPsiReferenceExpression methodCall = new EcjPsiReferenceExpression(mManager, send);
        call.adoptChild(methodCall);
        if (send.receiver != null && !send.receiverIsImplicitThis()) {
            EcjPsiExpression qualifier = toExpression(methodCall, send.receiver);
            methodCall.setQualifier(qualifier);
        }
        EcjPsiIdentifier nameElement = toIdentifier(methodCall, send.selector,
                toRange(send.nameSourcePosition));
        methodCall.setNameElement(nameElement);
        methodCall.setRange(send.sourceStart, nameElement.getTextRange().getEndOffset());
        call.setMethodExpression(methodCall);

        call.setArgumentList(toArguments(call, send.arguments));
        if (send.typeArguments != null) {
            call.setTypeArgumentList(toTypeParameterList(call, send.typeArguments));
        }

        return call;
    }

    @NonNull
    private EcjPsiNewExpression toNewExpression(@NonNull EcjPsiSourceElement parent,
            @NonNull AllocationExpression send) {
        EcjPsiNewExpression call = new EcjPsiNewExpression(mManager, send);

        QualifiedAllocationExpression node = null;
        if (send instanceof QualifiedAllocationExpression) {
            node = (QualifiedAllocationExpression) send;
            if (node.enclosingInstance != null) {
                call.setQualifier(toExpression(call, node.enclosingInstance));
            }
        }

        if (node != null && node.anonymousType != null) {
            EcjPsiAnonymousClass cls = new EcjPsiAnonymousClass(mManager, node.anonymousType);
            call.adoptChild(cls);
            cls.setSuperClass(node.anonymousType.superclass);
            cls.setSuperInterfaces(node.anonymousType.superInterfaces);
            cls.setRange(node.anonymousType.declarationSourceStart,
                    node.anonymousType.declarationSourceEnd + 1);

            EcjPsiJavaCodeReferenceElement typeReference = toTypeReference(cls, send.type);
            cls.setBaseClassReference(typeReference);
// TODO: Test with new List<X>() - hmm arguments vs parameters!
//            if (send.typeArguments != null) {
//                System.out.println("what here?");
//                cls.setTypeParameterList(toTypeParameterList(call, send.typeArguments));
            //cls.setTypeParameterList(toTypeParameterList(cls, declaration.typeParameters));
//            }

            EcjPsiExpressionList argumentList = toArguments(cls, send.arguments);
            cls.setArgumentList(argumentList);
            call.setArgumentList(argumentList);
            call.setAnonymousClass(cls);
            initializeClassBody(cls, node.anonymousType);
            cls.setInQualifiedNew(node.enclosingInstance != null);
        } else {
            EcjPsiJavaCodeReferenceElement typeReference = toTypeReference(call, send.type);
            call.setClassReference(typeReference);
            if (send.typeArguments != null) {
                call.setTypeArgumentList(toTypeParameterList(call, send.typeArguments));
            }
            EcjPsiExpressionList argumentList = toArguments(call, send.arguments);
            call.setArgumentList(argumentList);
        }
        call.setArrayDimensions(PsiExpression.EMPTY_ARRAY);

        parent.adoptChild(call);
        return call;
    }

    @NonNull
    private EcjPsiExpression toArrayAllocationExpression(@NonNull EcjPsiSourceElement parent,
            @NonNull ArrayAllocationExpression allocation) {
        EcjPsiNewExpression newExpression = new EcjPsiNewExpression(mManager, allocation);
        if (allocation.initializer != null) {
            newExpression.setArrayInitializer(toArrayInitializerExpression(newExpression,
                    allocation.initializer));
        }
        if (allocation.dimensions.length > 0 &&
                (allocation.dimensions.length != 1 || allocation.dimensions[0] != null)) {
            // PSI only includes the non-empty dimensions in its array
            List<PsiExpression> dimensions =
                    Lists.newArrayListWithCapacity(allocation.dimensions.length);
            for (int i = 0; i < allocation.dimensions.length; i++) {
                if (allocation.dimensions[i] != null) {
                    dimensions.add(toExpression(newExpression, allocation.dimensions[i]));
                }
            }
            newExpression.setArrayDimensions(dimensions.toArray(PsiExpression.EMPTY_ARRAY));
        } else {
            newExpression.setArrayDimensions(PsiExpression.EMPTY_ARRAY);
        }
        parent.adoptChild(newExpression);
        return newExpression;
    }

    @NonNull
    private EcjPsiExpression toReferenceExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Expression expression) {
        if (expression instanceof NameReference) {
            if (expression instanceof SingleNameReference) {
                SingleNameReference snr = (SingleNameReference) expression;
                EcjPsiReferenceExpression exp = new EcjPsiReferenceExpression(mManager, expression);
                parent.adoptChild(exp);
                exp.setNameElement(toIdentifier(exp, snr.token, toRange(snr)));
                return exp;
            } else if (expression instanceof QualifiedNameReference) {
                QualifiedNameReference qnr = (QualifiedNameReference) expression;
                // ECJ doesn't have a hierarchy of AST nodes, but PSI does
                char[][] tokens = qnr.tokens;
                assert tokens.length > 1; // otherwise should have been a SingleNameReference

                int startOffset = qnr.sourceStart;
                int endOffset = startOffset + tokens[0].length;
                // null instead of expression as native node: see comment inside token loop
                EcjPsiReferenceExpression prev = new EcjPsiReferenceExpression(mManager, null);
                prev.setTypeExpression(expression);
                prev.setNameElement(toIdentifier(prev, tokens[0], toRange(startOffset, endOffset)));
                prev.setRange(startOffset, endOffset);

                EcjPsiReferenceExpression curr = null;
                for (int i = 1; i < tokens.length; i++) {
                    // Passing in "expression" as the native node here is wrong:
                    // it will result in the wrong getQualifiedName() result being
                    // returned. Instead, we should try to find the corresponding
                    // bindings and pass those to the reference expression instead.
                    //curr = new EcjPsiReferenceExpression(mManager, expression);
                    // I might try for example using QualifiedNameReference#actualReceiverType
                    // for the first level qualifier, and inside of that, enclosingTypes, until
                    // I get to the package (where I have a fPackage as the PackageBinding,
                    // with successive parent packages.

                    curr = new EcjPsiReferenceExpression(mManager, null);
                    curr.setTypeExpression(expression);
                    curr.setQualifier(prev);
                    char[] name = tokens[i];
                    endOffset += name.length + 1; // +1: dot
                    curr.adoptChild(prev);
                    curr.setNameElement(toIdentifier(curr, name,
                            toRange(endOffset - name.length, endOffset)));
                    curr.setRange(startOffset, endOffset);
                    prev = curr;
                }

                assert curr != null;
                // Only set native node, used for reference resolution, on the full
                // expression, not any of the qualifiers
                curr.mNativeNode = expression;
                parent.adoptChild(curr);
                return curr;
            }
        } else if (expression instanceof ThisReference) {
            if (expression instanceof SuperReference) {
                EcjPsiSuperExpression e = new EcjPsiSuperExpression(mManager, expression);
                // ECJ seems to share a single instance of the SuperReference so
                // guess the correct offsets.
                assert !expression.isImplicitThis(); // Enforced in check which creates call
                parent.adoptChild(e);
                return e;
            } else if (expression instanceof QualifiedSuperReference) {
                QualifiedSuperReference ref = (QualifiedSuperReference) expression;
                EcjPsiSuperExpression e = new EcjPsiSuperExpression(mManager, expression);
                EcjPsiJavaCodeReferenceElement qualifier = toTypeReference(e,
                        ref.qualification);
                e.setQualifier(qualifier);
                parent.adoptChild(e);
                return e;
            } else if (expression instanceof QualifiedThisReference) {
                QualifiedThisReference ref = (QualifiedThisReference) expression;
                EcjPsiThisExpression e = new EcjPsiThisExpression(mManager, expression);
                EcjPsiJavaCodeReferenceElement qualifier = toTypeReference(e,
                        ref.qualification);
                e.setQualifier(qualifier);
                parent.adoptChild(e);
                return e;
            } else {
                assert !expression.isImplicitThis(); // Enforced in check which creates call
                EcjPsiThisExpression e = new EcjPsiThisExpression(mManager, expression);
                parent.adoptChild(e);
                return e;
            }
        } else if (expression instanceof FieldReference) {
            FieldReference ref = (FieldReference) expression;
            EcjPsiReferenceExpression exp = new EcjPsiReferenceExpression(mManager, expression);
            parent.adoptChild(exp);
            if (ref.receiver != null && !ref.receiverIsImplicitThis()) {
                exp.setQualifier(toExpression(exp, ref.receiver));
                int start = ref.receiver.sourceEnd + 2; // +1: ECJ offset. Another +1: the dot.
                int end = ref.sourceEnd + 1;
                int length = Math.min(end - start, ref.token.length);
                String name = new String(ref.token, ref.token.length - length, length);
                exp.setNameElement(toIdentifier(exp, name, new TextRange(start, end)));
            } else {
                exp.setNameElement(toIdentifier(exp, ref.token, toRange(ref)));
            }
            return exp;
        } else if (expression instanceof ArrayReference) {
            EcjPsiArrayAccessExpression accessExpression =
                    new EcjPsiArrayAccessExpression(mManager, expression);
            parent.adoptChild(accessExpression);
            ArrayReference ref = (ArrayReference) expression;
            accessExpression.setArrayExpression(toExpression(accessExpression, ref.receiver));
            accessExpression.setIndexExpression(toExpression(accessExpression, ref.position));
            return accessExpression;
        }
        throw new IllegalArgumentException(expression.getClass().getName());
    }

    @NonNull
    private EcjPsiExpression toAssignmentExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Assignment expression) {
        /*
          Assignment (org.eclipse.jdt.internal.compiler.ast)
            CompoundAssignment (org.eclipse.jdt.internal.compiler.ast)
                PostfixExpression (org.eclipse.jdt.internal.compiler.ast)
                PrefixExpression (org.eclipse.jdt.internal.compiler.ast)
            JavadocArgumentExpression (org.eclipse.jdt.internal.compiler.ast)
         */
        if (expression instanceof CompoundAssignment) {
            if (expression instanceof PrefixExpression) {
                EcjPsiPrefixExpression unaryExpression = new EcjPsiPrefixExpression(mManager,
                        expression);
                parent.adoptChild(unaryExpression);
                int operatorId = ((expression.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT);
                IElementType tokenType = ecjToPsiToken(operatorId);
                unaryExpression.setOperationType(tokenType);
                unaryExpression.setOperand(toExpression(unaryExpression, expression.lhs));
                return unaryExpression;
            } else if (expression instanceof PostfixExpression) {
                EcjPsiPostfixExpression unaryExpression = new EcjPsiPostfixExpression(mManager,
                        expression);
                parent.adoptChild(unaryExpression);
                int operatorId = ((expression.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT);
                IElementType tokenType = ecjToPsiToken(operatorId);
                unaryExpression.setOperationType(tokenType);
                unaryExpression.setOperand(toExpression(unaryExpression, expression.lhs));
                return unaryExpression;
            }
        }

        EcjPsiAssignmentExpression assignmentExpression
                = new EcjPsiAssignmentExpression(mManager, expression);
        parent.adoptChild(assignmentExpression);
        assignmentExpression.setLhs(toExpression(assignmentExpression, expression.lhs));
        assignmentExpression.setRhs(toExpression(assignmentExpression, expression.expression));
        if (expression instanceof CompoundAssignment) {
            int operatorId = ((CompoundAssignment) expression).operator;
            assignmentExpression.setOperation(ecjAssignmentToPsiToken(operatorId));
        } else {
            assignmentExpression.setOperation(JavaTokenType.EQ);
        }

        return assignmentExpression;
    }

    private EcjPsiExpression toOperatorExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull OperatorExpression expression) {
        if (expression instanceof BinaryExpression) {
            return toBinaryExpression(parent, (BinaryExpression)expression);
        } else if (expression instanceof UnaryExpression) {
            return toUnaryExpression(parent, (UnaryExpression)expression);
        } else if (expression instanceof ConditionalExpression) {
            return toConditionalExpression(parent, (ConditionalExpression)expression);
        } else if (expression instanceof InstanceOfExpression) {
            return toInstanceOfExpression(parent, (InstanceOfExpression)expression);
        } else {
            throw new IllegalArgumentException(expression.getClass().getName());
        }
    }

    @NonNull
    private EcjPsiExpression toConditionalExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ConditionalExpression expression) {
        EcjPsiConditionalExpression e = new EcjPsiConditionalExpression(mManager, expression);
        e.setCondition(toExpression(e, expression.condition));
        e.setThenExpression(toExpression(e, expression.valueIfTrue));
        e.setElseExpression(toExpression(e, expression.valueIfFalse));
        parent.adoptChild(e);
        return e;
    }

    @NonNull
    private EcjPsiExpression toInstanceOfExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull InstanceOfExpression expression) {
        EcjPsiInstanceOfExpression e = new EcjPsiInstanceOfExpression(mManager, expression);
        e.setOperand(toExpression(e, expression.expression));
        e.setCheckType(toTypeElement(e, expression.type));
        parent.adoptChild(e);
        return e;
    }

    /** Look up the PSI element type for the given ECJ operator */
    @NonNull
    private IElementType ecjToPsiToken(int operator) {
        switch (operator) {
            case OperatorIds.PLUS_PLUS:
                return JavaTokenType.PLUSPLUS;
            case OperatorIds.MINUS_MINUS:
                return JavaTokenType.MINUSMINUS;
            case OperatorIds.TWIDDLE:
                return JavaTokenType.TILDE;
            case OperatorIds.NOT:
                return JavaTokenType.EXCL;
            case OperatorIds.PLUS:
                return JavaTokenType.PLUS;
            case OperatorIds.MINUS:
                return JavaTokenType.MINUS;
            case OperatorIds.OR_OR:
                return JavaTokenType.OROR;
            case OperatorIds.AND_AND:
                return JavaTokenType.ANDAND;
            case OperatorIds.OR:
                return JavaTokenType.OR;
            case OperatorIds.XOR:
                return JavaTokenType.XOR;
            case OperatorIds.AND:
                return JavaTokenType.AND;
            case OperatorIds.EQUAL_EQUAL:
                return JavaTokenType.EQEQ;
            case OperatorIds.NOT_EQUAL:
                return JavaTokenType.NE;
            case OperatorIds.GREATER:
                return JavaTokenType.GT;
            case OperatorIds.GREATER_EQUAL:
                return JavaTokenType.GE;
            case OperatorIds.LESS:
                return JavaTokenType.LT;
            case OperatorIds.LESS_EQUAL:
                return JavaTokenType.LE;
            case OperatorIds.LEFT_SHIFT:
                return JavaTokenType.LTLT;
            case OperatorIds.RIGHT_SHIFT:
                return JavaTokenType.GTGT;
            case OperatorIds.UNSIGNED_RIGHT_SHIFT:
                return JavaTokenType.GTGTGT;
            case OperatorIds.MULTIPLY:
                return JavaTokenType.ASTERISK;
            case OperatorIds.DIVIDE:
                return JavaTokenType.DIV;
            case OperatorIds.REMAINDER:
                return JavaTokenType.PERC;
            case OperatorIds.EQUAL:
                return JavaTokenType.EQ;
            default:
                return JavaTokenType.IDENTIFIER;
        }
    }

    /** Look up the PSI element type for the given ECJ operator in an assignment */
    @NonNull
    private IElementType ecjAssignmentToPsiToken(int operator) {
        switch (operator) {
            case OperatorIds.PLUS:
                return JavaTokenType.PLUSEQ;
            case OperatorIds.MINUS:
                return JavaTokenType.MINUSEQ;
            case OperatorIds.MULTIPLY:
                return JavaTokenType.ASTERISKEQ;
            case OperatorIds.DIVIDE:
                return JavaTokenType.DIVEQ;
            case OperatorIds.REMAINDER:
                return JavaTokenType.PERCEQ;
            case OperatorIds.AND:
                return JavaTokenType.ANDEQ;
            case OperatorIds.XOR:
                return JavaTokenType.XOREQ;
            case OperatorIds.OR:
                return JavaTokenType.OREQ;
            case OperatorIds.LEFT_SHIFT:
                return JavaTokenType.LTLTEQ;
            case OperatorIds.RIGHT_SHIFT:
                return JavaTokenType.GTGTEQ;
            case OperatorIds.UNSIGNED_RIGHT_SHIFT:
                return JavaTokenType.GTGTGTEQ;
            default:
                return JavaTokenType.EQ;
        }
    }

    @NonNull
    private EcjPsiExpression toUnaryExpression(
            @NonNull EcjPsiSourceElement parent,
            @NonNull UnaryExpression expression) {
        EcjPsiPrefixExpression unaryExpression = new EcjPsiPrefixExpression(mManager, expression);
        parent.adoptChild(unaryExpression);
        int operatorId = ((expression.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT);
        IElementType tokenType = ecjToPsiToken(operatorId);
        // Should only appear in assignment nodes, not here
        assert tokenType != JavaTokenType.MINUSMINUS && tokenType != JavaTokenType.PLUSPLUS;
        unaryExpression.setOperationType(tokenType);
        unaryExpression.setOperand(toExpression(unaryExpression, expression.expression));
        return unaryExpression;
    }

    @NonNull
    private EcjPsiBinaryExpression toBinaryExpression(@NonNull EcjPsiSourceElement parent,
            @NonNull BinaryExpression expression) {
        // (There is a subclass of BinaryExpression which we eventually may
        // map to PsiPolyadicExpression.)
        EcjPsiBinaryExpression binaryExpression = new EcjPsiBinaryExpression(mManager, expression);
        parent.adoptChild(binaryExpression);
        int operatorId = ((expression.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT);
        IElementType tokenType = ecjToPsiToken(operatorId);
        binaryExpression.setOperationType(tokenType);
        binaryExpression.setLeftOperand(toExpression(binaryExpression, expression.left));
        if (expression.right != null) {
            binaryExpression.setRightOperand(toExpression(binaryExpression, expression.right));
        }
        return binaryExpression;
    }

    @NonNull
    private EcjPsiExpression toLiteral(@NonNull EcjPsiSourceElement parent, @NonNull Literal expression) {
        EcjPsiLiteralExpression literal = new EcjPsiLiteralExpression(mManager, expression);
        parent.adoptChild(literal);
        return literal;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    private EcjPsiReferenceList toTypeReferenceList(
            @NonNull EcjPsiSourceElement parent,
            @Nullable TypeReference reference,
            @NonNull Role role) {
        TypeReference[] references = reference == null ? TypeReference.NO_TYPE_ARGUMENTS :
                new TypeReference[] { reference };
        return toTypeReferenceList(parent, references, role);
    }

    @NonNull
    private EcjPsiReferenceList toTypeReferenceList(
            @NonNull EcjPsiSourceElement parent,
            @Nullable TypeReference[] references,
            @NonNull Role role) {
        EcjPsiReferenceList list = new EcjPsiReferenceList(mManager, references, role);
        parent.adoptChild(list);
        if (references != null) {
            List<PsiJavaCodeReferenceElement> elements = Lists.newArrayListWithCapacity(
                    references.length);
            // Compute elements for the referenced
            for (TypeReference reference : references) {
                elements.add(toTypeReference(list, reference));
            }
            list.setReferenceElements(elements.toArray(PsiJavaCodeReferenceElement.EMPTY_ARRAY));
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        } else {
            list.setReferenceElements(PsiJavaCodeReferenceElement.EMPTY_ARRAY);
        }

        return list;
    }

    @NonNull
    private EcjPsiJavaCodeReferenceElement toTypeReference(
            @NonNull EcjPsiSourceElement parent,
            @NonNull TypeReference reference) {
        char[][] tokens = reference.getTypeName();
        EcjPsiJavaCodeReferenceElement element = null;
        if (tokens.length == 1) {
            element = new EcjPsiJavaCodeReferenceElement(mManager, reference);
            parent.adoptChild(element);
            String referenceName = new String(tokens[tokens.length - 1]);
            int start = reference.sourceStart;
            start += (reference.bits & ASTNode.ParenthesizedMASK) >> ASTNode.ParenthesizedSHIFT;
            int end = start + referenceName.length();
            element.setNameElement(toIdentifier(element, referenceName, new TextRange(start, end)));
        } else {
            // ECJ doesn't have a hierarchy of AST nodes, but PSI does
            int startOffset = reference.sourceStart;
            int endOffset = startOffset + tokens[0].length;
            EcjPsiJavaCodeReferenceElement prev = new EcjPsiJavaCodeReferenceElement(mManager,
                    reference);
            prev.setNameElement(toIdentifier(prev, tokens[0], toRange(startOffset, endOffset)));
            prev.setRange(startOffset, endOffset);

            for (int i = 1; i < tokens.length; i++) {
                element = new EcjPsiJavaCodeReferenceElement(mManager, reference);
                element.setQualifier(prev);
                char[] name = tokens[i];
                endOffset += name.length + 1; // +1: dot
                element.adoptChild(prev);
                element.setNameElement(toIdentifier(element, name,
                        toRange(endOffset - name.length, endOffset)));
                element.setRange(startOffset, endOffset);
                prev = element;
            }

            assert element != null;
            if (reference instanceof ParameterizedSingleTypeReference) {
                ParameterizedSingleTypeReference typeReference = (ParameterizedSingleTypeReference) reference;
                element.setParameterList(toTypeParameterList(element, typeReference.typeArguments));
            }

            parent.adoptChild(element);
        }

        if (reference instanceof ParameterizedSingleTypeReference) {
            ParameterizedSingleTypeReference typeReference = (ParameterizedSingleTypeReference) reference;
            if (typeReference.typeArguments.length > 0) {
                EcjPsiReferenceParameterList parameterList = toTypeParameterList(element,
                        typeReference.typeArguments);
                element.setParameterList(parameterList);
                // Widen offset range: ECJ doesn't seem to include bounds of type parameters
                int endOffset = parameterList.getTextRange().getEndOffset();
                element.setRange(element.getTextRange().getStartOffset(),
                        endOffset);
                //noinspection ConstantConditions
                while (parent != null) {
                    if (parent.getTextRange().getEndOffset() < endOffset) {
                        parent.setRange(parent.getTextRange().getStartOffset(),
                                endOffset);
                    } else {
                        break;
                    }
                    parent = parent.getParent();
                }
            }
        }

        return element;
    }

    @NonNull
    private EcjPsiJavaCodeReferenceElement toImportReference(
            @NonNull EcjPsiSourceElement parent,
            @NonNull ImportReference reference) {
        char[][] tokens = reference.tokens;
        if (tokens.length == 1) {
            EcjPsiJavaCodeReferenceElement element =
                    new EcjPsiJavaCodeReferenceElement(mManager, reference);
            parent.adoptChild(element);
            String referenceName = new String(tokens[tokens.length - 1]);
            element.setNameElement(toIdentifier(element, referenceName, toRange(reference)));
            return element;
        } else {
            // ECJ doesn't have a hierarchy of AST nodes, but PSI does
            int startOffset = reference.sourceStart;
            int endOffset = startOffset + tokens[0].length;
            EcjPsiJavaCodeReferenceElement prev = new EcjPsiJavaCodeReferenceElement(mManager,
                    // Don't include native node for type reference here: only on outermost
                    // (should really point to package binding for the current tokens here!)
                    (ImportReference) null);
            prev.setNameElement(toIdentifier(prev, tokens[0], toRange(startOffset, endOffset)));
            prev.setRange(startOffset, endOffset);

            EcjPsiJavaCodeReferenceElement curr = null;
            for (int i = 1; i < tokens.length; i++) {
                curr = new EcjPsiJavaCodeReferenceElement(mManager, (ImportReference) null);
                curr.setQualifier(prev);
                char[] name = tokens[i];
                endOffset += name.length + 1; // +1: dot
                curr.adoptChild(prev);
                curr.setNameElement(toIdentifier(curr, name,
                        toRange(endOffset - name.length, endOffset)));
                curr.setRange(startOffset, endOffset);
                prev = curr;
            }

            assert curr != null;
            curr.setNativeNode(reference);
            parent.adoptChild(curr);
            return curr;
        }
    }

    @NonNull
    private EcjPsiTypeElement toTypeElement(
            @NonNull EcjPsiSourceElement parent,
            @NonNull TypeReference reference) {
        EcjPsiTypeElement element = new EcjPsiTypeElement(mManager, reference);
        parent.adoptChild(element);

        if (reference.resolvedType instanceof ReferenceBinding) {
            EcjPsiJavaCodeReferenceElement nameElement = toTypeReference(
                    element, reference);
            element.setReferenceElement(nameElement);
        }

        return element;
    }

    @NonNull
    private PsiParameterList toParameterList(@NonNull EcjPsiSourceElement parent,
            @Nullable Argument[] arguments) {
        EcjPsiParameterList list = new EcjPsiParameterList(mManager);
        if (arguments == null || arguments.length == 0) {
            list.setParameters(PsiParameter.EMPTY_ARRAY);
        } else {
            List<EcjPsiParameter> parameters = Lists.newArrayListWithCapacity(arguments.length);
            for (Argument argument : arguments) {
                EcjPsiParameter parameter = toParameter(list, argument);
                parameters.add(parameter);
            }
            list.setParameters(parameters.toArray(PsiParameter.EMPTY_ARRAY));
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        }

        parent.adoptChild(list);
        return list;
    }

    @NonNull
    private EcjPsiAnnotationParameterList toAnnotationParameterList(
            @NonNull EcjPsiSourceElement parent,
            MemberValuePair[] memberValuePairs) {
        EcjPsiAnnotationParameterList list = new EcjPsiAnnotationParameterList(mManager);
        if (memberValuePairs != null && memberValuePairs.length > 0) {
            PsiNameValuePair[] pairs = new PsiNameValuePair[memberValuePairs.length];
            for (int i = 0; i < memberValuePairs.length; i++) {
                pairs[i] = toMemberValuePair(list, memberValuePairs[i]);
            }
            list.setAttributes(pairs);
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        } else {
            list.setAttributes(PsiNameValuePair.EMPTY_ARRAY);
        }
        parent.adoptChild(list);
        return list;
    }

    @NonNull
    private EcjPsiLocalVariable toVariable(@NonNull EcjPsiSourceElement parent,
            @NonNull LocalDeclaration localDeclaration, boolean includeModifiersAndType) {
        EcjPsiLocalVariable variable = new EcjPsiLocalVariable(mManager, localDeclaration);
        if (includeModifiersAndType) {
            EcjPsiModifierList modifierList = toModifierList(variable, localDeclaration);
            variable.setModifierList(modifierList);
            variable.setTypeElement(toTypeElement(variable, localDeclaration.type));
        }
        variable.setNameIdentifier(toIdentifier(variable, localDeclaration.name,
                toRange(localDeclaration)));
        if (localDeclaration.initialization != null) {
            variable.setInitializer(toExpression(variable, localDeclaration.initialization));
            variable.setRange(toRange(localDeclaration.declarationSourceStart,
                    Math.max(localDeclaration.declarationSourceEnd + 1,
                            localDeclaration.initialization.sourceEnd + 1)));
        } else {
            variable.setRange(toRange(localDeclaration.declarationSourceStart,
                    localDeclaration.declarationSourceEnd + 1));
        }
        parent.adoptChild(variable);
        return variable;
    }

    @NonNull
    private EcjPsiResourceVariable toResourceVariable(@NonNull EcjPsiSourceElement parent,
            @NonNull LocalDeclaration localDeclaration, boolean includeModifiersAndType) {
        EcjPsiResourceVariable variable = new EcjPsiResourceVariable(mManager, localDeclaration);
        if (includeModifiersAndType) {
            EcjPsiModifierList modifierList = toModifierList(variable, localDeclaration);
            modifierList.setModifiers(modifierList.getModifiers() | Modifier.FINAL);
            variable.setModifierList(modifierList);
            variable.setTypeElement(toTypeElement(variable, localDeclaration.type));
        }
        variable.setNameIdentifier(toIdentifier(variable, localDeclaration.name,
                toRange(localDeclaration)));
        if (localDeclaration.initialization != null) {
            variable.setInitializer(toExpression(variable, localDeclaration.initialization));
            variable.setRange(toRange(localDeclaration.declarationSourceStart,
                    Math.max(localDeclaration.declarationSourceEnd + 1,
                            localDeclaration.initialization.sourceEnd + 1)));
        } else {
            variable.setRange(toRange(localDeclaration.declarationSourceStart,
                    localDeclaration.declarationSourceEnd + 1));
        }
        parent.adoptChild(variable);
        return variable;
    }

    @NonNull
    private EcjPsiParameter toParameter(@NonNull EcjPsiSourceElement parent,
            @NonNull LocalDeclaration argument) {
        EcjPsiParameter parameter = new EcjPsiParameter(mManager, argument);
        EcjPsiModifierList modifierList = toModifierList(parameter, argument);
        parameter.setModifierList(modifierList);
        if (argument.type != null) { // not set for lambda arguments for example
            parameter.setTypeElement(toTypeElement(parameter, argument.type));
        }
        parameter.setNameIdentifier(toIdentifier(parameter, argument.name, toRange(argument)));
        parameter.setRange(toRange(argument.declarationSourceStart,
                // For parameters, declarationSourceEnd sometimes includes more than what
                // we want; for example, for a for-each loop parameter, it includes the
                // iterated value too, not just the parameter.
                //argument.declarationSourceEnd + 1));
                // Instead use the sourceEnd, which gives us what we want.
                argument.sourceEnd + 1));
        parent.adoptChild(parameter);
        return parameter;
    }

    @NonNull
    private EcjPsiBlockStatement toBlockStatement(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Block block) {
        EcjPsiBlockStatement statement = new EcjPsiBlockStatement(mManager, block);
        parent.adoptChild(statement);
        EcjPsiCodeBlock nestedBlock = toBlock(statement, block.statements,
                null, block.sourceStart, block.sourceEnd + 1);
        statement.setBlock(nestedBlock);
        nestedBlock.setNativeNode(block);
        return statement;
    }

    @NonNull
    private EcjPsiCodeBlock toBlock(
            @NonNull EcjPsiSourceElement parent,
            @Nullable Statement[] statements,
            @Nullable ExplicitConstructorCall constructorCall,
            int startOffset,
            int endOffset) {
        EcjPsiCodeBlock block = new EcjPsiCodeBlock(mManager);

        if (statements != null) {
            List<PsiStatement> psiStatements = Lists
                    .newArrayListWithExpectedSize(statements.length + 1);
            if (constructorCall != null && !constructorCall.isImplicitSuper()) {
                // Not part of the normal statement list in ECJ
                psiStatements.add(toExplicitConstructorCall(block, constructorCall));
            }
            for (Statement statement : statements) {
                EcjPsiStatement psiStatement = toStatement(block, statement);
                psiStatements.add(psiStatement);
            }
            block.setStatements(psiStatements.toArray(PsiStatement.EMPTY_ARRAY));
        } else if (constructorCall != null && !constructorCall.isImplicitSuper()) {
            block.setStatements(new PsiStatement[]{ toExplicitConstructorCall(block,
                    constructorCall)});
        } else {
            block.setStatements(PsiStatement.EMPTY_ARRAY);
        }

        parent.adoptChild(block);
        if (startOffset != -1) {
            block.setRange(startOffset, endOffset);
        }

        return block;
    }


    @NonNull
    private EcjPsiCodeBlock toBlock(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Statement[] statements) {
        return toBlock(parent, statements, null, -1, -1);
    }

    @NonNull
    private EcjPsiCodeBlock toBlock(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Block block) {
        return toBlock(parent, block.statements, null, -1, -1);
    }

    @NonNull
    private EcjPsiStatement toStatement(
            @NonNull EcjPsiSourceElement parent,
            @NonNull Statement statement) {
        if (statement instanceof Expression) {
            return toExpressionStatement(parent, statement);
        } else if (statement instanceof AbstractVariableDeclaration) {
            return toDeclarationStatement(parent, (AbstractVariableDeclaration) statement);
        } else if (statement instanceof Block) {
            Block blockStatement = (Block) statement;
            return toBlockStatement(parent, blockStatement);
        } else if (statement instanceof LabeledStatement) {
            return toLabeledStatement(parent, (LabeledStatement) statement);
        } else if (statement instanceof IfStatement) {
            return toIfStatement(parent, (IfStatement) statement);
        } else if (statement instanceof ReturnStatement) {
            return toReturnStatement(parent, (ReturnStatement) statement);
        } else if (statement instanceof ForStatement) {
            return toForStatement(parent, (ForStatement) statement);
        } else if (statement instanceof ForeachStatement) {
            return toForEachStatement(parent, (ForeachStatement) statement);
        } else if (statement instanceof DoStatement) {
            return toDoWhileStatement(parent, (DoStatement) statement);
        } else if (statement instanceof WhileStatement) {
            return toWhileStatement(parent, (WhileStatement) statement);
        } else if (statement instanceof SwitchStatement) {
            return toSwitchStatement(parent, (SwitchStatement) statement);
        } else if (statement instanceof BreakStatement) {
            return toBreakStatement(parent, (BreakStatement) statement);
        } else if (statement instanceof ContinueStatement) {
            return toContinueStatement(parent, (ContinueStatement) statement);
        } else if (statement instanceof CaseStatement) {
            return toCaseStatement(parent, ((CaseStatement)statement));
        } else if (statement instanceof SynchronizedStatement) {
            return toSynchronizedStatement(parent, ((SynchronizedStatement)statement));
        } else if (statement instanceof TryStatement) {
            return toTryStatement(parent, ((TryStatement) statement));
        } else if (statement instanceof EmptyStatement) {
            return toEmptyStatement(parent);
        } else if (statement instanceof AssertStatement) {
            return toAssertStatement(parent, (AssertStatement) statement);
        } else if (statement instanceof ThrowStatement) {
            return toThrowStatement(parent, (ThrowStatement) statement);
        } else if (statement instanceof ExplicitConstructorCall) {
            return toExplicitConstructorCall(parent, (ExplicitConstructorCall) statement);
        } else if (statement instanceof TypeDeclaration) {
            EcjPsiClassLevelDeclarationStatement st = new
                    EcjPsiClassLevelDeclarationStatement(mManager);
            toClass(st, (TypeDeclaration) statement);
            parent.adoptChild(st);
            return st;
        } else {
            assert false : "Missing implementation for " + statement.getClass();
        }

        throw new IllegalArgumentException(statement.getClass().getName());
    }

    @NonNull
    private EcjPsiStatement toExpressionStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull Statement statement) {
        EcjPsiExpressionStatement expressionStatement = new EcjPsiExpressionStatement(mManager,
                statement);
        EcjPsiExpression expression = toExpression(expressionStatement, (Expression) statement);
        expressionStatement.setExpression(expression);
        parent.adoptChild(expressionStatement);
        return expressionStatement;
    }

    @NonNull
    private EcjPsiStatement toExplicitConstructorCall(@NonNull EcjPsiSourceElement parent,
            @NonNull final ExplicitConstructorCall constructorCall) {

        EcjPsiExpressionStatement expressionStatement = new EcjPsiExpressionStatement(mManager,
                constructorCall);

        // Build up an expression statement which contains an expression, which is a method call.
        // That method call has a method expression which is a reference expression,
        // and that reference expression and that expression usually has a null qualifier and a
        // reference name element which is the keyword "this" or "super" depending on the
        // constructor call.
        EcjPsiExplicitConstructorCall call = new EcjPsiExplicitConstructorCall(mManager,
                constructorCall);
        expressionStatement.adoptChild(call);

        EcjPsiReferenceExpression refExp = new EcjPsiConstructorReferenceExpression(mManager,
                constructorCall);
        EcjPsiExpression qualifier;
        if (constructorCall.qualification == null) {
            qualifier = null;
        } else {
            qualifier = toExpression(refExp, constructorCall.qualification);
        }
        refExp.setQualifier(qualifier);

        String keyword = constructorCall.isSuperAccess() ? "super" : "this";
        int identifierStart = constructorCall.qualification != null
                ? constructorCall.qualification.sourceEnd + 2 // +1 for ECJ offsets and 1 for dot
                : constructorCall.sourceStart;
        int identifierEnd = identifierStart + keyword.length();
        EcjPsiIdentifier identifier = toIdentifier(refExp,
                keyword, toRange(identifierStart, identifierEnd));
        refExp.setNameElement(identifier);
        refExp.setRange(constructorCall.sourceStart, identifierEnd);
        call.setMethodExpression(refExp);
        call.adoptChild(refExp);

        call.setArgumentList(toArguments(call, constructorCall.arguments));
        if (constructorCall.typeArguments != null) {
            EcjPsiReferenceParameterList typeArgumentList = toTypeParameterList(call,
                    constructorCall.typeArguments);
            typeArgumentList.setRange(constructorCall.typeArgumentsSourceStart,
                    constructorCall.sourceEnd + 1);
            call.setTypeArgumentList(typeArgumentList);
        }

        expressionStatement.setExpression(call);
        parent.adoptChild(expressionStatement);
        return expressionStatement;
    }

    @NonNull
    private EcjPsiThrowStatement toThrowStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull ThrowStatement statement) {
        EcjPsiThrowStatement throwStatement = new EcjPsiThrowStatement(mManager, statement);
        parent.adoptChild(throwStatement);
        throwStatement.setException(toExpression(throwStatement, statement.exception));
        return throwStatement;
    }

    @NonNull
    private EcjPsiAssertStatement toAssertStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull AssertStatement statement) {
        EcjPsiAssertStatement assertStatement = new EcjPsiAssertStatement(mManager, statement);
        parent.adoptChild(assertStatement);
        assertStatement.setCondition(toExpression(assertStatement, statement.assertExpression));
        if (statement.exceptionArgument != null) {
            assertStatement.setDescription(toExpression(assertStatement,
                    statement.exceptionArgument));
        }
        return assertStatement;
    }

    @NonNull
    private EcjPsiStatement toEmptyStatement(@NonNull EcjPsiSourceElement parent) {
        EcjPsiEmptyStatement empty = new EcjPsiEmptyStatement(mManager);
        parent.adoptChild(empty);
        return empty;
    }

    @NonNull
    private EcjPsiIfStatement toIfStatement(@NonNull EcjPsiSourceElement parent, IfStatement statement) {
        EcjPsiIfStatement ifStatement = new EcjPsiIfStatement(mManager, statement);
        parent.adoptChild(ifStatement);

        EcjPsiExpression condition = toExpression(ifStatement, statement.condition);
        ifStatement.setCondition(condition);

        if (statement.thenStatement != null) {
            EcjPsiStatement thenStatement = toStatement(ifStatement, statement.thenStatement);
            ifStatement.setThen(thenStatement);
        }

        if (statement.elseStatement != null) {
            EcjPsiStatement thenStatement = toStatement(ifStatement, statement.elseStatement);
            ifStatement.setElse(thenStatement);
        }

        return ifStatement;
    }

    @NonNull
    private EcjPsiReturnStatement toReturnStatement(@NonNull EcjPsiSourceElement parent, ReturnStatement statement) {
        EcjPsiReturnStatement returnStatement = new EcjPsiReturnStatement(mManager, statement);
        parent.adoptChild(returnStatement);

        if (statement.expression != null) {
            EcjPsiExpression expression = toExpression(returnStatement, statement.expression);
            returnStatement.setReturnValue(expression);
        }

        return returnStatement;
    }

    @NonNull
    private EcjPsiForStatement toForStatement(@NonNull EcjPsiSourceElement parent, ForStatement statement) {
        EcjPsiForStatement forStatement = new EcjPsiForStatement(mManager, statement);
        parent.adoptChild(forStatement);

        EcjPsiStatement initialization = toForDeclarationStatement(
                forStatement, statement.initializations);
        forStatement.setInitialization(initialization);

        if (statement.condition != null) {
            EcjPsiExpression condition = toExpression(forStatement, statement.condition);
            forStatement.setCondition(condition);
        }

        if (statement.increments != null && statement.increments.length > 0) {
            EcjPsiStatement updates = toForUpdateStatement(forStatement, statement.increments);
            forStatement.setUpdate(updates);
        } // else: do I need to add an empty statement?

        EcjPsiStatement body = toStatement(forStatement, statement.action);
        forStatement.setBody(body);

        return forStatement;
    }

    @NonNull
    private EcjPsiSwitchLabelStatement toCaseStatement(
            @NonNull EcjPsiSourceElement parent,
            @NonNull CaseStatement statement) {
        EcjPsiSwitchLabelStatement st = new EcjPsiSwitchLabelStatement(mManager, statement);
        if (statement.constantExpression != null) {
            st.setCaseValue(toExpression(st, statement.constantExpression));
        }
        parent.adoptChild(st);
        return st;
    }

    @NonNull
    private EcjPsiSwitchStatement toSwitchStatement(@NonNull EcjPsiSourceElement parent, SwitchStatement statement) {
        EcjPsiSwitchStatement switchStatement = new EcjPsiSwitchStatement(mManager, statement);
        parent.adoptChild(switchStatement);
        switchStatement.setExpression(toExpression(switchStatement, statement.expression));
        switchStatement.setBody(toBlock(switchStatement, statement.statements));
        return switchStatement;
    }

    @NonNull
    private EcjPsiBreakStatement toBreakStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull BreakStatement statement) {
        EcjPsiBreakStatement breakStatement = new EcjPsiBreakStatement(mManager, statement);
        parent.adoptChild(breakStatement);
        if (statement.label != null) {
            breakStatement.setIdentifier(toIdentifier(breakStatement, statement.label,
                    toRange(statement.sourceEnd - statement.label.length, statement.sourceEnd)));
        }
        return breakStatement;
    }

    @NonNull
    private EcjPsiContinueStatement toContinueStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull ContinueStatement statement) {
        EcjPsiContinueStatement continueStatement = new EcjPsiContinueStatement(mManager, statement);
        parent.adoptChild(continueStatement);
        if (statement.label != null) {
            continueStatement.setIdentifier(toIdentifier(continueStatement, statement.label,
                    toRange(statement.sourceEnd - statement.label.length, statement.sourceEnd)));
        }
        return continueStatement;
    }

    @NonNull
    private EcjPsiSynchronizedStatement toSynchronizedStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull SynchronizedStatement statement) {
        EcjPsiSynchronizedStatement s = new EcjPsiSynchronizedStatement(mManager, statement);
        parent.adoptChild(s);
        if (statement.expression != null) {
            s.setLockExpression(toExpression(s, statement.expression));
        }
        if (statement.block != null) {
            s.setBody(toBlock(s, statement.block));
        }
        return s;
    }

    @NonNull
    private EcjPsiWhileStatement toWhileStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull WhileStatement statement) {
        EcjPsiWhileStatement whileStatement = new EcjPsiWhileStatement(mManager, statement);
        parent.adoptChild(whileStatement);
        whileStatement.setCondition(toExpression(whileStatement, statement.condition));
        whileStatement.setBody(toStatement(whileStatement, statement.action));
        return whileStatement;
    }

    @NonNull
    private EcjPsiDoWhileStatement toDoWhileStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull DoStatement statement) {
        EcjPsiDoWhileStatement doWhile = new EcjPsiDoWhileStatement(mManager, statement);
        parent.adoptChild(doWhile);
        doWhile.setBody(toStatement(doWhile, statement.action));
        doWhile.setCondition(toExpression(doWhile, statement.condition));
        return doWhile;
    }

    @NonNull
    private EcjPsiTryStatement toTryStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull TryStatement statement) {
        EcjPsiTryStatement tryStatement = new EcjPsiTryStatement(mManager, statement);
        parent.adoptChild(tryStatement);

        if (statement.resources != null && statement.resources.length > 0) {
            EcjPsiResourceList list = new EcjPsiResourceList(mManager);
            List<PsiResourceVariable> variables = Lists.newArrayListWithCapacity(
                    statement.resources.length);
            boolean first = true;
            for (LocalDeclaration resource : statement.resources) {
                variables.add(toResourceVariable(list, resource, first));
                first = false;
            }
            list.setResourceVariables(variables);
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
            tryStatement.setResourceList(list);
            tryStatement.adoptChild(list);
        }

        if (statement.tryBlock != null) {
            tryStatement.setTryBlock(toBlock(tryStatement, statement.tryBlock));
        }

        if (statement.catchBlocks != null) {
            assert statement.catchArguments != null
                    && statement.catchArguments.length == statement.catchBlocks.length;

            List<PsiCatchSection> sections =
                    Lists.newArrayListWithCapacity(statement.catchBlocks.length);
            for (int i = 0; i < statement.catchBlocks.length; i++) {
                Block catchBlock = statement.catchBlocks[i];
                Argument catchArgument = statement.catchArguments[i];
                sections.add(toCatchSection(tryStatement, catchArgument, catchBlock));
            }
            tryStatement.setCatchSections(sections.toArray(PsiCatchSection.EMPTY_ARRAY));
        } else {
            tryStatement.setCatchSections(PsiCatchSection.EMPTY_ARRAY);
        }
        if (statement.finallyBlock != null) {
            tryStatement.setFinallyBlock(toBlock(tryStatement, statement.finallyBlock));
        }
        return tryStatement;
    }

    @NonNull
    private PsiCatchSection toCatchSection(@NonNull EcjPsiTryStatement parent,
            @NonNull Argument catchArgument,
            @NonNull Block catchBlock) {
        EcjPsiCatchSection section = new EcjPsiCatchSection(mManager, catchArgument, catchBlock);
        parent.adoptChild(section);
        EcjPsiParameter parameter = toParameter(section, catchArgument);
        section.setParameter(parameter);
        section.setCodeBlock(toBlock(section, catchBlock));

        if (catchArgument.type instanceof UnionTypeReference) {
            EcjPsiModifierList modifierList = (EcjPsiModifierList) parameter.getModifierList();
            modifierList.setModifiers(modifierList.getModifiers() | Modifier.FINAL);
        }
        return section;
    }

    @NonNull
    private EcjPsiForeachStatement toForEachStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull ForeachStatement statement) {
        EcjPsiForeachStatement forStatement = new EcjPsiForeachStatement(mManager, statement);
        parent.adoptChild(forStatement);
        forStatement.setIterationParameter(toParameter(forStatement, statement.elementVariable));
        forStatement.setIteratedValue(toExpression(forStatement, statement.collection));
        forStatement.setBody(toStatement(forStatement, statement.action));
        return forStatement;
    }

    @NonNull
    private EcjPsiLabeledStatement toLabeledStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull LabeledStatement statement) {
        EcjPsiLabeledStatement labeledStatement = new EcjPsiLabeledStatement(mManager,
                statement);
        parent.adoptChild(labeledStatement);
        EcjPsiIdentifier nameIdentifier = new EcjPsiIdentifier(mManager,
                new String(statement.label),
                toRange(statement.sourceStart, statement.labelEnd + 1));
        labeledStatement.adoptChild(nameIdentifier);
        labeledStatement.setIdentifier(nameIdentifier);
        if (statement.statement != null) {
            labeledStatement.setStatement(toStatement(labeledStatement, statement.statement));
        }
        return labeledStatement;
    }

    @NonNull
    private EcjPsiStatement toForDeclarationStatement(
            @NonNull EcjPsiSourceElement parent,
            @Nullable Statement[] statements) {
        if (statements == null || statements.length == 0) {
            return toEmptyStatement(parent);
        }

        if (!(statements[0] instanceof LocalDeclaration)) {
            // All the statements are assignments (you can't mix and match declarations
            // and assignments
            return toForUpdateStatement(parent, statements);
        }

        EcjPsiDeclarationStatement declaration =
                new EcjPsiDeclarationStatement(mManager, null);
        parent.adoptChild(declaration);
        List<EcjPsiVariable> variables = Lists.newArrayListWithCapacity(statements.length);
        EcjPsiVariable prevVariable = null;
        for (Statement statement : statements) {
            if (statement instanceof LocalDeclaration) {
                LocalDeclaration localDeclaration = (LocalDeclaration) statement;
                EcjPsiVariable variable = toVariable(declaration, localDeclaration,
                        prevVariable == null);
                variables.add(variable);

                if (prevVariable != null) {
                    // Offset ranges are wrong when there are multiple variable
                    // declarations (ECJ treats them all as separate declarations)
                    // so fix up the offsets such that they don't overlap
                    TextRange prevRange = prevVariable.getTextRange();
                    prevVariable.setRange(prevRange.getStartOffset(),
                            Math.min(prevRange.getEndOffset(), localDeclaration.sourceStart));
                    variable.setRange(localDeclaration.sourceStart,
                            localDeclaration.initialization != null
                                    ? localDeclaration.initialization.sourceEnd + 1
                                    : localDeclaration.sourceEnd + 1);
                }

                prevVariable = variable;
            } else {
                assert false : statement;
            }
        }
        declaration.setDeclaredElements(variables.toArray(PsiElement.EMPTY_ARRAY));
        return declaration;
    }

    @NonNull
    private EcjPsiExpressionList toArguments(@NonNull EcjPsiSourceElement parent,
            @Nullable Expression[] expressionArray) {
        EcjPsiExpressionList list = new EcjPsiExpressionList(mManager);
        if (expressionArray != null && expressionArray.length > 0) {
            List<EcjPsiExpression> expressions = Lists
                    .newArrayListWithCapacity(expressionArray.length);
            for (Expression e : expressionArray) {
                EcjPsiExpression expression = toExpression(list, e);
                expressions.add(expression);
            }
            list.setExpressions(expressions.toArray(PsiExpression.EMPTY_ARRAY));
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        } else {
            list.setExpressions(PsiExpression.EMPTY_ARRAY);
        }

        parent.adoptChild(list);
        return list;
    }

    @NonNull
    private EcjPsiStatement toForUpdateStatement(@NonNull EcjPsiSourceElement parent,
            @NonNull Statement[] statements) {
        if (statements.length == 1) {
            return toStatement(parent, statements[0]);
        } else {
            EcjPsiExpressionListStatement listStatement = new EcjPsiExpressionListStatement(mManager);
            EcjPsiExpressionList expressionList = new EcjPsiExpressionList(mManager);
            List<EcjPsiExpression> expressions = Lists.newArrayListWithCapacity(statements.length);
            for (Statement s : statements) {
                if (s instanceof Expression) {
                    EcjPsiExpression expression = toExpression(expressionList, (Expression) s);
                    expressions.add(expression);
                } else {
                    // Unexpected type of initializer
                    assert false : s;
                }
            }
            expressionList.setExpressions(expressions.toArray(PsiExpression.EMPTY_ARRAY));
            if (statements.length > 0) {
                expressionList.setRange(new TextRange(
                        expressionList.getFirstChild().getTextRange().getStartOffset(),
                        expressionList.getLastChild().getTextRange().getEndOffset()));
            }
            listStatement.setExpressionList(expressionList);
            listStatement.adoptChild(expressionList);
            parent.adoptChild(listStatement);
            return listStatement;
        }
    }

    @NonNull
    private EcjPsiDeclarationStatement toDeclarationStatement(
            @NonNull EcjPsiSourceElement parent,
            @NonNull AbstractVariableDeclaration statement) {
        // ECJ doesn't distinguish between
        //    int x;
        //    int y;
        // and
        //    int x, y;
        //
        // However, we need to preserve this distinction in the AST. Therefore, figure
        // out when this is the case and fold these separate, consecutive statement nodes into a
        // single declaration statement

        if (parent.mLastChild instanceof EcjPsiDeclarationStatement
                && parent.mLastChild.mNativeNode instanceof AbstractVariableDeclaration
                && statement.declarationSourceStart ==
                    ((AbstractVariableDeclaration)parent.mLastChild.mNativeNode).declarationSourceStart) {
            // Just create a new variable as a child of the existing declaration statement
            EcjPsiDeclarationStatement declaration = (EcjPsiDeclarationStatement)parent.mLastChild;
            PsiElement prevVariable = declaration.getLastChild();
            assert statement instanceof LocalDeclaration;
            assert prevVariable instanceof EcjPsiLocalVariable;
            LocalDeclaration localDeclaration = (LocalDeclaration) statement;
            // Constructing variable directly instead of calling toVariable() since we
            // don't want to include modifiers and type elements here
            EcjPsiLocalVariable variable = toVariable(declaration, localDeclaration, false);

            // Tweak offsets such that they don't overlap: use sourceStart instead of
            // declarationSourceStart to exclude type for the second node, and limit the
            // end of the previous node to the start
            TextRange prevRange = prevVariable.getTextRange();
            ((EcjPsiLocalVariable)prevVariable).setRange(prevRange.getStartOffset(),
                    Math.min(prevRange.getEndOffset(), localDeclaration.sourceStart));
            variable.setRange(localDeclaration.sourceStart,
                    localDeclaration.initialization != null
                            ? localDeclaration.initialization.sourceEnd + 1
                            : localDeclaration.sourceEnd + 1);

            // Merge array of declared elements
            PsiElement[] declaredElements = declaration.getDeclaredElements();
            declaration.setDeclaredElements(ObjectArrays.concat(declaredElements, variable));

            return declaration;
        }

        EcjPsiDeclarationStatement declaration =
                new EcjPsiDeclarationStatement(mManager, statement);
        parent.adoptChild(declaration);
        assert statement instanceof LocalDeclaration;
        EcjPsiVariable variable = toVariable(declaration, (LocalDeclaration) statement, true);
        declaration.setDeclaredElements(new PsiElement[] { variable });

        return declaration;
    }

    @NonNull
    private EcjPsiField toField(EcjPsiClass cls, FieldDeclaration field) {
        EcjPsiField psiField = new EcjPsiField(mManager, cls, field);
        cls.adoptChild(psiField);
        EcjPsiModifierList modifierList = toModifierList(psiField, field);
        psiField.setModifierList(modifierList);
        psiField.setTypeElement(toTypeElement(psiField, field.type));
        psiField.setIdentifier(toIdentifier(psiField, field.name, toRange(field.sourceStart,
                field.sourceStart + field.name.length)));
        if (field.initialization != null) {
            psiField.setFieldInitializer(toExpression(psiField, field.initialization));
        }

        return psiField;
    }

    @NonNull
    private EcjPsiEnumConstant toEnumConstant(EcjPsiClass cls, FieldDeclaration field) {
        EcjPsiEnumConstant psiField = new EcjPsiEnumConstant(mManager, cls, field);
        cls.adoptChild(psiField);

        EcjPsiModifierList modifierList = toModifierList(psiField, field);
        psiField.setModifierList(modifierList);
        int modifiers = modifierList.getModifiers();
        modifiers |= Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
        modifiers &= ~(Modifier.PROTECTED | Modifier.PRIVATE);
        modifierList.setModifiers(modifiers);
        psiField.setIdentifier(toIdentifier(psiField, field.name, toRange(field.sourceStart,
                field.sourceStart + field.name.length)));

        Expression init = field.initialization;
        if (init != null) {
            if (init instanceof AllocationExpression) {
                EcjPsiExpressionList arguments = toArguments(psiField,
                        ((AllocationExpression) init).arguments);
                psiField.setArgumentList(arguments);

                if (init instanceof QualifiedAllocationExpression) {
                    EcjPsiEnumConstantInitializer initializer = toEnumInitializer(psiField, init);
                    if (initializer != null) {
                        psiField.setInitializer(initializer);
                        initializer.setConstant(psiField);
                    }
                }
            }
        }

        return psiField;
    }

    @NonNull
    private EcjPsiReferenceParameterList toTypeParameterList(@NonNull EcjPsiSourceElement parent,
            @Nullable TypeReference[] references) {
        EcjPsiReferenceParameterList list = new EcjPsiReferenceParameterList(mManager);
        if (references != null && references.length > 0) {
            List<EcjPsiTypeElement> parameters =
                    Lists.newArrayListWithCapacity(references.length);
            for (TypeReference typeReference : references) {
                EcjPsiTypeElement typeElement = toTypeElement(list, typeReference);
                parameters.add(typeElement);
            }
            list.setTypeParameters(parameters.toArray(PsiTypeElement.EMPTY_ARRAY));
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        } else {
            list.setTypeParameters(PsiTypeElement.EMPTY_ARRAY);
        }
        parent.adoptChild(list);
        return list;
    }

    @NonNull
    private PsiTypeParameterList toTypeParameterList(@NonNull EcjPsiSourceElement parent,
            @Nullable TypeParameter[] typeParameters) {
        EcjPsiTypeParameterList list = new EcjPsiTypeParameterList(mManager);
        if (typeParameters != null && typeParameters.length > 0) {
            List<EcjPsiTypeParameter> parameters =
                    Lists.newArrayListWithCapacity(typeParameters.length);
            for (TypeParameter typeParameter : typeParameters) {
                EcjPsiTypeParameter p = new EcjPsiTypeParameter(mManager, typeParameter);
                list.adoptChild(p);
                EcjPsiReferenceList extendsList =
                        toTypeReferenceList(p, typeParameter.bounds, Role.EXTENDS_BOUNDS_LIST);
                p.setExtendsList(extendsList);
                parameters.add(p);
            }
            list.setTypeParameters(parameters.toArray(PsiTypeParameter.EMPTY_ARRAY));
            // Ensure that we recompute the text range from all the children
            list.mRange = null;
        } else {
            list.setTypeParameters(PsiTypeParameter.EMPTY_ARRAY);
        }
        parent.adoptChild(list);
        return list;
    }

    @NonNull
    private EcjPsiIdentifier toIdentifier(@NonNull EcjPsiSourceElement parent, @NonNull String name,
            @NonNull TextRange range) {
        EcjPsiIdentifier identifier = new EcjPsiIdentifier(mManager, name, range);
        parent.adoptChild(identifier);
        return identifier;
    }

    @NonNull
    private EcjPsiIdentifier toIdentifier(@NonNull EcjPsiSourceElement parent, @NonNull char[] name,
            @NonNull TextRange range) {
        EcjPsiIdentifier identifier = new EcjPsiIdentifier(mManager, new String(name), range);
        parent.adoptChild(identifier);
        return identifier;
    }

    @NonNull
    EcjPsiJavaFile toFile(@NonNull CompilationUnitDeclaration node, EcjSourceFile source) {
        EcjPsiJavaFile unit = new EcjPsiJavaFile(mManager, source, node);

        if (node.currentPackage != null) {
            EcjPsiPackageStatement packageStatement = toPackageStatement(unit, node.currentPackage);
            unit.setPackageStatement(packageStatement);
        }

        EcjPsiImportList importList = toImportList(unit, node.imports);
        unit.setImportList(importList);

        TypeDeclaration[] newTypes;
        if (node.types != null && node.types.length > 0 && CharOperation
                .equals(PACKAGE_INFO, node.types[0].name)) {
            newTypes = new TypeDeclaration[node.types.length - 1];
            System.arraycopy(node.types, 1, newTypes, 0, node.types.length - 1);
        } else {
            newTypes = node.types;
        }

        List<EcjPsiClass> classes = Lists.newArrayList();
        if (newTypes != null) {
            for (TypeDeclaration declaration : newTypes) {
                EcjPsiClass toClass = toClass(unit, declaration);
                classes.add(toClass);
            }
        }
        unit.setClasses(classes.toArray(new PsiClass[classes.size()]));

        return unit;
    }
}
