package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

public class SynchronizeNodeRecipeProject extends SynchronizeBaseNode {
    public final String projectKey;

    public SynchronizeNodeRecipeProject(String projectKey) {
        this.projectKey = projectKey;
    }

    public List<SynchronizeNodeRecipe> getRecipeNodes() {
        return listChildren(SynchronizeNodeRecipe.class);
    }

    @Override
    public String toString() {
        return projectKey;
    }
}
