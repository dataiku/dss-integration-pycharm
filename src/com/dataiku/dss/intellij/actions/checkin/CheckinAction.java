package com.dataiku.dss.intellij.actions.checkin;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;

public class CheckinAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("Cannot synchronize DSS recipes or plugins outside a project. Create or open a project and try again.", "No Active Project");
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> prepareAndShowWizard(project));
    }

    private void prepareAndShowWizard(Project project) {
        CheckinWizard wizard = new CheckinWizard(project, MonitoredFilesIndex.getInstance());
        if (wizard.showAndGet()) {
            CheckinModel model = wizard.getModel();
            try {
                new CheckinWorker(model).checkin();
            } catch (IOException e) {
                Messages.showErrorDialog(e.getMessage(), "I/O Error");
            } catch (RuntimeException e) {
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
}
