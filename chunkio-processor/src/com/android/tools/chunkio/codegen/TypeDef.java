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

package com.android.tools.chunkio.codegen;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TypeDef {
    private static final TypeDef TYPE_BOOLEAN = new TypeDef("boolean");
    private static final TypeDef TYPE_BYTE = new TypeDef("byte");
    private static final TypeDef TYPE_CHAR = new TypeDef("char");
    private static final TypeDef TYPE_DOUBLE = new TypeDef("double");
    private static final TypeDef TYPE_FLOAT = new TypeDef("float");
    private static final TypeDef TYPE_INT = new TypeDef("int");
    private static final TypeDef TYPE_LONG = new TypeDef("long");
    private static final TypeDef TYPE_SHORT = new TypeDef("short");
    private static final TypeDef TYPE_VOID = new TypeDef("void");

    private boolean isPrimitive = false;
    private boolean isArray = false;

    private List<String> names = new ArrayList<>();
    private List<TypeDef> typeParameters = new ArrayList<>();

    private TypeDef(String primitiveName) {
        names.add(primitiveName);
        isPrimitive = true;
    }

    private TypeDef(TypeDef arrayType) {
        names.addAll(arrayType.names);
        isArray = true;
        isPrimitive = arrayType.isPrimitive;
    }

    private TypeDef() {
    }

    void emit(CodeGenerator generator) throws IOException {
        if (isPrimitive) {
            generator.emitIndented(names.get(0));
        } else {
            generator.emitClassName(names);
            if (!typeParameters.isEmpty()) {
                generator.emit("<");
                boolean first = true;
                for (TypeDef type : typeParameters) {
                    if (!first) {
                        generator.emit(", ");
                    }
                    first = false;
                    generator.emit("$T", type);
                }
                generator.emit(">");
            }
        }
        if (isArray) {
            generator.emitIndented("[]");
        }
    }

    public static TypeDef of(Type type) {
        if (type instanceof Class<?>) {
            if (type == boolean.class) return TYPE_BOOLEAN;
            if (type == byte.class) return TYPE_BYTE;
            if (type == char.class) return TYPE_CHAR;
            if (type == double.class) return TYPE_DOUBLE;
            if (type == float.class) return TYPE_FLOAT;
            if (type == int.class) return TYPE_INT;
            if (type == long.class) return TYPE_LONG;
            if (type == short.class) return TYPE_SHORT;
            if (type == void.class) return TYPE_VOID;
            Class<?> classType = (Class<?>) type;
            if (classType.isArray()) return new TypeDef(of(classType.getComponentType()));
            return fromClass(classType);
        } else if (type instanceof ParameterizedType) {
            return fromParametrizedClass((ParameterizedType) type);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private static TypeDef fromParametrizedClass(ParameterizedType type) {
        Type[] typeArguments = type.getActualTypeArguments();
        TypeDef typeDef = fromClass((Class<?>) type.getRawType());
        for (Type typeArgument : typeArguments) {
            typeDef.typeParameters.add(of(typeArgument));
        }
        return typeDef;
    }

    public static TypeDef of(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor6<TypeDef, Void>() {
            @Override
            protected TypeDef defaultAction(TypeMirror typeMirror, Void ignore) {
                throw new IllegalArgumentException("Unsupported type: " + typeMirror);
            }

            @Override
            public TypeDef visitPrimitive(PrimitiveType primitiveType, Void ignore) {
                switch (primitiveType.getKind()) {
                    case BOOLEAN: return TYPE_BOOLEAN;
                    case BYTE: return TYPE_BYTE;
                    case SHORT: return TYPE_SHORT;
                    case INT: return TYPE_INT;
                    case LONG: return TYPE_LONG;
                    case CHAR: return TYPE_CHAR;
                    case FLOAT: return TYPE_FLOAT;
                    case DOUBLE: return TYPE_DOUBLE;
                    default: break;
                }
                return super.visitPrimitive(primitiveType, ignore);
            }

            @Override
            public TypeDef visitNoType(NoType noType, Void aVoid) {
                return TYPE_VOID;
            }

            @Override
            public TypeDef visitDeclared(DeclaredType declaredType, Void ignore) {
                TypeDef typeDef = new TypeDef();

                TypeElement typeElement = (TypeElement) declaredType.asElement();
                Element e;
                for (e = typeElement; e.getKind().isClass() || e.getKind().isInterface();
                        e = e.getEnclosingElement()) {
                    typeDef.names.add(e.getSimpleName().toString());
                }
                typeDef.names.add(((PackageElement) e).getQualifiedName().toString());

                Collections.reverse(typeDef.names);

                List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                for (TypeMirror typeArgument : typeArguments) {
                    typeDef.typeParameters.add(of(typeArgument));
                }

                return typeDef;
            }
        }, null);
    }

    public static TypeDef fromClass(String packageName, String name) {
        TypeDef type = new TypeDef();
        type.names.add(packageName);
        Collections.addAll(type.names, name.split("\\."));
        return type;
    }

    private static TypeDef fromClass(Class<?> classType) {
        TypeDef name = new TypeDef();
        for (Class<?> c = classType; c != null; c = c.getEnclosingClass()) {
            name.names.add(c.getSimpleName());
        }

        if (classType.getPackage() != null) {
            name.names.add(classType.getPackage().getName());
        }

        Collections.reverse(name.names);
        return name;
    }

    public String getSimpleName() {
        return names.get(names.size() - 1);
    }
}
