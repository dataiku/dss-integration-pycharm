package com.dataiku.dss.intellij;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * Dummy component that only acts as a requestor for all writes.
 */
public class DataikuDSSPlugin implements ApplicationComponent {
    @NotNull
    @Override
    public String getComponentName() {
        return "DataikuDSSPlugin";
    }

    public static DataikuDSSPlugin getInstance() {
        return ComponentUtils.getComponent(DataikuDSSPlugin.class);
    }
}
