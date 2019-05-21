package com.dataiku.dss.intellij;

import java.util.List;

import com.google.common.base.Preconditions;

public class SynchronizeRequest {
    public final List<MonitoredRecipeFile> recipeFiles;
    public final List<MonitoredPlugin> plugins;

    public SynchronizeRequest(List<MonitoredRecipeFile> recipeFiles, List<MonitoredPlugin> plugins) {
        Preconditions.checkNotNull(recipeFiles);
        Preconditions.checkNotNull(plugins);

        this.recipeFiles = recipeFiles;
        this.plugins = plugins;
    }

    public boolean isEmpty() {
        return recipeFiles.isEmpty() && plugins.isEmpty();
    }
}
