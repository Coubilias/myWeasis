package org.weasis.acquire.dockable.components.actions.resize;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class ResizeAction extends AbstractAcquireAction {

    public ResizeAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void init() {
        super.init();
        
        LOGGER.info("image width: " + getView().getSourceImage().getWidth() + "px");
        LOGGER.info("image height: " + getView().getSourceImage().getHeight() + "px");
    }
    
    @Override
    public void validate() {
         
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new ResizePanel();
    }

}
