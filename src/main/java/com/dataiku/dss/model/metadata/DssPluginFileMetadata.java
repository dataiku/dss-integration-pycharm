package com.dataiku.dss.model.metadata;

public class DssPluginFileMetadata extends DssFileMetadata {
    public final String pluginId;

    public DssPluginFileMetadata(String instance, String pluginId, String path, String remotePath, int contentHash, String dataBlobId) {
        super(instance, pluginId, path, remotePath, contentHash, dataBlobId);
        this.pluginId = pluginId;
    }

    public DssPluginFileMetadata(String instance, String pluginId, String path, String remotePath, int contentHash, byte[] data) {
        super(instance, pluginId, path, remotePath, contentHash, data);
        this.pluginId = pluginId;
    }
}
