package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DssPluginMetadata {
    public String instance;
    public String pluginId;
    public String path;
    public List<DssPluginFileMetadata> files;

    public DssPluginMetadata(String instance, String pluginId, String path) {
        this.instance = instance;
        this.pluginId = pluginId;
        this.path = path;
        this.files = new ArrayList<>();
    }

    public DssPluginFileMetadata findFile(String path) {
        for (DssPluginFileMetadata file : files) {
            if (path.equals(file.remotePath)) {
                return file;
            }
        }
        return null;
    }

    public void removeFile(String path) {
        for (Iterator<DssPluginFileMetadata> iterator = files.iterator(); iterator.hasNext(); ) {
            if (path.equals(iterator.next().remotePath)) {
                iterator.remove();
                return;
            }
        }
    }
}
