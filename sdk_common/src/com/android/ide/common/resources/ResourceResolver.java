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

package com.android.ide.common.resources;

import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ANDROID_THEME_PREFIX;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.REFERENCE_STYLE;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ResourceResolver extends RenderResources {

    private final Map<ResourceType, Map<String, ResourceValue>> mProjectResources;
    private final Map<ResourceType, Map<String, ResourceValue>> mFrameworkResources;

    private final Map<StyleResourceValue, StyleResourceValue> mStyleInheritanceMap =
        new HashMap<StyleResourceValue, StyleResourceValue>();

    private StyleResourceValue mTheme;

    private FrameworkResourceIdProvider mFrameworkProvider;
    private LayoutLog mLogger;
    private String mThemeName;
    private boolean mIsProjectTheme;

    private ResourceResolver(
            Map<ResourceType, Map<String, ResourceValue>> projectResources,
            Map<ResourceType, Map<String, ResourceValue>> frameworkResources) {
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;
    }

    /**
     * Creates a new {@link ResourceResolver} object.
     *
     * @param projectResources the project resources.
     * @param frameworkResources the framework resources.
     * @param themeName the name of the current theme.
     * @param isProjectTheme Is this a project theme?
     * @return a new {@link ResourceResolver}
     */
    public static ResourceResolver create(
            Map<ResourceType, Map<String, ResourceValue>> projectResources,
            Map<ResourceType, Map<String, ResourceValue>> frameworkResources,
            String themeName, boolean isProjectTheme) {

        ResourceResolver resolver = new ResourceResolver(
                projectResources, frameworkResources);

        resolver.computeStyleMaps(themeName, isProjectTheme);

        return resolver;
    }

    // ---- Methods to help dealing with older LayoutLibs.

    public String getThemeName() {
        return mThemeName;
    }

    public boolean isProjectTheme() {
        return mIsProjectTheme;
    }

    public Map<ResourceType, Map<String, ResourceValue>> getProjectResources() {
        return mProjectResources;
    }

    public Map<ResourceType, Map<String, ResourceValue>> getFrameworkResources() {
        return mFrameworkResources;
    }

    // ---- RenderResources Methods

    @Override
    public void setFrameworkResourceIdProvider(FrameworkResourceIdProvider provider) {
        mFrameworkProvider = provider;
    }

    @Override
    public void setLogger(LayoutLog logger) {
        mLogger = logger;
    }

    @Override
    public StyleResourceValue getCurrentTheme() {
        return mTheme;
    }

    @Override
    public StyleResourceValue getTheme(String name, boolean frameworkTheme) {
        ResourceValue theme = null;

        if (frameworkTheme) {
            Map<String, ResourceValue> frameworkStyleMap = mFrameworkResources.get(
                    ResourceType.STYLE);
            theme = frameworkStyleMap.get(name);
        } else {
            Map<String, ResourceValue> projectStyleMap = mProjectResources.get(ResourceType.STYLE);
            theme = projectStyleMap.get(name);
        }

        if (theme instanceof StyleResourceValue) {
            return (StyleResourceValue) theme;
        }

        return null;
    }

    @Override
    public boolean themeIsParentOf(StyleResourceValue parentTheme, StyleResourceValue childTheme) {
        do {
            childTheme = mStyleInheritanceMap.get(childTheme);
            if (childTheme == null) {
                return false;
            } else if (childTheme == parentTheme) {
                return true;
            }
        } while (true);
    }

    @Override
    public ResourceValue getFrameworkResource(ResourceType resourceType, String resourceName) {
        return getResource(resourceType, resourceName, mFrameworkResources);
    }

    @Override
    public ResourceValue getProjectResource(ResourceType resourceType, String resourceName) {
        return getResource(resourceType, resourceName, mProjectResources);
    }

    @Override
    @Deprecated
    public ResourceValue findItemInStyle(StyleResourceValue style, String attrName) {
        // this method is deprecated because it doesn't know about the namespace of the
        // attribute so we search for the project namespace first and then in the
        // android namespace if needed.
        ResourceValue item = findItemInStyle(style, attrName, false /*isFrameworkAttr*/);
        if (item == null) {
            item = findItemInStyle(style, attrName, true /*isFrameworkAttr*/);
        }

        return item;
    }

    @Override
    public ResourceValue findItemInStyle(StyleResourceValue style, String itemName,
            boolean isFrameworkAttr) {
        ResourceValue item = style.findValue(itemName, isFrameworkAttr);

        // if we didn't find it, we look in the parent style (if applicable)
        if (item == null && mStyleInheritanceMap != null) {
            StyleResourceValue parentStyle = mStyleInheritanceMap.get(style);
            if (parentStyle != null) {
                return findItemInStyle(parentStyle, itemName, isFrameworkAttr);
            }
        }

        return item;
    }

    @Override
    public ResourceValue findResValue(String reference, boolean forceFrameworkOnly) {
        if (reference == null) {
            return null;
        }
        if (reference.startsWith(PREFIX_THEME_REF)) {
            // no theme? no need to go further!
            if (mTheme == null) {
                return null;
            }

            boolean frameworkOnly = false;

            // eliminate the prefix from the string
            if (reference.startsWith(ANDROID_THEME_PREFIX)) {
                frameworkOnly = true;
                reference = reference.substring(ANDROID_THEME_PREFIX.length());
            } else {
                reference = reference.substring(PREFIX_THEME_REF.length());
            }

            // at this point, value can contain type/name (drawable/foo for instance).
            // split it to make sure.
            String[] segments = reference.split("\\/");

            // we look for the referenced item name.
            String referenceName = null;

            if (segments.length == 2) {
                // there was a resType in the reference. If it's attr, we ignore it
                // else, we assert for now.
                if (ResourceType.ATTR.getName().equals(segments[0])) {
                    referenceName = segments[1];
                } else {
                    // At this time, no support for ?type/name where type is not "attr"
                    return null;
                }
            } else {
                // it's just an item name.
                referenceName = segments[0];
            }

            // now we look for android: in the referenceName in order to support format
            // such as: ?attr/android:name
            if (referenceName.startsWith(PREFIX_ANDROID)) {
                frameworkOnly = true;
                referenceName = referenceName.substring(PREFIX_ANDROID.length());
            }

            // Now look for the item in the theme, starting with the current one.
            ResourceValue item = findItemInStyle(mTheme, referenceName,
                    forceFrameworkOnly || frameworkOnly);

            if (item == null && mLogger != null) {
                mLogger.warning(LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR,
                        String.format("Couldn't find theme resource %1$s for the current theme",
                                reference),
                        new ResourceValue(ResourceType.ATTR, referenceName, frameworkOnly));
            }

            return item;
        } else if (reference.startsWith(PREFIX_RESOURCE_REF)) {
            boolean frameworkOnly = false;

            // check for the specific null reference value.
            if (REFERENCE_NULL.equals(reference)) {
                return null;
            }

            // Eliminate the prefix from the string.
            if (reference.startsWith(ANDROID_PREFIX)) {
                frameworkOnly = true;
                reference = reference.substring(ANDROID_PREFIX.length());
            } else {
                reference = reference.substring(PREFIX_RESOURCE_REF.length());
            }

            // at this point, value contains type/[android:]name (drawable/foo for instance)
            String[] segments = reference.split("\\/");
            if (segments.length <= 1) {
                return null;
            }

            // now we look for android: in the resource name in order to support format
            // such as: @drawable/android:name
            if (segments[1].startsWith(PREFIX_ANDROID)) {
                frameworkOnly = true;
                segments[1] = segments[1].substring(PREFIX_ANDROID.length());
            }

            ResourceType type = ResourceType.getEnum(segments[0]);

            // unknown type?
            if (type == null) {
                return null;
            }

            return findResValue(type, segments[1],
                    forceFrameworkOnly ? true :frameworkOnly);
        }

        // Looks like the value didn't reference anything. Return null.
        return null;
    }

    @Override
    public ResourceValue resolveValue(ResourceType type, String name, String value,
            boolean isFrameworkValue) {
        if (value == null) {
            return null;
        }

        // get the ResourceValue referenced by this value
        ResourceValue resValue = findResValue(value, isFrameworkValue);

        // if resValue is null, but value is not null, this means it was not a reference.
        // we return the name/value wrapper in a ResourceValue. the isFramework flag doesn't
        // matter.
        if (resValue == null) {
            return new ResourceValue(type, name, value, isFrameworkValue);
        }

        // we resolved a first reference, but we need to make sure this isn't a reference also.
        return resolveResValue(resValue);
    }

    @Override
    public ResourceValue resolveResValue(ResourceValue resValue) {
        if (resValue == null) {
            return null;
        }

        // if the resource value is null, we simply return it.
        String value = resValue.getValue();
        if (value == null) {
            return resValue;
        }

        // else attempt to find another ResourceValue referenced by this one.
        ResourceValue resolvedResValue = findResValue(value, resValue.isFramework());

        // if the value did not reference anything, then we simply return the input value
        if (resolvedResValue == null) {
            return resValue;
        }

        // detect potential loop due to mishandled namespace in attributes
        if (resValue == resolvedResValue) {
            if (mLogger != null) {
                mLogger.error(LayoutLog.TAG_BROKEN,
                        String.format("Potential stackoverflow trying to resolve '%s'. Render may not be accurate.", value),
                        null);
            }
            return resValue;
        }

        // otherwise, we attempt to resolve this new value as well
        return resolveResValue(resolvedResValue);
    }

    // ---- Private helper methods.

    /**
     * Searches for, and returns a {@link ResourceValue} by its name, and type.
     * @param resType the type of the resource
     * @param resName  the name of the resource
     * @param frameworkOnly if <code>true</code>, the method does not search in the
     * project resources
     */
    private ResourceValue findResValue(ResourceType resType, String resName,
            boolean frameworkOnly) {
        // map of ResouceValue for the given type
        Map<String, ResourceValue> typeMap;

        // if allowed, search in the project resources first.
        if (frameworkOnly == false) {
            typeMap = mProjectResources.get(resType);
            ResourceValue item = typeMap.get(resName);
            if (item != null) {
                return item;
            }
        }

        // now search in the framework resources.
        typeMap = mFrameworkResources.get(resType);
        ResourceValue item = typeMap.get(resName);
        if (item != null) {
            return item;
        }

        // if it was not found and the type is an id, it is possible that the ID was
        // generated dynamically when compiling the framework resources.
        // Look for it in the R map.
        if (mFrameworkProvider != null && resType == ResourceType.ID) {
            if (mFrameworkProvider.getId(resType, resName) != null) {
                return new ResourceValue(resType, resName, true);
            }
        }

        // didn't find the resource anywhere.
        if (mLogger != null) {
            mLogger.warning(LayoutLog.TAG_RESOURCES_RESOLVE,
                    "Couldn't resolve resource @" +
                    (frameworkOnly ? "android:" : "") + resType + "/" + resName,
                    new ResourceValue(resType, resName, frameworkOnly));
        }
        return null;
    }

    private ResourceValue getResource(ResourceType resourceType, String resourceName,
            Map<ResourceType, Map<String, ResourceValue>> resourceRepository) {
        Map<String, ResourceValue> typeMap = resourceRepository.get(resourceType);
        if (typeMap != null) {
            ResourceValue item = typeMap.get(resourceName);
            if (item != null) {
                item = resolveResValue(item);
                return item;
            }
        }

        // didn't find the resource anywhere.
        return null;

    }

    /**
     * Compute style information from the given list of style for the project and framework.
     * @param themeName the name of the current theme.
     * @param isProjectTheme Is this a project theme?
     */
    private void computeStyleMaps(String themeName, boolean isProjectTheme) {
        mThemeName = themeName;
        mIsProjectTheme = isProjectTheme;
        Map<String, ResourceValue> projectStyleMap = mProjectResources.get(ResourceType.STYLE);
        Map<String, ResourceValue> frameworkStyleMap = mFrameworkResources.get(ResourceType.STYLE);

        // first, get the theme
        ResourceValue theme = null;

        // project theme names have been prepended with a *
        if (isProjectTheme) {
            theme = projectStyleMap.get(themeName);
        } else {
            theme = frameworkStyleMap.get(themeName);
        }

        if (theme instanceof StyleResourceValue) {
            // compute the inheritance map for both the project and framework styles
            computeStyleInheritance(projectStyleMap.values(), projectStyleMap,
                    frameworkStyleMap);

            // Compute the style inheritance for the framework styles/themes.
            // Since, for those, the style parent values do not contain 'android:'
            // we want to force looking in the framework style only to avoid using
            // similarly named styles from the project.
            // To do this, we pass null in lieu of the project style map.
            computeStyleInheritance(frameworkStyleMap.values(), null /*inProjectStyleMap */,
                    frameworkStyleMap);

            mTheme = (StyleResourceValue) theme;
        }
    }



    /**
     * Compute the parent style for all the styles in a given list.
     * @param styles the styles for which we compute the parent.
     * @param inProjectStyleMap the map of project styles.
     * @param inFrameworkStyleMap the map of framework styles.
     * @param outInheritanceMap the map of style inheritance. This is filled by the method.
     */
    private void computeStyleInheritance(Collection<ResourceValue> styles,
            Map<String, ResourceValue> inProjectStyleMap,
            Map<String, ResourceValue> inFrameworkStyleMap) {
        for (ResourceValue value : styles) {
            if (value instanceof StyleResourceValue) {
                StyleResourceValue style = (StyleResourceValue)value;
                StyleResourceValue parentStyle = null;

                // first look for a specified parent.
                String parentName = style.getParentStyle();

                // no specified parent? try to infer it from the name of the style.
                if (parentName == null) {
                    parentName = getParentName(value.getName());
                }

                if (parentName != null) {
                    parentStyle = getStyle(parentName, inProjectStyleMap, inFrameworkStyleMap);

                    if (parentStyle != null) {
                        mStyleInheritanceMap.put(style, parentStyle);
                    }
                }
            }
        }
    }


    /**
     * Computes the name of the parent style, or <code>null</code> if the style is a root style.
     */
    private String getParentName(String styleName) {
        int index = styleName.lastIndexOf('.');
        if (index != -1) {
            return styleName.substring(0, index);
        }

        return null;
    }

    /**
     * Searches for and returns the {@link StyleResourceValue} from a given name.
     * <p/>The format of the name can be:
     * <ul>
     * <li>[android:]&lt;name&gt;</li>
     * <li>[android:]style/&lt;name&gt;</li>
     * <li>@[android:]style/&lt;name&gt;</li>
     * </ul>
     * @param parentName the name of the style.
     * @param inProjectStyleMap the project style map. Can be <code>null</code>
     * @param inFrameworkStyleMap the framework style map.
     * @return The matching {@link StyleResourceValue} object or <code>null</code> if not found.
     */
    private StyleResourceValue getStyle(String parentName,
            Map<String, ResourceValue> inProjectStyleMap,
            Map<String, ResourceValue> inFrameworkStyleMap) {
        boolean frameworkOnly = false;

        String name = parentName;

        // remove the useless @ if it's there
        if (name.startsWith(PREFIX_RESOURCE_REF)) {
            name = name.substring(PREFIX_RESOURCE_REF.length());
        }

        // check for framework identifier.
        if (name.startsWith(PREFIX_ANDROID)) {
            frameworkOnly = true;
            name = name.substring(PREFIX_ANDROID.length());
        }

        // at this point we could have the format <type>/<name>. we want only the name as long as
        // the type is style.
        if (name.startsWith(REFERENCE_STYLE)) {
            name = name.substring(REFERENCE_STYLE.length());
        } else if (name.indexOf('/') != -1) {
            return null;
        }

        ResourceValue parent = null;

        // if allowed, search in the project resources.
        if (frameworkOnly == false && inProjectStyleMap != null) {
            parent = inProjectStyleMap.get(name);
        }

        // if not found, then look in the framework resources.
        if (parent == null) {
            parent = inFrameworkStyleMap.get(name);
        }

        // make sure the result is the proper class type and return it.
        if (parent instanceof StyleResourceValue) {
            return (StyleResourceValue)parent;
        }

        if (mLogger != null) {
            mLogger.error(LayoutLog.TAG_RESOURCES_RESOLVE,
                    String.format("Unable to resolve parent style name: %s", parentName),
                    null /*data*/);
        }

        return null;
    }
}
