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

import com.android.tools.chunkio.ChunkUtils;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class CodeGenerator {
    private final Appendable out;

    private final String indent;
    private int indentLevel = 0;
    private boolean needIndent = false;
    private int multiline = -1;

    private String currentPackage;

    private final Set<String> importCandidates = new TreeSet<>();
    private final Set<String> imports;

    CodeGenerator(Appendable out, String indent) {
        this.out = out;
        this.indent = indent;
        imports = Collections.emptySet();
    }

    CodeGenerator(Appendable out, String indent, Set<String> imports) {
        this.out = out;
        this.indent = indent;
        this.imports = imports;
    }

    Set<String> getImportCandidates() {
        return importCandidates;
    }

    Set<String> getImports() {
        return imports;
    }

    CodeGenerator indent() {
        indentLevel++;
        return this;
    }

    private CodeGenerator indent(int level) {
        indentLevel += level;
        return this;
    }

    CodeGenerator unindent() {
        if (indentLevel > 0) indentLevel--;
        return this;
    }

    private CodeGenerator unindent(int level) {
        if (indentLevel > level - 1) indentLevel -= level;
        return this;
    }

    CodeGenerator emit(CodeSnippet snippet) throws IOException {
        Iterator<Object> values = snippet.getValues().iterator();

        for (String part : snippet.getParts()) {
            if ("$L".equals(part)) {
                emitLiteral(values.next());
            } else if ("$N".equals(part)) {
                emitIndented(String.valueOf(values.next()));
            } else if ("$S".equals(part)) {
                emitString(values.next());
            } else if ("$T".equals(part)) {
                ((TypeDef) values.next()).emit(this);
            } else if ("$$".equals(part)) {
                emitIndented("$");
            } else if ("$>".equals(part)) {
                indent();
            } else if ("$<".equals(part)) {
                unindent();
            } else if ("$[".equals(part)) {
                multiline = 0;
            } else if ("$]".equals(part)) {
                if (multiline > 0) unindent(2);
                multiline = -1;
            } else {
                emitIndented(part);
            }
        }
        return this;
    }

    private void emitString(Object value) throws IOException {
        if (value == null) {
            emitIndented("null");
        } else {
            emitIndented(escapeString(value.toString()));
        }
    }

    private static String escapeString(String s) {
        StringBuilder builder = new StringBuilder(s.length());
        builder.append('"');
        char c;
        for (int i = 0; i < s.length(); i++) {
            switch (c = s.charAt(i)) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(c);
            }
        }
        builder.append('"');
        return builder.toString();
    }

    CodeGenerator emit(String format, Object... args) throws IOException {
        return emit(CodeSnippet.builder().add(format, args).build());
    }

    private void emitLiteral(Object value) throws IOException {
        if (value instanceof CodeSnippet) {
            emit((CodeSnippet) value);
        } else {
            emitIndented(String.valueOf(value));
        }
    }

    void emitIndented(String s) throws IOException {
        String[] lines = s.split("\n", -1);
        boolean firstLine = true;
        boolean lastLineWasStatement = false;

        for (String line : lines) {
            if (!firstLine) {
                out.append('\n');
                if (!lastLineWasStatement) {
                    if (multiline >= 0) {
                        if (multiline == 0) indent(2);
                        multiline++;
                    }
                }
                needIndent = true;
            }
            firstLine = false;

            if (line.isEmpty()) continue;
            if (needIndent) emitIndent();
            needIndent = false;

            out.append(line);
            lastLineWasStatement = line.charAt(line.length() - 1) == ';';
        }
    }

    private void emitIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            out.append(indent);
        }
    }

    void emitModifiers(Set<Modifier> modifiers) throws IOException {
        for (Modifier modifier : modifiers) {
            emitIndented(modifier.name().toLowerCase(Locale.ENGLISH) + " ");
        }
    }

    void setPackage(String currentPackage) {
        this.currentPackage = currentPackage;
    }

    void emitClassName(List<String> names) throws IOException {
        String packageName = names.get(0);
        if (!packageName.equals(currentPackage) && !"java.lang".equals(packageName)) {
            String canonicalName = ChunkUtils.join(names, ".");
            if (imports.contains(canonicalName)) {
                emit(ChunkUtils.join(names.subList(1, names.size()), "."));
                return;
            }
            importCandidates.add(canonicalName);
            emitIndented(canonicalName);
            return;
        }
        emitIndented(ChunkUtils.join(names.subList(1, names.size()), "."));
    }
}
