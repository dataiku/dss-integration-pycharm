package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
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

    public DssPluginMetadata() {
    }
}
