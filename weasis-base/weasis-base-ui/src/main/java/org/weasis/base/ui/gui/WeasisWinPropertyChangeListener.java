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
package org.weasis.base.ui.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ViewerPlugin;

/**
 * User: boraldo Date: 05.02.14 Time: 17:37
 */
public class WeasisWinPropertyChangeListener implements PropertyChangeListener {

    private WeasisWinPropertyChangeListener() {
    }

    private static WeasisWinPropertyChangeListener instance = new WeasisWinPropertyChangeListener();

    public static WeasisWinPropertyChangeListener getInstance() {
        return instance;
    }

    private WeasisWin weasisWin = WeasisWin.getInstance();

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        ViewerPlugin selectedPlugin = weasisWin.getSelectedPlugin();

        // Get only ObservableEvent
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object source = event.getNewValue();
            if (evt.getSource() instanceof DataExplorerModel) {
                if (ObservableEvent.BasicAction.SELECT.equals(action)) {
                    if (source instanceof DataExplorerModel) {
                        DataExplorerModel model = (DataExplorerModel) source;
                        DataExplorerView view = null;
                        synchronized (UIManager.EXPLORER_PLUGINS) {
                            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
                            for (DataExplorerView dataExplorerView : explorers) {
                                if (dataExplorerView.getDataExplorerModel() == model) {
                                    view = dataExplorerView;
                                    break;
                                }
                            }
                            if (view instanceof PluginTool) {
                                PluginTool tool = (PluginTool) view;
                                tool.showDockable();
                            }
                        }
                    }
                    // Select a plugin from that as the same key as the MediaSeriesGroup
                    else if (source instanceof MediaSeriesGroup) {
                        MediaSeriesGroup group = (MediaSeriesGroup) source;
                        // If already selected do not reselect or select a second window
                        if (selectedPlugin == null || !group.equals(selectedPlugin.getGroupID())) {
                            synchronized (UIManager.VIEWER_PLUGINS) {
                                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                                    ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                                    if (group.equals(p.getGroupID())) {
                                        p.setSelectedAndGetFocus();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (ObservableEvent.BasicAction.REGISTER.equals(action)) {
                    if (source instanceof ViewerPlugin) {
                        weasisWin.registerPlugin((ViewerPlugin) source);
                    } else if (source instanceof ViewerPluginBuilder) {
                        ViewerPluginBuilder builder = (ViewerPluginBuilder) source;
                        DataExplorerModel model = builder.getModel();
                       List<MediaSeries<MediaElement>> series = builder.getSeries();
                        Map<String, Object> props = builder.getProperties();
                        if (series != null
                            && JMVUtils.getNULLtoTrue(props.get(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER))
                            && model.getTreeModelNodeForNewPlugin() != null && model instanceof TreeModel) {
                            TreeModel treeModel = (TreeModel) model;
                            boolean inSelView =
                                JMVUtils.getNULLtoFalse(props.get(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW))
                                    && builder.getFactory().isViewerCreatedByThisFactory(selectedPlugin);

                            if (series.size() == 1) {
                                MediaSeries<MediaElement> s = series.get(0);
                                MediaSeriesGroup group = treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
                                if (inSelView && s.getMimeType().indexOf("dicom") == -1) { //$NON-NLS-1$
                                    // Change the group attribution. DO NOT use it with DICOM.
                                    group = selectedPlugin.getGroupID();
                                }
                                weasisWin.openSeriesInViewerPlugin(builder, group);
                            } else if (series.size() > 1) {
                                HashMap<MediaSeriesGroup, List<MediaSeries<?>>> map =
                                    weasisWin.getSeriesByEntry(treeModel, series, model.getTreeModelNodeForNewPlugin());
                                for (Iterator<Map.Entry<MediaSeriesGroup, List<MediaSeries<?>>>> iterator =
                                    map.entrySet().iterator(); iterator.hasNext();) {
                                    Map.Entry<MediaSeriesGroup, List<MediaSeries<?>>> entry =
                                        iterator.next();
                                    MediaSeriesGroup group = entry.getKey();

                                    if (inSelView) {
                                        List<MediaSeries<?>> seriesList = entry.getValue();
                                        if (!seriesList.isEmpty()) {
                                            // Change the group attribution. DO NOT use it with DICOM.
                                            if (seriesList.get(0).getMimeType().indexOf("dicom") == -1) { //$NON-NLS-1$
                                                group = selectedPlugin.getGroupID();
                                            }
                                        }
                                    }
                                    weasisWin.openSeriesInViewerPlugin(builder, group);
                                }
                            }

                        } else {
                            weasisWin.openSeriesInViewerPlugin(builder, null);
                        }
                    }
                } else if (ObservableEvent.BasicAction.UNREGISTER.equals(action)) {
                    if (source instanceof SeriesViewerFactory) {
                        SeriesViewerFactory viewerFactory = (SeriesViewerFactory) source;
                        final List<ViewerPlugin<?>> pluginsToRemove = new ArrayList<>();
                        String name = viewerFactory.getUIName();
                        synchronized (UIManager.VIEWER_PLUGINS) {
                            for (final ViewerPlugin<?> plugin : UIManager.VIEWER_PLUGINS) {
                                if (name.equals(plugin.getName())) {
                                    // Do not close Series directly, it can produce deadlock.
                                    pluginsToRemove.add(plugin);
                                }
                            }
                        }
                        UIManager.closeSeriesViewer(pluginsToRemove);
                    }
                }
            } else if (event.getSource() instanceof ViewerPlugin) {
                ViewerPlugin plugin = (ViewerPlugin) event.getSource();
                if (ObservableEvent.BasicAction.UPDTATE_TOOLBARS.equals(action)) {
                    List toolaBars = selectedPlugin == null ? null : selectedPlugin.getToolBar();
                    weasisWin.updateToolbars(toolaBars, toolaBars, true);
                } else if (ObservableEvent.BasicAction.NULL_SELECTION.equals(action)) {
                    weasisWin.setSelectedPlugin(null);
                }
            }
        }
    }

}
