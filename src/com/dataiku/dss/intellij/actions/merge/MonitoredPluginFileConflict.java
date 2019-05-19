package com.dataiku.dss.intellij.actions.merge;

import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredPluginFileConflict extends MonitoredFileConflict {
    public MonitoredPlugin plugin;
    public DssPluginFileMetadata pluginFile;

    public MonitoredPluginFileConflict(VirtualFile file, MonitoredPlugin plugin, DssPluginFileMetadata pluginFile) {
        super(file);
        this.plugin = plugin;
        this.pluginFile = pluginFile;
    }
}
