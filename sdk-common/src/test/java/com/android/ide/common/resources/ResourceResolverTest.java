package com.android.ide.common.resources;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;

import junit.framework.TestCase;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResourceResolverTest extends TestCase {
    public void test() throws Exception {
        TestResourceRepository frameworkRepository = TestResourceRepository.create(true,
                new Object[]{
                        "values/strings.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"ok\">Ok</string>\n"
                        + "</resources>\n",

                        "values/themes.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <style name=\"Theme\">\n"
                        + "        <item name=\"colorForeground\">@android:color/bright_foreground_dark</item>\n"
                        + "        <item name=\"colorBackground\">@android:color/background_dark</item>\n"
                        + "    </style>\n"
                        + "    <style name=\"Theme.Light\">\n"
                        + "        <item name=\"colorBackground\">@android:color/background_light</item>\n"
                        + "        <item name=\"colorForeground\">@color/bright_foreground_light</item>\n"
                        + "    </style>\n"
                        + "</resources>\n",

                        "values/colors.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <color name=\"background_dark\">#ff000000</color>\n"
                        + "    <color name=\"background_light\">#ffffffff</color>\n"
                        + "    <color name=\"bright_foreground_dark\">@android:color/background_light</color>\n"
                        + "    <color name=\"bright_foreground_light\">@android:color/background_dark</color>\n"
                        + "</resources>\n",
                });

        TestResourceRepository projectRepository = TestResourceRepository.create(false,
                new Object[]{
                        "layout/layout1.xml", "<!--contents doesn't matter-->",

                        "layout/layout2.xml", "<!--contents doesn't matter-->",

                        "layout-land/layout1.xml", "<!--contents doesn't matter-->",

                        "layout-land/onlyLand.xml", "<!--contents doesn't matter-->",

                        "drawable/graphic.9.png", new byte[0],

                        "values/styles.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <style name=\"MyTheme\" parent=\"android:Theme.Light\">\n"
                        + "        <item name=\"android:textColor\">#999999</item>\n"
                        + "    </style>\n"
                        + "    <style name=\"MyTheme.Dotted1\" parent=\"\">\n"
                        + "    </style>"
                        + "    <style name=\"MyTheme.Dotted2\">\n"
                        + "    </style>"
                        + "    <style name=\"RandomStyle\">\n"
                        + "    </style>"
                        + "    <style name=\"RandomStyle2\" parent=\"RandomStyle\">\n"
                        + "    </style>"
                        + "</resources>\n",

                        "values/strings.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <item type=\"id\" name=\"action_bar_refresh\" />\n"
                        + "    <item type=\"dimen\" name=\"dialog_min_width_major\">45%</item>\n"
                        + "    <string name=\"home_title\">Home Sample</string>\n"
                        + "    <string name=\"show_all_apps\">All</string>\n"
                        + "    <string name=\"menu_wallpaper\">Wallpaper</string>\n"
                        + "    <string name=\"menu_search\">Search</string>\n"
                        + "    <string name=\"menu_settings\">Settings</string>\n"
                        + "    <string name=\"dummy\" translatable=\"false\">Ignore Me</string>\n"
                        + "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n"
                        + "</resources>\n",

                        "values-es/strings.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"show_all_apps\">Todo</string>\n"
                        + "</resources>\n",
                });

        assertFalse(projectRepository.isFrameworkRepository());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, Map<String, ResourceValue>> projectResources =
                projectRepository.getConfiguredResources(config);
        Map<ResourceType, Map<String, ResourceValue>> frameworkResources =
                frameworkRepository.getConfiguredResources(config);
        assertNotNull(projectResources);
        ResourceResolver resolver = ResourceResolver.create(projectResources, frameworkResources,
                "MyTheme", true);
        assertNotNull(resolver);

        LayoutLog logger = new LayoutLog() {
            @Override
            public void warning(String tag, String message, Object data) {
                fail(message);
            }

            @Override
            public void fidelityWarning(String tag, String message, Throwable throwable,
                    Object data) {
                fail(message);
            }

            @Override
            public void error(String tag, String message, Object data) {
                fail(message);
            }

            @Override
            public void error(String tag, String message, Throwable throwable, Object data) {
                fail(message);
            }
        };
        resolver.setLogger(logger);

        assertEquals("MyTheme", resolver.getThemeName());
        assertTrue(resolver.isProjectTheme());

        // findResValue
        assertNotNull(resolver.findResValue("@string/show_all_apps", false));
        assertNotNull(resolver.findResValue("@android:string/ok", false));
        assertNotNull(resolver.findResValue("@android:string/ok", true));
        assertEquals("Todo", resolver.findResValue("@string/show_all_apps", false).getValue());
        assertEquals("Home Sample", resolver.findResValue("@string/home_title", false).getValue());
        assertEquals("45%", resolver.findResValue("@dimen/dialog_min_width_major",
                false).getValue());
        assertNotNull(resolver.findResValue("@android:color/bright_foreground_dark", true));
        assertEquals("@android:color/background_light",
                resolver.findResValue("@android:color/bright_foreground_dark", true).getValue());
        assertEquals("#ffffffff",
                resolver.findResValue("@android:color/background_light", true).getValue());

        // getTheme
        StyleResourceValue myTheme = resolver.getTheme("MyTheme", false);
        assertNotNull(myTheme);
        assertSame(resolver.findResValue("@style/MyTheme", false), myTheme);
        assertNull(resolver.getTheme("MyTheme", true));
        assertNull(resolver.getTheme("MyNonexistentTheme", true));
        StyleResourceValue themeLight = resolver.getTheme("Theme.Light", true);
        assertNotNull(themeLight);
        StyleResourceValue theme = resolver.getTheme("Theme", true);
        assertNotNull(theme);

        // themeIsParentOf
        assertTrue(resolver.themeIsParentOf(themeLight, myTheme));
        assertFalse(resolver.themeIsParentOf(myTheme, themeLight));
        assertTrue(resolver.themeIsParentOf(theme, themeLight));
        assertFalse(resolver.themeIsParentOf(themeLight, theme));
        assertTrue(resolver.themeIsParentOf(theme, myTheme));
        assertFalse(resolver.themeIsParentOf(myTheme, theme));
        StyleResourceValue dotted1 = resolver.getTheme("MyTheme.Dotted1", false);
        assertNotNull(dotted1);
        StyleResourceValue dotted2 = resolver.getTheme("MyTheme.Dotted2", false);
        assertNotNull(dotted2);
        assertTrue(resolver.themeIsParentOf(myTheme, dotted2));
        assertFalse(resolver.themeIsParentOf(myTheme, dotted1)); // because parent=""

        // isTheme
        assertFalse(resolver.isTheme(resolver.findResValue("@style/RandomStyle", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/RandomStyle2", false), null));
        assertTrue(resolver.isTheme(resolver.findResValue("@style/MyTheme.Dotted2", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/MyTheme.Dotted1", false),
                null));
        assertTrue(resolver.isTheme(resolver.findResValue("@style/MyTheme", false), null));
        assertTrue(resolver.isTheme(resolver.findResValue("@android:style/Theme.Light", false),
                null));
        assertTrue(resolver.isTheme(resolver.findResValue("@android:style/Theme", false), null));

        // findItemInStyle
        assertNotNull(resolver.findItemInStyle(myTheme, "colorForeground", true));
        assertEquals("@color/bright_foreground_light",
                resolver.findItemInStyle(myTheme, "colorForeground", true).getValue());
        assertNotNull(resolver.findItemInStyle(dotted2, "colorForeground", true));
        assertNull(resolver.findItemInStyle(dotted1, "colorForeground", true));

        // findItemInTheme
        assertNotNull(resolver.findItemInTheme("colorForeground", true));
        assertEquals("@color/bright_foreground_light",
                resolver.findItemInTheme("colorForeground", true).getValue());

        // getFrameworkResource
        assertNull(resolver.getFrameworkResource(ResourceType.STRING, "show_all_apps"));
        assertNotNull(resolver.getFrameworkResource(ResourceType.STRING, "ok"));
        assertEquals("Ok", resolver.getFrameworkResource(ResourceType.STRING, "ok").getValue());

        // getProjectResource
        assertNull(resolver.getProjectResource(ResourceType.STRING, "ok"));
        assertNotNull(resolver.getProjectResource(ResourceType.STRING, "show_all_apps"));
        assertEquals("Todo", resolver.getProjectResource(ResourceType.STRING,
                "show_all_apps").getValue());


        // resolveResValue
        //    android:color/bright_foreground_dark => @android:color/background_light => white
        assertEquals("Todo", resolver.resolveResValue(
                resolver.findResValue("@string/show_all_apps", false)).getValue());
        assertEquals("#ffffffff", resolver.resolveResValue(
                resolver.findResValue("@android:color/bright_foreground_dark", false)).getValue());

        // resolveValue
        assertEquals("#ffffffff",
                resolver.resolveValue(ResourceType.STRING, "bright_foreground_dark",
                        "@android:color/background_light", true).getValue());


        // Switch to MyTheme.Dotted1 (to make sure the parent="" inheritence works properly.)
        // To do that we need to create a new resource resolver.
        resolver = ResourceResolver.create(projectResources, frameworkResources,
                "MyTheme.Dotted1", true);
        resolver.setLogger(logger);
        assertNotNull(resolver);
        assertEquals("MyTheme.Dotted1", resolver.getThemeName());
        assertTrue(resolver.isProjectTheme());
        assertNull(resolver.findItemInTheme("colorForeground", true));

        resolver = ResourceResolver.create(projectResources, frameworkResources,
                "MyTheme.Dotted2", true);
        resolver.setLogger(logger);
        assertNotNull(resolver);
        assertEquals("MyTheme.Dotted2", resolver.getThemeName());
        assertTrue(resolver.isProjectTheme());
        assertNotNull(resolver.findItemInTheme("colorForeground", true));

        frameworkRepository.dispose();
        projectRepository.dispose();
    }

    public void testMissingMessage() throws Exception {
        TestResourceRepository projectRepository = TestResourceRepository.create(false,
                new Object[]{
                        "values/colors.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <color name=\"loop1\">@color/loop1</color>\n"
                        + "    <color name=\"loop2a\">@color/loop2b</color>\n"
                        + "    <color name=\"loop2b\">@color/loop2a</color>\n"
                        + "</resources>\n",

                });

        assertFalse(projectRepository.isFrameworkRepository());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, Map<String, ResourceValue>> projectResources =
                projectRepository.getConfiguredResources(config);
        assertNotNull(projectResources);
        ResourceResolver resolver = ResourceResolver.create(projectResources, projectResources,
                "MyTheme", true);
        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger = new LayoutLog() {
            @Override
            public void warning(String tag, String message, Object data) {
                if ("Couldn't resolve resource @android:string/show_all_apps".equals(message)) {
                    wasWarned.set(true);
                } else {
                    fail(message);
                }
            }
        };
        resolver.setLogger(logger);
        assertNull(resolver.findResValue("@string/show_all_apps", true));
        assertTrue(wasWarned.get());
        projectRepository.dispose();
    }

    public void testLoop() throws Exception {
        TestResourceRepository projectRepository = TestResourceRepository.create(false,
                new Object[]{
                        "values/colors.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <color name=\"loop1\">@color/loop1</color>\n"
                        + "    <color name=\"loop2a\">@color/loop2b</color>\n"
                        + "    <color name=\"loop2b\">@color/loop2a</color>\n"
                        + "</resources>\n",

                });

        assertFalse(projectRepository.isFrameworkRepository());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, Map<String, ResourceValue>> projectResources =
                projectRepository.getConfiguredResources(config);
        assertNotNull(projectResources);
        ResourceResolver resolver = ResourceResolver.create(projectResources, projectResources,
                "MyTheme", true);
        assertNotNull(resolver);

        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger = new LayoutLog() {
            @Override
            public void error(String tag, String message, Object data) {
                if (("Potential stack overflow trying to resolve "
                        + "'@color/loop1': cyclic resource definitions?"
                        + " Render may not be accurate.").equals(message)) {
                    wasWarned.set(true);
                } else if (("Potential stack overflow trying to resolve "
                        + "'@color/loop2b': cyclic resource definitions? "
                        + "Render may not be accurate.").equals(message)) {
                    wasWarned.set(true);
                } else {
                    fail(message);
                }
            }
        };
        resolver.setLogger(logger);

        assertNotNull(resolver.findResValue("@color/loop1", false));
        resolver.resolveResValue(resolver.findResValue("@color/loop1", false));
        assertTrue(wasWarned.get());

        wasWarned.set(false);
        assertNotNull(resolver.findResValue("@color/loop2a", false));
        resolver.resolveResValue(resolver.findResValue("@color/loop2a", false));
        assertTrue(wasWarned.get());

        projectRepository.dispose();
    }
}
