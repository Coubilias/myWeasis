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
package org.weasis.core.ui.model.layer;

import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.weasis.core.ui.model.utils.imp.DefaultUUID;

public abstract class AbstractGraphicLayer extends DefaultUUID implements GraphicLayer {
    private static final long serialVersionUID = 845033167886327915L;

    private String name;
    private LayerType type;
    private Boolean visible;
    private Boolean locked;
    private Integer level; // Layers are sorted by level number (ascending order)

    public AbstractGraphicLayer(LayerType type) {
        setType(type);
        this.level = type.getLevel();
        this.visible = type.getVisible();
        this.locked = type.getLocked();
    }

    @XmlAttribute
    @Override
    public Boolean getLocked() {
        return locked;
    }

    @Override
    public void setLocked(Boolean locked) {
        this.locked = Optional.ofNullable(locked).orElse(getType().getLocked());
    }

    @Override
    public void setVisible(Boolean visible) {
        this.visible = Optional.ofNullable(visible).orElse(getType().getVisible());
    }

    @XmlAttribute
    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setLevel(Integer level) {
        this.level = Optional.ofNullable(level).orElse(getType().getLevel());
    }

    @XmlAttribute
    @Override
    public Integer getLevel() {
        return level;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    @Override
    public String getName() {
        return name;
    }

    @XmlAttribute
    @Override
    public LayerType getType() {
        return type;
    }

    @Override
    public void setType(LayerType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String toString() {
        return Optional.ofNullable(getName()).orElse(getType().getDefaultName());
    }

    static class Adapter extends XmlAdapter<AbstractGraphicLayer, Layer> {

        @Override
        public Layer unmarshal(AbstractGraphicLayer v) throws Exception {
            return v;
        }

        @Override
        public AbstractGraphicLayer marshal(Layer v) throws Exception {
            return (AbstractGraphicLayer) v;
        }
    }
}
