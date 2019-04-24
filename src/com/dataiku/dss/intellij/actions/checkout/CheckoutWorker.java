package com.dataiku.dss.intellij.actions.checkout;

import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualFile;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.MetadataFile;
import com.dataiku.dss.intellij.MetadataFilesIndex;
import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.RecipeUtils;
import com.dataiku.dss.intellij.VirtualFileUtils;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class CheckoutWorker {

    public VirtualFile checkout(CheckoutDSSItem item) throws IOException {
        Preconditions.checkNotNull(item, "item");

        // Retrieve project key
        String projectKey = item.recipe.projectKey;

        // Retrieve recipe & its payload
        DssServer dssServer = item.server;
        DSSClient dssClient = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));

        RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(projectKey, item.recipe.name);
        String recipeContent = recipeAndPayload.payload;
        if (recipeContent == null) {
            recipeContent = "";
        }

        // Write recipe file
        VirtualFile moduleRootFolder = getModuleRootFolder(ModuleRootManager.getInstance(item.module));
        String serverName = item.server.name;
        String filename = getFilename(item.recipe);
        Object requestor = this;
        VirtualFile result = getOrCreateVirtualFile(requestor, moduleRootFolder, serverName, projectKey, filename);
        VirtualFileUtils.writeToFile(result, recipeContent);

        // Write metadata
        MetadataFile metadata = MetadataFilesIndex.getInstance().getOrCreateMetadata(moduleRootFolder);

        DssRecipeMetadata recipeMetadata = new DssRecipeMetadata();
        recipeMetadata.path = serverName + "/" + projectKey + "/" + filename;
        recipeMetadata.versionNumber = recipeAndPayload.recipe.versionTag.versionNumber;
        recipeMetadata.contentHash = getContentHash(recipeContent);
        recipeMetadata.projectKey = projectKey;
        recipeMetadata.recipeName = item.recipe.name;
        recipeMetadata.instance = serverName;

        metadata.addOrUpdateRecipe(recipeMetadata);

        // Monitor the file so that if the underlying recipe is edited on DSS side, the file is updated and vice-versa.
        MonitoredFilesIndex.getInstance().index(result, metadata, recipeMetadata);

        return result;
    }

    private static String getFilename(Recipe recipe) {
        return recipe.name + RecipeUtils.extension(recipe.type);
    }

    @NotNull
    private static VirtualFile getModuleRootFolder(ModuleRootManager module) {
        VirtualFile[] contentRoots = module.getContentRoots();
        VirtualFile contentRoot = contentRoots.length == 0 ? null : contentRoots[0];
        if (contentRoot == null) {
            throw new IllegalStateException(String.format("No source root directory defined for module %s. Update its configuration to fix the problem.", module.getModule().getName()));
        }
        return contentRoot;
    }

}
