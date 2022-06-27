package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
import java.util.List;

public class DssLibraryMetadata extends DssFileSystemMetadata {
    public String instance;
    public String projectKey;
    public String path;
    public List<DssLibraryFileMetadata> files;

    public DssLibraryMetadata(String instance, String projectKey, String path) {
        this.instance = instance;
        this.projectKey = projectKey;
        this.path = path;
        this.files = new ArrayList<>();
    }

}
