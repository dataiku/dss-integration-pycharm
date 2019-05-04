package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.SynchronizerUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.io.Files;
import com.intellij.openapi.application.ReadAction;

class SynchronizerWorker {
    private static final Logger log = Logger.getInstance(SynchronizerWorker.class);

    private final DssSettings dssSettings;
    private final RecipeCache recipeCache;
    private final MonitoredFilesIndex monitoredFilesIndex;
    private final boolean monitorLocalChanges;

    SynchronizerWorker(DssSettings dssSettings, RecipeCache recipeCache, MonitoredFilesIndex monitoredFilesIndex, boolean monitorLocalChanges) {
        this.dssSettings = dssSettings;
        this.recipeCache = recipeCache;
        this.monitoredFilesIndex = monitoredFilesIndex;
        this.monitorLocalChanges = monitorLocalChanges;
    }

    void run() {
        log.info("Starting synchronization of monitored files");
        try {
            // Detect changes (we're not reading files through IntelliJ anymore, we're doing it from the local filesystem, so no protection needed)
            for (MonitoredRecipeFile monitoredFile : monitoredFilesIndex.getMonitoredRecipeFiles()) {
                try {
                    detectAndProcessChanges(monitoredFile);
                } catch (IOException | RuntimeException e) {
                    log.warn(String.format("Unable to detect change for recipe '%s.%s.%s'.", monitoredFile.recipe.instance, monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName), e);
                }
            }
            log.info("Synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error while trying to synchronize DSS items", e);
        }
    }

    //-------------------------------------------------------------------------
    //
    // Detect whether some files have changed
    //
    //-------------------------------------------------------------------------

    /**
     * Check whether recipe has been updated on DSS side, and if changes are detected, update the local files.
     */
    private void detectAndProcessChanges(MonitoredRecipeFile monitoredFile) throws IOException {
        DssRecipeMetadata recipe = monitoredFile.recipe;
        Recipe dssRecipe = recipeCache.getRecipe(recipe.instance, recipe.projectKey, recipe.recipeName);
        if (dssRecipe == null) {
            // Recipe deleted on DSS instance. We don't want to delete the file (because it might still be useful
            // locally), but we'll stop synchronizing it for once.
            log.info(String.format("Recipe '%s' has been deleted on remote DSS instance. Stop synchronizing changes with local file %s",
                    recipe,
                    monitoredFile.file.getCanonicalPath()));

            MonitoredFilesIndex.getInstance().removeFromIndex(monitoredFile);
            monitoredFile.metadataFile.removeRecipe(recipe);
            return;
        }
        if (monitorLocalChanges) {
            if (!monitoredFile.file.exists() || !monitoredFile.file.isValid()) {
                // File deleted locally, stop synchronizing it.
                log.info(String.format("File '%s' has been deleted locally. Stop synchronizing changes with recipe '%s'.",
                        monitoredFile.file.getCanonicalPath(),
                        recipe));
                MonitoredFilesIndex.getInstance().removeFromIndex(monitoredFile);
                monitoredFile.metadataFile.removeRecipe(recipe);
                return;
            } else {
                String fileContent = ReadAction.compute(() -> VirtualFileUtils.readVirtualFile(monitoredFile.file));
                if (VirtualFileUtils.getContentHash(fileContent) != monitoredFile.recipe.contentHash) {
                    // File has been updated locally, update it in DSS.
                    log.info(String.format("Recipe '%s' has been locally modified. Saving it onto the remote DSS instance", monitoredFile.recipe));
                    saveRecipeToDss(dssSettings, monitoredFile, fileContent);
                    return;
                }
            }
        }
        if (dssRecipe.versionTag.versionNumber != recipe.versionNumber) {
            // Recipe updated on DSS instance. We update the file directly on the file system so that if the developer
            // has also updated the file in the editor, IntelliJ/PyCharm displays a nice dialog to choose which version
            // to keep.
            log.info(String.format("Recipe '%s' has been updated on remote DSS instance (remote version: %d, local version: %d). Updating local file %s",
                    recipe,
                    dssRecipe.versionTag.versionNumber,
                    recipe.versionNumber,
                    monitoredFile.file.getCanonicalPath()));

            updateLocalRecipe(monitoredFile);
        }
    }

    /**
     * Recipe has been updated on DSS side, update the local file.
     */
    private void updateLocalRecipe(MonitoredRecipeFile monitoredFile) throws IOException {
        DssRecipeMetadata recipe = monitoredFile.recipe;
        DSSClient dssClient = dssSettings.getDssClient(recipe.instance);
        RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        String recipeContent = recipeAndPayload.payload;
        if (recipeContent == null) {
            recipeContent = "";
        }

        // Write recipe file, but directly to file-system so that IntelliJ can ask users whether they want to upload the content in theirs editors or not.
        Files.write(recipeContent, monitoredFile.physicalFile, UTF_8);

        // Update metadata & schedule associated metadata file to be updated
        recipe.versionNumber = recipeAndPayload.recipe.versionTag.versionNumber;
        recipe.contentHash = getContentHash(recipeContent);

        // Update the metadata file (directly on file-system too).
        monitoredFile.metadataFile.flush();
    }
}
