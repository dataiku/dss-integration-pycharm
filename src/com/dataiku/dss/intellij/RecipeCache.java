package com.dataiku.dss.intellij;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.Recipe;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.intellij.openapi.util.PasswordUtil;

public class RecipeCache {
    private final DssSettings dssSettings;
    private final Map<RecipeCacheProject, List<Recipe>> cachedRecipes = new HashMap<>();

    RecipeCache(DssSettings dssSettings) {
        this.dssSettings = dssSettings;
    }

    public Recipe getRecipe(String dssServerName, String projectKey, String recipeName) throws DssException {
        Preconditions.checkNotNull(dssServerName, "dssServerName");
        Preconditions.checkNotNull(projectKey, "projectKey");
        Preconditions.checkNotNull(recipeName, "recipeName");

        List<Recipe> projectRecipes = getProjectRecipes(dssServerName, projectKey);
        for (Recipe recipe: projectRecipes) {
            if (recipeName.equals(recipe.name))
                return recipe;
        }
        return null;
    }

    private List<Recipe> getProjectRecipes(String dssServerName, String projectKey) throws DssException {
        RecipeCacheProject dssProject = new RecipeCacheProject(dssServerName, projectKey);
        List<Recipe> projectRecipes = cachedRecipes.get(dssProject);
        if (projectRecipes == null) {
            DssServer dssServer = dssSettings.getDssServer(dssServerName);
            if (dssServer == null) {
                throw new IllegalStateException("Unknown DSS server name: " + dssServerName);
            }
            DSSClient dssClient = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));
            projectRecipes = dssClient.listRecipes(projectKey);
            cachedRecipes.put(dssProject, projectRecipes);
        }
        return projectRecipes;
    }

    private static class RecipeCacheProject {
        private String dssServerName;
        private String projectKey;

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
