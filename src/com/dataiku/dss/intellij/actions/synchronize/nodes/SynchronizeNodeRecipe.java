package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.intellij.MonitoredFile;

public class SynchronizeNodeRecipe extends SynchronizeBaseNode {
    public final MonitoredFile recipe;

    public SynchronizeNodeRecipe(MonitoredFile recipe) {
        this.recipe = recipe;
    }

    @Override
    public String toString() {
        return recipe.file.getName();
    }
}
