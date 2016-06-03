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
package com.android.build.gradle.external.gnumake;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FlowAnalyzerTest {

    private final ArrayList<String> COMPILE_FLAG_C = Lists.newArrayList("-c");

    private void assertFlowAnalysisEquals(String string, String expected) {
        ListMultimap<String, List<BuildStepInfo>> io = FlowAnalyzer
                    .analyze(string, true);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<BuildStepInfo>> output : io.entries()) {
            sb.append(output.getKey());
            sb.append(":\n");
            List<BuildStepInfo> commandSummaries = output.getValue();
            Collections.sort(commandSummaries, new Comparator<BuildStepInfo>() {
                @Override
                public int compare(BuildStepInfo o1, BuildStepInfo o2) {
                    return o1.getOnlyInput().compareTo(o2.getOnlyInput());
                }
            });

            for (BuildStepInfo input : commandSummaries) {
                sb.append("  ");
                sb.append(input.getOnlyInput()).append(" -> ").append(input.getCommand());
                sb.append("\n");
            }
        }
        assertThat(expected).isEqualTo(sb.toString());
    }

    private void assertFlowAnalysisEquals(
            String string,
            FlowAnalysisBuilder expected) {
        ListMultimap<String, List<BuildStepInfo>> io = FlowAnalyzer
                .analyze(string, true);

        assertThat(io).isEqualTo(expected.map);
    }

    private static class FlowAnalysisBuilder {
        ListMultimap<String, List<BuildStepInfo>> map;
        FlowAnalysisBuilder() {
            this.map = ArrayListMultimap.create();
        }

        FlowAnalysisBuilder with(String library) {
            return with(library, new BuildStepInfosBuilder());
        }

        FlowAnalysisBuilder with(String library, BuildStepInfosBuilder info) {
            map.put(library, info.infos);
            return this;
        }
    }

    private static FlowAnalysisBuilder flow() {
        return new FlowAnalysisBuilder();
    }

    private static class BuildStepInfosBuilder {
        List<BuildStepInfo> infos = Lists.newArrayList();
        BuildStepInfosBuilder with(
                String command,
                List<String> commandArgs,
                String input,
                List<String> outputs,
                boolean inputsAreSourceFiles) {
            infos.add(new BuildStepInfo(
                    new CommandLine(command, commandArgs),
                    Lists.newArrayList(input),
                    outputs,
                    inputsAreSourceFiles));
            return this;
        }
    }

    private static BuildStepInfosBuilder step() {
        return new BuildStepInfosBuilder();
    }

    @Test
    public void disallowedTerminal() throws FileNotFoundException {
        assertFlowAnalysisEquals("g++ -c a.c -o a.o\ng++ a.o -o a.so",
                flow().with("a.so",
                        step().with("g++", COMPILE_FLAG_C, "a.c",
                                Lists.newArrayList("a.o"), true)));
    }

    @Test
    public void doubleTarget() throws FileNotFoundException {
        assertFlowAnalysisEquals("g++ -c a.c -o x/a.o\n" +
                "g++ x/a.o -o x/a.so\n" +
                "g++ -c a.c -o y/a.o\n" +
                "g++ y/a.o -o y/a.so",
                flow()
                    .with("y/a.so",
                        step().with("g++", COMPILE_FLAG_C, "a.c",
                                Lists.newArrayList("y/a.o"), true))
                    .with("x/a.so",
                        step().with("g++", COMPILE_FLAG_C, "a.c",
                                Lists.newArrayList("x/a.o"), true)));
    }

    @Test
    public void simple() throws FileNotFoundException {
        assertFlowAnalysisEquals("g++ -c a.c -o a.o\ng++ a.o -o a.so",
                flow().with("a.so",
                        step().with("g++", COMPILE_FLAG_C, "a.c",
                                Lists.newArrayList("a.o"), true)
                ));
    }
}
