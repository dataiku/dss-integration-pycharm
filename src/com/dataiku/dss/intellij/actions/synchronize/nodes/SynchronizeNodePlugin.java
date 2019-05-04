package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

import com.dataiku.dss.intellij.MonitoredPlugin;

public class SynchronizeNodePlugin extends SynchronizeBaseNode {
    public final MonitoredPlugin monitoredPlugin;

    public SynchronizeNodePlugin(MonitoredPlugin monitoredPlugin) {
        this.monitoredPlugin = monitoredPlugin;
    }

    public List<SynchronizeNodePluginContent> getContentNodes() {
        return listChildren(SynchronizeNodePluginContent.class);
    }

    @Override
    public String toString() {
        return monitoredPlugin.plugin.pluginId;
    }
}
