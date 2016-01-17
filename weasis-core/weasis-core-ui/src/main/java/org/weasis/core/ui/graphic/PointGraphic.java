package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.Icon;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */

// TODO should a draggable graphic

@XmlType(name = "point", factoryMethod = "createDefaultInstance")
@XmlAccessorType(XmlAccessType.NONE)
public class PointGraphic extends BasicGraphic {

    @XmlAttribute(name = "pt_size")
    private int pointSize;

    public PointGraphic(float lineThickness, Color paintColor, boolean labelVisible) throws IllegalStateException {
        super(0, paintColor, lineThickness, labelVisible);
    }

    public PointGraphic(Point2D.Double point, float lineThickness, Color paintColor, boolean labelVisible,
        boolean filled, int pointSize) throws IllegalStateException {
        super(0, paintColor, lineThickness, labelVisible, filled);
        if (point == null) {
            point = new Point2D.Double();
        }
        this.handlePointList.add(point);
        this.pointSize = pointSize;
        buildShape();
    }

    public static PointGraphic createDefaultInstance() {
        return new PointGraphic(1.0f, Color.YELLOW, true);
    }

    @Override
    public void buildShape() {
        if (this.handlePointList.size() == 1) {
            Point2D.Double point = this.handlePointList.get(0);
            Ellipse2D ellipse = new Ellipse2D.Double(point.getX() - pointSize / 2.0f, point.getY() - pointSize / 2.0f,
                pointSize, pointSize);
            setShape(ellipse, null);
            updateLabel(null, null);
        }
    }

    public Point2D getPoint() {
        if (this.handlePointList.size() == 1) {
            return (Point2D) this.handlePointList.get(0).clone();
        }
        return null;
    }

    public int getPointSize() {
        return pointSize;
    }

    public void setPointSize(int pointSize) {
        this.pointSize = pointSize;
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return Messages.getString("PointGraphic.point"); //$NON-NLS-1$
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }

    @Override
    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        return false;
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
    }

    @Override
    public Area getArea(AffineTransform transform) {
        return new Area();
    }

}
