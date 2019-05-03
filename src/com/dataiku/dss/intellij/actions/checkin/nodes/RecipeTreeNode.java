package com.dataiku.dss.intellij.actions.checkin.nodes;

import com.dataiku.dss.intellij.MonitoredFile;

public class RecipeTreeNode extends CheckinBaseNode {
    public final MonitoredFile recipe;

    public RecipeTreeNode(MonitoredFile recipe) {
        this.recipe = recipe;
    }

    @Override
    public String toString() {
        return recipe.file.getName();
    }
}
