package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

import com.dataiku.dss.model.metadata.DssPluginFileMetadata;

public class PluginContentTreeNode extends CheckinBaseNode {
    public final DssPluginFileMetadata pluginFile;
    public final String name;

    public PluginContentTreeNode(DssPluginFileMetadata pluginFile, String name) {
        this.pluginFile = pluginFile;
        this.name = name;
    }

    public List<PluginContentTreeNode> getContentNodes() {
        return listChildren(PluginContentTreeNode.class);
    }

    @Override
    public String toString() {
        return name;
    }
}
