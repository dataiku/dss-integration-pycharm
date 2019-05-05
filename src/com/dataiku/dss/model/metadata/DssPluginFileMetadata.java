package com.dataiku.dss.model.metadata;

public class DssPluginFileMetadata {
    public final String instance;
    public final String pluginId;
    public final String path;
    public final String remotePath;
    public final int contentHash;

    public DssPluginFileMetadata(String instance, String pluginId, String path, String remotePath, int contentHash) {
        this.instance = instance;
        this.pluginId = pluginId;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
    }

    @Override
    public String toString() {
        return instance + '/' + pluginId + '/' + remotePath;
    }
}
