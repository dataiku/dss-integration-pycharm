package com.dataiku.dss.model.metadata;

public class DssLibraryFileMetadata {
    public final String instance;
    public final String projectKey;
    public final String path;
    public final String remotePath;
    public int contentHash;
    public String dataBlobId;
    public byte[] data; // Present if not flushed yet into a data-blob

    public DssLibraryFileMetadata(String instance, String projectKey, String path, String remotePath, int contentHash, String dataBlobId) {
        this.instance = instance;
        this.path = path;
        this.projectKey = projectKey;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.dataBlobId = dataBlobId;
    }

    public DssLibraryFileMetadata(String instance, String projectKey, String path, String remotePath, int contentHash, byte[] data) {
        this.instance = instance;
        this.projectKey = projectKey;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.data = data;
    }

    @Override
    public String toString() {
        return instance + '/' + remotePath;
    }
}
