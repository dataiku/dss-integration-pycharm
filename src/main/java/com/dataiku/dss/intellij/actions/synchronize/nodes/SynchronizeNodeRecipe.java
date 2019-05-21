package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.intellij.MonitoredRecipeFile;

public class SynchronizeNodeRecipe extends SynchronizeBaseNode {
    public final MonitoredRecipeFile recipe;

    public SynchronizeNodeRecipe(MonitoredRecipeFile recipe) {
        this.recipe = recipe;
    }

    @Override
    public String toString() {
        return recipe.file.getName();
    }
}
