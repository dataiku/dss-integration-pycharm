package com.dataiku.dss.model.metadata;

public class DssLibraryMetadata extends DssFileSystemMetadata {
    public String projectKey;

    public DssLibraryMetadata(String instance, String projectKey, String path) {
        super(instance, path, projectKey);
        this.projectKey = projectKey;
    }

}
