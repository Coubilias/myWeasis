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
package org.weasis.core.api.gui.util;

import org.weasis.core.api.Messages;

public interface ActionState {

    String NONE = Messages.getString("ActionState.none"); //$NON-NLS-1$
    String NONE_SERIES = Messages.getString("ActionState.none_all"); //$NON-NLS-1$

    void enableAction(boolean enabled);

    boolean isActionEnabled();

    ActionW getActionW();

    public boolean registerActionState(Object c);

    public void unregisterActionState(Object c);

}
