package com.dataiku.dss.intellij.actions.checkout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

public class CheckoutStep1 extends AbstractWizardStepEx {
    public static final Object ID = "CheckoutStep1";

    private final CheckoutModel model;
    private final Project project;
    private JComboBox<String> intellijModulesComboBox;
    private JComboBox<InstanceItem> instanceComboBox;
    private JComboBox<String> itemTypeComboBox;
    private JPanel panel;

    public CheckoutStep1(CheckoutModel model, Project project) {
        super("Recipe or Plugin");
        this.model = model;
        this.project = project;
        init();
    }

    public void init() {
        listModules().forEach(module -> intellijModulesComboBox.addItem(module.getName()));

        // Display all instances, with the default one first.
        List<DssInstance> dssInstances = new ArrayList<>(DssSettings.getInstance().getDssInstances());
        dssInstances.sort((instance1, instance2) -> instance1.isDefault ? -1 : (instance2.isDefault ? 1 : 0));
        dssInstances.forEach(instance -> instanceComboBox.addItem(new InstanceItem(instance)));

        instanceComboBox.setRenderer(new ColoredListCellRenderer<InstanceItem>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends InstanceItem> list, InstanceItem item, int index, boolean selected, boolean hasFocus) {
                append(item.instance.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append("  " + item.instance.baseUrl, SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
        });
    }

    @NotNull
    @Override
    public Object getStepId() {
        return ID;
    }

    @Nullable
    @Override
    public Object getNextStepId() {
        return "Plugin".equals(itemTypeComboBox.getSelectedItem()) ?
                CheckoutStep2Plugin.ID :
                CheckoutStep2Recipe.ID;
    }

    @Nullable
    @Override
    public Object getPreviousStepId() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void commit(CommitType commitType) throws CommitStepException {
        model.module = validateModule((String) intellijModulesComboBox.getSelectedItem());

        InstanceItem instanceItem = (InstanceItem) instanceComboBox.getSelectedItem();
        if (instanceItem == null) {
            throw new CommitStepException("Please select a DSS instance");
        }
        model.server = validateDssInstance(instanceItem);
        model.serverClient = instanceItem.client;
        model.serverVersion = instanceItem.client.getDssVersion();

        model.itemType = validateItemType((String) itemTypeComboBox.getSelectedItem());
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return itemTypeComboBox;
    }

    @NotNull
    private Module validateModule(String moduleName) throws CommitStepException {
        if (moduleName == null) {
            throw new CommitStepException("Please create a project");
        }
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) {
            throw new CommitStepException("Cannot locate module " + moduleName);
        }
        return module;
    }

    private DssInstance validateDssInstance(InstanceItem instanceItem) throws CommitStepException {
        if (!instanceItem.client.canConnect()) {
            throw new CommitStepException("Unable to connect to the selected DSS instance. Make sure it is running and reachable from your computer.");
        }
        return instanceItem.instance;
    }

    private CheckoutModel.ItemType validateItemType(String itemType) throws CommitStepException {
        switch (itemType) {
        case "Recipe":
            return CheckoutModel.ItemType.RECIPE;
        case "Plugin":
            return CheckoutModel.ItemType.PLUGIN;
        default:
            throw new CommitStepException("Unexpected item type: " + itemType);
        }
    }

    @NotNull
    private List<Module> listModules() {
        List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getSortedModules());
        Collections.reverse(modules);
        return modules;
    }

    private static class InstanceItem {
        private final DssInstance instance;
        private final DSSClient client;

        InstanceItem(DssInstance dssInstance) {
            this.instance = dssInstance;
            this.client = dssInstance.createClient();
        }
    }
}

