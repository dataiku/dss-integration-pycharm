package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

public class SynchronizeNodeRecipes extends SynchronizeBaseNode {

    @Override
    public String toString() {
        return "Recipes";
    }

    public SynchronizeNodeRecipeProject getOrAddProjectNode(String projectKey) {
        for (SynchronizeNodeRecipeProject node : listChildren(SynchronizeNodeRecipeProject.class)) {
            if (node.projectKey.equals(projectKey)) {
                return node;
            }
        }
        // Not found, add it
        SynchronizeNodeRecipeProject newNode = new SynchronizeNodeRecipeProject(projectKey);
        add(newNode);
        return newNode;
    }

    public List<SynchronizeNodeRecipeProject> getProjectNodes() {
        return listChildren(SynchronizeNodeRecipeProject.class);
    }
}

