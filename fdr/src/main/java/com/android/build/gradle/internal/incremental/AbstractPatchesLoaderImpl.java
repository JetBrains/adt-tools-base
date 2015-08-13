package com.android.build.gradle.internal.incremental;

import android.util.Log;

/**
 * Created by jedo on 8/5/15.
 */
public abstract class AbstractPatchesLoaderImpl implements PatchesLoader {

    public abstract String[] getPatchedClasses();

    @Override
    public boolean load() {
        try {
            for (String className : getPatchedClasses()) {
                ClassLoader cl = getClass().getClassLoader();
                Class<?> aClass = cl.loadClass(className + "$override");
                Object o = aClass.newInstance();
                Class<?> originalClass = cl.loadClass(className);
                originalClass.getDeclaredField("$change").set(null, o);
                System.out.println(String.format("patched %s", className));
                //Log.i("fd", String.format("patched %s", className));
            }
        } catch (Exception e) {
            //Log.e("fd", String.format("Exception while patching %s", "foo.bar"), e);
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
