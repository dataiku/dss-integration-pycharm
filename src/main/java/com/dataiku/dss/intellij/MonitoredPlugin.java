package com.dataiku.dss.intellij;

import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredPlugin extends MonitoredFileSystem {
    public final DssPluginMetadata plugin;

    public MonitoredPlugin(VirtualFile pluginBaseDir, MetadataFile metadataFile, DssPluginMetadata plugin) {
        super(pluginBaseDir, metadataFile, plugin);
        this.plugin = plugin;
    }

}
