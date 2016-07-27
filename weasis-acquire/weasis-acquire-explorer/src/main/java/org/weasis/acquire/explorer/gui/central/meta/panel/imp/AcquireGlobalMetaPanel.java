package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireGlobalMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;

public class AcquireGlobalMetaPanel extends AcquireMetadataPanel {
    private static final long serialVersionUID = -2751941971479265507L;
        
    public AcquireGlobalMetaPanel(String title) {
       super(title);
       setMetaVisible(true);
    }

    @Override
    public AcquireMetadataTableModel newTableModel() {
        return new AcquireGlobalMeta();
    }
}
