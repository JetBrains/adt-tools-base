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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Checks for unreachable states in an Android state list definition
 */
public class VectorDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "VectorRaster", //$NON-NLS-1$
            "Vector Image Generation",
            "Vector icons require API 21, but when using Android Gradle plugin 1.4 or higher, " +
            "vectors placed in the `drawable` folder are automatically moved to `drawable-*dpi-v21` " +
            "and a bitmap image is generated each `drawable-*dpi` folder instead, for backwards " +
            "compatibility (provided `minSdkVersion` is less than 21.).\n" +
            "\n" +
            "However, there are some limitations to this vector image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for those " +
            "older devices.",

            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }


    /**
     * Returns true if the given Gradle project model supports vector image generation
     *
     * @param project the project to check
     * @return true if the plugin supports vector image generation
     */
    public static boolean isVectorGenerationSupported(@NonNull AndroidProject project) {
        String modelVersion = project.getModelVersion();

        // Requires 1.4.x or higher. Rather than doing string => x.y.z decomposition and then
        // checking higher than 1.4.0, we'll just exclude the 4 possible prefixes that don't satisfy
        // this requirement.
        return !(modelVersion.startsWith("1.0")
                 || modelVersion.startsWith("1.1")
                 || modelVersion.startsWith("1.2")
                 || modelVersion.startsWith("1.3"));
    }


    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // If minSdkVersion >= 21, we're not generating compatibility vector icons
        Project project = context.getMainProject();
        if (project.getMinSdkVersion().getFeatureLevel() >= 21) {
            return;
        }

        // Vector generation is only done for Gradle projects
        if (!project.isGradleProject()) {
            return;
        }

        // Not using a plugin that supports vector image generation?
        AndroidProject model = project.getGradleProjectModel();
        if (model == null || !isVectorGenerationSupported(model)) {
            return;
        }

        Element root = document.getDocumentElement();
        // If this is not actually a vector icon, nothing to do in this detector
        if (root == null || !root.getTagName().equals("vector")) { //$NON-NLS-1$
            return;
        }

        // If this vector asset is in a -v21 folder, we're not generating vector assets
        if (context.getFolderVersion() >= 21) {
            return;
        }

        // TODO: Check to see if there already is a -?dpi version of the file; if so,
        // we also won't be generating a vector image


        checkSupported(context, root);
    }

    /** Recursive element check for unsupported attributes and tags */
    private static void checkSupported(@NonNull XmlContext context, @NonNull Element element) {
        // Unsupported tags
        String tag = element.getTagName();
        if ("clip-path".equals(tag) || "group".equals(tag)) {
            String message = "This tag is not supported in images generated from this "
                    + "vector icon for API < 21; check generated icon to make sure it looks "
                    + "acceptable";
            context.report(ISSUE, element, context.getLocation(element), message);
        } else if ("group".equals(tag)) {
            String message = "This tag is not fully supported in images generated from this "
                    + "vector icon for API < 21; check generated icon to make sure it looks "
                    + "acceptable";
            context.report(ISSUE, element, context.getLocation(element), message);
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr)attributes.item(i);
            String name = attr.getLocalName();
            if (("autoMirrored".equals(name)
                    || "trimPathStart".equals(name)
                    || "trimPathEnd".equals(name)
                    || "trimPathOffset".equals(name))
                    && ANDROID_URI.equals(attr.getNamespaceURI())) {
                String message = "This attribute is not supported in images generated from this "
                        + "vector icon for API < 21; check generated icon to make sure it looks "
                        + "acceptable";
                context.report(ISSUE, attr, context.getNameLocation(attr), message);
            }

            String value = attr.getValue();
            if (ResourceUrl.parse(value) != null) {
                String message = "Resource references will not work correctly in images generated "
                        + "for this vector icon for API < 21; check generated icon to make sure "
                        + "it looks acceptable";
                context.report(ISSUE, attr, context.getValueLocation(attr), message);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkSupported(context, ((Element) child));
            }
        }
    }
}
