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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.xml.AndroidManifest;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detects references to resources in the manifest that vary by configuration
 */
public class ManifestResourceDetector extends ResourceXmlDetector {
    /** Using resources in the manifest that vary by configuration */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE = Issue.create(
            "ManifestResource", //$NON-NLS-1$
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon.)",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    Scope.MANIFEST_AND_RESOURCE_SCOPE,
                    Scope.MANIFEST_SCOPE));

    /**
     * Map from resource name to resource type to manifest location; used
     * in batch mode to report errors when resource overrides are found
     */
    private Map<String, Multimap<ResourceType, Location>> mManifestLocations;

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (endsWithIgnoreCase(context.file.getPath(), ANDROID_MANIFEST_XML)) {
            checkManifest(context, document);
        } else {
            //noinspection VariableNotUsedInsideIf
            if (mManifestLocations != null) {
                checkResourceFile(context, document);
            }
        }
    }

    private void checkManifest(@NonNull XmlContext context, @NonNull Document document) {
        LintClient client = context.getClient();
        Project project = context.getProject();
        AbstractResourceRepository repository = null;
        if (client.supportsProjectResources()) {
            repository = client.getProjectResources(project, true);
        }
        if (repository == null && !context.getScope().contains(Scope.RESOURCE_FILE)) {
            // Can't perform incremental analysis without a resource repository
            return;
        }

        Element root = document.getDocumentElement();
        if (root != null) {
            visit(context, root, repository);
        }
    }

    private void visit(@NonNull XmlContext context, @NonNull Element element,
            @Nullable AbstractResourceRepository repository) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node node = attributes.item(i);
            String value = node.getNodeValue();
            if (value.startsWith(PREFIX_RESOURCE_REF)) {
                Attr attribute = (Attr) node;
                if (!isAllowedToVary(attribute)) {
                    checkReference(context, attribute, value, repository);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visit(context, ((Element)child), repository);
            }
        }
    }

    /**
     * Is the given attribute allowed to reference a resource that has different
     * values across configurations (other than with version qualifiers) ?
     * <p>
     * When the manifest is read, it has a fixed configuration with only the API level set.
     * When strings are read, we can either read the actual string, or a resource reference.
     * For labels and icons, we only read the resource reference -- that is the package manager
     * doesn't need the actual string (like it would need for, say, the name of an activity),
     * but just gets the resource ID, and then clients if they need the actual resource value can
     * load it at that point using their current configuration.
     * <p>
     * To see which specific attributes in the manifest are processed this way, look at
     * android.content.pm.PackageItemInfo to see what pieces of data are kept as raw resource
     * IDs instead of loading their value. (For label resources we also keep the non localized
     * label resource to allow people to specify hardcoded strings instead of a resource reference.)
     *
     * @param attribute the attribute node to look up
     * @return true if this resource is allowed to have delayed configuration values
     */
    private static boolean isAllowedToVary(@NonNull Attr attribute) {
        // This corresponds to the getResourceId() calls in PackageParser
        // where we store the actual resource id such that they can be
        // resolved later
        String name = attribute.getLocalName();
        if (ATTR_LABEL.equals(name)
                || ATTR_ICON.equals(name)
                || ATTR_THEME.equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "sharedUserLabel".equals(name)) {
            return ANDROID_URI.equals(attribute.getNamespaceURI());
        }

        if ("description".equals(name)) {
            String tagName = attribute.getOwnerElement().getTagName();
            return AndroidManifest.NODE_PERMISSION_GROUP.equals(tagName);
        }

        return false;
    }

    private void checkReference(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull String value,
            @Nullable AbstractResourceRepository repository) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && !url.framework) {
            if (repository != null) {
                List<ResourceItem> items = repository.getResourceItem(url.type, url.name);
                if (items != null && items.size() > 1) {
                    List<String> list = Lists.newArrayListWithExpectedSize(5);
                    for (ResourceItem item : items) {
                        String qualifiers = item.getQualifiers();
                        // Default folder is okay
                        if (qualifiers.isEmpty()) {
                            continue;
                        }

                        // Version qualifier is okay
                        if (VersionQualifier.getQualifier(qualifiers) != null) {
                            continue;
                        }

                        list.add(qualifiers);
                    }
                    if (!list.isEmpty()) {
                        Collections.sort(list);
                        String message = getErrorMessage(Joiner.on(", ").join(list));
                        context.report(ISSUE, attribute, context.getValueLocation(attribute),
                                message);
                    }
                }
            } else if (!context.getDriver().isSuppressed(context, ISSUE, attribute)) {
                // Don't have a resource repository; need to check resource files during batch
                // run
                if (mManifestLocations == null) {
                    mManifestLocations = Maps.newHashMap();
                }
                Multimap<ResourceType, Location> typeMap = mManifestLocations.get(url.name);
                if (typeMap == null) {
                    typeMap = ArrayListMultimap.create();
                    mManifestLocations.put(url.name, typeMap);
                }
                typeMap.put(url.type, context.getValueLocation(attribute));
            }
        }
    }

    private void checkResourceFile(
            @NonNull XmlContext context,
            @NonNull Document document) {
        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }
        String parentName = parentFile.getName();
        // Base folders are okay
        int index = parentName.indexOf('-');
        if (index == -1) {
            return;
        }

        // Version qualifier is okay
        String qualifiers = parentName.substring(index + 1);
        if (VersionQualifier.getQualifier(qualifiers) != null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            Element root = document.getDocumentElement();
            if (root != null) {
                NodeList children = root.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element item = (Element)child;
                        String name = item.getAttribute(ATTR_NAME);
                        if (name != null && mManifestLocations.containsKey(name)) {
                            String tag = item.getTagName();
                            String typeString = tag;
                            if (tag.equals(TAG_ITEM)) {
                                typeString = item.getAttribute(ATTR_TYPE);
                            }
                            ResourceType type = ResourceType.getEnum(typeString);
                            if (type != null) {
                                reportIfFound(context, qualifiers, name, type, item);
                            }
                        }
                    }
                }

            }
        } else if (folderType != null) {
            String name = LintUtils.getBaseName(context.file.getName());
            if (mManifestLocations.containsKey(name)) {
                List<ResourceType> types =
                        FolderTypeRelationship.getRelatedResourceTypes(folderType);
                for (ResourceType type : types) {
                    reportIfFound(context, qualifiers, name, type, document.getDocumentElement());
                }
            }
        }
    }

    private void reportIfFound(@NonNull XmlContext context, @NonNull  String qualifiers,
            @NonNull  String name, @NonNull  ResourceType type, @Nullable Node secondary) {
        Multimap<ResourceType, Location> typeMap = mManifestLocations.get(name);
        if (typeMap != null) {
            Collection<Location> locations = typeMap.get(type);
            if (locations != null) {
                for (Location location : locations) {
                    String message = getErrorMessage(qualifiers);
                    if (secondary != null) {
                        Location secondaryLocation = context.getLocation(secondary);
                        secondaryLocation.setSecondary(location.getSecondary());
                        secondaryLocation.setMessage("This value will not be used");
                        location.setSecondary(secondaryLocation);
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @NonNull
    private static String getErrorMessage(@NonNull  String qualifiers) {
        return "Resources referenced from the manifest cannot vary by configuration "
                + "(except for version qualifiers, e.g. `-v21`.) Found variation in " + qualifiers;
    }
}
