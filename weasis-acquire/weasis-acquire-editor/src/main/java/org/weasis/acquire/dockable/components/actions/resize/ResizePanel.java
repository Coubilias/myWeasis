package org.weasis.acquire.dockable.components.actions.resize;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;

public class ResizePanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 3176116747953034000L;

    public ResizePanel() {
        super();
        
        int width = 100;
        int height = 150;

        SpinnerModel spinnerModelWidth = new SpinnerNumberModel(width, 0, width, 1);
        SpinnerModel spinnerModelHeight = new SpinnerNumberModel(height, 0, height, 1);

        JSpinner spinnerWidth = new JSpinner(spinnerModelWidth);
        JSpinner spinnerHeight = new JSpinner(spinnerModelHeight);

        add(new JLabel("width: "));
        add(spinnerWidth);

        add(new JLabel("height: "));
        add(spinnerHeight);
    }
}
