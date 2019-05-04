package com.dataiku.dss.intellij;

import java.io.IOException;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;

public class SynchronizerUtils {

    public static void saveRecipeToDss(DssSettings dssSettings, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredFile.recipe.instance);
        saveRecipeToDss(dssClient, monitoredFile, fileContent, true);
    }

    public static void saveRecipeToDss(DssServer dssInstance, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        saveRecipeToDss(dssInstance.createClient(), monitoredFile, fileContent, true);
    }

    public static void saveRecipeToDss(DSSClient dssClient, MonitoredRecipeFile monitoredFile, String fileContent, boolean flushMetadata) throws IOException {
        // File has been updated locally, it needs to be sent to DSS.
        DssRecipeMetadata recipe = monitoredFile.recipe;
        RecipeAndPayload existingRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (existingRecipe != null && !fileContent.equals(existingRecipe.payload)) {
            dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, fileContent);
            RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

            // Update metadata & schedule associated metadata file to be updated
            recipe.versionNumber = updatedRecipe.recipe.versionTag.versionNumber;
            recipe.contentHash = VirtualFileUtils.getContentHash(fileContent);

            if (flushMetadata) {
                monitoredFile.metadataFile.flush();
            }
        }
    }
}
