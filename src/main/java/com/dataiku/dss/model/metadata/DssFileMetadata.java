package com.dataiku.dss.model.metadata;

public class DssFileMetadata {
    public final String instance;
    public final String id;
    public final String path;
    public final String remotePath;
    public int contentHash;
    public String dataBlobId;
    public byte[] data; // Present if not flushed yet into a data-blob

    public DssFileMetadata(String instance, String id, String path, String remotePath, int contentHash, String dataBlobId) {
        this.instance = instance;
        this.id = id;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.dataBlobId = dataBlobId;
    }

    public DssFileMetadata(String instance, String id, String path, String remotePath, int contentHash, byte[] data) {
        this.instance = instance;
        this.id = id;
        this.path = path;
        this.remotePath = remotePath;
        this.contentHash = contentHash;
        this.data = data;
    }

    public String toString() {
        return instance + '/' + remotePath;
    }
}
