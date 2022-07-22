package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DssFileSystemMetadata {
    public String instance;
    public String path;
    public String id;
    public List<DssFileMetadata> files;

    public DssFileSystemMetadata(String instance, String path, String id) {
        this.instance = instance;
        this.id = id;
        this.path = path;
        this.files = new ArrayList<>();
    }

    public DssFileMetadata findFile(String path) {
        for (DssFileMetadata file : files) {
            if (path.equals(file.remotePath)) {
                return file;
            }
        }
        return null;
    }

    public void removeFile(String path) {
        for (Iterator<DssFileMetadata> iterator = files.iterator(); iterator.hasNext(); ) {
            if (path.equals(iterator.next().remotePath)) {
                iterator.remove();
                return;
            }
        }
    }
}
