/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.base.ui.action;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.AbstractUIAction;
import org.weasis.core.ui.util.ColorLayerUI;

public class OpenPreferencesAction extends AbstractUIAction {

    /** The singleton instance of this singleton class. */
    private static OpenPreferencesAction openAction = null;

    /** Return the singleton instance */
    public static OpenPreferencesAction getInstance() {
        if (openAction == null) {
            openAction = new OpenPreferencesAction();
        }
        return openAction;
    }

    private OpenPreferencesAction() {
        super(org.weasis.core.ui.Messages.getString("OpenPreferencesAction.title")); //$NON-NLS-1$
        setDescription(org.weasis.core.ui.Messages.getString("OpenPreferencesAction.description")); //$NON-NLS-1$
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(WeasisWin.getInstance().getRootPaneContainer());
        PreferenceDialog dialog = new PreferenceDialog(WeasisWin.getInstance().getFrame());
        ColorLayerUI.showCenterScreen(dialog, layer);
    }
}
