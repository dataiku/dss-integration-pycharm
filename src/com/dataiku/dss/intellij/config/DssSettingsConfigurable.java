package com.dataiku.dss.intellij.config;

import java.awt.*;
import javax.swing.*;

import org.jetbrains.annotations.Nls;

import com.intellij.openapi.options.Configurable;

public class DssSettingsConfigurable implements Configurable, Configurable.NoScroll {
    private final DssSettings settings = (DssSettings) Utils.get(DssSettings.class);
    private JPanel rootPanel;
    private DssSettingsPanel serversPanel;

    public DssSettingsConfigurable() {
    }

    @Nls
    public String getDisplayName() {
        return "Dataiku DSS Settings";
    }

    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        return getPanel();
    }

    public boolean isModified() {
        return serversPanel.isModified(settings);
    }

    public void apply() {
        serversPanel.save(settings);
    }

    public void reset() {
        serversPanel.load(settings);
    }

    public void disposeUIResources() {
        if (rootPanel != null) {
            rootPanel.setVisible(false);
            rootPanel = null;
        }

        if (serversPanel != null) {
            serversPanel.dispose();
            serversPanel = null;
        }
    }

    private JPanel getPanel() {
        if (rootPanel == null) {
            serversPanel = new DssSettingsPanel();
            rootPanel = new JPanel(new BorderLayout());
            rootPanel.add(serversPanel.getComponent(), "Center");
        }
        return rootPanel;
    }
}
