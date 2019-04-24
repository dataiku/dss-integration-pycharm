package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.google.common.base.Charsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import com.google.common.io.Files;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.VirtualFile;

class DssToLocalSynchronizer {
    private static final Logger log = Logger.getInstance(DssToLocalSynchronizer.class);

    private final RecipeCache recipeCache;
    private final DssSettings dssSettings;
    private final List<MonitoredFile> monitoredFiles = new ArrayList<>();
    private final List<UpdateLocalRecipeFileAction> actions = new ArrayList<>();

    DssToLocalSynchronizer(DssSettings dssSettings, RecipeCache recipeCache) {
        this.dssSettings = dssSettings;
        this.recipeCache = recipeCache;
    }

    void run() {
        log.info("Starting synchronization done");
        try {
            // Retrieve all monitored files and their respective .dss metadata file (read-only phase)
            Application application = ApplicationManager.getApplication();
            application.runReadAction(() -> retrieveMonitoredFiles(ProjectManager.getInstance().getOpenProjects()));

            // Detect changes (we're not reading files through IntelliJ anymore, we're doing it from the local filesystem, so no protection needed)
            detectChanges();

            // Execute all actions
            if (!actions.isEmpty()) {
                application.invokeAndWait(this::performUpdateActions, ModalityState.defaultModalityState());
            }
            log.info("Synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error while trying to synchronize DSS items", e);
        }
    }

    //-------------------------------------------------------------------------
    //
    // Gather all files to be checked for changes
    //
    //-------------------------------------------------------------------------

