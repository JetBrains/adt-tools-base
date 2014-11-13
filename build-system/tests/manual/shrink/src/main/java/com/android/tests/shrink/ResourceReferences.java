package com.android.tests.shrink;

import android.content.Context;
import android.content.res.Resources;

public class ResourceReferences {
    public static void referenceResources(Context context) {
        Resources resources = context.getResources();
        int dynamicId1 = resources.getIdentifier("used3", "layout", context.getPackageName());
        System.out.println(dynamicId1);

        int dynamicId2 = resources.getIdentifier("com.android.tests.shrink:layout/used4", null, null);
        System.out.println(dynamicId2);

        int dynamicId3 = resources.getIdentifier("com.android.tests.shrink:" + getType() + "/" + getLayoutUrl(),
                null, null);
        System.out.println(dynamicId3);

        int dynamicId4 = resources.getIdentifier("com.android.tests.shrink:string/unused2", null, null);
        System.out.println(dynamicId4);

        // These literal strings really match everything, which is why the resource shrinker should
        // ignore it:
        System.out.println(String.format("%5d", 5));
        System.out.println(String.format("%x", 15));
    }

    public static String getType() {
        return "string";
    }

    public static String getLayoutUrl() {
        // Prevent inlining
        if (System.currentTimeMillis() % 2 == 0) {
            return "used5";
        } else {
            return "used6";
        }
    }
}
