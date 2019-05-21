package com.dataiku.dss.intellij.utils;

import com.dataiku.dss.Logger;
import com.intellij.openapi.application.ApplicationManager;

public class ComponentUtils {
    private static final Logger log = Logger.getInstance(ComponentUtils.class);

    private ComponentUtils() {
    }

    public static <T> T getComponent(Class<T> clazz) {
        T t = ApplicationManager.getApplication().getComponent(clazz);
        if (t == null) {
            log.error("Could not find class in container: " + clazz.getName());
            throw new IllegalArgumentException("Class not found: " + clazz.getName());
        } else {
            return t;
        }
    }
}