    private void retrieveMonitoredFiles(Project[] projects) {
        log.debug("Detecting changes...");
        for (Project project : projects) {
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
                        retrieveMonitoredFilesForModule(moduleContentRoot, dssMetadata);
                    }
                } catch (Exception e) {
                    log.warn("Unable to synchronize module.", e);
                }
            }
        }
    }

    private void retrieveMonitoredFilesForModule(VirtualFile moduleContentRoot, DssMetadata dssMetadata) {
        log.info("Analyzing module " + moduleContentRoot);
        for (DssRecipeMetadata recipe : dssMetadata.recipes) {
            try {
                retrieveMonitoredFilesForRecipe(moduleContentRoot, dssMetadata, recipe);
            } catch (Exception e) {
                log.warn(String.format("Unable to synchronize recipe '%s.%s' from '%s' DSS server.", recipe.projectKey, recipe.recipeName, recipe.instance), e);
            }
        }
    }

    private void retrieveMonitoredFilesForRecipe(VirtualFile moduleContentRoot, DssMetadata dssMetadata, DssRecipeMetadata recipeMetadata) throws IOException {
        log.info(String.format("Analyzing recipe %s.%s.%s", recipeMetadata.instance, recipeMetadata.projectKey, recipeMetadata.recipeName));
        VirtualFile file = moduleContentRoot.findFileByRelativePath(recipeMetadata.path);
        if (file == null || !file.isValid() || !file.exists()) {
            throw new IllegalStateException("File not longer exists on the filesystem.");
        }
        File physicalFile = new File(file.getPath());
        if (!physicalFile.exists()) {
            return; // File is not persisted on filesystem yet
        }
        monitoredFiles.add(new MonitoredFile(moduleContentRoot, dssMetadata, recipeMetadata, physicalFile));
    }

    private static class MonitoredFile {
        final VirtualFile moduleContentRoot;
        final DssMetadata dssMetadata;
        final DssRecipeMetadata recipeMetadata;
        final File physicalFile;

        MonitoredFile(VirtualFile moduleContentRoot, DssMetadata dssMetadata, DssRecipeMetadata recipeMetadata, File physicalFile) {
            this.moduleContentRoot = moduleContentRoot;
            this.dssMetadata = dssMetadata;
            this.recipeMetadata = recipeMetadata;
            this.physicalFile = physicalFile;
        }
    }

    //-------------------------------------------------------------------------
    //
    // Detect whether some files have changed
    //
    //-------------------------------------------------------------------------

    private void detectChanges() throws IOException {
        for (MonitoredFile recipe : monitoredFiles) {
            try {
                detectChange(recipe);
            } catch (Exception e) {
                log.warn(String.format("Unable to detect change for recipe '%s.%s.%s'.", recipe.recipeMetadata.instance, recipe.recipeMetadata.projectKey, recipe.recipeMetadata.recipeName), e);
            }
        }
    }

    private void detectChange(MonitoredFile task) throws IOException {
        DssRecipeMetadata recipeMetadata = task.recipeMetadata;
        VirtualFile moduleContentRoot = task.moduleContentRoot;
        DssMetadata dssMetadata = task.dssMetadata;
        File physicalFile = task.physicalFile;

        // Check whether recipe has been updated on DSS side
        Recipe dssRecipe = recipeCache.getRecipe(recipeMetadata.instance, recipeMetadata.projectKey, recipeMetadata.recipeName);
        if (dssRecipe == null) {
            throw new IllegalStateException(String.format("Recipe '%s' not longer exists on the remote DSS server.", recipeMetadata.recipeName));
        }
        if (dssRecipe.versionTag.versionNumber != recipeMetadata.contentDssVersion) {
            actions.add(new UpdateLocalRecipeFileAction(moduleContentRoot, dssMetadata, recipeMetadata, physicalFile));
        }
    }

    /**
     * Recipe has been updated on DSS side, update the file.
     */
    private class UpdateLocalRecipeFileAction {
        private final VirtualFile moduleContentRoot;
        private final DssMetadata dssMetadata;
        private final DssRecipeMetadata recipeMetadata;
        private final File file;

        UpdateLocalRecipeFileAction(VirtualFile moduleContentRoot, DssMetadata dssMetadata, DssRecipeMetadata recipeMetadata, File file) {
            this.moduleContentRoot = moduleContentRoot;
            this.dssMetadata = dssMetadata;
            this.recipeMetadata = recipeMetadata;
            this.file = file;
        }

        private void run() throws IOException {
            DSSClient dssClient = getDssClient(dssSettings, recipeMetadata);
            RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(recipeMetadata.projectKey, recipeMetadata.recipeName);
            String recipeContent = recipeAndPayload.payload;
            if (recipeContent == null) {
                recipeContent = "";
            }

            // Write recipe file, but directly to file-system so that IntelliJ can ask users whether they want to upload the content in theirs editors or not.
            Files.write(recipeContent, file, UTF_8);

            // Update metadata & schedule associated metadata file to be updated
            recipeMetadata.contentDssVersion = recipeAndPayload.recipe.versionTag.versionNumber;
            recipeMetadata.contentHash = getContentHash(recipeContent);

            // Update the metadata file (directly on file-system too).
            try {
                VirtualFile file = moduleContentRoot.findFileByRelativePath(".dss/recipes.json");
                if (file != null) {
                    log.info(String.format("Updating DSS metadata file '%s'.", file));
                    DssMetadataManager.writeMetadata(new File(file.getPath()), dssMetadata);
                }
            } catch (IOException e) {
                log.error("Unable to write DSS metadata file", e);
            }
        }

        @Override
        public String toString() {
            return "UpdateLocalRecipeFileAction{" + "recipe=" + recipeMetadata.instance + '.' + recipeMetadata.projectKey + '.' + recipeMetadata.recipeName + '}';
        }
    }

    //-------------------------------------------------------------------------
    //
    // Actually update the local files or remote DSS instance
    //
    //-------------------------------------------------------------------------

    private void performUpdateActions() {
        for (UpdateLocalRecipeFileAction action : actions) {
            try {
                log.debug("Performing update: " + action.toString());
                action.run();
            } catch (IOException e) {
                log.error("Unable to perform synchronization update action", e);
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
