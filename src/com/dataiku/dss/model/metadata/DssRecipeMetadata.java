package com.dataiku.dss.model.metadata;

public class DssRecipeMetadata {
    public String instance;
    public String projectKey;
    public String recipeName;
    public String path;
    public int contentHash;
    public String dataBlobId;
    public transient byte[] data; // Present if not flushed yet into a data-block
    public long versionNumber;

    @Override
    public String toString() {
        return instance + '.' + projectKey + '.' + recipeName;
    }
}
