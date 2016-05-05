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
package org.weasis.core.ui.editor.image;

import java.awt.event.KeyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.BoundedRangeModel;
import javax.swing.event.SwingPropertyChangeSupport;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.pref.ZoomSetting;

public abstract class ImageViewerEventManager<E extends ImageElement> implements KeyListener {
    public static final int ZOOM_SLIDER_MIN = -100;
    public static final int ZOOM_SLIDER_MAX = 100;
    public static final int WINDOW_SMALLEST = 0;
    public static final int WINDOW_LARGEST = 4096;
    public static final int WINDOW_DEFAULT = 700;
    public static final int LEVEL_SMALLEST = 0;
    public static final int LEVEL_LARGEST = 4096;
    public static final int LEVEL_DEFAULT = 300;

    protected final ArrayList<SeriesViewerListener> seriesViewerListeners = new ArrayList<SeriesViewerListener>();
    protected final MouseActions mouseActions = new MouseActions(null);
    protected final ZoomSetting zoomSetting = new ZoomSetting();
    protected final WProperties options = new WProperties();
    protected ImageViewerPlugin<E> selectedView2dContainer;
    // Manages all PropertyChangeListeners in EDT
    protected final SwingPropertyChangeSupport propertySupport = new SwingPropertyChangeSupport(this);
    protected final HashMap<ActionW, ActionState> actions = new HashMap<ActionW, ActionState>();
    protected boolean enabledAction = true;

    public ImageViewerEventManager() {
        super();
    }

    protected void iniAction(ActionState action) {
        actions.put(action.getActionW(), action);
    }

    protected SliderCineListener getMoveTroughSliceAction(int speed, final TIME time, double mouseSensivity) {
        return new SliderCineListener(ActionW.SCROLL_SERIES, 1, 2, 1, speed, time, mouseSensivity) {

            private volatile boolean cining = true;

            protected CineThread currentCine;

            @Override
            public void stateChanged(BoundedRangeModel model) {

                ViewCanvas<ImageElement> view2d = null;
                Series<ImageElement> series = null;
                SynchCineEvent mediaEvent = null;
                ImageElement image = null;

                if (selectedView2dContainer != null) {
                    view2d = (ViewCanvas<ImageElement>) selectedView2dContainer.getSelectedImagePane();
                }

                if (view2d != null && view2d.getSeries() instanceof Series) {
                    series = (Series<ImageElement>) view2d.getSeries();
                    if (series != null) {
                        // Model contains display value, value-1 is the index value of a sequence
                        int index = model.getValue() - 1;
                        image = series.getMedia(index,
                            (Filter<ImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                            view2d.getCurrentSortComparator());
                        mediaEvent = new SynchCineEvent(view2d, image, index);
                        // Ensure to load image before calling the default preset (requires pixel min and max)
                        if (image != null && !image.isImageAvailable()) {
                            image.getImage();
                        }
                    }
                }

                // ActionState synch = getAction(ActionW.SYNCH);
                // if (media != null && synch instanceof ComboItemListener) {
                // SynchView synchview = (SynchView) ((ComboItemListener) synch).getSelectedItem();
                // if (synchview.isActionEnable(ActionW.SCROLL_SERIES)) {
                // Float val = (Float) media.getTagValue(TagW.SliceLocation);
                // if (val != null) {
                // location = val;
                // }
                // }
                // }

                firePropertyChange(ActionW.SYNCH.cmd(), null, mediaEvent);
                if (image != null) {
                    fireSeriesViewerListeners(
                        new SeriesViewerEvent(selectedView2dContainer, series, image, EVENT.SELECT));
                }
            }

            @Override
            public void setSpeed(int speed) {
                super.setSpeed(speed);
                if (currentCine != null) {
                    currentCine.iniSpeed();
                }
            }

            /** Create a thread to cine the images. */

            class CineThread extends Thread {

                private volatile int iteration;
                private volatile int wait;
                private volatile int currentCineRate;
                private volatile long start;
                private final int timeDiv =
                    TIME.second.equals(time) ? 1000 : TIME.minute.equals(time) ? 60000 : 3600000;

                @Override
                public void run() {
                    iniSpeed();
                    // Create a robot to monitor the paintImageViewerEventManager
                    // Robot robot = null;
                    //
                    // try {
                    // robot = new java.awt.Robot();
                    // }
                    // catch (Exception e) {
                    // }

                    while (cining) {
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                int frameIndex = getValue() + 1;
                                frameIndex = frameIndex > getMax() ? 0 : frameIndex;
                                setValue(frameIndex);
                            }
                        });

                        iteration++;
                        // Wait until the paint is finished
                        // robot.waitForIdle();

                        // adjust the delay time based on the current performance
                        long elapsed = (System.currentTimeMillis() - start) / 1000;
                        if (elapsed > 0) {
                            currentCineRate = (int) (iteration / elapsed);
                            // System.out.println("fps:" + fps);

                            if (currentCineRate < getSpeed()) {
                                wait--;
                            } else {
                                wait++;
                            }
                            if (wait < 0) {
                                wait = 0;
                            }
                        }

                        // wait
                        if (wait > 0) {
                            try {
                                Thread.sleep(wait);
                            } catch (Exception e) {
                            }
                        }
                    }
                }

                public void iniSpeed() {
                    iteration = 0;
                    wait = timeDiv / getSpeed();
                    currentCineRate = getSpeed();
                    start = System.currentTimeMillis();
                }

                public int getCurrentCineRate() {
                    return currentCineRate;
                }

            }

