package com.dataiku.dss.intellij.actions.checkout;

import java.util.ArrayList;
import java.util.List;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.project.Project;

public class CheckoutWizard {
    private CheckoutModel model;
    private AbstractWizardEx wizard;

    public CheckoutWizard(Project project) {
        model = new CheckoutModel();
        init(project, model);
    }

    private void init(Project project, CheckoutModel model) {
        List<AbstractWizardStepEx> steps = createSteps(project, model);
        wizard = new Wizard("Open Dataiku DSS", project, steps);
    }

    private static List<AbstractWizardStepEx> createSteps(Project project, CheckoutModel model) {
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

    public CheckoutModel getModel() {
        return model;
    }
}
