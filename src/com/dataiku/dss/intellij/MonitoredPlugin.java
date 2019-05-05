package com.dataiku.dss.intellij;

import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredPlugin {
    public final VirtualFile pluginBaseDir;
    public final MetadataFile metadataFile;
    public final DssPluginMetadata plugin;

    public MonitoredPlugin(VirtualFile pluginBaseDir, MetadataFile metadataFile, DssPluginMetadata plugin) {
        Preconditions.checkNotNull(pluginBaseDir, "pluginBaseDir");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(plugin, "plugin");

        this.pluginBaseDir = pluginBaseDir;
        this.metadataFile = metadataFile;
        this.plugin = plugin;
    }

    public DssPluginFileMetadata findFile(String path) {
        return plugin.findFile(path);
    }

    public void removeFile(String path) {
        plugin.removeFile(path);
    }
}
