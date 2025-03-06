package com.dataiku.dss.intellij.actions.synchronize;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.*;
import com.dataiku.dss.intellij.actions.merge.ResolveConflictsDialog;
import com.dataiku.dss.intellij.actions.synchronize.nodes.*;
import com.dataiku.dss.intellij.config.DssSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.SystemInfo.isMac;

public class SynchronizeAction extends AnAction implements DumbAware {
    private static final Logger log = Logger.getInstance(SynchronizeAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            String msg = "Cannot synchronize DSS recipes or plugins outside a project.\nCreate or open a project and try again.";
            log.error(msg);
            Messages.showErrorDialog(msg, "No Active Project");
            return;
        }

        DssSettings dssSettings = DssSettings.getInstance();
        if (dssSettings.getDssInstances().isEmpty()) {
            String preferencesMenu = isMac ? "Preferences" : "File > Settings";
            String msg = String.format("No DSS instance defined.\nGo to %s > Dataiku DSS Settings, and configure a DSS instance.", preferencesMenu);

            log.error(msg);
            Messages.showErrorDialog(msg, "Configuration Error");
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

                if (summary.hasConflicts()) {
                    ResolveConflictsDialog resolveConflictsDialog = new ResolveConflictsDialog(project, summary);
                    resolveConflictsDialog.showAndGet();
                }

                // Notify when it's done.
                SynchronizationNotifier.getInstance().notifySuccess(summary, project);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                Messages.showErrorDialog(e.getMessage(), "I/O Error");
            } catch (RuntimeException e) {
                log.error(e.getMessage(), e);
                Messages.showErrorDialog(e.getMessage(), "Error");
            }
        }
    }

    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
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
        List<MonitoredLibrary> libraries = new ArrayList<>();

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

            SynchronizeNodeLibraries librariesNode = instanceNode.getLibrariesNode();
            if (librariesNode != null) {
                for (SynchronizeNodeLibrary libraryNode : librariesNode.getLibraryNodes()) {
                    if (libraryNode.isSelected()) {
                        libraries.add(libraryNode.monitoredLibrary);
                    }
                }
            }

        }
        return new SynchronizeRequest(recipeFiles, plugins, libraries);
    }
}
