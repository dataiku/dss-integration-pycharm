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

    @NotNull
    @Override
    public String getComponentName() {
        return "DataikuDSSPlugin";
    }

    public static DataikuDSSPlugin getInstance() {
        return ComponentUtils.getComponent(DataikuDSSPlugin.class);
    }

    @Override
    public void initComponent() {
        DssSettings settings = DssSettings.getInstance();
        if (settings.isTrackingEnabled()) {
            WT1 wt1 = WT1.getInstance();
            wt1.track("pycharm-start");
        }
    }
}
