package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

import com.dataiku.dss.model.metadata.DssPluginFileMetadata;

public class SynchronizeNodePluginContent extends SynchronizeBaseNode {
    public final DssPluginFileMetadata pluginFile;
    public final String name;

    public SynchronizeNodePluginContent(DssPluginFileMetadata pluginFile, String name) {
        this.pluginFile = pluginFile;
        this.name = name;
    }

    public List<SynchronizeNodePluginContent> getContentNodes() {
        return listChildren(SynchronizeNodePluginContent.class);
    }

    @Override
    public String toString() {
        return name;
    }
}
