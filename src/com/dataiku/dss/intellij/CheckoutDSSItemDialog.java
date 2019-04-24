package com.dataiku.dss.intellij;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.Recipe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

public class CheckoutDSSItemDialog extends DialogWrapper {
    private static final Logger log = Logger.getInstance(CheckoutDSSItemDialog.class);

    private final Project project;

    private ComboBox<DssInstance> instanceComboBox;
    private ComboBox<DssProject> projectComboBox;
    private ComboBox<DssRecipe> recipeComboBox;
    private ComboBox<String> intellijModulesComboBox;
    private CheckoutDSSItem selectedItem;
    private JPanel dialogPanel;

    public CheckoutDSSItemDialog(Project project) {
        super(true); // use current window as parent
        this.project = project;
        init();
        setTitle("Checkout DSS Recipe or Plugin");
    }

    public CheckoutDSSItem getSelectedItem() {
        return selectedItem;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        dialogPanel = new JPanel(new GridLayoutManager(4, 2));

        dialogPanel.add(new JLabel("DSS Instance"), newConstraints(0, 0, GridConstraints.FILL_NONE));
        instanceComboBox = new ComboBox<>();
        dialogPanel.add(instanceComboBox, newConstraints(0, 1, GridConstraints.FILL_HORIZONTAL));

        dialogPanel.add(new JLabel("Project"), newConstraints(1, 0, GridConstraints.FILL_NONE));
        projectComboBox = new ComboBox<>();
        dialogPanel.add(projectComboBox, newConstraints(1, 1, GridConstraints.FILL_HORIZONTAL));

        dialogPanel.add(new JLabel("Recipe"), newConstraints(2, 0, GridConstraints.FILL_NONE));
        recipeComboBox = new ComboBox<>();
        dialogPanel.add(recipeComboBox, newConstraints(2, 1, GridConstraints.FILL_HORIZONTAL));

        dialogPanel.add(new JLabel("PyCharm Project"), newConstraints(3, 0, GridConstraints.FILL_NONE));
        intellijModulesComboBox = new ComboBox<>();
        dialogPanel.add(intellijModulesComboBox, newConstraints(3, 1, GridConstraints.FILL_HORIZONTAL));

        instanceComboBox.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                try {
                    updateProjects();
                } catch (Exception ex) {
                    Messages.showErrorDialog(dialogPanel, "Can't list projects for selected DSS server.", "Invalid DSS Server");
                }
            }
        });
        projectComboBox.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                try {
                    updateRecipes();
                } catch (Exception ex) {
                    Messages.showErrorDialog(dialogPanel, "Can't list recipes for selected project and DSS server.", "Invalid DSS Server");
                }
            }
        });

        DssSettings.getInstance().getDssServers()
                .forEach(root -> instanceComboBox.addItem(new DssInstance(root)));

        ModuleManager.getInstance(project)
                .getAllModuleDescriptions()
                .forEach(module -> intellijModulesComboBox.addItem(module.getName()));

        try {
            updateProjects();
            updateRecipes();
        } catch (Exception ex) {
            log.error("Unable to load projects/recipes from default DSS server.");
        }
        return dialogPanel;
    }

    @Override
    protected void doOKAction() {
        if (!isOKActionEnabled()) {
            return;
        }
        selectedItem = new CheckoutDSSItem(getSelectedInstance().server, getSelectedRecipe().recipe, getSelectedIntelliJModule());
        super.doOKAction();
    }

    private Module getSelectedIntelliJModule() {
        String moduleName = (String) intellijModulesComboBox.getSelectedItem();
        if (moduleName == null) {
            throw new IllegalStateException("Please create a project");
        }
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) {
            throw new IllegalStateException("Cannot locate module " + moduleName);
        }
        return module;
    }

    private DssProject getSelectedProject() {
        return (DssProject) projectComboBox.getSelectedItem();
    }

    private DssInstance getSelectedInstance() {
        return (DssInstance) instanceComboBox.getSelectedItem();
    }

    private DssRecipe getSelectedRecipe() {
        return (DssRecipe) recipeComboBox.getSelectedItem();
    }

    @Override
    public final void doCancelAction() {
        selectedItem = null;
        super.doCancelAction();
    }

    @NotNull
    private GridConstraints newConstraints(int row, int column, int fill) {
        GridConstraints result = new GridConstraints();
        result.setRow(row);
        result.setColumn(column);
        result.setFill(fill);
        return result;
    }

    private void updateProjects() {
        DssInstance dssInstance = getSelectedInstance();
        projectComboBox.removeAllItems();
        if (dssInstance != null) {
            dssInstance.client.listProjects()
                    .forEach(project -> projectComboBox.addItem(new DssProject(project)));
        }
    }

    private void updateRecipes() {
        DssProject dssProject = getSelectedProject();
        recipeComboBox.removeAllItems();
        if (dssProject != null) {
            getSelectedInstance().client.listRecipes(dssProject.project.projectKey).stream()
                    .filter(recipe -> RecipeUtils.isEditableRecipe(recipe.type))
                    .forEach(recipe -> recipeComboBox.addItem(new DssRecipe(recipe)));
        }
    }

    private static class DssInstance {
        private final DssServer server;
        private final DSSClient client;

        DssInstance(DssServer dssServer) {
            this.server = dssServer;
            this.client = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));
        }

        @Override
        public String toString() {
            return server.name;
        }
    }

    private static class DssProject {
        private final com.dataiku.dss.model.Project project;

        DssProject(com.dataiku.dss.model.Project project) {
            this.project = project;
        }

        @Override
        public String toString() {
            return project.name;
        }
    }

    private static class DssRecipe {
        public final Recipe recipe;

        DssRecipe(Recipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public String toString() {
            return recipe.name;
        }
    }

}

