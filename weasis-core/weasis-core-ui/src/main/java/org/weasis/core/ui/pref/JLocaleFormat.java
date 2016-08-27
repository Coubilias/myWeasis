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
package org.weasis.core.ui.pref;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JComboBox;

import org.weasis.core.api.util.LocalUtil;

@SuppressWarnings("serial")
public class JLocaleFormat extends JComboBox<JLocale> implements ItemListener {

    public JLocaleFormat() {
        super();
        sortLocales();
        addItemListener(this);
    }

    private void sortLocales() {
        Locale[] locales = Locale.getAvailableLocales();

        Locale defaultLocale = Locale.getDefault();
        // Allow to sort correctly string in each language
        final Collator collator = Collator.getInstance(defaultLocale);
        Arrays.sort(locales, (l1, l2) -> collator.compare(l1.getDisplayName(), l2.getDisplayName()));

        JLocale dloc = null;
        for (int i = 0; i < locales.length; i++) {
            JLocale val = new JLocale(locales[i]);
            if (val.getLocale().equals(defaultLocale)) {
                dloc = val;
            }
            addItem(val);
        }
        this.setSelectedItem(dloc);
    }

    public void selectLocale() {
        Locale sLoc = LocalUtil.getLocaleFormat();
        Object item = getSelectedItem();
        if (item instanceof JLocale) {
            if (sLoc.equals(((JLocale) item).getLocale())) {
                return;
            }
        }

        for (int i = 0; i < getItemCount(); i++) {
            Locale l = getItemAt(i).getLocale();
            if (l.equals(sLoc)) {
                setSelectedIndex(i);
                break;
            }
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            Object item = e.getItem();
            if (item instanceof JLocale) {
                Locale locale = ((JLocale) item).getLocale();
                LocalUtil.setLocaleFormat(locale.equals(Locale.getDefault()) ? null : locale);
                valueHasChanged();
            }
        }
    }

    protected void valueHasChanged() {
    }

    public void refresh() {
        removeItemListener(this);
        removeAllItems();
        sortLocales();
        selectLocale();
        valueHasChanged();
        addItemListener(this);
    }
}
