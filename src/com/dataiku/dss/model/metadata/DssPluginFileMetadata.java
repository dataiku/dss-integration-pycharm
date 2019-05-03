package com.dataiku.dss.model.metadata;

public class DssPluginFileMetadata {
    public String instance;
    public String pluginId;
    public String path;
    public String remotePath;
    public int contentHash;
    public boolean isFolder;

    @Override
    public String toString() {
        return instance + '/' + pluginId + '/' + remotePath;
    }
}
