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
package org.weasis.base.ui.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.LookAndFeel;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.weasis.base.ui.WeasisApp;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.base.ui.gui.WeasisWinPropertyChangeListener;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.pref.GeneralSetting;

public class Activator implements BundleActivator, ServiceListener {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // Starts core bundles for initialization before calling UI components
        Bundle bundle = FrameworkUtil.getBundle(BundleTools.class);
        if (bundle != null) {
            bundle.start();
        }
        bundle = FrameworkUtil.getBundle(UIManager.class);
        if (bundle != null) {
            bundle.start();
        }
        String className = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.look");
        if (StringUtil.hasText(className)) {
            LookAndFeel lf = javax.swing.UIManager.getLookAndFeel();
            if (lf == null || !className.equals(lf.getClass().getName())) {
                GeneralSetting.setLookAndFeel(className);
            }
        }

        // WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                final WeasisWin app = WeasisWin.getInstance();
                try {
                    app.createMainPanel();
                    app.showWindow();

                } catch (Exception ex) {
                    // Nimbus bug, hangs GUI: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6785663
                    // It is better to exit than to let run a zombie process
                    System.err.println("Could not start GUI: " + ex); //$NON-NLS-1$
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        });

        // Register "weasis" command
        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "weasis"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, WeasisApp.functions);
        bundleContext.registerService(WeasisApp.class.getName(), WeasisApp.getInstance(), dict);

        // Explorer (with non immediate instance)
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                // Register default model
                ViewerPluginBuilder.DefaultDataModel
                    .addPropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());

                try {
                    for (ServiceReference<DataExplorerViewFactory> serviceReference : bundleContext
                        .getServiceReferences(DataExplorerViewFactory.class, null)) {
                        DataExplorerViewFactory service = bundleContext.getService(serviceReference);
                        if (service != null && !UIManager.EXPLORER_PLUGINS.contains(service)) {
                            final DataExplorerView explorer = service.createDataExplorerView(null);
                            UIManager.EXPLORER_PLUGINS.add(explorer);

                            if (explorer.getDataExplorerModel() != null) {
                                explorer.getDataExplorerModel()
                                    .addPropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());
                            }

                            if (explorer instanceof DockableTool) {
                                final DockableTool dockable = (DockableTool) explorer;
                                dockable.showDockable();
                            }
                        }
                    }

                } catch (InvalidSyntaxException e1) {
                    e1.printStackTrace();
                }

                // Add all the service listeners
                try {
                    bundleContext.addServiceListener(Activator.this,
                        String.format("(%s=%s)", Constants.OBJECTCLASS, DataExplorerViewFactory.class.getName())); //$NON-NLS-1$
                } catch (InvalidSyntaxException e) {
                    e.printStackTrace();
                }
            }

        });
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // UnRegister default model
        ViewerPluginBuilder.DefaultDataModel
            .removePropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Explorer (with non immediate instance) and WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {

                final ServiceReference<?> m_ref = event.getServiceReference();
                final BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
                Object service = context.getService(m_ref);
                if (service instanceof DataExplorerViewFactory) {
                    final DataExplorerView explorer = ((DataExplorerViewFactory) service).createDataExplorerView(null);
                    if (event.getType() == ServiceEvent.REGISTERED) {
                        if (!UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                            // if ("Media Explorer".equals(explorer.getUIName())) { //$NON-NLS-1$
                            // // in this case, if there are several Explorers, the Media Explorer is selected by
                            // // default
                            // UIManager.EXPLORER_PLUGINS.add(0, explorer);
                            // } else {
                            UIManager.EXPLORER_PLUGINS.add(explorer);
                            // }
                            if (explorer.getDataExplorerModel() != null) {
                                explorer.getDataExplorerModel()
                                    .addPropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());
                            }
                            if (explorer instanceof DockableTool) {
                                final DockableTool dockable = (DockableTool) explorer;
                                dockable.showDockable();
                            }

                            // BundleTools.logger.log(LogService.LOG_INFO, "Register data explorer Plug-in: " +
                            // m_ref.toString());
                        }
                    } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                if (UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                                    if (explorer.getDataExplorerModel() != null) {
                                        explorer.getDataExplorerModel().removePropertyChangeListener(
                                            WeasisWinPropertyChangeListener.getInstance());
                                    }
                                    UIManager.EXPLORER_PLUGINS.remove(explorer);
                                    explorer.dispose();
                                    // TODO unregister property change of the model
                                    // BundleTools.logger.log(LogService.LOG_INFO,
                                    // "Unregister data explorer Plug-in: " +
                                    // m_ref.toString());
                                }
                                // Unget service object and null references.
                                context.ungetService(m_ref);
                            }
                        });
                    }
                }
            }
        });
    }

}
