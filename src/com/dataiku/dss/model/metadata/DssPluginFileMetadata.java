package com.dataiku.dss.model.metadata;

public class DssPluginFileMetadata {
    public final String instance;
    public final String pluginId;
    public final String path;
    public final String remotePath;
    public final int contentHash;
    public String dataBlobId;
    public byte[] data; // Present if not flushed yet into a data-blob

    public DssPluginFileMetadata(String instance, String pluginId, String path, String remotePath, int contentHash, String dataBlobId) {
        this.instance = instance;
        this.pluginId = pluginId;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.dataBlobId = dataBlobId;
    }

    public DssPluginFileMetadata(String instance, String pluginId, String path, String remotePath, int contentHash, byte[] data) {
        this.instance = instance;
        this.pluginId = pluginId;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.data = data;
    }

    @Override
    public String toString() {
        return instance + '/' + pluginId + '/' + remotePath;
    }
}
