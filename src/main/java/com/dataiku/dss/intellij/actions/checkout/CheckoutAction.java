package com.dataiku.dss.intellij.actions.checkout;

import static com.intellij.openapi.util.SystemInfo.isMac;

import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.DataikuDSSPlugin;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.wt1.WT1;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;

public class CheckoutAction extends AnAction implements DumbAware {
    private static final Logger log = Logger.getInstance(CheckoutAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("Cannot checkout DSS item outside a project", "No Active Project");
            return;
        }

        DssSettings dssSettings = DssSettings.getInstance();
        if (dssSettings.getDssInstances().isEmpty()) {
            String preferencesMenu = isMac ? "Preferences" : "File > Settings";
            String msg = String.format("No DSS instance defined.\nGo to %s > Dataiku DSS Settings, and configure a DSS instance.", preferencesMenu);

            log.error(msg);
            Messages.showErrorDialog(msg, "Configuration Error");
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> prepareAndShowWizard(project, dssSettings));
    }

    private void prepareAndShowWizard(Project project, DssSettings dssSettings) {
        CheckoutWizard wizard = new CheckoutWizard(project);
        if (wizard.showAndGet()) {
            CheckoutModel model = wizard.getModel();
            try {
                CheckoutWorker worker = new CheckoutWorker(dssSettings, DataikuDSSPlugin.getInstance(), WT1.getInstance(), model);
                List<VirtualFile> files = worker.checkout();
                for (VirtualFile file : files) {
                    PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true);
                }
            } catch (IOException e) {
                Messages.showErrorDialog(e.getMessage(), "I/O Error");
            } catch (IllegalStateException e) {
                Messages.showErrorDialog(e.getMessage(), "Error");
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
            e.getPresentation().setIcon(AllIcons.Actions.Menu_open);
        }
    }
}
