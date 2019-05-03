package com.dataiku.dss.intellij.actions.checkin.nodes;

import com.dataiku.dss.model.metadata.DssPluginFileMetadata;

public class PluginFileTreeNode extends PluginContentTreeNode {
    public PluginFileTreeNode(DssPluginFileMetadata pluginFile, String name) {
        super(pluginFile, name);
    }
}
