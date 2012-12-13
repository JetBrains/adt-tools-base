/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.google.common.annotations.Beta;

import java.util.EnumSet;

/**
 * The scope of a detector is the set of files a detector must consider when
 * performing its analysis. This can be used to determine when issues are
 * potentially obsolete, whether a detector should re-run on a file save, etc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public enum Scope {
    /**
     * The analysis only considers a single XML resource file at a time.
     * <p>
     * Issues which are only affected by a single resource file can be checked
     * for incrementally when a file is edited.
     */
    RESOURCE_FILE,

    /**
     * The analysis considers <b>all</b> the resource file. This scope must not
     * be used in conjunction with {@link #RESOURCE_FILE}; an issue scope is
     * either considering just a single resource file or all the resources, not
     * both.
     */
    ALL_RESOURCE_FILES,

    /**
     * The analysis only considers a single Java source file at a time.
     * <p>
     * Issues which are only affected by a single Java source file can be
     * checked for incrementally when a Java source file is edited.
     */
    JAVA_FILE,

    /**
     * The analysis considers <b>all</b> the Java source files together.
     * <p>
     * This flag is mutually exclusive with {@link #JAVA_FILE}.
     */
    ALL_JAVA_FILES,

    /**
     * The analysis only considers a single Java class file at a time.
     * <p>
     * Issues which are only affected by a single Java class file can be checked
     * for incrementally when a Java source file is edited and then recompiled.
     */
    CLASS_FILE,

    /**
     * The analysis considers <b>all</b> the Java class files together.
     * <p>
     * This flag is mutually exclusive with {@link #CLASS_FILE}.
     */
    ALL_CLASS_FILES,

    /** The analysis considers the manifest file */
    MANIFEST,

    /** The analysis considers the Proguard configuration file */
    PROGUARD_FILE,

    /**
     * The analysis considers classes in the libraries for this project. These
     * will be analyzed before the classes themselves.
     */
    JAVA_LIBRARIES;

    /**
     * Returns true if the given scope set corresponds to scanning a single file
     * rather than a whole project
     *
     * @param scopes the scope set to check
     * @return true if the scope set references a single file
     */
    public static boolean checkSingleFile(@NonNull EnumSet<Scope> scopes) {
        int size = scopes.size();
        if (size == 2) {
            // When single checking a Java source file, we check both its Java source
            // and the associated class files
            return scopes.contains(JAVA_FILE) && scopes.contains(CLASS_FILE);
        } else {
            return size == 1 &&
                (scopes.contains(JAVA_FILE)
                        || scopes.contains(CLASS_FILE)
                        || scopes.contains(RESOURCE_FILE)
                        || scopes.contains(PROGUARD_FILE)
                        || scopes.contains(MANIFEST));
        }
    }

    /**
     * Returns the intersection of two scope sets
     *
     * @param scope1 the first set to intersect
     * @param scope2 the second set to intersect
     * @return the intersection of the two sets
     */
    @NonNull
    public static EnumSet<Scope> intersect(
            @NonNull EnumSet<Scope> scope1,
            @NonNull EnumSet<Scope> scope2) {
        EnumSet<Scope> scope = EnumSet.copyOf(scope1);
        scope.retainAll(scope2);

        return scope;
    }

    /** All scopes: running lint on a project will check these scopes */
    public static final EnumSet<Scope> ALL = EnumSet.allOf(Scope.class);
    /** Scope-set used for detectors which are affected by a single resource file */
    public static final EnumSet<Scope> RESOURCE_FILE_SCOPE = EnumSet.of(RESOURCE_FILE);
    /** Scope-set used for detectors which scan all resources */
    public static final EnumSet<Scope> ALL_RESOURCES_SCOPE = EnumSet.of(ALL_RESOURCE_FILES);
    /** Scope-set used for detectors which are affected by a single Java source file */
    public static final EnumSet<Scope> JAVA_FILE_SCOPE = EnumSet.of(JAVA_FILE);
    /** Scope-set used for detectors which are affected by a single Java class file */
    public static final EnumSet<Scope> CLASS_FILE_SCOPE = EnumSet.of(CLASS_FILE);
    /** Scope-set used for detectors which are affected by the manifest only */
    public static final EnumSet<Scope> MANIFEST_SCOPE = EnumSet.of(MANIFEST);
}
