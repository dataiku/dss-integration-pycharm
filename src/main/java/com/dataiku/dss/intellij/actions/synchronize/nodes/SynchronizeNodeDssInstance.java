package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.intellij.config.DssInstance;

public class SynchronizeNodeDssInstance extends SynchronizeBaseNode {
    public final DssInstance dssInstance;

    public SynchronizeNodeDssInstance(DssInstance dssInstance) {
        this.dssInstance = dssInstance;
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

    public SynchronizeNodeLibraries getLibrariesNode() {
        return listChildren(SynchronizeNodeLibraries.class).stream().findAny().orElse(null);
    }

    public SynchronizeNodePlugins getOrAddPluginsNode() {
        SynchronizeNodePlugins result = getPluginsNode();
        if (result == null) {
            result = new SynchronizeNodePlugins();
            add(result);
        }
        return result;
    }

    public SynchronizeNodeLibraries getOrAddLibrariesNode() {
        SynchronizeNodeLibraries result = getLibrariesNode();
        if (result == null) {
            result = new SynchronizeNodeLibraries();
            add(result);
        }
        return result;
    }

    @Override
    public String toString() {
        return dssInstance.label + "  [ " + dssInstance.baseUrl + " ]";
    }
}
