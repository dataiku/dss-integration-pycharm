package com.dataiku.dss.intellij;

import java.io.IOException;

import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;

public class SynchronizerUtils {

    public static void saveRecipeToDss(DssSettings dssSettings, MonitoredFile monitoredFile, String fileContent) throws IOException {
        // File has been updated locally, it needs to be sent to DSS.
        DssRecipeMetadata recipe = monitoredFile.recipe;
        DSSClient dssClient = dssSettings.getDssClient(recipe.instance);
        RecipeAndPayload existingRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (existingRecipe != null && !fileContent.equals(existingRecipe.payload)) {
            dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, fileContent);
            RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

            // Update metadata & schedule associated metadata file to be updated
            recipe.versionNumber = updatedRecipe.recipe.versionTag.versionNumber;
            recipe.contentHash = VirtualFileUtils.getContentHash(fileContent);

            monitoredFile.metadataFile.flush();
        }
    }
}
