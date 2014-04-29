/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

/**
 * Ensure that resources in Gradle projects which specify a resource prefix
 * conform to the given name
 *
 * TODO: What about id's?
 * TODO: Check file resources!
 */
public class ResourcePrefixDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ResourceName", //$NON-NLS-1$
            "Resource with Wrong Prefix",
            "Ensures that resource names follow the specified name prefix for the project",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",

            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link com.android.tools.lint.checks.ResourcePrefixDetector} */
    public ResourcePrefixDetector() {
    }

    private String mPrefix;

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_RESOURCES, TAG_DECLARE_STYLEABLE);
    }

    @Nullable
    private static String computeResourcePrefix(@NonNull Project project) {
        if (project.isGradleProject()) {
            return LintUtils.computeResourcePrefix(project.getGradleProjectModel());
        }

        return null;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mPrefix = computeResourcePrefix(context.getProject());
    }

    @Override
    public void beforeCheckLibraryProject(@NonNull Context context) {
        // TODO: Make sure this doesn't wipe out the prefix for the remaining projects
        mPrefix = computeResourcePrefix(context.getProject());
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void afterCheckLibraryProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String name = LintUtils.getBaseName(context.file.getName());
                if (!name.startsWith(mPrefix)) {
                    context.report(ISSUE, Location.create(context.file),
                            getErrorMessage(name), null);
                }
            }
        }

        // TODO: Handle bitmap resources
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        for (Element item : LintUtils.getChildren(element)) {
            Attr nameAttribute = item.getAttributeNode(ATTR_NAME);
            if (nameAttribute != null) {
                String name = nameAttribute.getValue();
                if (!name.startsWith(mPrefix)) {
                    String message = getErrorMessage(name);
                    context.report(ISSUE, nameAttribute, context.getLocation(nameAttribute),
                            message, null);
                }
            }
        }
    }

    private String getErrorMessage(String name) {
        assert mPrefix != null && !name.startsWith(mPrefix);
        return String.format("Resource named '%1$s' does not start "
                    + "with the project's resource prefix '%2$s'; rename to '%3$s' ?",
                    name, mPrefix, LintUtils.computeResourceName(mPrefix, name));
    }
}
