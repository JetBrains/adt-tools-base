package com.android.test.jarjar;

import com.android.build.api.transform.Transform;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.lang.reflect.Method;

public class JarJarPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project target) {
        Object androidExtension = target.getExtensions().findByName("android");
        try {
            Method registerTransform = androidExtension.getClass()
                    .getMethod("registerTransform", Transform.class, Object[].class);
            registerTransform.invoke(androidExtension, new JarJarTransform(), new Object[]{});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
