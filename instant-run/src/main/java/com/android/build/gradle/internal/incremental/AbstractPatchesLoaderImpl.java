package com.android.build.gradle.internal.incremental;

import android.util.Log;

import java.lang.reflect.Field;

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
                Field changeField = originalClass.getDeclaredField("$change");
                // force the field accessibility as the class might not be "visible"
                // from this package.
                changeField.setAccessible(true);

                // If there was a previous change set, mark it as obsolete:
                Object previous = changeField.get(null);
                if (previous != null) {
                    Field isObsolete = previous.getClass().getDeclaredField("$obsolete");
                    if (isObsolete != null) {
                        isObsolete.set(null, true);
                    }
                }
                changeField.set(null, o);

                Log.i("fd", String.format("patched %s", className));
            }
        } catch (Exception e) {
            Log.e("fd", String.format("Exception while patching %s", "foo.bar"), e);
            return false;
        }
        return true;
    }
}
