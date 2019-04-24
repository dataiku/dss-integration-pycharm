package com.dataiku.dss.intellij.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;

public class Utils {
    private static final Logger LOG = Logger.getInstance(Utils.class);

    private Utils() {
    }

    public static <T> T get(ComponentManager container, Class<T> clazz) {
        T t = container.getComponent(clazz);
        if (t == null) {
            LOG.error("Could not find class in container: " + clazz.getName());
            throw new IllegalArgumentException("Class not found: " + clazz.getName());
        } else {
            return t;
        }
    }

    public static <T> T get(Class<T> clazz) {
        return get(ApplicationManager.getApplication(), clazz);
    }
}
