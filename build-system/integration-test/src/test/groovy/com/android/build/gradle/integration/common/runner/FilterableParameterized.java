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

package com.android.build.gradle.integration.common.runner;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Parameterized;

/**
 * Version of {@link Parameterized} that supports filtering methods by name and category.
 *
 * <p>To use it, just assume all the generated methods have names like
 * {@code com.example.ClassName.methodName[0]} etc. - the part in brackets can be configured with
 * the {@link org.junit.runners.Parameterized.Parameters} annotation on the test class.
 *
 * <p>Concrete example: {@code gw :b:i:test --tests=*.ArchivesBaseNameTest.*application*}
 *
 * <p>It also supports filtering methods out by categories (but not classes, for now).
 *
 * <p>Note that regular {@link Parameterized} implements
 * {@link org.junit.runner.manipulation.Filterable} but in a way that's not compatible with
 * Gradle's --tests flag.
 */
public class FilterableParameterized extends Parameterized {
    public FilterableParameterized(Class<?> klass) throws Throwable {
        super(klass);
    }

    @Override
    public void filter(final Filter filter) throws NoTestsRemainException {
        Filter wrapper = new Filter() {
            @Override
            public boolean shouldRun(Description description) {
                if (description.getTestClass() == null
                        && description.getClassName().startsWith("[")
                        && description.getClassName().endsWith("]")) {
                    // This is the artificial Description that Parameterized uses at class level.
                    return true;
                }
                return filter.shouldRun(description);
            }

            @Override
            public String describe() {
                return filter.describe();
            }
        };
        super.filter(wrapper);
    }
}
