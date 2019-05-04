package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.intellij.config.DssServer;

public class SynchronizeNodeDssInstance extends SynchronizeBaseNode {
    public final DssServer dssServer;

    public SynchronizeNodeDssInstance(DssServer dssServer) {
        this.dssServer = dssServer;
    }

    public SynchronizeNodeRecipes getRecipesNode() {
        return listChildren(SynchronizeNodeRecipes.class).stream().findAny().orElse(null);
    }

    public SynchronizeNodeRecipes getOrAddRecipesNode() {
        SynchronizeNodeRecipes result = getRecipesNode();
        if (result == null) {
            result = new SynchronizeNodeRecipes();
            add(result);
        }
        return result;
    }

    public SynchronizeNodePlugins getPluginsNode() {
        return listChildren(SynchronizeNodePlugins.class).stream().findAny().orElse(null);
    }

    public SynchronizeNodePlugins getOrAddPluginsNode() {
        SynchronizeNodePlugins result = getPluginsNode();
        if (result == null) {
            result = new SynchronizeNodePlugins();
            add(result);
        }
        return result;
    }

    @Override
    public String toString() {
        return dssServer.name + " [" + dssServer.baseUrl + "]";
    }
}