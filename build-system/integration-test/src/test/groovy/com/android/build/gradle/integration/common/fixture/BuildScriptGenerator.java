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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.text.StrSubstitutor;

import java.util.List;
import java.util.Map;

/**
 * Builder class to assist in creating build files for both gradle and gradle-experimental plugin.
 */
public class BuildScriptGenerator {

    private static class PatternSubstitution {

        @NonNull
        private String keyword;
        @NonNull
        private String gradlePluginString;
        @NonNull
        private String componentModelPluginString;

        public PatternSubstitution(
                @NonNull String keyword,
                @NonNull String gradlePluginString,
                @NonNull String componentModelPluginString) {
            this.keyword = keyword;
            this.gradlePluginString = gradlePluginString;
            this.componentModelPluginString = componentModelPluginString;
        }

        @NonNull
        public String getKeyword() {
            return keyword;
        }

        @NonNull
        public String getGradlePluginString() {
            return gradlePluginString;
        }

        @NonNull
        public String getComponentModelPluginString() {
            return componentModelPluginString;
        }
    }

    private static final List<PatternSubstitution> DEFAULT_PATTERN = ImmutableList.of(
            new PatternSubstitution("model_start", "", "model {\n"),
            new PatternSubstitution("model_end", "", "}\n"),
            new PatternSubstitution("application_plugin", "com.android.application", "com.android.model.application"),
            new PatternSubstitution("library_plugin", "com.android.library", "com.android.model.library")
    );

    @NonNull
    private String template;
    @NonNull
    private List<PatternSubstitution> substitions = Lists.newArrayList(DEFAULT_PATTERN);

    /**
     * Create BuildScriptGenerator for the specified template.
     */
    public BuildScriptGenerator(@NonNull String template) {
        this.template = template;
    }

    /**
     * Add a custom pattern.
     *
     * Strings matching "${keyword}" in the template will replaced by either the gradlePluginString
     * or componentModelPluginString.
     */
    public BuildScriptGenerator addPattern(
            @NonNull String keyword,
            @NonNull String gradlePluginString,
            @NonNull String componentModelPluginString) {
        substitions.add(new PatternSubstitution(
                keyword,
                gradlePluginString,
                componentModelPluginString));
        return this;
    }

    /**
     * Create String for build script by replacing all keywords in the template.
     */
    public String build(boolean forComponentModelPlugin) {
        Map<String, String> substitutionMap = Maps.newHashMap();
        for (PatternSubstitution substitution : substitions) {
            substitutionMap.put(
                    substitution.getKeyword(),
                    forComponentModelPlugin
                            ? substitution.getComponentModelPluginString()
                            : substitution.getGradlePluginString());
        }
        StrSubstitutor substitutor = new StrSubstitutor(substitutionMap);
        return substitutor.replace(template);
    }
}
