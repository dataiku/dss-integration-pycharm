package com.dataiku.dss.intellij.actions.checkout;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Icons;
import com.dataiku.dss.Logger;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.Plugin;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.ui.Messages;

public class CheckoutStep2Plugin extends AbstractWizardStepEx {
    static final Object ID = "CheckoutStep2Plugin";
    private static final Logger log = Logger.getInstance(CheckoutStep2Plugin.class);
    private static final PluginItem NO_PLUGIN = new PluginItem(null);

    private final CheckoutModel model;

    private JPanel panel;
    private JList<PluginItem> pluginList;
    private DefaultListModel<PluginItem> pluginListItems = new DefaultListModel<>();

    CheckoutStep2Plugin(CheckoutModel model) {
        super("Plugin");
        this.model = model;

        pluginList.setModel(pluginListItems);
        pluginList.setCellRenderer(new PluginListCellRenderer());
    }

    private void updatePlugins() throws DssException {
        model.serverClient.listPlugins();
        List<Plugin> plugins = model.serverClient.listPluginsInDevelopment();
        pluginListItems.removeAllElements();
        if (plugins.size() == 0) {
            pluginListItems.addElement(NO_PLUGIN);
            pluginList.setEnabled(false);
        } else {
            plugins.forEach(plugin -> pluginListItems.addElement(new PluginItem(plugin)));
            pluginList.setEnabled(true);
            pluginList.setSelectedIndex(0);
        }
    }

    @Override
    public void _init() {
        super._init();
        try {
            init();
        } catch (IOException e) {
            Messages.showErrorDialog(e.getMessage(), "Error");
        }
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
        // Recipes
        model.plugins = pluginList.getSelectedValuesList().stream().map(item -> item.plugin).collect(Collectors.toList());
        if (model.plugins.isEmpty()) {
            throw new CommitStepException("Please select at least one plugin");
        }
    }

    private void init() throws DssException {
        // Fill plugins
        updatePlugins();
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return pluginList;
    }

    private static class PluginItem {
        final Plugin plugin;

        PluginItem(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String toString() {
            return plugin != null ? plugin.id : "";
        }
    }

    class PluginListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean expanded) {
            PluginItem item = (PluginItem) value;
            JLabel result = (JLabel) super.getListCellRendererComponent(list, value, index, selected, expanded);
            if (item == NO_PLUGIN) {
                result.setText("No plugin in development found on this Dataiku DSS instance");
                result.setFont(list.getFont().deriveFont(Font.ITALIC));
                result.setEnabled(false);
            } else {
                boolean darkBackground = getLuma(result.getBackground()) < 112.0d;
                result.setIcon(darkBackground ? Icons.PLUGIN_WHITE : Icons.PLUGIN);
            }
            return result;
        }

        @NotNull
        @Override
        public Dimension getPreferredSize() {
            Dimension preferredSize = super.getPreferredSize();
            return new Dimension(preferredSize.width, Math.max(preferredSize.height, 24));
        }

        private double getLuma(Color c) {
            return 0.2126d * c.getRed() + 0.7152d * c.getGreen() + 0.0722d * c.getBlue();
        }
    }
}
