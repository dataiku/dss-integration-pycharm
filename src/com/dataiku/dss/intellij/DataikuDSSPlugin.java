package com.dataiku.dss.intellij;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.wt1.WT1;
import com.intellij.openapi.components.ApplicationComponent;

/**
 * Dummy component that only acts as a requestor for all writes, and entry point for the plugin
 */
public class DataikuDSSPlugin implements ApplicationComponent {
    private final DssSettings settings;
    private final WT1 wt1;

    @NotNull
    @Override
    public String getComponentName() {
        return "DataikuDSSPlugin";
    }

    public static DataikuDSSPlugin getInstance() {
        return ComponentUtils.getComponent(DataikuDSSPlugin.class);
    }

    public DataikuDSSPlugin(DssSettings settings, WT1 wt1) {
        this.settings = settings;
        this.wt1 = wt1;
    }

    @Override
    public void initComponent() {
        if (settings.isTrackingEnabled()) {
            wt1.track("pycharm-start");
        }
    }
}
