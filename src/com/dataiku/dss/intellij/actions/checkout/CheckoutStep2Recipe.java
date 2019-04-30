package com.dataiku.dss.intellij.actions.checkout;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Icons;
import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.RecipeUtils;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.Recipe;
import com.google.common.base.Joiner;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;

public class CheckoutStep2Recipe extends AbstractWizardStepEx {
    static final Object ID = "CheckoutStep2Recipe";
    private static final Logger log = Logger.getInstance(CheckoutStep2Recipe.class);
    private static final RecipeItem NO_RECIPE = new RecipeItem(null);

    private final CheckoutDSSItemModel model;
    private final Map<String, String> sdkInstallationCacheState = new HashMap<>();

    private JPanel panel;
    private JComboBox<ProjectItem> projectComboBox;
    private JList<RecipeItem> recipesList;
    private DefaultListModel<RecipeItem> recipesListItems = new DefaultListModel<>();
    private JTextField locationTextField;
    private JCheckBox runConfigurationCheckBox;
    private JComboBox<SdkItem> sdkComboBox;
    private JButton installClientLibsButton;
    private JLabel installClientLibsWarningLabel;
    private JPanel runConfigurationPanel;
    private JLabel errorLabel;
    private JPanel installClientLibsPanel;
    private JPanel sdkPanel;
    private JLabel sdkLabel;

