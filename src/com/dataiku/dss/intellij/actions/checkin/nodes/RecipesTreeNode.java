package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.List;

public class RecipesTreeNode extends CheckinBaseNode {

    @Override
    public String toString() {
        return "Recipes";
    }

    public RecipeProjectTreeNode getOrAddProjectNode(String projectKey) {
        for (RecipeProjectTreeNode node : listChildren(RecipeProjectTreeNode.class)) {
            if (node.projectKey.equals(projectKey)) {
                return node;
            }
        }
        // Not found, add it
        RecipeProjectTreeNode newNode = new RecipeProjectTreeNode(projectKey);
        add(newNode);
        return newNode;
    }

    public List<RecipeProjectTreeNode> getProjectNodes() {
        return listChildren(RecipeProjectTreeNode.class);
    }
}

