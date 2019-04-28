package com.dataiku.dss.intellij.actions.checkout;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.DSSClient;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.PasswordUtil;

public class CheckoutStep1 extends AbstractWizardStepEx {
    public static final Object ID = "CheckoutStep1";

    private final CheckoutDSSItemModel model;
    private final Project project;
    private JComboBox<String> intellijModulesComboBox;
    private JComboBox<ServerItem> instanceComboBox;
    private JComboBox<String> itemTypeComboBox;
    private JPanel panel;

    public CheckoutStep1(CheckoutDSSItemModel model, Project project) {
        super("Checkout DSS Recipe/Plugin");
        this.model = model;
        this.project = project;
        init();
    }

    public void init() {
        ModuleManager.getInstance(project)
                .getAllModuleDescriptions()
                .forEach(module -> intellijModulesComboBox.addItem(module.getName()));

        DssSettings.getInstance().getDssServers()
                .forEach(root -> instanceComboBox.addItem(new ServerItem(root)));
    }

    @NotNull
    @Override
    public Object getStepId() {
        return ID;
    }

    @Nullable
    @Override
    public Object getNextStepId() {
        return CheckoutStep2Recipe.ID;
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

        ServerItem serverItem = (ServerItem) instanceComboBox.getSelectedItem();
        if (serverItem == null) {
            throw new CommitStepException("Please select a server");
        }
        model.server = validateDssServer(serverItem);
        model.serverClient = serverItem.client;
        model.serverVersion = serverItem.client.getDssVersion();

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

    private DssServer validateDssServer(ServerItem serverItem) throws CommitStepException {
        if (!serverItem.client.canConnect()) {
            throw new CommitStepException("Unable to connect to the selected DSS server. Make sure it is running and reachable from your computer.");
        }
        return serverItem.server;
    }

    private CheckoutDSSItemModel.ItemType validateItemType(String itemType) throws CommitStepException {
        switch (itemType) {
        case "Recipe":
            return CheckoutDSSItemModel.ItemType.RECIPE;
        case "Plugin":
            return CheckoutDSSItemModel.ItemType.PLUGIN;
        default:
            throw new CommitStepException("Unexpected item type: " + itemType);
        }
    }

    private static class ServerItem {
        private final DssServer server;
        private final DSSClient client;

        ServerItem(DssServer dssServer) {
            this.server = dssServer;
            this.client = new DSSClient(dssServer.baseUrl, PasswordUtil.decodePassword(dssServer.encryptedApiKey));
        }

        @Override
        public String toString() {
            return server.name;
        }
    }
}