    CheckoutStep2Recipe(CheckoutDSSItemModel model) {
        super("Recipe");
        this.model = model;

        projectComboBox.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                resetError();
                try {
                    updateRecipes();
                    updateCheckoutLocation();
                } catch (Exception ex) {
                    showError("Can't list recipes for selected project and DSS instance.", ex);
                }
            }
        });
        recipesList.setModel(recipesListItems);
        recipesList.setCellRenderer(new RecipeListCellRenderer());
        recipesList.addListSelectionListener(e -> updateRunConfiguration());
        runConfigurationCheckBox.addActionListener(e -> {
            boolean selected = runConfigurationCheckBox.isSelected();
            installClientLibsPanel.setEnabled(selected);
            sdkLabel.setEnabled(selected);
            sdkComboBox.setEnabled(selected);
            installClientLibsButton.setEnabled(selected);
            installClientLibsWarningLabel.setEnabled(selected);
        });
        sdkComboBox.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                if (runConfigurationPanel.isVisible()) {
                    updateInstallClientSetting();
                }
            }
        });
        installClientLibsButton.addActionListener(e -> {
            SdkItem selectedSdkItem = (SdkItem) sdkComboBox.getSelectedItem();
            if (selectedSdkItem != null) {
                if (new DataikuInternalClientLibsInstallerDialog(selectedSdkItem.sdk, model.server).showAndGet()) {
                    sdkInstallationCacheState.remove(selectedSdkItem.sdk.getName());
                    updateInstallClientSetting();
                }
            }
        });
    }

    private void updateRecipes() throws DssException {
        ProjectItem projectItem = (ProjectItem) projectComboBox.getSelectedItem();
        recipesListItems.removeAllElements();
        if (projectItem != null) {
            model.serverClient.listRecipes(projectItem.projectKey).stream()
                    .filter(recipe -> RecipeUtils.isEditableRecipe(recipe.type))
                    .forEach(recipe -> recipesListItems.addElement(new RecipeItem(recipe)));
        }
        if (recipesListItems.size() == 0) {
            recipesListItems.addElement(NO_RECIPE);
            recipesList.setEnabled(false);
        } else {
            recipesList.setEnabled(true);
            recipesList.setSelectedIndex(0);
        }
    }

    @Override
    public void _init() {
        super._init();
        resetError();
        try {
            init();
        } catch (DssException e) {
            showError("Unable to retrieve projects from DSS instance (more details in tooltip).", e);
        }
    }

    private void resetError() {
        errorLabel.setVisible(false);
    }

    private void showError(String msg, Exception e) {
        errorLabel.setVisible(true);
        errorLabel.setText(msg);
        errorLabel.setToolTipText(e.getMessage());
    }

    @NotNull
    @Override
    public Object getStepId() {
        return ID;
    }

    @Nullable
    @Override
    public Object getNextStepId() {
        return null;
    }

    @Nullable
    @Override
    public Object getPreviousStepId() {
        return CheckoutStep1.ID;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void commit(CommitType commitType) throws CommitStepException {
        if (commitType == CommitType.Prev) {
            return; // Ignore everything.
        }

        // Project
        ProjectItem selectedProject = (ProjectItem) projectComboBox.getSelectedItem();
        if (selectedProject == null) {
            throw new CommitStepException("Please select a project.");
        }
        model.projectKey = selectedProject.projectKey;

        // Recipes
        model.recipes = recipesList.getSelectedValuesList().stream().map(item -> item.recipe).collect(Collectors.toList());
        if (model.recipes.isEmpty()) {
            throw new CommitStepException("Please select at least one recipe");
        }

        // Checkout location
        String checkoutLocation = locationTextField.getText().trim();
        if (!checkoutLocation.isEmpty()) {
            List<String> directories = new ArrayList<>();
            for (String directory : checkoutLocation.trim().split(SystemInfo.isWindows ? "[/|\\\\]" : "/")) {
                if (directory.isEmpty()) {
                    throw new CommitStepException("Invalid checkout location. It must not contains empty directories.");
                }
                if (directory.equals(".") || directory.equals("..")) {
                    throw new CommitStepException("Invalid checkout location. It must not contains '.' or '..' in paths.");
                }
                directories.add(directory.trim());
            }
            model.checkoutLocation = Joiner.on("/").join(directories);
        } else {
            model.checkoutLocation = "";
        }

        // Run Configuration
        boolean generateRunConfigurations = runConfigurationCheckBox.isSelected();
        model.generateRunConfigurations = generateRunConfigurations;
        if (generateRunConfigurations) {
            SdkItem selectedSdkItem = (SdkItem) sdkComboBox.getSelectedItem();
            if (selectedSdkItem == null) {
                throw new CommitStepException("Please select at least one python SDK");
            }
            model.runConfigurationsPythonSdk = selectedSdkItem.sdk;
        }
    }

    private void init() throws DssException {
        // Fill projects model
        DssServer dssServer = model.server;
        projectComboBox.removeAllItems();
        if (dssServer != null) {
            HashSet<String> labels = new HashSet<>();
            for (com.dataiku.dss.model.dss.Project p : model.serverClient.listProjects()) {
                // If two projects have the same name, use the projectKey in label to distinguish them
                String label = p.name;
                if (labels.contains(label)) {
                    label = label + " (" + p.projectKey + ")";
                }
                labels.add(label);
                projectComboBox.addItem(new ProjectItem(p.projectKey, label, dssServer));
            }
        }
        // Fill SDKs model
        sdkComboBox.removeAllItems();
        List<Sdk> pythonSdks = getAllPythonSdks();
        Sdk modulePythonSdk = findPythonSdk(model.module);
        if (!pythonSdks.contains(modulePythonSdk)) {
            pythonSdks.add(modulePythonSdk);
        }
        for (Sdk pythonSdk : pythonSdks) {
            sdkComboBox.addItem(new SdkItem(pythonSdk));
        }
        sdkComboBox.setSelectedIndex(pythonSdks.indexOf(modulePythonSdk));

        updateRecipes();
        updateCheckoutLocation();
        updateRunConfiguration();
        updateInstallClientSetting();
    }

    private void updateRunConfiguration() {
        boolean hasPythonRecipe = recipesList.getSelectedValuesList().stream().anyMatch(item -> "python".equals(item.recipe.type));
        runConfigurationPanel.setVisible(hasPythonRecipe);
        runConfigurationCheckBox.setSelected(hasPythonRecipe);
        sdkPanel.setEnabled(hasPythonRecipe);
        installClientLibsPanel.setEnabled(hasPythonRecipe);
        if (hasPythonRecipe) {
            updateInstallClientSetting();
        }
    }

    private void updateCheckoutLocation() {
        ProjectItem projectItem = (ProjectItem) projectComboBox.getSelectedItem();
        if (projectItem != null) {
            locationTextField.setText(projectItem.dssServer.name + "/" + projectItem.projectKey);
        }
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return projectComboBox;
    }

    @SuppressWarnings("WeakerAccess")
    private class ProjectItem {
        public final String projectKey;
        public final String label;
        public final DssServer dssServer;

        public ProjectItem(String projectKey, String label, DssServer dssServer) {
            this.projectKey = projectKey;
            this.label = label;
            this.dssServer = dssServer;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class RecipeItem {
        public final Recipe recipe;

        RecipeItem(Recipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public String toString() {
            return recipe != null ? recipe.name : "";
        }
    }

    class RecipeListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean expanded) {
            RecipeItem item = (RecipeItem) value;
            JLabel result = (JLabel) super.getListCellRendererComponent(list, value, index, selected, expanded);
            if (item == NO_RECIPE) {
                result.setText("No recipe found in this project");
                result.setFont(result.getFont().deriveFont(Font.ITALIC));
                result.setEnabled(false);
            } else {
                result.setIcon(RecipeUtils.icon(item.recipe.type));
            }
            return result;
        }
    }

    private void updateInstallClientSetting() {
        SdkItem selectedItem = (SdkItem) sdkComboBox.getSelectedItem();
        if (selectedItem != null) {
            String installedVersion = isClientLibraryInstalled(selectedItem.sdk);
            switch (installedVersion) {
            case "__NOT_INSTALLED__":
                installClientLibsPanel.setVisible(true);
                installClientLibsWarningLabel.setText("Dataiku Client library is not installed in the selected SDK.");
                installClientLibsWarningLabel.setIcon(Icons.WARNING_16);
                installClientLibsWarningLabel.setVisible(true);
                installClientLibsButton.setVisible(true);
                installClientLibsButton.setText("Install");
                break;
            case "__UNKNOWN__":
                installClientLibsPanel.setVisible(true);
                installClientLibsWarningLabel.setText("Dataiku Client library might not be installed in the selected SDK.");
                installClientLibsWarningLabel.setIcon(Icons.WARNING_16);
                installClientLibsWarningLabel.setVisible(true);
                installClientLibsButton.setVisible(false);
                break;
            default:
                installClientLibsPanel.setVisible(true);
                installClientLibsWarningLabel.setText("Dataiku Client library " + installedVersion + " is installed in the selected SDK.");
                installClientLibsWarningLabel.setIcon(Icons.INFO_16);
                installClientLibsWarningLabel.setVisible(true);
                installClientLibsButton.setVisible(false);
                break;
            }
        } else {
            // No SDK selected.
            installClientLibsPanel.setVisible(false);
        }
    }

    private static java.util.List<Sdk> getAllPythonSdks() {
        java.util.List<Sdk> result = new ArrayList<>();
        SdkType pythonSdkType = Arrays.stream(SdkType.getAllTypes()).filter(sdk -> sdk.getName().equals("Python SDK")).findFirst().orElse(null);
        if (pythonSdkType != null) {
            result.addAll(ProjectJdkTable.getInstance().getSdksOfType(pythonSdkType));
        }
        return result;
    }

    private String isClientLibraryInstalled(Sdk pythonSdk) {
        String installationState = sdkInstallationCacheState.get(pythonSdk.getName());
        if (installationState == null) {
            installationState = isClientLibraryInstalledNonCached(pythonSdk);
            sdkInstallationCacheState.put(pythonSdk.getName(), installationState);
        }
        return installationState;
    }

    private String isClientLibraryInstalledNonCached(Sdk pythonSdk) {
        try {
            String version = new DataikuInternalClientInstaller().getInstalledVersion(pythonSdk.getHomePath());
            return version == null ? "__NOT_INSTALLED__" : version;
        } catch (RuntimeException e) {
            log.warn("Unable to determine if Dataiku Client library is installed in Python SDK of selected module.", e);
            return "__UNKNOWN__";
        }
    }

    @Nullable
    private static Sdk findPythonSdk(@Nullable Module module) {
        if (module == null) {
            return null;
        }
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType().getClass().getName().equals("com.jetbrains.python.sdk.PythonSdkType")) {
            return sdk;
        }
        Facet[] facets = FacetManager.getInstance(module).getAllFacets();
        for (Facet facet : facets) {
            FacetConfiguration configuration = facet.getConfiguration();
            if (configuration.getClass().getName().equals("com.jetbrains.python.facet.PythonFacetSettings")) {
                return getSdk(configuration);
            }
        }
        return null;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static Sdk getSdk(FacetConfiguration configuration) {
        try {
            return (Sdk) configuration.getClass().getMethod("getSdk").invoke(configuration);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Unable to retrieve the Python interpreter path for the new Run Configuration", e);
            return null;
        }
    }

    private static class SdkItem {
        Sdk sdk;

        SdkItem(Sdk sdk) {
            this.sdk = sdk;
        }

        @Override
        public String toString() {
            String name = sdk.getName();
            if (name.length() > 60) {
                name = name.substring(0, 57) + "...";
            }
            return name;
        }
    }
}
