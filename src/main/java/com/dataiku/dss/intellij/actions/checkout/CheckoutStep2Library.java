package com.dataiku.dss.intellij.actions.checkout;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.model.dss.DssException;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;

public class CheckoutStep2Library extends AbstractWizardStepEx {

    static final Object ID = "CheckoutStep2Library";
    private static final Logger log = Logger.getInstance(CheckoutStep2Library.class);

    private final CheckoutModel model;

    private JPanel panel;
    private JComboBox<ProjectItem> projectComboBox;

    CheckoutStep2Library(CheckoutModel model) {
        super("Library");
        this.model = model;

    }

    private class ProjectItem {
        public final String projectKey;
        public final String label;
        public final DssInstance dssInstance;

        public ProjectItem(String projectKey, String label, DssInstance dssInstance) {
            this.projectKey = projectKey;
            this.label = label;
            this.dssInstance = dssInstance;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Override
    public void _init() {
        super._init();
        try {
            init();
        } catch (DssException e) {
            log.error("Unable to retrieve projects from DSS instance", e);
            Messages.showErrorDialog("Unable to retrieve projects from DSS instance", "Error");
        }
    }

    private void init() throws DssException {
        // Fill projects model
        DssInstance dssInstance = model.server;
        projectComboBox.removeAllItems();
        if (dssInstance != null) {
            HashSet<String> labels = new HashSet<>();
            for (com.dataiku.dss.model.dss.Project p : model.serverClient.listProjects()) {
                // If two projects have the same name, use the projectKey in label to distinguish them
                String label = p.name;
                if (labels.contains(label)) {
                    label = label + " (" + p.projectKey + ")";
                }
                labels.add(label);
                projectComboBox.addItem(new ProjectItem(p.projectKey, label, dssInstance));
            }
        }
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
        model.libraryProjectKey = selectedProject.projectKey;

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

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return projectComboBox;
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

}
