package com.dataiku.dss.intellij.actions.synchronize;

import java.io.IOException;
import javax.swing.event.HyperlinkEvent;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;

public class SynchronizeAction extends AnAction implements DumbAware {

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
        SynchronizeWizard wizard = new SynchronizeWizard(project, MonitoredFilesIndex.getInstance());
        if (wizard.showAndGet()) {
            SynchronizeModel model = wizard.getModel();
            try {
                // Do the work
                new SynchronizeWorker(model).synchronizeWithDSS();

                // Notify when it's done.
                Notification notification = new Notification("Dataiku DSS",
                        "Synchronization with DSS completed",
                        model.summary.getQuickSummary(),
                        model.summary.conflicts.isEmpty() ? NotificationType.INFORMATION : NotificationType.WARNING,
                        new SynchronizationNotificationListener(project, model));
                Notifications.Bus.notify(notification, project);
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

    private static class SynchronizationNotificationListener extends NotificationListener.Adapter {
        private final Project project;
        private final SynchronizeModel model;

        SynchronizationNotificationListener(Project project, SynchronizeModel model) {
            this.project = project;
            this.model = model;
        }

        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            new SynchronizeSummaryDialog(project, model).showAndGet();
        }
    }
}
