/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.FontTools;

public class ImportPanel extends JPanel {
    private static final long serialVersionUID = -8658686020451614960L;

    private JButton importBtn = new JButton(Messages.getString("ImportPanel.import")); //$NON-NLS-1$

    public ImportPanel(AcquireThumbnailListPane<MediaElement> mainPanel, ImageGroupPane centralPane) {
        importBtn.setPreferredSize(new Dimension(150, 40));
        importBtn.setFont(FontTools.getFont12Bold());

        importBtn.addActionListener(e -> {
            List<ImageElement> selected = AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
            if (!selected.isEmpty()) {
                AcquireImportDialog dialog = new AcquireImportDialog(mainPanel, selected);
                JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
            }
        });
        add(importBtn);
    }
}
