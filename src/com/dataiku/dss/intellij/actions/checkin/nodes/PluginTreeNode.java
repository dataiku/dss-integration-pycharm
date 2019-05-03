package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

import com.dataiku.dss.intellij.MonitoredPlugin;

public class PluginTreeNode extends CheckinBaseNode {
    public final MonitoredPlugin monitoredPlugin;

    public PluginTreeNode(MonitoredPlugin monitoredPlugin) {
        this.monitoredPlugin = monitoredPlugin;
    }

    public List<PluginContentTreeNode> getContentNodes() {
        return listChildren(PluginContentTreeNode.class);
    }

    @Override
    public String toString() {
        return monitoredPlugin.plugin.pluginId;
    }
}
