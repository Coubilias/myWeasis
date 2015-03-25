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
package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.dcm4che3.data.Attributes;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.dockable.DisplayTool;
import org.weasis.dicom.viewer2d.dockable.ImageTool;

public class View2dContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(View2dContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());
    static {
        SYNCH_LIST.add(SynchView.NONE);
        SYNCH_LIST.add(SynchView.DEFAULT_STACK);
        SYNCH_LIST.add(SynchView.DEFAULT_TILE);
    }

    public static final GridBagLayoutModel VIEWS_2x1_r1xc2_dump =
        new GridBagLayoutModel(
            View2dContainer.class.getResourceAsStream("/config/layoutModel.xml"), "layout_dump", Messages.getString("View2dContainer.layout_dump"), new ImageIcon( //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                View2dContainer.class.getResource("/icon/22x22/layout1x2_c2.png"))); //$NON-NLS-1$

    public static final List<GridBagLayoutModel> LAYOUT_LIST = Collections
        .synchronizedList(new ArrayList<GridBagLayoutModel>());
    static {
        LAYOUT_LIST.add(VIEWS_1x1);
        LAYOUT_LIST.add(VIEWS_1x2);
        LAYOUT_LIST.add(VIEWS_2x1);
        LAYOUT_LIST.add(VIEWS_2x2_f2);
        LAYOUT_LIST.add(VIEWS_2_f1x2);
        LAYOUT_LIST.add(VIEWS_2x1_r1xc2_dump);
        LAYOUT_LIST.add(VIEWS_2x2);
        LAYOUT_LIST.add(VIEWS_3x2);
        LAYOUT_LIST.add(VIEWS_3x3);
        LAYOUT_LIST.add(VIEWS_4x3);
        LAYOUT_LIST.add(VIEWS_4x4);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    private static WtoolBar statusBar = null;
    private static volatile boolean INI_COMPONENTS = false;

    public View2dContainer() {
        this(VIEWS_1x1, null, View2dFactory.NAME, View2dFactory.ICON, null);
    }

    public View2dContainer(GridBagLayoutModel layoutModel, String uid, String pluginName, Icon icon, String tooltips) {
        super(EventManager.getInstance(), layoutModel, uid, pluginName, icon, tooltips);
        setSynchView(SynchView.DEFAULT_STACK);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;

            // Add standard toolbars
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            EventManager evtMg = EventManager.getInstance();

            String bundleName = context.getBundle().getSymbolicName();
            String componentName = InsertableUtil.getCName(this.getClass());
            String key = "enable"; //$NON-NLS-1$

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ViewerToolBar.class), key, true)) {
                TOOLBARS.add(new ViewerToolBar<DicomImageElement>(evtMg, evtMg.getMouseActions().getActiveButtons(),
                    BundleTools.SYSTEM_PREFERENCES, 10));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MeasureToolBar.class), key, true)) {
                TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ZoomToolBar.class), key, true)) {
                TOOLBARS.add(new ZoomToolBar(evtMg, 20));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(RotationToolBar.class), key, true)) {
                TOOLBARS.add(new RotationToolBar(evtMg, 30));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(DcmHeaderToolBar.class), key, true)) {
                TOOLBARS.add(new DcmHeaderToolBar<DicomImageElement>(35));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(LutToolBar.class), key, true)) {
                TOOLBARS.add(new LutToolBar<DicomImageElement>(40));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(Basic3DToolBar.class), key, true)) {
                TOOLBARS.add(new Basic3DToolBar<DicomImageElement>(50));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(CineToolBar.class), key, true)) {
                TOOLBARS.add(new CineToolBar<DicomImageElement>(80));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(KeyObjectToolBar.class), key, true)) {
                TOOLBARS.add(new KeyObjectToolBar(90));
            }

            PluginTool tool = null;

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MiniTool.class), key, true)) {
                tool = new MiniTool(MiniTool.BUTTON_NAME) {

                    @Override
                    public SliderChangeListener[] getActions() {

                        ArrayList<SliderChangeListener> listeners = new ArrayList<SliderChangeListener>(3);
                        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                        if (seqAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) seqAction);
                        }
                        ActionState zoomAction = eventManager.getAction(ActionW.ZOOM);
                        if (zoomAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) zoomAction);
                        }
                        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                        if (rotateAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) rotateAction);
                        }
                        return listeners.toArray(new SliderChangeListener[listeners.size()]);
                    }
                };
                // DefaultSingleCDockable dock = tool.registerToolAsDockable();
                // dock.setDefaultLocation(ExtendedMode.NORMALIZED,
                // CLocation.base(UIManager.BASE_AREA).normalRectangle(1.0, 0.0, 0.05, 1.0));
                // dock.setExtendedMode(ExtendedMode.NORMALIZED);
                TOOLS.add(tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ImageTool.class), key, true)) {
                tool = new ImageTool(Messages.getString("View2dContainer.image_tools")); //$NON-NLS-1$
                TOOLS.add(tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(DisplayTool.class), key, true)) {
                tool = new DisplayTool(DisplayTool.BUTTON_NAME);
                TOOLS.add(tool);
                eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MeasureTool.class), key, true)) {
                tool = new MeasureTool(eventManager);
                TOOLS.add(tool);
            }

            InsertableUtil.sortInsertable(TOOLS);

            // Send event to synchronize the series selection.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) dicomView);
            }

            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, componentName, Type.TOOLBAR);
                InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, componentName, Type.TOOL);
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> viewCanvas) {
        setSelectedImagePane(viewCanvas);
        if (viewCanvas != null && viewCanvas.getSeries() instanceof DicomSeries) {
            DicomSeries series = (DicomSeries) viewCanvas.getSeries();
            DicomSeries.startPreloading(
                series,
                series.copyOfMedias(
                    (Filter<DicomImageElement>) viewCanvas.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                    viewCanvas.getCurrentSortComparator()), viewCanvas.getFrameIndex());
        }
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            if (eventManager instanceof EventManager) {
                EventManager manager = (EventManager) eventManager;
                JMenu menu = new JMenu(Messages.getString("View2dContainer.3d")); //$NON-NLS-1$
                ActionState scrollAction = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
                menu.setEnabled(manager.getSelectedSeries() != null
                    && (scrollAction != null && scrollAction.isActionEnabled()));

                if (menu.isEnabled()) {
                    JMenuItem mpr = new JMenuItem(Messages.getString("View2dContainer.mpr")); //$NON-NLS-1$
                    mpr.addActionListener(Basic3DToolBar.getMprAction());
                    menu.add(mpr);

                    JMenuItem mip = new JMenuItem(Messages.getString("View2dContainer.mip")); //$NON-NLS-1$
                    mip.addActionListener(Basic3DToolBar.getMipAction());
                    menu.add(mip);
                }
                menuRoot.add(menu);
                menuRoot.add(new JSeparator());
                JMVUtils.addItemToMenu(menuRoot, manager.getPresetMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getFilterMenu(null));
                menuRoot.add(new JSeparator());
                JMVUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getSortStackMenu(null));
                menuRoot.add(new JSeparator());
                menuRoot.add(manager.getResetMenu(null));
            }

        }
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        super.close();
        View2dFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (ViewCanvas v : view2ds) {
                    resetMaximizedSelectedImagePane(v);
                    v.dispose();
                }
            }
        });

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();

            if (newVal instanceof SeriesEvent) {
                SeriesEvent event2 = (SeriesEvent) newVal;

                SeriesEvent.Action action2 = event2.getActionCommand();
                Object source = event2.getSource();
                Object param = event2.getParam();

                if (ObservableEvent.BasicAction.Add.equals(action)) {

                    if (SeriesEvent.Action.AddImage.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries series = (DicomSeries) source;
                            ViewCanvas<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
                            if (view2DPane != null) {
                                DicomImageElement img = view2DPane.getImage();
                                if (img != null && view2DPane.getSeries() == series) {
                                    ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                    if (seqAction instanceof SliderCineListener) {
                                        SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                        if (param instanceof DicomImageElement) {
                                            Filter<DicomImageElement> filter =
                                                (Filter<DicomImageElement>) view2DPane
                                                    .getActionValue(ActionW.FILTERED_SERIES.cmd());
                                            int imgIndex =
                                                series
                                                    .getImageIndex(img, filter, view2DPane.getCurrentSortComparator());
                                            if (imgIndex < 0) {
                                                imgIndex = 0;
                                                // add again the series for registering listeners
                                                // (require at least one image)
                                                view2DPane.setSeries(series, null);
                                            }
                                            if (imgIndex >= 0) {
                                                sliceAction.setMinMaxValue(1, series.size(filter), imgIndex + 1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.UpdateImage.equals(action2)) {
                        if (source instanceof DicomImageElement) {
                            DicomImageElement dcm = (DicomImageElement) source;
                            for (ViewCanvas<DicomImageElement> v : view2ds) {
                                if (dcm == v.getImage()) {
                                    // Force to repaint the same image
                                    if (v.getImageLayer().getDisplayImage() == null) {
                                        v.setActionsInView(ActionW.PROGRESSION.cmd(), param);
                                        // Set image to null for getting correct W/L values
                                        v.getImageLayer().setImage(null, null);
                                        v.setSeries(v.getSeries());
                                    } else {
                                        v.propertyChange(new PropertyChangeEvent(dcm, ActionW.PROGRESSION.cmd(), null,
                                            param));
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.loadImageInMemory.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries dcm = (DicomSeries) source;
                            for (ViewCanvas<DicomImageElement> v : view2ds) {
                                if (dcm == v.getSeries()) {
                                    v.getJComponent().repaint(v.getInfoLayer().getPreloadingProgressBound());
                                }
                            }
                        }
                    }
                } else if (ObservableEvent.BasicAction.Update.equals(action)) {
                    if (SeriesEvent.Action.Update.equals(action2)) {
                        if (source instanceof KOSpecialElement) {
                            setKOSpecialElement((KOSpecialElement) source, null, false, param.equals("updateAll")); //$NON-NLS-1$
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Remove.equals(action)) {
                if (newVal instanceof DicomSeries) {
                    DicomSeries dicomSeries = (DicomSeries) newVal;
                    for (ViewCanvas<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (dicomSeries.equals(s)) {
                            v.setSeries(null);
                        }
                    }
                } else if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                        }
                    }
                    // Study Group
                    else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                        if (event.getSource() instanceof DicomModel) {
                            DicomModel model = (DicomModel) event.getSource();
                            for (MediaSeriesGroup s : model.getChildren(group)) {
                                for (ViewCanvas<DicomImageElement> v : view2ds) {
                                    MediaSeries series = v.getSeries();
                                    if (s.equals(series)) {
                                        v.setSeries(null);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Replace.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (ViewCanvas<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            /*
                             * Set to null to be sure that all parameters from the view are apply again to the Series
                             * (for instance it is the same series with more images)
                             */
                            v.setSeries(null);
                            v.setSeries(series, null);
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Update.equals(action)) {

                DicomSpecialElement specialElement = null;

                // When a dicom KO element is loaded an ObservableEvent.BasicAction.Update is sent
                // Either it's a new DicomObject or it's content is updated

                // TODO - a choice should be done about sending either a DicomSpecialElement or a Series object as the
                // new value for this event. A DicomSpecialElement seems to be a better choice since a Series of
                // DicomSpecialElement do not necessarily concerned the series in the Viewer2dContainer

                if (newVal instanceof Series) {
                    specialElement = DicomModel.getFirstSpecialElement((Series) newVal, DicomSpecialElement.class);
                } else if (newVal instanceof DicomSpecialElement) {
                    specialElement = (DicomSpecialElement) newVal;
                }

                if (specialElement instanceof PRSpecialElement) {
                    for (ViewCanvas<DicomImageElement> view : view2ds) {
                        if (view instanceof View2d) {
                            DicomImageElement img = view.getImage();
                            if (img != null) {
                                if (DicomSpecialElement.isSopuidInReferencedSeriesSequence(
                                    (Attributes[]) specialElement.getTagValue(TagW.ReferencedSeriesSequence),
                                    (String) img.getTagValue(TagW.SeriesInstanceUID),
                                    (String) img.getTagValue(TagW.SOPInstanceUID), (Integer) img.getKey())) {
                                    ((View2d) view).updatePR();

                                }
                            }
                        }
                    }
                }

                /*
                 * Update if necessary all the views with the KOSpecialElement
                 */
                else if (specialElement instanceof KOSpecialElement) {
                    setKOSpecialElement((KOSpecialElement) specialElement, null, false, false);
                }
            } else if (ObservableEvent.BasicAction.Select.equals(action)) {
                if (newVal instanceof KOSpecialElement) {
                    // Match using UID of the plugin window and the source event
                    if (this.getDockableUID().equals(evt.getSource())) {
                        setKOSpecialElement((KOSpecialElement) newVal, true, true, false);
                    }
                }
            }
        }
    }

    private void setKOSpecialElement(KOSpecialElement updatedKOSelection, Boolean enableFilter, boolean forceUpdate,
        boolean updateAll) {
        ViewCanvas<DicomImageElement> selectedView = getSelectedImagePane();

        if (updatedKOSelection != null && selectedView instanceof View2d) {
            if (SynchData.Mode.Tile.equals(this.getSynchView().getSynchData().getMode())) {

                ActionState koSelection = selectedView.getEventManager().getAction(ActionW.KO_SELECTION);
                if (koSelection instanceof ComboItemListener) {
                    ((ComboItemListener) koSelection).setSelectedItem(updatedKOSelection);
                }

                if (forceUpdate || enableFilter != null) {
                    ActionState koFilterAction = selectedView.getEventManager().getAction(ActionW.KO_FILTER);
                    if (koFilterAction instanceof ToggleButtonListener) {
                        if (enableFilter == null) {
                            enableFilter =
                                JMVUtils.getNULLtoFalse(selectedView.getActionValue(ActionW.KO_FILTER.cmd()));
                        }
                        ((ToggleButtonListener) koFilterAction).setSelected(enableFilter);
                    }
                }

                if (updateAll) {
                    ArrayList<ViewCanvas<DicomImageElement>> viewList = getImagePanels(true);
                    for (ViewCanvas<DicomImageElement> view : viewList) {
                        ((View2d) view).updateKOButtonVisibleState();
                    }
                } else {
                    ((View2d) selectedView).updateKOButtonVisibleState();
                }

            } else {
                /*
                 * Set the selected view at the end of the list to trigger the synchronization of the SCROLL_SERIES
                 * action at the end of the process
                 */
                ArrayList<ViewCanvas<DicomImageElement>> viewList = getImagePanels(true);

                for (ViewCanvas<DicomImageElement> view : viewList) {

                    if ((view.getSeries() instanceof DicomSeries) == false || (view instanceof View2d) == false) {
                        continue;
                    }

                    if (forceUpdate || updatedKOSelection == view.getActionValue(ActionW.KO_SELECTION.cmd())) {
                        KOManager.updateKOFilter(view, forceUpdate ? updatedKOSelection : null, enableFilter, -1);
                    }

                    DicomSeries dicomSeries = (DicomSeries) view.getSeries();
                    String seriesInstanceUID = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);

                    if (updatedKOSelection.containsSeriesInstanceUIDReference(seriesInstanceUID) == false) {
                        continue;
                    }

                    ((View2d) view).updateKOButtonVisibleState();
                }
            }

            EventManager.getInstance().updateKeyObjectComponentsListener(selectedView);
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return View2dFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        return new View2d(eventManager);
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        if (isViewType(DefaultView2d.class, clazz)) {
            return createDefaultView(clazz);
        }

        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            return component;

        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }

        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized WtoolBar getStatusBar() {
        return statusBar;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        List<Action> actions = selectedImagePane == null ? null : selectedImagePane.getExportToClipboardAction();
        // TODO Add option in properties to deactivate this option
        if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
            AbstractAction importAll =
                new AbstractAction(
                    Messages.getString("View2dContainer.expOsirixMes"), new ImageIcon(View2dContainer.class.getResource("/icon/16x16/osririx.png"))) { //$NON-NLS-1$//$NON-NLS-2$

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String cmd = "/usr/bin/open -b com.rossetantoine.osirix"; //$NON-NLS-1$
                        String baseDir = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
                        if (baseDir != null) {
                            String prop = System.getProperty("weasis.portable.dicom.directory"); //$NON-NLS-1$
                            if (prop != null) {
                                String[] dirs = prop.split(","); //$NON-NLS-1$
                                File[] files = new File[dirs.length];
                                for (int i = 0; i < files.length; i++) {
                                    File file = new File(baseDir, dirs[i].trim());
                                    if (file.canRead()) {
                                        cmd += " " + file.getAbsolutePath(); //$NON-NLS-1$
                                    }
                                }
                            }
                        } else {
                            File file = new File(AppProperties.APP_TEMP_DIR, "dicom"); //$NON-NLS-1$
                            if (file.canRead()) {
                                cmd += " " + file.getAbsolutePath(); //$NON-NLS-1$
                            }
                        }
                        System.out.println("Execute cmd:" + cmd); //$NON-NLS-1$
                        try {
                            Process p = Runtime.getRuntime().exec(cmd);
                            BufferedReader buffer = new BufferedReader(new InputStreamReader(p.getInputStream()));

                            String data;
                            while ((data = buffer.readLine()) != null) {
                                System.out.println(data);
                            }
                            int val = 0;
                            if (p.waitFor() != 0) {
                                val = p.exitValue();
                            }
                            if (val != 0) {
                                JOptionPane.showMessageDialog(View2dContainer.this,
                                    Messages.getString("View2dContainer.expOsirixTitle"), //$NON-NLS-1$
                                    Messages.getString("View2dContainer.expOsirixMes"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                            }

                        } catch (IOException e1) {
                            e1.printStackTrace();
                        } catch (InterruptedException e2) {
                            LOGGER.error("Cannot get the exit status of the open Osirix command: ", e2.getMessage()); //$NON-NLS-1$
                        }
                    }
                };
            if (actions == null) {
                actions = new ArrayList<Action>(1);
            }
            actions.add(importAll);
        }
        return actions;
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<Action>(2);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        AbstractAction printStd =
            new AbstractAction(title, new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png"))) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
                    PrintDialog dialog =
                        new PrintDialog(SwingUtilities.getWindowAncestor(View2dContainer.this), title, eventManager);
                    ColorLayerUI.showCenterScreen(dialog, layer);
                }
            };
        actions.add(printStd);

        final String title2 = Messages.getString("View2dContainer.dcm_print"); //$NON-NLS-1$
        AbstractAction printStd2 = new AbstractAction(title2, null) {

            @Override
            public void actionPerformed(ActionEvent e) {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
                DicomPrintDialog dialog =
                    new DicomPrintDialog(SwingUtilities.getWindowAncestor(View2dContainer.this), title2, eventManager);
                ColorLayerUI.showCenterScreen(dialog, layer);
            }
        };
        actions.add(printStd2);
        return actions;
    }

    @Override
    public List<SynchView> getSynchList() {
        return SYNCH_LIST;
    }

    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        return LAYOUT_LIST;
    }

}
