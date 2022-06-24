package com.dataiku.dss.model.metadata;

import java.util.List;

public class DssMetadata {
    public int version;
    public List<DssRecipeMetadata> recipes;
    public List<DssPluginMetadata> plugins;
    public List<DssLibraryMetadata> libraries;

    public DssPluginMetadata getPluginById(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        return plugins.stream()
                .filter(plugin -> pluginId.equals(plugin.pluginId))
                .findAny().orElse(null);
    }

    public DssLibraryMetadata getLibByProjectKey(String projectKey) {
        if (projectKey == null) {
            return null;
        }
        return libraries.stream()
                .filter(lib -> projectKey.equals(lib.projectKey))
                .findAny().orElse(null);
    }

}
