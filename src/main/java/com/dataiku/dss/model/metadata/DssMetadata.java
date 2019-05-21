package com.dataiku.dss.model.metadata;

import java.util.List;

public class DssMetadata {
    public int version;
    public List<DssRecipeMetadata> recipes;
    public List<DssPluginMetadata> plugins;

    public DssPluginMetadata getPluginById(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        return plugins.stream()
                .filter(plugin -> pluginId.equals(plugin.pluginId))
                .findAny().orElse(null);
    }
}
