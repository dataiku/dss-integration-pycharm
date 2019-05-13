package com.dataiku.dss.intellij.actions.synchronize;

import static com.dataiku.dss.intellij.SynchronizeUtils.notifySynchronizationComplete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.DataikuDSSPlugin;
import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.MonitoredRecipeFile;
import com.dataiku.dss.intellij.RecipeCache;
import com.dataiku.dss.intellij.SynchronizeRequest;
import com.dataiku.dss.intellij.SynchronizeSummary;
import com.dataiku.dss.intellij.SynchronizeWorker;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeDssInstance;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugin;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugins;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipe;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipeProject;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipes;
import com.dataiku.dss.intellij.config.DssSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;

public class SynchronizeAction extends AnAction implements DumbAware {
    private static final Logger log = Logger.getInstance(SynchronizeAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            String msg = "Cannot synchronize DSS recipes or plugins outside a project. Create or open a project and try again.";
            log.error(msg);
            Messages.showErrorDialog(msg, "No Active Project");
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> prepareAndShowWizard(project));
    }

    private void prepareAndShowWizard(Project project) {
        SynchronizeDialog dialog = new SynchronizeDialog(project, MonitoredFilesIndex.getInstance());
        if (dialog.showAndGet()) {
            SynchronizeModel model = dialog.getModel();
            try {
                // Do the work
                DssSettings dssSettings = DssSettings.getInstance();
                SynchronizeWorker synchronizeWorker = new SynchronizeWorker(DataikuDSSPlugin.getInstance(), dssSettings, new RecipeCache(dssSettings), false);
                SynchronizeSummary summary = synchronizeWorker.synchronizeWithDSS(buildRequest(model));

                // Notify when it's done.
                notifySynchronizationComplete(summary, project);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                Messages.showErrorDialog(e.getMessage(), "I/O Error");
            } catch (RuntimeException e) {
                log.error(e.getMessage(), e);
                Messages.showErrorDialog(e.getMessage(), "Error");
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
            e.getPresentation().setIcon(AllIcons.Actions.Menu_saveall);
        }
    }

    @NotNull
    private SynchronizeRequest buildRequest(SynchronizeModel model) {
        List<MonitoredRecipeFile> recipeFiles = new ArrayList<>();
        List<MonitoredPlugin> plugins = new ArrayList<>();
        for (SynchronizeNodeDssInstance instanceNode : model.selectionRootNode.getInstanceNodes()) {
            SynchronizeNodeRecipes recipesNode = instanceNode.getRecipesNode();
            if (recipesNode != null) {
                for (SynchronizeNodeRecipeProject projectNode : recipesNode.getProjectNodes()) {
                    for (SynchronizeNodeRecipe recipeNode : projectNode.getRecipeNodes()) {
                        if (recipeNode.isSelected()) {
                            recipeFiles.add(recipeNode.recipe);
                        }
                    }
                }
            }

            SynchronizeNodePlugins pluginsNode = instanceNode.getPluginsNode();
            if (pluginsNode != null) {
                for (SynchronizeNodePlugin pluginNode : pluginsNode.getPluginNodes()) {
                    if (pluginNode.isSelected()) {
                        plugins.add(pluginNode.monitoredPlugin);
                    }
                }
            }
        }
        return new SynchronizeRequest(recipeFiles, plugins);
    }
}
