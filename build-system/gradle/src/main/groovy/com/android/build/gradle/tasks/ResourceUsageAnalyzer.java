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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_GIF;
import static com.android.SdkConstants.DOT_JPEG;
import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.utils.SdkUtils.endsWith;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.google.common.base.Charsets.UTF_8;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Class responsible for searching through a Gradle built tree (after resource merging,
 * compilation and ProGuarding has been completed, but before final .apk assembly), which
 * figures out which resources if any are unused, and removes them.
 * <p>
 * It does this by examining
 * <ul>
 *     <li>The merged manifest, to find root resource references (such as drawables
 *         used for activity icons)</li>
 *     <li>The merged R class (to find the actual integer constants assigned to resources)</li>
 *     <li>The ProGuard log files (to find the mapping from original symbol names to
 *         short names)</li>*
 *     <li>The merged resources (to find which resources reference other resources, e.g.
 *         drawable state lists including other drawables, or layouts including other
 *         layouts, or styles referencing other drawables, or menus items including action
 *         layouts, etc.)</li>
 *     <li>The ProGuard output classes (to find resource references in code that are
 *         actually reachable)</li>
 * </ul>
 * From all this, it builds up a reference graph, and based on the root references (e.g.
 * from the manifest and from the remaining code) it computes which resources are actually
 * reachable in the app, and anything that is not reachable is then marked for deletion.
 * <p>
 * A resource is referenced in code if either the field R.type.name is referenced (which
 * is the case for non-final resource references, e.g. in libraries), or if the corresponding
 * int value is referenced (for final resource values). We check this by looking at the
 * ProGuard output classes with an ASM visitor. One complication is that code can also
 * call {@code Resources#getIdentifier(String,String,String)} where they can pass in the names
 * of resources to look up. To handle this scenario, we use the ClassVisitor to see if
 * there are any calls to the specific {@code Resources#getIdentifier} method. If not,
 * great, the usage analysis is completely accurate. If we <b>do</b> find one, we check
 * <b>all</b> the string constants found anywhere in the app, and look to see if any look
 * relevant. For example, if we find the string "string/foo" or "my.pkg:string/foo", we
 * will then mark the string resource named foo (if any) as potentially used. Similarly,
 * if we find just "foo" or "/foo", we will mark <b>all</b> resources named "foo" as
 * potentially used. However, if the string is "bar/foo" or " foo " these strings are
 * ignored. This means we can potentially miss resources usages where the resource name
 * is completed computed (e.g. by concatenating individual characters or taking substrings
 * of strings that do not look like resource names), but that seems extremely unlikely
 * to be a real-world scenario.
 * <p>
 * For now, for reasons detailed in the code, this only applies to file-based resources
 * like layouts, menus and drawables, not value-based resources like strings and dimensions.
 */
public class ResourceUsageAnalyzer {
    /**
     Whether we support running aapt twice, to regenerate the resources.arsc file
     such that we can strip out value resources as well. We don't do this yet, for
     reasons detailed in the ShrinkResources task

     We have two options:
     (1) Copy the resource files over to a new destination directory, filtering out
     removed file resources and rewriting value resource files by stripping out
     the declarations for removed value resources. We then re-run aapt on this
     new destination directory.

     The problem with this approach is that when we re-run aapt it will assign new
     id's to all the resources, so we have to create dummy placeholders for all the
     removed resources. (The alternative would be to then run compilation one more
     time -- regenerating classes.jar, regenerating .dex) -- this would really slow
     down builds.)

     A cleaner solution than this is to get aapt to support using a predefined set
     of id's. It can emit R.txt symbol files now; if we can get it to read R.txt
     and use those numbers in its assignment, we can solve this cleanly. This request
     is tracked in https://code.google.com/p/android/issues/detail?id=70869

     (2) Just rewrite the .ap_ file directly. It's just a .zip file which contains
     (a) binary files for bitmaps and XML file resources such as layouts and menus
     (b) a binary file, resources.arsc, containing all the values.
     The resources.arsc format is opaque to us. However, MOST of the resource bulk
     comes from the bitmap and other resource files.

     So here we don't even need to run aapt a second time; we simply rewrite the
     .ap_ zip file directly, filtering out res/ files we know to be unused.

     Approach #2 gives us most of the space savings without the risk of #1 (running aapt
     a second time introduces the possibility of aapt compilation errors if we haven't
     been careful enough to insert resource aliases for all necessary items (such as
     inline @+id declarations), or if we haven't carefully not created aliases for items
     already defined in other value files as aliases, and perhaps most importantly,
     introduces risk that aapt will pick a different resource order anyway, which we can
     only guard against by doing a full compilation over again.

     Therefore, for now the below code uses #2, but since we can solve #1 with support
     from aapt), we're preserving all the code to rewrite resource files since that will
     give additional space savings, particularly for apps with a lot of strings or a lot
     of translations.
     */
    @SuppressWarnings("SpellCheckingInspection") // arsc
    public static final boolean TWO_PASS_AAPT = false;
    public static final int TYPICAL_RESOURCE_COUNT = 200;

    private final File mResourceClassDir;
    private final File mProguardMapping;
    private final File mClassesJar;
    private final File mMergedManifest;
    private final File mMergedResourceDir;

    private boolean mVerbose;
    private boolean mDebug;
    private boolean mDryRun;

    /** The computed set of unused resources */
    private List<Resource> mUnused;

    /** List of all known resources (parsed from R.java) */
    private List<Resource> mResources = Lists.newArrayListWithExpectedSize(TYPICAL_RESOURCE_COUNT);
    /** Map from R field value to corresponding resource */
    private Map<Integer, Resource> mValueToResource =
            Maps.newHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT);
    /** Map from resource type to map from resource name to resource object */
    private Map<ResourceType, Map<String, Resource>> mTypeToName =
            Maps.newEnumMap(ResourceType.class);
    /** Map from resource class owners (VM format class) to corresponding resource types.
     * This will typically be the fully qualified names of the R classes, as well as
     * any renamed versions of those discovered in the mapping.txt file from ProGuard */
    private Map<String, ResourceType> mResourceClassOwners = Maps.newHashMapWithExpectedSize(20);

    public ResourceUsageAnalyzer(
            @NonNull File rDir,
            @NonNull File classesJar,
            @NonNull File manifest,
            @Nullable File mapping,
            @NonNull File resources) {
        mResourceClassDir = rDir;
        mProguardMapping = mapping;
        mClassesJar = classesJar;
        mMergedManifest = manifest;
        mMergedResourceDir = resources;
    }

    public void analyze() throws IOException, ParserConfigurationException, SAXException {
        gatherResourceValues(mResourceClassDir);
        recordMapping(mProguardMapping);
        recordUsages(mClassesJar);
        recordManifestUsages(mMergedManifest);
        recordResources(mMergedResourceDir);
        keepPossiblyReferencedResources();
        dumpReferences();
        findUnused();
    }

    public boolean isDryRun() {
        return mDryRun;
    }

    public void setDryRun(boolean dryRun) {
        mDryRun = dryRun;
    }

    public boolean isVerbose() {
        return mVerbose;
    }

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }


    public boolean isDebug() {
        return mDebug;
    }

    public void setDebug(boolean verbose) {
        mDebug = verbose;
    }

    /**
     * "Removes" resources from an .ap_ file by writing it out while filtering out
     * unused resources. This won't touch the values XML data (resources.arsc) but
     * will remove the individual file-based resources, which is where most of
     * the data is anyway (usually in drawable bitmaps)
     *
     * @param source the .ap_ file created by aapt
     * @param dest a new .ap_ file with unused file-based resources removed
     */
    public void rewriteResourceZip(@NonNull File source, @NonNull File dest)
            throws IOException {
        if (dest.exists()) {
            boolean deleted = dest.delete();
            if (!deleted) {
                throw new IOException("Could not delete " + dest);
            }
        }

        JarInputStream zis = null;
        try {
            FileInputStream fis = new FileInputStream(source);
            try {
                FileOutputStream fos = new FileOutputStream(dest);
                zis = new JarInputStream(fis);
                JarOutputStream zos = new JarOutputStream(fos);
                try {
                    // The .ap_ file is also compressed
                    zos.setLevel(9);

                    ZipEntry entry = zis.getNextEntry();
                    while (entry != null) {
                        String name = entry.getName();
                        boolean directory = entry.isDirectory();
                        Resource resource = getResourceByJarPath(name);
                        if (resource == null || resource.reachable) {
                            JarEntry outEntry = new JarEntry(entry.getName());
                            if (entry.getTime() != -1L) {
                                outEntry.setTime(entry.getTime());
                            }
                            zos.putNextEntry(outEntry);

                            if (!directory) {
                                byte[] bytes = ByteStreams.toByteArray(zis);
                                if (bytes != null) {
                                    zos.write(bytes);
                                }
                            }

                            zos.closeEntry();
                        } else if (isVerbose()) {
                            System.out.println("Skipped unused resource " + name + ": "
                                    + entry.getSize() + " bytes");
                        }
                        entry = zis.getNextEntry();
                    }
                    zos.flush();
                } finally {
                    Closeables.close(zos, false);
                }
            } finally {
                Closeables.close(fis, true);
            }
        } finally {
            Closeables.close(zis, false);
        }
    }

    /**
     * Remove resources (already identified by {@link #analyze()}).
     *
     * This task will copy all remaining used resources over from the full resource
     * directory to a new reduced resource directory. However, it can't just
     * delete the resources, because it has no way to tell aapt to continue to use
     * the same id's for the resources. When we re-run aapt on the stripped resource
     * directory, it will assign new id's to some of the resources (to fill the gaps)
     * which means the resource id's no longer match the constants compiled into the
     * dex files, and as a result, the app crashes at runtime.
     * <p>
     * Therefore, it needs to preserve all id's by actually keeping all the resource
     * names. It can still save a lot of space by making these resources tiny; e.g.
     * all strings are set to empty, all styles, arrays and plurals are set to not contain
     * any children, and most importantly, all file based resources like bitmaps and
     * layouts are replaced by simple resource aliases which just point to @null.
     *
     * @param destination directory to copy resources into; if null, delete resources in place
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public void removeUnused(@Nullable File destination) throws IOException,
            ParserConfigurationException, SAXException {
        if (TWO_PASS_AAPT) {
            assert mUnused != null; // should always call analyze() first

            int resourceCount = mUnused.size()
                    * 4; // *4: account for some resource folder repetition
            boolean inPlace = destination == null;
            Set<File> skip = inPlace ? null : Sets.<File>newHashSetWithExpectedSize(resourceCount);
            Set<File> rewrite = Sets.newHashSetWithExpectedSize(resourceCount);
            for (Resource resource : mUnused) {
                if (resource.declarations != null) {
                    for (File file : resource.declarations) {
                        String folder = file.getParentFile().getName();
                        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
                        if (folderType != null && folderType != ResourceFolderType.VALUES) {
                            if (isVerbose()) {
                                System.out.println("Deleted unused resource " + file);
                            }
                            if (inPlace) {
                                if (!isDryRun()) {
                                    boolean delete = file.delete();
                                    if (!delete) {
                                        System.err.println("Could not delete " + file);
                                    }
                                }
                            } else {
                                assert skip != null;
                                skip.add(file);
                            }
                        } else {
                            // Can't delete values immediately; there can be many resources
                            // in this file, so we have to process them all
                            rewrite.add(file);
                        }
                    }
                }
            }

            // Special case the base values.xml folder
            File values = new File(mMergedResourceDir,
                    FD_RES_VALUES + File.separatorChar + "values.xml");
            boolean valuesExists = values.exists();
            if (valuesExists) {
                rewrite.add(values);
            }

            Map<File, String> rewritten = Maps.newHashMapWithExpectedSize(rewrite.size());

            // Delete value resources: Must rewrite the XML files
            for (File file : rewrite) {
                String xml = Files.toString(file, UTF_8);
                Document document = XmlUtils.parseDocument(xml, true);
                Element root = document.getDocumentElement();
                if (root != null && TAG_RESOURCES.equals(root.getTagName())) {
                    List<String> removed = Lists.newArrayList();
                    stripUnused(root, removed);
                    if (isVerbose()) {
                        System.out.println("Removed " + removed.size() +
                                " unused resources from " + file + ":\n  " +
                                Joiner.on(", ").join(removed));
                    }

                    String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
                    rewritten.put(file, formatted);
                }
            }

            if (isDryRun()) {
                return;
            }

            if (valuesExists) {
                String xml = rewritten.get(values);
                if (xml == null) {
                    xml = Files.toString(values, UTF_8);
                }
                Document document = XmlUtils.parseDocument(xml, true);
                Element root = document.getDocumentElement();

                for (Resource resource : mResources) {
                    if (resource.type == ResourceType.ID && !resource.hasDefault) {
                        Element item = document.createElement(TAG_ITEM);
                        item.setAttribute(ATTR_TYPE, resource.type.getName());
                        item.setAttribute(ATTR_NAME, resource.name);
                        root.appendChild(item);
                    } else if (!resource.reachable
                            && !resource.hasDefault
                            && resource.type != ResourceType.DECLARE_STYLEABLE
                            && resource.type != ResourceType.STYLE
                            && resource.type != ResourceType.PLURALS
                            && resource.type != ResourceType.ARRAY
                            && resource.isRelevantType()) {
                        Element item = document.createElement(TAG_ITEM);
                        item.setAttribute(ATTR_TYPE, resource.type.getName());
                        item.setAttribute(ATTR_NAME, resource.name);
                        root.appendChild(item);
                        String s = "@null";
                        item.appendChild(document.createTextNode(s));
                    }
                }

                String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
                rewritten.put(values, formatted);
            }

            if (inPlace) {
                for (Map.Entry<File, String> entry : rewritten.entrySet()) {
                    File file = entry.getKey();
                    String formatted = entry.getValue();
                    Files.write(formatted, file, UTF_8);
                }
            } else {
                filteredCopy(mMergedResourceDir, destination, skip, rewritten);
            }
        } else {
            assert false;
        }
    }

    /**
     * Copies one resource directory tree into another; skipping some files, replacing
     * the contents of some, and passing everything else through unmodified
     */
    private static void filteredCopy(File source, File destination, Set<File> skip,
            Map<File, String> replace) throws IOException {
        if (TWO_PASS_AAPT) {
            if (source.isDirectory()) {
                File[] children = source.listFiles();
                if (children != null) {
                    if (!destination.exists()) {
                        boolean success = destination.mkdirs();
                        if (!success) {
                            throw new IOException("Could not create " + destination);
                        }
                    }
                    for (File child : children) {
                        filteredCopy(child, new File(destination, child.getName()), skip, replace);
                    }
                }
            } else if (!skip.contains(source) && source.isFile()) {
                String contents = replace.get(source);
                if (contents != null) {
                    Files.write(contents, destination, Charsets.UTF_8);
                } else {
                    Files.copy(source, destination);
                }
            }
        } else {
            assert false;
        }
    }

    private void stripUnused(Element element, List<String> removed) {
        if (TWO_PASS_AAPT) {
            ResourceType type = getResourceType(element);
            if (type == ResourceType.ATTR) {
                // Not yet properly handled
                return;
            }

            Resource resource = getResource(element);
            if (resource != null) {
                if (resource.type == ResourceType.DECLARE_STYLEABLE ||
                        resource.type == ResourceType.ATTR) {
                    // Don't strip children of declare-styleable; we're not correctly
                    // tracking field references of the R_styleable_attr fields yet
                    return;
                }

                if (!resource.reachable &&
                        (resource.type == ResourceType.STYLE ||
                                resource.type == ResourceType.PLURALS ||
                                resource.type == ResourceType.ARRAY)) {
                    NodeList children = element.getChildNodes();
                    for (int i = children.getLength() - 1; i >= 0; i--) {
                        Node child = children.item(i);
                        element.removeChild(child);
                    }
                    return;
                }
            }

            NodeList children = element.getChildNodes();
            for (int i = children.getLength() - 1; i >= 0; i--) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    stripUnused((Element) child, removed);
                }
            }

            if (resource != null && !resource.reachable) {
                if (mVerbose) {
                    removed.add(resource.getUrl());
                }
                // for themes etc where .'s have been replaced by _'s
                String name = element.getAttribute(ATTR_NAME);
                if (name.isEmpty()) {
                    name = resource.name;
                }
                Node nextSibling = element.getNextSibling();
                Node parent = element.getParentNode();
                NodeList oldChildren = element.getChildNodes();
                parent.removeChild(element);
                Document document = element.getOwnerDocument();
                element = document.createElement("item");
                for (int i = 0; i < oldChildren.getLength(); i++) {
                    element.appendChild(oldChildren.item(i));
                }

                element.setAttribute(ATTR_NAME, name);
                element.setAttribute(ATTR_TYPE, resource.type.getName());
                String text = null;
                switch (resource.type) {
                    case BOOL:
                        text = "true";
                        break;
                    case DIMEN:
                        text = "0dp";
                        break;
                    case INTEGER:
                        text = "0";
                        break;
                }
                element.setTextContent(text);
                parent.insertBefore(element, nextSibling);
            }
        } else {
            assert false;
        }
    }

    private static String getFieldName(Element element) {
        return getFieldName(element.getAttribute(ATTR_NAME));
    }

    @Nullable
    private Resource getResource(Element element) {
        ResourceType type = getResourceType(element);
        if (type != null) {
            String name = getFieldName(element);
            return getResource(type, name);
        }

        return null;
    }

    @Nullable
    private Resource getResourceByJarPath(String path) {
        // Jars use forward slash paths, not File.separator
        if (path.startsWith("res/")) {
            int folderStart = 4; // "res/".length
            int folderEnd = path.indexOf('/', folderStart);
            if (folderEnd != -1) {
                String folderName = path.substring(folderStart, folderEnd);
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
                if (folderType != null) {
                    int nameStart = folderEnd + 1;
                    int nameEnd = path.indexOf('.', nameStart);
                    if (nameEnd != -1) {
                        String name = path.substring(nameStart, nameEnd);
                        List<ResourceType> types =
                                FolderTypeRelationship.getRelatedResourceTypes(folderType);
                        for (ResourceType type : types) {
                            if (type != ResourceType.ID) {
                                Resource resource = getResource(type, name);
                                if (resource != null) {
                                    return resource;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static ResourceType getResourceType(Element element) {
        String tagName = element.getTagName();
        if (tagName.equals(TAG_ITEM)) {
            String typeName = element.getAttribute(ATTR_TYPE);
            if (!typeName.isEmpty()) {
                return ResourceType.getEnum(typeName);
            }
        } else if ("string-array".equals(tagName) || "integer-array".equals(tagName)) {
            return ResourceType.ARRAY;
        } else {
            return ResourceType.getEnum(tagName);
        }
        return null;
    }

    private void findUnused() {
        List<Resource> roots = Lists.newArrayList();

        for (Resource resource : mResources) {
            if (resource.reachable && resource.type != ResourceType.ID
                    && resource.type != ResourceType.ATTR) {
                roots.add(resource);
            }
        }

        if (mDebug) {
            System.out.println("The root reachable resources are: " +
                    Joiner.on(",\n   ").join(roots));
        }

        Map<Resource,Boolean> seen = new IdentityHashMap<Resource,Boolean>(mResources.size());
        for (Resource root : roots) {
            visit(root, seen);
        }

        List<Resource> unused = Lists.newArrayListWithExpectedSize(mResources.size());
        for (Resource resource : mResources) {
            if (!resource.reachable && resource.isRelevantType()) {
                unused.add(resource);
            }
        }

        mUnused = unused;
    }

    private static void visit(Resource root, Map<Resource, Boolean> seen) {
        if (seen.containsKey(root)) {
            return;
        }
        seen.put(root, Boolean.TRUE);
        root.reachable = true;
        if (root.references != null) {
            for (Resource referenced : root.references) {
                visit(referenced, seen);
            }
        }
    }

    private void dumpReferences() {
        if (mDebug) {
            for (Resource resource : mResources) {
                if (resource.references != null) {
                    System.out.println(resource + " => " + resource.references);
                }
            }
        }
    }

    private void keepPossiblyReferencedResources() {
        if (!mFoundGetIdentifier || mStrings == null) {
            // No calls to android.content.res.Resources#getIdentifier; no need
            // to worry about string references to resources
            return;
        }

        if (mDebug) {
            List<String> strings = new ArrayList<String>(mStrings);
            Collections.sort(strings);
            System.out.println("android.content.res.Resources#getIdentifier present: "
                    + mFoundGetIdentifier);
            System.out.println("Referenced Strings:");
            for (String s : strings) {
                s = s.trim().replace("\n", "\\n");
                if (s.length() > 40) {
                    s = s.substring(0, 37) + "...";
                } else if (s.isEmpty()) {
                    continue;
                }
                System.out.println("  " + s);
            }
        }

        Set<String> names = Sets.newHashSetWithExpectedSize(50);
        for (Map<String, Resource> map : mTypeToName.values()) {
            names.addAll(map.keySet());
        }

        for (String string : mStrings) {
            // Check whether the string looks relevant
            // We consider three types of strings:
            //  (1) simple resource names, e.g. "foo" from @layout/foo
            //      These might be the parameter to a getIdentifier() call, or could
            //      be composed into a fully qualified resource name for the getIdentifier()
            //      method. We match these for *all* resource types.
            //  (2) Relative source names, e.g. layout/foo, from @layout/foo
            //      These might be composed into a fully qualified resource name for
            //      getIdentifier().
            //  (3) Fully qualified resource names of the form package:type/name.
            int n = string.length();
            boolean justName = true;
            boolean haveSlash = false;
            for (int i = 0; i < n; i++) {
                char c = string.charAt(i);
                if (c == '/') {
                    haveSlash = true;
                    justName = false;
                } else if (c == '.' || c == ':') {
                    justName = false;
                } else if (!Character.isJavaIdentifierPart(c)) {
                    // This shouldn't happen; we've filtered out these strings in
                    // the {@link #referencedString} method
                    assert false : string;
                    break;
                }
            }

            String name;
            if (justName) {
                // Check name (below)
                name = string;
            } else if (!haveSlash) {
                // If we have more than just a symbol name, we expect to also see a slash
                //noinspection UnnecessaryContinue
                continue;
            } else {
                // Try to pick out the resource name pieces; if we can find the
                // resource type unambiguously; if not, just match on names
                int slash = string.indexOf('/');
                assert slash != -1; // checked with haveSlash above
                name = string.substring(slash + 1);
                if (name.isEmpty() || !names.contains(name)) {
                    continue;
                }
                // See if have a known specific resource type
                if (slash > 0) {
                    int colon = string.indexOf(':');
                    String typeName = string.substring(colon != -1 ? colon + 1 : 0, slash);
                    ResourceType type = ResourceType.getEnum(typeName);
                    if (type == null) {
                        continue;
                    }
                    Resource resource = getResource(type, name);
                    if (mDebug && resource != null) {
                        System.out.println("Marking " + resource + " used because it "
                                + "matches string pool constant " + string);
                    }
                    markReachable(resource);
                    continue;
                }

                // fall through and check the name
            }

            if (names.contains(name)) {
                for (Map<String, Resource> map : mTypeToName.values()) {
                    Resource resource = map.get(string);
                    if (mDebug && resource != null) {
                        System.out.println("Marking " + resource + " used because it "
                                + "matches string pool constant " + string);
                    }
                    markReachable(resource);
                }
            } else if (Character.isDigit(name.charAt(0))) {
                // Just a number? There are cases where it calls getIdentifier by
                // a String number; see for example SuggestionsAdapter in the support
                // library which reports supporting a string like "2130837524" and
                // "android.resource://com.android.alarmclock/2130837524".
                try {
                    int id = Integer.parseInt(name);
                    if (id != 0) {
                        markReachable(mValueToResource.get(id));
                    }
                } catch (NumberFormatException e) {
                    // pass
                }
            }
        }
    }

    private void recordResources(File resDir)
            throws IOException, SAXException, ParserConfigurationException {
        File[] resourceFolders = resDir.listFiles();
        if (resourceFolders != null) {
            for (File folder : resourceFolders) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
                if (folderType != null) {
                    recordResources(folderType, folder);
                }
            }
        }
    }

    private void recordResources(@NonNull ResourceFolderType folderType, File folder)
            throws ParserConfigurationException, SAXException, IOException {
        File[] files = folder.listFiles();
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folder.getName());
        boolean isDefaultFolder = false;
        if (config != null) {
            isDefaultFolder = true;
            for (int i = 0, n = FolderConfiguration.getQualifierCount(); i < n; i++) {
                ResourceQualifier qualifier = config.getQualifier(i);
                // Densities are special: even if they're present in just (say) drawable-hdpi
                // we'll match it on any other density
                if (qualifier != null && !(qualifier instanceof DensityQualifier)) {
                    isDefaultFolder = false;
                    break;
                }
            }
        }

        if (files != null) {
            for (File file : files) {
                String path = file.getPath();
                boolean isXml = endsWithIgnoreCase(path, DOT_XML);

                Resource from = null;
                // Record resource for the whole file
                if (folderType != ResourceFolderType.VALUES
                        && (isXml
                            || endsWith(path, DOT_PNG) //also true for endsWith(name, DOT_9PNG)
                            || endsWith(path, DOT_JPG)
                            || endsWith(path, DOT_GIF)
                            || endsWith(path, DOT_JPEG))) {
                    List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(
                            folderType);
                    ResourceType type = types.get(0);
                    assert type != ResourceType.ID : folderType;
                    String name = file.getName();
                    name = name.substring(0, name.indexOf('.'));
                    Resource resource = getResource(type, name);
                    if (resource != null) {
                        resource.addLocation(file);
                        if (isDefaultFolder) {
                            resource.hasDefault = true;
                        }
                        from = resource;
                    }
                }

                if (isXml) {
                    // For value files, and drawables and colors etc also pull in resource
                    // references inside the file
                    recordResourcesUsages(file, isDefaultFolder, from);
                }
            }
        }
    }

    private void recordMapping(@Nullable File mapping) throws IOException {
        if (mapping == null || !mapping.exists()) {
            return;
        }
        final String ARROW = " -> ";
        final String RESOURCE = ".R$";
        for (String line : Files.readLines(mapping, UTF_8)) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            int index = line.indexOf(RESOURCE);
            if (index == -1) {
                continue;
            }
            int arrow = line.indexOf(ARROW, index + 3);
            if (arrow == -1) {
                continue;
            }
            String typeName = line.substring(index + RESOURCE.length(), arrow);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type == null) {
                continue;
            }
            int end = line.indexOf(':', arrow + ARROW.length());
            if (end == -1) {
                end = line.length();
            }
            String target = line.substring(arrow + ARROW.length(), end).trim();
            String ownerName = target.replace('.', '/');
            mResourceClassOwners.put(ownerName, type);
        }
    }

    private void recordManifestUsages(File manifest)
            throws IOException, ParserConfigurationException, SAXException {
        String xml = Files.toString(manifest, UTF_8);
        Document document = XmlUtils.parseDocument(xml, true);
        recordManifestUsages(document.getDocumentElement());
    }

    private void recordResourcesUsages(@NonNull File file, boolean isDefaultFolder,
            @Nullable Resource from)
            throws IOException, ParserConfigurationException, SAXException {
        String xml = Files.toString(file, UTF_8);
        Document document = XmlUtils.parseDocument(xml, true);
        recordResourceReferences(file, isDefaultFolder, document.getDocumentElement(), from);
    }

    @Nullable
    private Resource getResource(@NonNull ResourceType type, @NonNull String name) {
        Map<String, Resource> nameMap = mTypeToName.get(type);
        if (nameMap != null) {
            return nameMap.get(getFieldName(name));
        }
        return null;
    }

    @Nullable
    private Resource getResource(@NonNull String possibleUrlReference) {
        ResourceUrl url = ResourceUrl.parse(possibleUrlReference);
        if (url != null && !url.framework) {
            return getResource(url.type, url.name);
        }

        return null;
    }

    private void recordManifestUsages(Node node) {
        short nodeType = node.getNodeType();
        if (nodeType == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attr = (Attr) attributes.item(i);
                markReachable(getResource(attr.getValue()));
            }
        } else if (nodeType == Node.TEXT_NODE) {
            // Does this apply to any manifests??
            String text = node.getNodeValue().trim();
            markReachable(getResource(text));
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            recordManifestUsages(child);
        }
    }


    private void recordResourceReferences(@NonNull File file, boolean isDefaultFolder,
            @NonNull Node node, @Nullable Resource from) {
        short nodeType = node.getNodeType();
        if (nodeType == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            if (from != null) {
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attr = (Attr) attributes.item(i);
                    Resource resource = getResource(attr.getValue());
                    if (resource != null) {
                        from.addReference(resource);
                    }
                }

                // Android Wear. We *could* limit ourselves to only doing this in files
                // referenced from a manifest meta-data element, e.g.
                // <meta-data android:name="com.google.android.wearable.beta.app"
                //    android:resource="@xml/wearable_app_desc"/>
                // but given that that property has "beta" in the name, it seems likely
                // to change and therefore hardcoding it for that key risks breakage
                // in the future.
                if ("rawPathResId".equals(element.getTagName())) {
                    StringBuilder sb = new StringBuilder();
                    NodeList children = node.getChildNodes();
                    for (int i = 0, n = children.getLength(); i < n; i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Element.TEXT_NODE
                                || child.getNodeType() == Element.CDATA_SECTION_NODE) {
                            sb.append(child.getNodeValue());
                        }
                    }
                    if (sb.length() > 0) {
                        Resource resource = getResource(ResourceType.RAW, sb.toString().trim());
                        from.addReference(resource);
                    }
                }
            }

            Resource definition = getResource(element);
            if (definition != null) {
                from = definition;
                definition.addLocation(file);
                if (isDefaultFolder) {
                    definition.hasDefault = true;
                }
            }

            String tagName = element.getTagName();
            if (TAG_STYLE.equals(tagName)) {
                if (element.hasAttribute(ATTR_PARENT)) {
                    String parent = element.getAttribute(ATTR_PARENT);
                    if (!parent.isEmpty() && !parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) &&
                            !parent.startsWith(PREFIX_ANDROID)) {
                        String parentStyle = parent;
                        if (!parentStyle.startsWith(STYLE_RESOURCE_PREFIX)) {
                            parentStyle = STYLE_RESOURCE_PREFIX + parentStyle;
                        }
                        Resource ps = getResource(getFieldName(parentStyle));
                        if (ps != null && definition != null) {
                            definition.addReference(ps);
                        }
                    }
                } else {
                    // Implicit parent styles by name
                    String name = getFieldName(element);
                    while (true) {
                        int index = name.lastIndexOf('_');
                        if (index != -1) {
                            name = name.substring(0, index);
                            Resource ps = getResource(STYLE_RESOURCE_PREFIX + getFieldName(name));
                            if (ps != null && definition != null) {
                                definition.addReference(ps);
                            }
                        } else {
                            break;
                        }
                    }
                }
            }

            if (TAG_ITEM.equals(tagName)) {
                // In style? If so the name: attribute can be a reference
                if (element.getParentNode() != null
                        && element.getParentNode().getNodeName().equals(TAG_STYLE)) {
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (!name.isEmpty() && !name.startsWith("android:")) {
                        Resource resource = getResource(ResourceType.ATTR, name);
                        if (definition == null) {
                            Element style = (Element) element.getParentNode();
                            definition = getResource(style);
                            if (definition != null) {
                                from = definition;
                                definition.addReference(resource);
                            }
                        }
                    }
                }
            }
        } else if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue().trim();
            Resource textResource = getResource(getFieldName(text));
            if (textResource != null && from != null) {
                from.addReference(textResource);
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            recordResourceReferences(file, isDefaultFolder, child, from);
        }
    }

    public static String getFieldName(@NonNull String styleName) {
        return styleName.replace('.', '_').replace('-', '_').replace(':', '_');
    }

    private static void markReachable(@Nullable Resource resource) {
        if (resource != null) {
            resource.reachable = true;
        }
    }

    private Set<String> mStrings;
    private boolean mFoundGetIdentifier;

    private void referencedString(@NonNull String string) {
        // See if the string is at all eligible; ignore strings that aren't
        // identifiers (has java identifier chars and nothing but .:/), or are empty or too long
        if (string.isEmpty() || string.length() > 80) {
            return;
        }
        boolean haveIdentifierChar = false;
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            boolean identifierChar = Character.isJavaIdentifierPart(c);
            if (!identifierChar && c != '.' && c != ':' && c != '/') {
                // .:/ are for the fully qualified resuorce names
                return;
            } else if (identifierChar) {
                haveIdentifierChar = true;
            }
        }
        if (!haveIdentifierChar) {
            return;
        }

        if (mStrings == null) {
            mStrings = Sets.newHashSetWithExpectedSize(300);
        }
        mStrings.add(string);
    }

    private void recordUsages(File jarFile) throws IOException {
        if (!jarFile.exists()) {
            return;
        }
        ZipInputStream zis = null;
        try {
            FileInputStream fis = new FileInputStream(jarFile);
            try {
                zis = new ZipInputStream(fis);
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    if (name.endsWith(DOT_CLASS)) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            ClassReader classReader = new ClassReader(bytes);
                            classReader.accept(new UsageVisitor(), SKIP_DEBUG | SKIP_FRAMES);
                        }
                    }

                    entry = zis.getNextEntry();
                }
            } finally {
                Closeables.close(fis, true);
            }
        } finally {
            Closeables.close(zis, true);
        }
    }

    private void gatherResourceValues(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    gatherResourceValues(child);
                }
            }
        } else if (file.isFile() && file.getName().equals(SdkConstants.FN_RESOURCE_CLASS)) {
            parseResourceClass(file);
        }
    }

    // TODO: Use Lombok/ECJ here
    private void parseResourceClass(File file) throws IOException {
        String s = Files.toString(file, UTF_8);
        // Simple parser which handles only aapt's special R output
        String pkg = null;
        int index = s.indexOf("package ");
        if (index != -1) {
            int end = s.indexOf(';', index);
            pkg = s.substring(index + "package ".length(), end).trim().replace('.', '/');
        }
        index = 0;
        int length = s.length();
        String classDeclaration = "public static final class ";
        while (true) {
            index = s.indexOf(classDeclaration, index);
            if (index == -1) {
                break;
            }
            int start = index + classDeclaration.length();
            int end = s.indexOf(' ', start);
            if (end == -1) {
                break;
            }
            String typeName = s.substring(start, end);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type == null) {
                break;
            }

            if (pkg != null) {
                mResourceClassOwners.put(pkg + "/R$" + type.getName(), type);
            }

            index = end;

            // Find next declaration
            for (; index < length - 1; index++) {
                char c = s.charAt(index);
                if (Character.isWhitespace(c)) {
                    //noinspection UnnecessaryContinue
                    continue;
                } else if (c == '/') {
                    char next = s.charAt(index + 1);
                    if (next == '*') {
                        // Scan forward to comment end
                        end = index + 2;
                        while (end < length -2) {
                            c = s.charAt(end);
                            if (c == '*' && s.charAt(end + 1) == '/') {
                                end++;
                                break;
                            } else {
                                end++;
                            }
                        }
                        index = end;
                    } else if (next == '/') {
                        // Scan forward to next newline
                        assert false : s.substring(index - 1, index + 50); // we don't put line comments in R files
                    } else {
                        assert false : s.substring(index - 1, index + 50); // unexpected division
                    }
                } else if (c == 'p' && s.startsWith("public ", index)) {
                    if (type == ResourceType.STYLEABLE) {
                        start = s.indexOf(" int", index);
                        if (s.startsWith(" int[] ", start)) {
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String styleable = s.substring(start, end).trim();
                            addResource(ResourceType.DECLARE_STYLEABLE, styleable, null);

                            // TODO: Read in all the action bar ints!
                            // For now, we're simply treating all R.attr fields as used
                        } else if (s.startsWith(" int ")) {
                            // Read these fields in and correlate with the attr R's. Actually
                            // we don't need this for anything; the local attributes are
                            // found by the R attr thing. I just need to record the class
                            // (style).
                            // public static final int ActionBar_background = 10;
                            // ignore - jump to end
                            index = s.indexOf(';', index);
                            if (index == -1) {
                                break;
                            }
                            // For now, we're simply treating all R.attr fields as used
                        }
                    } else {
                        start = s.indexOf(" int ", index);
                        if (start != -1) {
                            start += " int ".length();
                            // e.g. abc_fade_in=0x7f040000;
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String name = s.substring(start, end).trim();
                            start = end + 1;
                            end = s.indexOf(';', start);
                            assert end != -1;
                            String value = s.substring(start, end).trim();
                            addResource(type, name, value);
                        }
                    }
                } else if (c == '}') {
                    // Done with resource class
                    break;
                }
            }
        }
    }

    private void addResource(@NonNull ResourceType type, @NonNull String name,
            @Nullable String value) {
        int realValue = value != null ? Integer.decode(value) : -1;
        Resource resource = getResource(type, name);
        if (resource != null) {
            //noinspection VariableNotUsedInsideIf
            if (value != null) {
                if (resource.value == -1) {
                    resource.value = realValue;
                } else {
                    assert realValue == resource.value;
                }
            }
            return;
        }

        resource = new Resource(type, name, realValue);
        mResources.add(resource);
        if (realValue != -1) {
            mValueToResource.put(realValue, resource);
        }
        Map<String, Resource> nameMap = mTypeToName.get(type);
        if (nameMap == null) {
            nameMap = Maps.newHashMapWithExpectedSize(30);
            mTypeToName.put(type, nameMap);
        }
        nameMap.put(name, resource);

        // TODO: Assert that we don't set the same resource multiple times to different values.
        // Could happen if you pass in stale data!
    }

    public int getUnusedResourceCount() {
        return mUnused.size();
    }

    @VisibleForTesting
    List<Resource> getAllResources() {
        return mResources;
    }

    public static class Resource {
        /** Type of resource */
        public ResourceType type;
        /** Name of resource */
        public String name;
        /** Integer id location */
        public int value;
        /** Whether this resource can be reached from one of the roots (manifest, code) */
        public boolean reachable;
        /** Whether this resource has a default definition (e.g. present in a resource folder
         * with no qualifiers). For id references, an inline definition (@+id) does not count as
         * a default definition.*/
        public boolean hasDefault;
        /** Resources this resource references. For example, a layout can reference another via
         * an include; a style reference in a layout references that layout style, and so on. */
        public List<Resource> references;
        public final List<File> declarations = Lists.newArrayList();

        private Resource(ResourceType type, String name, int value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + ":" + name + ":" + value;
        }

        @SuppressWarnings("RedundantIfStatement") // Generated by IDE
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Resource resource = (Resource) o;

            if (name != null ? !name.equals(resource.name) : resource.name != null) {
                return false;
            }
            if (type != resource.type) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        public void addLocation(@NonNull File file) {
            declarations.add(file);
        }

        public void addReference(@Nullable Resource resource) {
            if (resource != null) {
                if (references == null) {
                    references = Lists.newArrayList();
                } else if (references.contains(resource)) {
                    return;
                }
                references.add(resource);
            }
        }

        public String getUrl() {
            return '@' + type.getName() + '/' + name;
        }

        public boolean isRelevantType() {
            return type != ResourceType.ID; // && getFolderType() != ResourceFolderType.VALUES;
        }
    }

    /**
     * Class visitor responsible for looking for resource references in code.
     * It looks for R.type.name references (as well as inlined constants for these,
     * in the case of non-library code), as well as looking both for Resources#getIdentifier
     * calls and recording string literals, used to handle dynamic lookup of resources.
     */
    private class UsageVisitor extends ClassVisitor {
        public UsageVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name,
                String desc, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM4) {
                @Override
                public void visitLdcInsn(Object cst) {
                    handleCodeConstant(cst);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    if (opcode == Opcodes.GETSTATIC) {
                        ResourceType type = mResourceClassOwners.get(owner);
                        if (type != null) {
                            Resource resource = getResource(type, name);
                            if (resource != null) {
                                markReachable(resource);
                            }
                        }
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                    super.visitMethodInsn(opcode, owner, name, desc);
                    if (owner.equals("android/content/res/Resources")
                            && name.equals("getIdentifier")
                            && desc.equals(
                            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I")) {
                        mFoundGetIdentifier = true;
                        // TODO: Check previous instruction and see if we can find a literal
                        // String; if so, we can more accurately dispatch the resource here
                        // rather than having to check the whole string pool!
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return new AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String desc,
                        boolean visible) {
                    return new AnnotationUsageVisitor();
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return new AnnotationUsageVisitor();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature,
                Object value) {
            handleCodeConstant(value);
            return new FieldVisitor(Opcodes.ASM4) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new AnnotationUsageVisitor();
                }
            };
        }
    }

    private class AnnotationUsageVisitor extends AnnotationVisitor {
        public AnnotationUsageVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            return new AnnotationUsageVisitor();
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationUsageVisitor();
        }

        @Override
        public void visit(String name, Object value) {
            handleCodeConstant(value);
            super.visit(name, value);
        }
    }

    /** Invoked when an ASM visitor encounters a constant: record corresponding reference */
    private void handleCodeConstant(@Nullable Object cst) {
        if (cst instanceof Integer) {
            Integer value = (Integer) cst;
            markReachable(mValueToResource.get(value));
        } else if (cst instanceof int[]) {
            int[] values = (int[]) cst;
            for (int value : values) {
                markReachable(mValueToResource.get(value));
            }
        } else if (cst instanceof String) {
            String string = (String) cst;
            referencedString(string);
        }
    }

    @VisibleForTesting
    String dumpResourceModel() {
        StringBuilder sb = new StringBuilder(1000);
        Collections.sort(mResources, new Comparator<Resource>() {
            @Override
            public int compare(Resource resource1,
                    Resource resource2) {
                int delta = resource1.type.compareTo(resource2.type);
                if (delta != 0) {
                    return delta;
                }
                return resource1.name.compareTo(resource2.name);
            }
        });

        for (Resource resource : mResources) {
            sb.append(resource.getUrl()).append(" : reachable=").append(resource.reachable);
            sb.append("\n");
            if (resource.references != null) {
                for (Resource referenced : resource.references) {
                    sb.append("    ");
                    sb.append(referenced.getUrl());
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }
}
