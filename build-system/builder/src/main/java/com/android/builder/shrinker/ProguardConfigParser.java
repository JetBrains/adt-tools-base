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

package com.android.builder.shrinker;

import com.android.utils.AsmUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stub of a real parser. Only checks the most simple rules produced by AAPT.
 */
public class ProguardConfigParser {

    private static final Pattern AAPT_PATTERN =
            Pattern.compile("-keep class ([a-zA-Z0-9$.]+) \\{ <init>\\(\\.\\.\\.\\); \\}");

    private static final ImmutableSet<String> FLAGS = ImmutableSet.of(
            "-dontoptimize",
            "-dontobfuscate");

    private Set<String> classesToKeep = Sets.newHashSet();

    public void parse(File configFile) throws IOException {
        for (String line : Files.readLines(configFile, Charsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Matcher matcher = AAPT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String className = matcher.group(1);
                this.classesToKeep.add(AsmUtils.toInternalName(className));
                continue;
            }

            if (FLAGS.contains(line)) {
                continue;
            }

            throw new RuntimeException("Don't know how to parse '" + line + "'");
        }
    }

    public KeepRules getKeepRules() {
        final Set<String> classesSoFar = ImmutableSet.copyOf(classesToKeep);

        return new KeepRules() {
            @Override
            public boolean keep(ClassNode classNode, MethodNode methodNode) {
                return AsmUtils.CONSTRUCTOR.equals(methodNode.name)
                        && classesSoFar.contains(classNode.name);
            }
        };
    }
}
