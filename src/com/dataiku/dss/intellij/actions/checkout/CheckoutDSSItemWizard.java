package com.dataiku.dss.intellij.actions.checkout;

import java.util.ArrayList;
import java.util.List;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.project.Project;

public class CheckoutDSSItemWizard {
    private CheckoutDSSItemModel model;
    private AbstractWizardEx wizard;

    public CheckoutDSSItemWizard(Project project) {
        model = new CheckoutDSSItemModel();
        init(project, model);
    }

    private void init(Project project, CheckoutDSSItemModel model) {
        List<AbstractWizardStepEx> steps = createSteps(project, model);
        wizard = new Wizard("Checkout DSS Item", project, steps);
    }

    private static List<AbstractWizardStepEx> createSteps(Project project, CheckoutDSSItemModel model) {
        List<AbstractWizardStepEx> steps = new ArrayList<>();
        steps.add(new CheckoutStep1(model, project));
        steps.add(new CheckoutStep2Recipe(model));
        steps.add(new CheckoutStep2Plugin(model));
        return steps;
    }

    private static class Wizard extends AbstractWizardEx {
        public Wizard(String title, Project project, List<AbstractWizardStepEx> steps) {
            super(title, project, steps);
            this.setHorizontalStretch(1.25f);
            this.setVerticalStretch(1.25f);
        }

        @Override
        protected void helpAction() {
            BrowserUtil.browse("https://doc.dataiku.com/dss/latest/python-api/outside-usage.html");
        }

        @Override
        protected String getDimensionServiceKey() {
            return this.getClass().getName();
        }
    }

    public boolean showAndGet() {
        return wizard.showAndGet();
    }

    public CheckoutDSSItemModel getModel() {
        return model;
    }
}
