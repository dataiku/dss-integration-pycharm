package com.dataiku.dss.intellij;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.metadata.DssMetadata;
import com.dataiku.dss.intellij.metadata.DssMetadataManager;
import com.dataiku.dss.intellij.metadata.DssRecipeMetadata;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.Recipe;
import com.dataiku.dss.model.RecipeAndPayload;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.VirtualFile;

class LocalToDssSynchronizer {
    private static final Logger log = Logger.getInstance(LocalToDssSynchronizer.class);

    private final RecipeCache recipeCache;
    private final DssSettings dssSettings;
    private final VirtualFile modifiedFile;

    LocalToDssSynchronizer(DssSettings dssSettings, RecipeCache recipeCache, VirtualFile modifiedFile) {
        this.dssSettings = dssSettings;
        this.recipeCache = recipeCache;
        this.modifiedFile = modifiedFile;
    }

    void run() {
        log.info("Starting synchronization done");
        MonitoredFile monitoredFile = null;
        try {
            // Check whether the modified file is a monitored file from an opened project
            monitoredFile = ReadAction.compute(this::findMonitoredFile);
        } catch (Exception e) {
            log.error(String.format("Error while trying to determine whether the file '%s' is monitored by DSS plugin.", modifiedFile), e);
        }

        // Detect changes (we're not reading files through IntelliJ anymore, we're doing it from the local filesystem, so no protection needed)
        if (monitoredFile != null) {
            try {
                if (detectChange(monitoredFile)) {
                    sendToDss(monitoredFile);
                }
            } catch (Exception e) {
                System.err.println("XXXXX");
                e.printStackTrace();
                log.warn(String.format("Unable to detect change for recipe '%s.%s.%s'.", monitoredFile.recipeMetadata.instance, monitoredFile.recipeMetadata.projectKey, monitoredFile.recipeMetadata.recipeName), e);
            }
        }
        log.info("Synchronization completed successfully");
    }

    //-------------------------------------------------------------------------
    //
    // Gather all files to be checked for changes
    //
    //-------------------------------------------------------------------------

    private MonitoredFile findMonitoredFile() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
            Set<String> analyzedModulesRoot = new HashSet<>();
            for (VirtualFile moduleContentRoot : projectRootManager.getContentRootsFromAllModules()) {
                if (analyzedModulesRoot.contains(moduleContentRoot.getUrl())) {
                    continue;
                }
                analyzedModulesRoot.add(moduleContentRoot.getUrl());
                try {
                    DssMetadata dssMetadata = DssMetadataManager.readMetadata(moduleContentRoot);
                    if (dssMetadata != null) {
                        MonitoredFile monitoredFile = findMonitoredFile(moduleContentRoot, dssMetadata);
                        if (monitoredFile != null) {
                            return monitoredFile;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Unable to synchronize module.", e);
                }
            }
        }
        return null;
    }

    private MonitoredFile findMonitoredFile(VirtualFile moduleContentRoot, DssMetadata dssMetadata) {
        log.info("Analyzing module " + moduleContentRoot);
        for (DssRecipeMetadata recipe : dssMetadata.recipes) {
            try {
                MonitoredFile monitoredFile = findMonitoredFile(moduleContentRoot, dssMetadata, recipe);
                if (monitoredFile != null) {
                    return monitoredFile;
                }
            } catch (Exception e) {
                log.warn(String.format("Unable to synchronize recipe '%s.%s' from '%s' DSS server.", recipe.projectKey, recipe.recipeName, recipe.instance), e);
            }
        }
        return null;
    }

    private MonitoredFile findMonitoredFile(VirtualFile moduleContentRoot, DssMetadata dssMetadata, DssRecipeMetadata recipeMetadata) throws IOException {
        log.info(String.format("Analyzing recipe %s.%s.%s", recipeMetadata.instance, recipeMetadata.projectKey, recipeMetadata.recipeName));
        VirtualFile file = moduleContentRoot.findFileByRelativePath(recipeMetadata.path);
        if (file == null || !file.isValid() || !file.exists()) {
            throw new IllegalStateException("File not longer exists on the filesystem.");
        }
        if (this.modifiedFile.getCanonicalPath() != null && this.modifiedFile.getCanonicalPath().equals(file.getCanonicalPath())) {
            return new MonitoredFile(moduleContentRoot, dssMetadata, recipeMetadata, VirtualFileUtils.readFile(file));
        }
        return null;
    }

    private static class MonitoredFile {
        final VirtualFile moduleContentRoot;
        final DssMetadata dssMetadata;
        final DssRecipeMetadata recipeMetadata;
        final String newRecipeContent;

        MonitoredFile(VirtualFile moduleContentRoot, DssMetadata dssMetadata, DssRecipeMetadata recipeMetadata, String newRecipeContent) {
            this.moduleContentRoot = moduleContentRoot;
            this.dssMetadata = dssMetadata;
            this.recipeMetadata = recipeMetadata;
            this.newRecipeContent = newRecipeContent;
        }
    }

    //-------------------------------------------------------------------------
    //
    // Detect whether some files have changed
    //
    //-------------------------------------------------------------------------

    private boolean detectChange(MonitoredFile task) throws IOException {
        // Check whether recipe has been updated locally
        DssRecipeMetadata recipe = task.recipeMetadata;
        int actualContentHash = VirtualFileUtils.getContentHash(task.newRecipeContent);
        if (actualContentHash != recipe.contentHash) {
            // Check whether recipe has been updated on DSS side
            Recipe dssRecipe = recipeCache.getRecipe(recipe.instance, recipe.projectKey, recipe.recipeName);
            if (dssRecipe == null) {
                throw new IllegalStateException(String.format("Recipe '%s' not longer exists on the remote DSS server.", recipe.recipeName));
            }
            return true;
        }
        return false;
    }

    /**
     * File has been updated, it needs to be sent to DSS.
     */
    private void sendToDss(MonitoredFile monitoredFile) throws IOException {
        DssRecipeMetadata recipe = monitoredFile.recipeMetadata;
        String newRecipeContent = monitoredFile.newRecipeContent;

        DSSClient dssClient = getDssClient(dssSettings, recipe);
        RecipeAndPayload existingRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (!existingRecipe.payload.equals(newRecipeContent)) {
            dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, newRecipeContent);
            RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

            // Update metadata & schedule associated metadata file to be updated
            recipe.contentDssVersion = updatedRecipe.recipe.versionTag.versionNumber;
            recipe.contentHash = VirtualFileUtils.getContentHash(newRecipeContent);

            // Update the metadata file (directly on file-system).
            try {
                VirtualFile file = monitoredFile.moduleContentRoot.findFileByRelativePath(".dss/recipes.json");
                if (file != null) {
                    log.info(String.format("Updating DSS metadata file '%s'.", file));
                    DssMetadataManager.writeMetadata(new File(file.getPath()), monitoredFile.dssMetadata);
                }
            } catch (IOException e) {
                log.error("Unable to write DSS metadata file", e);
            }
        }
    }

    //-------------------------------------------------------------------------
    //
    // Implementation
    //
    //-------------------------------------------------------------------------

    @NotNull
    private DSSClient getDssClient(DssSettings dssSettings, DssRecipeMetadata recipe) {
        DssServer dssServer = dssSettings.getDssServer(recipe.instance);
        if (dssServer == null) {
            throw new IllegalStateException(String.format("Unknown DSS server name: '%s'", recipe.instance));
        }
        return new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));
    }
}
