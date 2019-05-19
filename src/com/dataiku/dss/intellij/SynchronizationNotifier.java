package com.dataiku.dss.intellij;

import javax.swing.event.HyperlinkEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.actions.merge.ResolveConflictsDialog;
import com.dataiku.dss.intellij.actions.synchronize.SynchronizeSummaryDialog;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class SynchronizationNotifier implements ApplicationComponent {
    public Notification activeNotification;

    public static SynchronizationNotifier getInstance() {
        return ComponentUtils.getComponent(SynchronizationNotifier.class);
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "DSSSynchronizationNotifier";
    }

    public void notifySuccess(SynchronizeSummary summary, @Nullable Project project) {
        disableActiveNotification();
        boolean hasConflicts = summary.hasConflicts();
        activeNotification = new Notification("Dataiku DSS",
                "Synchronization with DSS completed",
                summary.getQuickSummary(),
                hasConflicts ? NotificationType.WARNING : NotificationType.INFORMATION,
                new SynchronizationNotificationListener(project, summary));
        activeNotification.setImportant(hasConflicts);
        Notifications.Bus.notify(activeNotification, project);
    }

    public void notifyFailure(Exception e, Project project) {
        disableActiveNotification();
        activeNotification = new Notification("Dataiku DSS",
                "Synchronization with DSS failed",
                e.getMessage(),
                NotificationType.ERROR);
        Notifications.Bus.notify(activeNotification, project);
    }

    private void disableActiveNotification() {
        if (activeNotification != null) {
            activeNotification.expire();
            activeNotification = null;
        }
    }

    private class SynchronizationNotificationListener extends NotificationListener.Adapter {
        private final Project project;
        private final SynchronizeSummary summary;

        SynchronizationNotificationListener(@Nullable Project project, SynchronizeSummary summary) {
            this.project = project;
            this.summary = summary;
        }

        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            switch (event.getDescription()) {
            case "viewSummary":
                new SynchronizeSummaryDialog(project, summary).showAndGet();
                break;
            case "resolveConflicts":
                if (summary.hasConflicts()) {
                    new ResolveConflictsDialog(project, summary).showAndGet();
                } else {
                    Messages.showDialog("All conflicts have been resolved.", "Dataiku DSS", new String[]{Messages.OK_BUTTON}, 0, null);
                }
                break;
            }
        }
    }
}
