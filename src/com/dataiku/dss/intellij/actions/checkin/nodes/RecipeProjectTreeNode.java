package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

public class RecipeProjectTreeNode extends CheckinBaseNode {
    public final String projectKey;

    public RecipeProjectTreeNode(String projectKey) {
        this.projectKey = projectKey;
    }

    public List<RecipeTreeNode> getRecipeNodes() {
        return listChildren(RecipeTreeNode.class);
    }

    @Override
    public String toString() {
        return projectKey;
    }
}
