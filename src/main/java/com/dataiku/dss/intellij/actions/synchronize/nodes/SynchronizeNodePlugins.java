package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

public class SynchronizeNodePlugins extends SynchronizeBaseNode {

    public List<SynchronizeNodePlugin> getPluginNodes() {
        return listChildren(SynchronizeNodePlugin.class);
    }

    @Override
    public String toString() {
        return "Plugins";
    }
}
