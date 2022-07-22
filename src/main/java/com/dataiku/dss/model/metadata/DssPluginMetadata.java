package com.dataiku.dss.model.metadata;

public class DssPluginMetadata extends DssFileSystemMetadata {
    public String pluginId;

    public DssPluginMetadata(String instance, String pluginId, String path) {
        super(instance, path, pluginId);
        this.pluginId = pluginId;
    }

}
