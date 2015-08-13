package com.android.build.gradle.internal.incremental;

/**
 * Created by jedo on 8/5/15.
 */
public class PatchesLoaderDumper {

    public static void main(String[] args) {
        try {
            Class<?> aClass = Class.forName("com.android.build.gradle.internal.incremental.AppPatchesLoaderImpl");
            PatchesLoader patchesLoader = (PatchesLoader) aClass.newInstance();
            patchesLoader.load();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
