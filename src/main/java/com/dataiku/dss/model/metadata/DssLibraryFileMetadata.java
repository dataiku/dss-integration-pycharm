package com.dataiku.dss.model.metadata;

public class DssLibraryFileMetadata extends DssFileMetadata {
    public final String projectKey;

    public DssLibraryFileMetadata(String instance, String projectKey, String path, String remotePath, int contentHash, String dataBlobId) {
        super(instance, projectKey, path, remotePath, contentHash, dataBlobId);
        this.projectKey = projectKey;
    }

    public DssLibraryFileMetadata(String instance, String projectKey, String path, String remotePath, int contentHash, byte[] data) {
        super(instance, projectKey, path, remotePath, contentHash, data);
        this.projectKey = projectKey;
    }

}
