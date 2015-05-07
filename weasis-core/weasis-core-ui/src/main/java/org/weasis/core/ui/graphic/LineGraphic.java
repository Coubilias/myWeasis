/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Benoit Jacquemoud
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

@Root(name = "line")
public class LineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public static final Measurement FIRST_POINT_X = new Measurement(
        Messages.getString("measure.firstx"), 1, true, true, false); //$NON-NLS-1$
    public static final Measurement FIRST_POINT_Y = new Measurement(
        Messages.getString("measure.firsty"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_X = new Measurement(
        Messages.getString("measure.lastx"), 3, true, true, false); //$NON-NLS-1$
    public static final Measurement LAST_POINT_Y = new Measurement(
        Messages.getString("measure.lasty"), 4, true, true, false); //$NON-NLS-1$
    public static final Measurement LINE_LENGTH = new Measurement(
        Messages.getString("measure.length"), 5, true, true, true); //$NON-NLS-1$
    public static final Measurement ORIENTATION = new Measurement(
        Messages.getString("measure.orientation"), 6, true, true, false); //$NON-NLS-1$
    public static final Measurement AZIMUTH = new Measurement(
        Messages.getString("measure.azimuth"), 7, true, true, false); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptA, ptB; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public LineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    public LineGraphic(Point2D.Double ptStart, Point2D.Double ptEnd, float lineThickness, Color paintColor,
        boolean labelVisible) throws InvalidShapeException {
        super(2, paintColor, lineThickness, labelVisible, false);
        if (ptStart == null || ptEnd == null) {
            throw new InvalidShapeException("Point2D is null!"); //$NON-NLS-1$
        }
        setHandlePointList(ptStart, ptEnd);
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
        buildShape(null);
    }

    protected LineGraphic(
        @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class) List<Point2D.Double> handlePointList,
        @Attribute(name = "handle_pts_nb") int handlePointTotalNumber,
        @Element(name = "paint", required = false) Paint paintColor,
        @Attribute(name = "thickness") float lineThickness, @Attribute(name = "label_visible") boolean labelVisible)
        throws InvalidShapeException {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
        if (handlePointTotalNumber != 2) {
            throw new InvalidShapeException("Not a valid LineGraphic!"); //$NON-NLS-1$
        }
        buildShape(null);
    }

    protected void setHandlePointList(Point2D.Double ptStart, Point2D.Double ptEnd) {
        setHandlePoint(0, ptStart == null ? null : (Point2D.Double) ptStart.clone());
        setHandlePoint(1, ptEnd == null ? null : (Point2D.Double) ptEnd.clone());
        buildShape(null);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.line"); //$NON-NLS-1$
    }

    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {

        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            newShape = new Line2D.Double(ptA, ptB);
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FIRST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter
                        .getUnit()));
                }
                if (FIRST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_Y, adapter.getXCalibratedValue(ptA.getY()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_Y, adapter.getXCalibratedValue(ptB.getY()), adapter
                        .getUnit()));
                }
                if (LINE_LENGTH.isComputed()) {
                    measVal.add(new MeasureItem(LINE_LENGTH, ptA.distance(ptB) * adapter.getCalibRatio(), adapter
                        .getUnit()));
                }
                if (ORIENTATION.isComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptA, ptB), Messages
                        .getString("measure.deg"))); //$NON-NLS-1$
                }
                if (AZIMUTH.isComputed()) {
                    measVal.add(new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages
                        .getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);

        lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getStartPoint() {
        updateTool();
        return ptA == null ? null : (Point2D) ptA.clone();
    }

    public Point2D getEndPoint() {
        updateTool();
        return ptB == null ? null : (Point2D) ptB.clone();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(FIRST_POINT_X);
        list.add(FIRST_POINT_Y);
        list.add(LAST_POINT_X);
        list.add(LAST_POINT_Y);
        list.add(LINE_LENGTH);
        list.add(ORIENTATION);
        list.add(AZIMUTH);
        return list;
    }
}
