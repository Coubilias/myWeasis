package org.weasis.dicom.explorer;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.CIELab;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PrSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrSerializer.class);

    public static void writePresentation(DicomImageElement img, File destinationFile) {
        GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
        if (model != null && !model.getModels().isEmpty()) {
            File prFile = new File(destinationFile.getParent(), destinationFile.getName() + ".dcm"); //$NON-NLS-1$

            try {
                if (img.getMediaReader() instanceof DicomMediaIO) {
                    DicomMediaIO dicomImageLoader = (DicomMediaIO) img.getMediaReader();
                    Attributes attributes = DicomMediaUtils.createDicomPR(dicomImageLoader.getDicomObject(), null);

                    writeCommonTags(img, attributes);
                    writeReferences(img, attributes);
                    writeGraphics(img, model, attributes);

                    saveToFile(prFile, attributes);
                }
            } catch (Exception e) {
                LOGGER.error("Cannot save xml: ", e);
            }
        }
    }

    private static void writeCommonTags(DicomImageElement img, Attributes attributes) {
        String label = AppProperties.WEASIS_NAME + " GSPS";
        attributes.setString(Tag.ContentCreatorName, VR.PN, AppProperties.WEASIS_USER);
        attributes.setString(Tag.ContentLabel, VR.CS, label);
        attributes.setString(Tag.ContentDescription, VR.LO, "Description");
        attributes.setInt(Tag.SeriesNumber, VR.IS, 999);
        try {
            attributes.setString(Tag.StationName, VR.SH, InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            LOGGER.error("Cannot get host name: ", e);
        }
        attributes.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
        attributes.setString(Tag.SeriesDescription, VR.LO, label);
    }

    private static void writeReferences(DicomImageElement img, Attributes attributes) {
        Attributes rfs = new Attributes(2);
        rfs.setString(Tag.SeriesInstanceUID, VR.UI, TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class));

        Attributes rfi = new Attributes(2);
        rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, TagD.getTagValue(img, Tag.SOPClassUID, String.class));
        rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, TagD.getTagValue(img, Tag.SOPInstanceUID, String.class));
        int frames = img.getMediaReader().getMediaElementNumber();
        if (frames > 1 && img.getKey() instanceof Integer) {
            rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, (Integer) img.getKey());
        }
        rfs.newSequence(Tag.ReferencedImageSequence, 1).add(rfi);

        attributes.newSequence(Tag.ReferencedSeriesSequence, 1).add(rfs);
    }

    private static void writeGraphics(DicomImageElement img, GraphicModel model, Attributes attributes) {
        List<GraphicLayer> layers = model.getLayers();

        Sequence annotationSeq = attributes.newSequence(Tag.GraphicAnnotationSequence, layers.size());
        Sequence layerSeq = attributes.newSequence(Tag.GraphicLayerSequence, layers.size());
        for (int i = 0; i < layers.size(); i++) {
            GraphicLayer layer = layers.get(i);
            Attributes l = new Attributes(2);
            l.setString(Tag.GraphicLayer, VR.CS, layer.getType().name());
            l.setInt(Tag.GraphicLayerOrder, VR.IS, i);
            layerSeq.add(l);

            Attributes a = new Attributes(2);
            a.setString(Tag.GraphicLayer, VR.CS, layer.getType().name());
            List<Graphic> graphics = getGraphicsByLayer(model, layer.getUuid());
            Sequence graphicSeq = a.newSequence(Tag.GraphicObjectSequence, graphics.size());

            for (Graphic graphic : graphics) {
                Attributes g = new Attributes(5);
                g.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL");
                g.setInt(Tag.GraphicDimensions, VR.US, 2);
                buildDicomGraphic(graphic, g);
                graphicSeq.add(g);
            }
            annotationSeq.add(a);
        }

    }

    private static List<Graphic> getGraphicsByLayer(GraphicModel model, String layerUid) {
        return model.getModels().stream().filter(g -> layerUid.equals(g.getLayer().getUuid()))
            .collect(Collectors.toList());
    }

    private static double[] getGraphicsPoints(List<Point2D.Double> pts) {
        double[] list = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            Point2D.Double p = pts.get(i);
            list[i * 2] = p.x;
            list[i * 2 + 1] = p.y;
        }
        return list;
    }

    private static void buildDicomGraphic(Graphic graphic, Attributes dcm) {

        List<Point2D.Double> pts;

        if (graphic instanceof RectangleGraphic) {
            boolean ellipse = graphic instanceof EllipseGraphic;
            dcm.setString(Tag.GraphicType, VR.CS, ellipse ? PrGraphicUtil.ELLIPSE : PrGraphicUtil.POLYLINE);
            RectangleGraphic rg = (RectangleGraphic) graphic;

            if (ellipse) {
                Point2D.Double wPt = rg.getHandlePoint(RectangleGraphic.eHandlePoint.W.getIndex());
                Point2D.Double ePt = rg.getHandlePoint(RectangleGraphic.eHandlePoint.E.getIndex());
                Point2D.Double nPt = rg.getHandlePoint(RectangleGraphic.eHandlePoint.N.getIndex());
                Point2D.Double sPt = rg.getHandlePoint(RectangleGraphic.eHandlePoint.S.getIndex());
                pts = wPt.distance(ePt) > nPt.distance(sPt) ? Arrays.asList(wPt, ePt, nPt, sPt)
                    : Arrays.asList(nPt, sPt, wPt, ePt);
            } else {
                pts = Arrays.asList(rg.getHandlePoint(RectangleGraphic.eHandlePoint.NW.getIndex()),
                    rg.getHandlePoint(RectangleGraphic.eHandlePoint.NE.getIndex()),
                    rg.getHandlePoint(RectangleGraphic.eHandlePoint.SE.getIndex()),
                    rg.getHandlePoint(RectangleGraphic.eHandlePoint.SW.getIndex()),
                    rg.getHandlePoint(RectangleGraphic.eHandlePoint.NW.getIndex()));
            }
        } else if (graphic instanceof ThreePointsCircleGraphic) {
            dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.CIRCLE);
            Point2D.Double centerPt = GeomUtil.getCircleCenter(graphic.getPts());
            pts = Arrays.asList(centerPt, graphic.getPts().get(0));
        } else if (graphic instanceof PointGraphic) {
            dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POINT);
            Point2D.Double centerPt = GeomUtil.getCircleCenter(graphic.getPts());
            pts = Arrays.asList(centerPt, graphic.getPts().get(0));
        } else {
            dcm.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
            pts = graphic.getPts();
            if (graphic instanceof GraphicArea) {
                pts.add(pts.get(0));
            }
        }

        dcm.setDouble(Tag.GraphicData, VR.FL, getGraphicsPoints(pts));
        dcm.setInt(Tag.NumberOfGraphicPoints, VR.US, pts.size());
        dcm.setString(Tag.GraphicFilled, VR.CS, graphic.getFilled() ? "Y" : "N");

        Sequence style = dcm.newSequence(Tag.LineStyleSequence, 1);
        Attributes styles = new Attributes();
        styles.setFloat(Tag.LineThickness, VR.FL, graphic.getLineThickness());

        if (graphic.getColorPaint() instanceof Color) {
            Color color = (Color) graphic.getColorPaint();
            float[] rgb = PresentationStateReader.ColorToLAB(color);
            if (rgb != null) {
                styles.setInt(Tag.PatternOnColorCIELabValue, VR.US, CIELab.convertToDicomLab(rgb));
            }
        }
        style.add(styles);
    }

    private static boolean saveToFile(File output, Attributes dcm) {
        if (dcm != null) {
            try (DicomOutputStream out = new DicomOutputStream(output)) {
                out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
                return true;
            } catch (IOException e) {
                LOGGER.error("Cannot write dicom PR: {}", e); //$NON-NLS-1$
            }
        }
        return false;
    }
}
