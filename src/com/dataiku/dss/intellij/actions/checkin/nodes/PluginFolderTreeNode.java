package com.dataiku.dss.intellij.actions.checkin.nodes;

import com.dataiku.dss.model.metadata.DssPluginFileMetadata;

public class PluginFolderTreeNode extends PluginContentTreeNode {
    public PluginFolderTreeNode(DssPluginFileMetadata pluginFile, String name) {
        super(pluginFile, name);
    }
}
