package com.dataiku.dss.intellij.actions.checkout;

import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;

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

public class CheckoutDSSItemAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("Cannot checkout DSS item outside a project", "No Active Project");
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> prepareFileChooserAndOpen(project));
    }

    private void prepareFileChooserAndOpen(Project project) {
        CheckoutDSSItemWizard wizard = new CheckoutDSSItemWizard(project);
        if (wizard.showAndGet()) {
            CheckoutDSSItemModel model = wizard.getModel();
            try {
                List<VirtualFile> files = new CheckoutWorker(model).checkout();
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
