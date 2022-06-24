package com.dataiku.dss.intellij;

import com.google.common.base.Preconditions;

import java.util.List;

public class SynchronizeRequest {
    public final List<MonitoredRecipeFile> recipeFiles;
    public final List<MonitoredPlugin> plugins;
    public final List<MonitoredLibrary> libraries;

    public SynchronizeRequest(List<MonitoredRecipeFile> recipeFiles, List<MonitoredPlugin> plugins, List<MonitoredLibrary> libraries) {
        Preconditions.checkNotNull(recipeFiles);
        Preconditions.checkNotNull(plugins);
        Preconditions.checkNotNull(libraries);

        this.recipeFiles = recipeFiles;
        this.plugins = plugins;
        this.libraries = libraries;
    }

    public boolean isEmpty() {
        return recipeFiles.isEmpty() && plugins.isEmpty() && libraries.isEmpty();
    }
}