            /** Start the cining. */

            @Override
            public synchronized void start() {
                if (currentCine != null) {
                    stop();
                }
                if (getMax() - getMin() > 0) {
                    cining = true;
                    currentCine = new CineThread();
                    currentCine.start();
                }
            }

            /** Stop the cining. */

            @Override
            public synchronized void stop() {
                CineThread moribund = currentCine;
                currentCine = null;
                if (moribund != null) {
                    cining = false;
                    moribund.interrupt();
                }
            }

            @Override
            public boolean isCining() {
                return cining;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                setValue(getValue() + e.getWheelRotation());
            }

            @Override
            public int getCurrentCineRate() {
                if (currentCine != null) {
                    return currentCine.getCurrentCineRate();
                }
                return 0;
            }

        };
    }

    protected SliderChangeListener newWindowAction() {

        return new SliderChangeListener(ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT, true, 1.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
            }
        };
    }

    protected SliderChangeListener newLevelAction() {
        return new SliderChangeListener(ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
            }
        };
    }

    protected SliderChangeListener newRotateAction() {
        return new SliderChangeListener(ActionW.ROTATION, 0, 360, 0, true, 0.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), getActionW().cmd(), model.getValue()));
            }

            @Override
            public String getValueToDisplay() {
                return getValue() + " \u00b0"; //$NON-NLS-1$
            }
        };
    }

    protected SliderChangeListener newZoomAction() {
        return new SliderChangeListener(ActionW.ZOOM, ZOOM_SLIDER_MIN, ZOOM_SLIDER_MAX, 0, true, 0.1) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), getActionW().cmd(),
                    sliderValueToViewScale(model.getValue())));
            }

            @Override
            public String getValueToDisplay() {
                return DecFormater.percentTwoDecimal(sliderValueToViewScale(getValue()));
            }

        };
    }

    protected PannerListener newPanAction() {
        return new PannerListener(ActionW.PAN, null) {

            @Override
            public void pointChanged(Point2D point) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), getActionW().cmd(), point));
            }

        };
    }

    protected CrosshairListener newCrosshairAction() {
        return new CrosshairListener(ActionW.CROSSHAIR, null) {

            @Override
            public void pointChanged(Point2D point) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), getActionW().cmd(), point));
            }
        };
    }

    protected ToggleButtonListener newFlipAction() {
        return new ToggleButtonListener(ActionW.FLIP, false) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
            }

        };
    }

    protected ToggleButtonListener newInverseLutAction() {
        return new ToggleButtonListener(ActionW.INVERT_LUT, false) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
            }
        };
    }

    protected ToggleButtonListener newLensAction() {
        return new ToggleButtonListener(ActionW.LENS, false) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
            }

        };
    }

    protected SliderChangeListener newLensZoomAction() {
        return new SliderChangeListener(ActionW.LENSZOOM, ZOOM_SLIDER_MIN, ZOOM_SLIDER_MAX, viewScaleToSliderValue(2.0),
            true, 0.1) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), getActionW().cmd(),
                    sliderValueToViewScale(model.getValue())));
            }

            @Override
            public String getValueToDisplay() {
                return DecFormater.percentTwoDecimal(sliderValueToViewScale(getValue()));
            }

        };
    }

    // protected ComboItemListener getViewModeAction() {
    // return new ComboItemListener(ActionW.VIEW_MODE, Mode.values()) {
    //
    // @Override
    // public void itemStateChanged(Object object) {
    // if (object instanceof Mode && selectedView2dContainer != null) {
    // Mode synchView = (Mode) object;
    // selectedView2dContainer.setViewingMode(synchView);
    // updateAllListeners(synchView);
    // }
    // }
    // };
    // }

    protected ComboItemListener newLayoutAction(GridBagLayoutModel[] layouts) {
        if (layouts == null) {
            layouts = new GridBagLayoutModel[0];
        }
        return new ComboItemListener(ActionW.LAYOUT, layouts) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof GridBagLayoutModel && selectedView2dContainer != null) {
                    // change layout
                    clearAllPropertyChangeListeners();
                    ViewCanvas view = selectedView2dContainer.getSelectedImagePane();
                    selectedView2dContainer.setLayoutModel((GridBagLayoutModel) object);
                    if (!selectedView2dContainer.isContainingView(view)) {
                        view = selectedView2dContainer.getSelectedImagePane();
                    }
                    selectedView2dContainer.setSelectedImagePane(view);
                    ActionState synch = getAction(ActionW.SYNCH);
                    if (synch instanceof ComboItemListener) {
                        selectedView2dContainer.setSynchView((SynchView) ((ComboItemListener) synch).getSelectedItem());
                    }
                }
            }
        };
    }

    protected ComboItemListener newSynchAction(SynchView[] synchViewList) {
        if (synchViewList == null) {
            synchViewList = new SynchView[0];
        }
        return new ComboItemListener(ActionW.SYNCH, synchViewList) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof SynchView && selectedView2dContainer != null) {
                    SynchView synchView = (SynchView) object;
                    selectedView2dContainer.setSynchView(synchView);
                }
            }
        };
    }

    protected ToggleButtonListener newInverseStackAction() {
        return new ToggleButtonListener(ActionW.INVERSESTACK, false) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
            }
        };
    }

    protected ComboItemListener newMeasurementAction(Graphic[] graphics) {
        if (graphics == null) {
            graphics = new Graphic[0];
        }
        return new ComboItemListener(ActionW.DRAW_MEASURE, graphics) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof Graphic && selectedView2dContainer != null) {
                    selectedView2dContainer.setDrawActions((Graphic) object);
                }
            }

        };
    }

    protected ToggleButtonListener newDrawOnlyOnceAction() {
        return new ToggleButtonListener(ActionW.DRAW_ONLY_ONCE, true) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
                MeasureTool.viewSetting.setDrawOnlyOnce(selected);
            }
        };
    }

    protected ComboItemListener newSpatialUnit(Unit[] units) {
        if (units == null) {
            units = new Unit[0];
        }
        return new ComboItemListener(ActionW.SPATIAL_UNIT, units) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), object));
            }
        };
    }

    public abstract boolean updateComponentsListener(ViewCanvas<E> viewCanvas);

    private static double roundAndCropViewScale(double viewScale, double minViewScale, double maxViewScale) {
        viewScale *= 1000.0;
        double v = Math.floor(viewScale);
        if (viewScale - v >= 0.5) {
            v += 0.5;
        }
        viewScale = v / 1000.0;
        if (viewScale < minViewScale) {
            viewScale = minViewScale;
        }
        if (viewScale > maxViewScale) {
            viewScale = maxViewScale;
        }
        return viewScale;
    }

    /** Fire property change event. */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    /** Add a property change listener. */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(propertyName, listener);
    }

    /** Remove a property change listener. */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(propertyName, listener);
    }

    public void addSeriesViewerListener(SeriesViewerListener listener) {
        if (!seriesViewerListeners.contains(listener)) {
            seriesViewerListeners.add(listener);
        }
    }

    /** Remove a property change listener. */
    public void removeSeriesViewerListener(SeriesViewerListener listener) {
        seriesViewerListeners.remove(listener);
    }

    public void fireSeriesViewerListeners(SeriesViewerEvent event) {
        if (event != null) {
            for (SeriesViewerListener listener : seriesViewerListeners) {
                listener.changingViewContentEvent(event);
            }
        }
    }

    public void clearAllPropertyChangeListeners() {
        PropertyChangeListener[] changeListeners = propertySupport.getPropertyChangeListeners();
        for (PropertyChangeListener propertyChangeListener : changeListeners) {
            propertySupport.removePropertyChangeListener(propertyChangeListener);
        }
    }

    public ActionState getAction(ActionW action) {
        if (action != null) {
            return actions.get(action);
        }
        return null;
    }

    public ActionW getActionFromCommand(String command) {
        if (command != null) {
            for (Iterator<ActionW> iterator = actions.keySet().iterator(); iterator.hasNext();) {
                ActionW action = iterator.next();
                if (action.cmd().equals(command)) {
                    return action;
                }
            }
        }
        return null;
    }

    public ActionW getLeftMouseActionFromkeyEvent(int keyEvent, int modifier) {
        if (keyEvent != 0) {
            for (Iterator<ActionW> iterator = actions.keySet().iterator(); iterator.hasNext();) {
                ActionW action = iterator.next();
                if (action.getKeyCode() == keyEvent && action.getModifier() == modifier) {
                    return action;
                }
            }
        }
        return null;
    }

    public void changeLeftMouseAction(String command) {
        ImageViewerPlugin<E> view = getSelectedView2dContainer();
        if (view != null) {
            ViewerToolBar<E> toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                MouseActions mouseActions = getMouseActions();
                if (!command.equals(mouseActions.getAction(MouseActions.LEFT))) {
                    mouseActions.setAction(MouseActions.LEFT, command);
                    view.setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.LEFT, command);
                }
            }
        }
    }

    public void nextLeftMouseAction() {
        ImageViewerPlugin<E> view = getSelectedView2dContainer();
        if (view != null) {
            ViewerToolBar<E> toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                String command = ViewerToolBar
                    .getNextCommand(ViewerToolBar.actionsButtons, toolBar.getMouseLeft().getActionCommand()).cmd();
                changeLeftMouseAction(command);
            }
        }
    }

    public Collection<ActionState> getAllActionValues() {
        return actions.values();
    }

    public MouseActions getMouseActions() {
        return mouseActions;
    }

    public ZoomSetting getZoomSetting() {
        return zoomSetting;
    }

    public WProperties getOptions() {
        return options;
    }

    public static int viewScaleToSliderValue(double viewScale) {
        final double v = Math.log(viewScale) / Math.log(DefaultViewModel.SCALE_MAX) * ZOOM_SLIDER_MAX;
        return (int) Math.round(v);
    }

    public static double sliderValueToViewScale(final int sliderValue) {
        final double v = sliderValue / (double) ZOOM_SLIDER_MAX;
        double viewScale = Math.exp(v * Math.log(DefaultViewModel.SCALE_MAX));
        viewScale = roundAndCropViewScale(viewScale, DefaultViewModel.SCALE_MIN, DefaultViewModel.SCALE_MAX);
        return viewScale;
    }

    public synchronized void enableActions(boolean enabled) {
        enabledAction = enabled;
        for (ActionState a : actions.values()) {
            a.enableAction(enabled);
        }
    }

    public ViewCanvas<E> getSelectedViewPane() {
        ImageViewerPlugin<E> container = selectedView2dContainer;
        if (container != null) {
            return container.getSelectedImagePane();
        }
        return null;
    }

    public ImageViewerPlugin<E> getSelectedView2dContainer() {
        return selectedView2dContainer;
    }

    public boolean isSelectedView2dContainerInTileMode() {
        ImageViewerPlugin<E> container = selectedView2dContainer;
        if (container != null) {
            return SynchData.Mode.Tile.equals(container.getSynchView().getSynchData().getMode());
        }
        return false;
    }

    public void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView) {
        clearAllPropertyChangeListeners();
        if (viewerPlugin != null) {
            ViewCanvas<E> viewPane = viewerPlugin.getSelectedImagePane();
            if (viewPane == null) {
                return;
            }
            if (viewPane.getSeries() != null) {
                SynchData synch = synchView.getSynchData();
                viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
                addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

                final List<ViewCanvas<E>> panes = viewerPlugin.getImagePanels();
                panes.remove(viewPane);
                if (SynchView.NONE.equals(synchView)) {
                    for (int i = 0; i < panes.size(); i++) {
                        panes.get(i).setActionsInView(ActionW.SYNCH_LINK.cmd(), synch);
                    }
                } else if (Mode.Stack.equals(synch.getMode())) {
                    // TODO if Pan is activated than rotation is required

                    boolean hasLink = false;
                    for (int i = 0; i < panes.size(); i++) {
                        boolean synchByDefault = isCompatible(viewPane.getSeries(), panes.get(i).getSeries());
                        panes.get(i).setActionsInView(ActionW.SYNCH_LINK.cmd(), synchByDefault ? synch.clone() : null);
                        if (synchByDefault) {
                            hasLink = true;
                            addPropertyChangeListener(ActionW.SYNCH.cmd(), panes.get(i));
                        }
                    }
                } else if (Mode.Tile.equals(synch.getMode())) {
                    for (int i = 0; i < panes.size(); i++) {
                        panes.get(i).setActionsInView(ActionW.SYNCH_LINK.cmd(), synch.clone());
                        addPropertyChangeListener(ActionW.SYNCH.cmd(), panes.get(i));
                    }
                }

            }
        }
    }

    protected boolean isCompatible(MediaSeries<E> series, MediaSeries<E> series2) {
        return true;
    }

    public void setSelectedView2dContainer(ImageViewerPlugin<E> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            this.selectedView2dContainer.setDrawActions(null);
        }
        this.selectedView2dContainer = selectedView2dContainer;
        if (selectedView2dContainer != null) {
            selectedView2dContainer.setMouseActions(mouseActions);
            Graphic graphic = null;
            ActionState action = getAction(ActionW.DRAW_MEASURE);
            if (action instanceof ComboItemListener) {
                graphic = (Graphic) ((ComboItemListener) action).getSelectedItem();
            }
            selectedView2dContainer.setDrawActions(graphic);
        }
    }

    public abstract void resetDisplay();

}
