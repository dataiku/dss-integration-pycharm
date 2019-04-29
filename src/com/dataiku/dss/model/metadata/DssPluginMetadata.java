package com.dataiku.dss.model.metadata;

import java.util.ArrayList;
import java.util.List;

public class DssPluginMetadata {
    public String pluginId;
    public List<DssPluginFileMetadata> files;

    public DssPluginMetadata(String pluginId) {
        this.pluginId = pluginId;
        this.files = new ArrayList<>();
    }

    public DssPluginMetadata() {
    }
}
