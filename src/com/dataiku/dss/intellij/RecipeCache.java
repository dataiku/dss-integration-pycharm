package com.dataiku.dss.intellij;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.Recipe;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class RecipeCache {
    private final DssSettings dssSettings;
    private final Map<RecipeCacheProject, List<Recipe>> cachedRecipes = new HashMap<>();

    public RecipeCache(DssSettings dssSettings) {
        this.dssSettings = dssSettings;
    }

    public Recipe getRecipe(String instanceId, String projectKey, String recipeName) throws DssException {
        Preconditions.checkNotNull(instanceId, "instanceId");
        Preconditions.checkNotNull(projectKey, "projectKey");
        Preconditions.checkNotNull(recipeName, "recipeName");

        List<Recipe> projectRecipes = getProjectRecipes(instanceId, projectKey);
        for (Recipe recipe: projectRecipes) {
            if (recipeName.equals(recipe.name))
                return recipe;
        }
        return null;
    }

    private List<Recipe> getProjectRecipes(String instanceId, String projectKey) throws DssException {
        RecipeCacheProject dssProject = new RecipeCacheProject(instanceId, projectKey);
        List<Recipe> projectRecipes = cachedRecipes.get(dssProject);
        if (projectRecipes == null) {
            DssInstance dssServer = dssSettings.getDssServer(instanceId);
            if (dssServer == null) {
                throw new IllegalStateException("Unknown DSS instance name: " + instanceId);
            }
            projectRecipes = dssServer.createClient().listRecipes(projectKey);
            cachedRecipes.put(dssProject, projectRecipes);
        }
        return projectRecipes;
    }

    private static class RecipeCacheProject {
        private final String dssServerName;
        private final String projectKey;

        RecipeCacheProject(String dssServerName, String projectKey) {
            this.dssServerName = dssServerName;
            this.projectKey = projectKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RecipeCacheProject that = (RecipeCacheProject) o;
            return Objects.equal(dssServerName, that.dssServerName) &&
                    Objects.equal(projectKey, that.projectKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(dssServerName, projectKey);
        }
    }
}
