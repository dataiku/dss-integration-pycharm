package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DssLibraryMetadata implements DssFileSystemMetadataInterface {
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

    public DssLibraryFileMetadata findFile(String path) {
        for (DssLibraryFileMetadata file : files) {
            if (path.equals(file.remotePath)) {
                return file;
            }
        }
        return null;
    }

    public void removeFile(String path) {
        for (Iterator<DssLibraryFileMetadata> iterator = files.iterator(); iterator.hasNext(); ) {
            if (path.equals(iterator.next().remotePath)) {
                iterator.remove();
                return;
            }
        }
    }
}
