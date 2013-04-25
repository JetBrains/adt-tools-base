/*
 * Copyright (C) 2012 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This detector identifies cases where a resource is defined multiple times in the
 * same resource folder
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition", //$NON-NLS-1$
            "Duplicate definitions of resources",
            "Discovers duplicate definitions of resources",

            "You can define a resource multiple times in different resource folders; that's how " +
            "string translations are done, for example. However, defining the same resource " +
            "more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, " +
            "and so on.",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.ALL_RESOURCES_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String PRODUCT = "product";   //$NON-NLS-1$
    private Map<ResourceType, Set<String>> mTypeMap;
    private Map<ResourceType, List<Pair<String, Location.Handle>>> mLocations;
    private File mParent;

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.NORMAL;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File parent = context.file.getParentFile();
        if (!parent.equals(mParent)) {
            mParent = parent;
            mTypeMap = Maps.newEnumMap(ResourceType.class);
            mLocations = Maps.newEnumMap(ResourceType.class);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();

        if (element.hasAttribute(PRODUCT)) {
            return;
        }

        String tag = element.getTagName();
        String typeString = tag;
        if (tag.equals(TAG_ITEM)) {
            typeString = element.getAttribute(ATTR_TYPE);
        }
        ResourceType type = ResourceType.getEnum(typeString);
        if (type == null) {
            return;
        }

        if (type == ResourceType.ATTR
                && element.getParentNode().getNodeName().equals(
                        ResourceType.DECLARE_STYLEABLE.getName())) {
            return;
        }

        Set<String> names = mTypeMap.get(type);
        if (names == null) {
            names = Sets.newHashSetWithExpectedSize(40);
            mTypeMap.put(type, names);
        }

        String name = attribute.getValue();
        if (names.contains(name)) {
            String message = String.format("%1$s has already been defined in this folder",
                    name);
            Location location = context.getLocation(attribute);
            List<Pair<String, Handle>> list = mLocations.get(type);
            for (Pair<String, Handle> pair : list) {
                if (name.equals(pair.getFirst())) {
                    Location secondary = pair.getSecond().resolve();
                    secondary.setMessage("Previously defined here");
                    location.setSecondary(secondary);
                }
            }
            context.report(ISSUE, attribute, location, message, null);
        } else {
            names.add(name);
            List<Pair<String, Handle>> list = mLocations.get(type);
            if (list == null) {
                list = Lists.newArrayList();
                mLocations.put(type, list);
            }
            Location.Handle handle = context.parser.createLocationHandle(context, attribute);
            list.add(Pair.of(name, handle));
        }
    }
}
