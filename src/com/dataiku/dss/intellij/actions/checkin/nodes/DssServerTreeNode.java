package com.dataiku.dss.intellij.actions.checkin.nodes;

import com.dataiku.dss.intellij.config.DssServer;

public class DssServerTreeNode extends CheckinBaseNode {
    public final DssServer dssServer;

    public DssServerTreeNode(DssServer dssServer) {
        this.dssServer = dssServer;
    }

    public RecipesTreeNode getRecipesNode() {
        return listChildren(RecipesTreeNode.class).stream().findAny().orElse(null);
    }

    public RecipesTreeNode getOrAddRecipesNode() {
        RecipesTreeNode result = getRecipesNode();
        if (result == null) {
            result = new RecipesTreeNode();
            add(result);
        }
        return result;
    }

    public PluginsTreeNode getPluginsNode() {
        return listChildren(PluginsTreeNode.class).stream().findAny().orElse(null);
    }

    public PluginsTreeNode getOrAddPluginsNode() {
        PluginsTreeNode result = getPluginsNode();
        if (result == null) {
            result = new PluginsTreeNode();
            add(result);
        }
        return result;
    }

    @Override
    public String toString() {
        return dssServer.name + " [" + dssServer.baseUrl + "]";
    }
}
