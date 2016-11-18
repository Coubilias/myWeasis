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
package org.weasis.acquire.explorer;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerViewFactory;

@org.apache.felix.scr.annotations.Component(immediate = false)
@org.apache.felix.scr.annotations.Service
@Properties(value = { @Property(name = "service.name", value = "Media Dicomizer"),
    @Property(name = "service.description", value = "Import media and dicomize them") })
public class MediaImporterFactory implements DataExplorerViewFactory {
    private AcquireExplorer explorer = null;

    @Override
    public AcquireExplorer createDataExplorerView(Hashtable<String, Object> properties) {
        if (explorer == null) {
            explorer = new AcquireExplorer();
            explorer.initImageGroupPane();
            AcquireManager.getInstance().registerDataExplorerView(explorer);
        }
        return explorer;
    }

    @Activate
    protected void activate(ComponentContext context) {
        registerCommands(context);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
            AcquireManager.getInstance().unRegisterDataExplorerView();
            // TODO handle user message if all data is not published !!!
        }

    }

    private void registerCommands(ComponentContext context) {
        if (context != null) {
            ServiceReference<?>[] val = null;

            String serviceClassName = AcquireManager.class.getName();
            try {
                val = context.getBundleContext().getServiceReferences(serviceClassName, null);
            } catch (InvalidSyntaxException e) {
                // Do nothing
            }
            if (val == null || val.length == 0) {
                Dictionary<String, Object> dict = new Hashtable<>();
                dict.put(CommandProcessor.COMMAND_SCOPE, "acquire"); //$NON-NLS-1$
                dict.put(CommandProcessor.COMMAND_FUNCTION, AcquireManager.functions);
                context.getBundleContext().registerService(serviceClassName, AcquireManager.getInstance(), dict);
            }
        }
    }

}