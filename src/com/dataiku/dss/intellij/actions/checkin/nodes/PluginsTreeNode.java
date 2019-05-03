package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

public class PluginsTreeNode extends CheckinBaseNode {

    public List<PluginTreeNode> getPluginNodes() {
        return listChildren(PluginTreeNode.class);
    }

    @Override
    public String toString() {
        return "Plugins";
    }
}
