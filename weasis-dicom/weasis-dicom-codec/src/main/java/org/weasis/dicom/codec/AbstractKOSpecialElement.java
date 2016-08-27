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
package org.weasis.dicom.codec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.macro.HierachicalSOPInstanceReference;
import org.weasis.dicom.codec.macro.KODocumentModule;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;

public class AbstractKOSpecialElement extends DicomSpecialElement {

    protected Map<String, Map<String, SOPInstanceReferenceAndMAC>> sopInstanceReferenceMapBySeriesUID;
    protected Map<String, Map<String, SeriesAndInstanceReference>> seriesAndInstanceReferenceMapByStudyUID;
    protected Map<String, HierachicalSOPInstanceReference> hierachicalSOPInstanceReferenceByStudyUID;

    public AbstractKOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    @Override
    protected void initLabel() {
        /*
         * DICOM PS 3.3 - 2011 - C.17.3 SR Document Content Module
         *
         * Concept Name Code Sequence: mandatory when type is CONTAINER or the root content item.
         */
        StringBuilder buf = new StringBuilder(getLabelPrefix());

        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        Attributes item = dicom.getNestedDataset(Tag.ContentSequence);
        if (item != null) {
            buf.append(item.getString(Tag.TextValue));
        }
        label = buf.toString();
    }

    public Set<String> getReferencedStudyInstanceUIDSet() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return hierachicalSOPInstanceReferenceByStudyUID.keySet();
    }

    public boolean containsStudyInstanceUIDReference(String studyInstanceUIDReference) {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return hierachicalSOPInstanceReferenceByStudyUID.containsKey(studyInstanceUIDReference);
    }

    public Set<String> getReferencedSeriesInstanceUIDSet(String studyUID) {
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);
        return seriesAndInstanceReferenceBySeriesUID != null
            ? seriesAndInstanceReferenceMapByStudyUID.get(studyUID).keySet() : null;
    }

    public Set<String> getReferencedSeriesInstanceUIDSet() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        return sopInstanceReferenceMapBySeriesUID.keySet();
    }

    public boolean containsSeriesInstanceUIDReference(String seriesInstanceUIDReference) {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.containsKey(seriesInstanceUIDReference);
    }

    public Set<String> getReferencedSOPInstanceUIDSet() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        Set<String> referencedSOPInstanceUIDSet = new LinkedHashSet<String>();
        for (Map<String, SOPInstanceReferenceAndMAC> sopInstanceReference : sopInstanceReferenceMapBySeriesUID
            .values()) {
            referencedSOPInstanceUIDSet.addAll(sopInstanceReference.keySet());
        }
        return referencedSOPInstanceUIDSet;
    }
    
    public Set<String> getReferencedSOPInstanceUIDSet(String seriesUID) {
        if (seriesUID == null) {
            return getReferencedSOPInstanceUIDSet();
        }

        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);
        return sopInstanceReferenceBySOPInstanceUID != null ? sopInstanceReferenceBySOPInstanceUID.keySet() : null;
    }
    
    
    public Map<String, SOPInstanceReferenceAndMAC> getReferencedSOPInstanceUIDObject(String seriesUID) {
        if (seriesUID == null) {
            return null;
        }

        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.get(seriesUID);
    }

    public boolean containsSopInstanceUIDReference(String seriesInstanceUID, String sopInstanceUIDReference) {
        Set<String> sopInstanceUIDSet = getReferencedSOPInstanceUIDSet(seriesInstanceUID);
        return (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUIDReference));
    }

    public boolean containsSopInstanceUIDReference(String sopInstanceUIDReference) {
        return containsSopInstanceUIDReference(null, sopInstanceUIDReference);
    }

    public boolean isEmpty() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.isEmpty();
    }

    /**
     * Extract all the hierarchical SOP Instance References from the CurrentRequestedProcedureEvidences of the root
     * DicomObject into the dedicated Maps. These collections are used to improve access performance for data queries.
     *
     * @note This method should be called only once since any call to add/remove methods should keep in sync with the
     *       CurrentRequestedProcedureEvidences of the root DicomObject
     */
    protected void updateHierachicalSOPInstanceReference() {
        init();

        Attributes dcmItems = getMediaReader().getDicomObject();

        if (dcmItems != null) {
            Collection<HierachicalSOPInstanceReference> referencedStudySequence =
                HierachicalSOPInstanceReference.toHierachicalSOPInstanceReferenceMacros(
                    dcmItems.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));

            if (referencedStudySequence != null) {

                boolean sopInstanceExist = false;

                for (HierachicalSOPInstanceReference studyRef : referencedStudySequence) {
                    Collection<SeriesAndInstanceReference> referencedSeriesSequence = studyRef.getReferencedSeries();
                    if (referencedSeriesSequence == null) {
                        continue;
                    }

                    String studyUID = studyRef.getStudyInstanceUID();

                    for (SeriesAndInstanceReference serieRef : referencedSeriesSequence) {
                        Collection<SOPInstanceReferenceAndMAC> referencedSOPInstanceSequence =
                            serieRef.getReferencedSOPInstances();
                        if (referencedSOPInstanceSequence == null) {
                            continue;
                        }

                        String seriesUID = serieRef.getSeriesInstanceUID();

                        for (SOPInstanceReferenceAndMAC sopRef : referencedSOPInstanceSequence) {
                            String SOPInstanceUID = sopRef.getReferencedSOPInstanceUID();

                            if (SOPInstanceUID == null || SOPInstanceUID.equals("")) { //$NON-NLS-1$
                                continue;
                            }

                            Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
                                sopInstanceReferenceMapBySeriesUID.get(seriesUID);

                            if (sopInstanceReferenceBySOPInstanceUID == null) {
                                sopInstanceReferenceMapBySeriesUID.put(seriesUID, sopInstanceReferenceBySOPInstanceUID =
                                    new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
                            }

                            sopInstanceReferenceBySOPInstanceUID.put(SOPInstanceUID, sopRef);
                            sopInstanceExist = true;
                        }

                        if (sopInstanceExist) {

                            Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
                                seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

                            if (seriesAndInstanceReferenceBySeriesUID == null) {
                                seriesAndInstanceReferenceMapByStudyUID.put(studyUID,
                                    seriesAndInstanceReferenceBySeriesUID =
                                        new LinkedHashMap<String, SeriesAndInstanceReference>());
                            }

                            seriesAndInstanceReferenceBySeriesUID.put(seriesUID, serieRef);
                        }
                    }

                    if (sopInstanceExist) {
                        hierachicalSOPInstanceReferenceByStudyUID.put(studyUID, studyRef);
                    }

                }
            }
        }
    }

    private void init() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            hierachicalSOPInstanceReferenceByStudyUID = new LinkedHashMap<String, HierachicalSOPInstanceReference>();
        }
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            seriesAndInstanceReferenceMapByStudyUID =
                new LinkedHashMap<String, Map<String, SeriesAndInstanceReference>>();
        }
        if (sopInstanceReferenceMapBySeriesUID == null) {
            sopInstanceReferenceMapBySeriesUID = new LinkedHashMap<String, Map<String, SOPInstanceReferenceAndMAC>>();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObject(DicomImageElement dicomImage) {

        String studyInstanceUID = TagD.getTagValue(this, Tag.StudyInstanceUID, String.class);
        String seriesInstanceUID = TagD.getTagValue(this, Tag.SeriesInstanceUID, String.class);
        String sopInstanceUID = TagD.getTagValue(this, Tag.SOPInstanceUID, String.class);
        String sopClassUID = TagD.getTagValue(this, Tag.SOPClassUID, String.class);

        return addKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
    }

    public boolean addKeyObject(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
        String sopClassUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null) {
            // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
            sopInstanceReferenceMapBySeriesUID.put(seriesInstanceUID,
                sopInstanceReferenceBySOPInstanceUID = new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
        } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
            // the sopInstanceUID is already referenced, skip the job
            return false;
        }

        // Create the new SOPInstanceReferenceAndMAC and add to the SOPInstanceReferenceMap
        SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
        referencedSOP.setReferencedSOPInstanceUID(sopInstanceUID);
        referencedSOP.setReferencedSOPClassUID(sopClassUID);

        sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, referencedSOP);

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyInstanceUID,
                seriesAndInstanceReferenceBySeriesUID = new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSerie = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

        if (referencedSerie == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSerie = new SeriesAndInstanceReference();
            referencedSerie.setSeriesInstanceUID(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesInstanceUID, referencedSerie);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
            new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

        referencedSerie.setReferencedSOPInstances(referencedSOPInstances);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom =
            hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyInstanceUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyInstanceUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        List<SeriesAndInstanceReference> referencedSeries =
            new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

        hierachicalDicom.setReferencedSeries(referencedSeries);

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();

        List<HierachicalSOPInstanceReference> referencedStudies =
            new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObjects(String studyInstanceUID, String seriesInstanceUID, Collection<String> sopInstanceUIDs,
        String sopClassUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        boolean newReferenceAdded = false;

        for (String sopInstanceUID : sopInstanceUIDs) {

            if (sopInstanceReferenceBySOPInstanceUID == null) {
                // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
                sopInstanceReferenceMapBySeriesUID.put(seriesInstanceUID,
                    sopInstanceReferenceBySOPInstanceUID = new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
            } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
                // the sopInstanceUID is already referenced, keep continue
                continue;
            }

            // Create the new SOPInstanceReferenceAndMAC and add to the SOPInstanceReferenceMap
            SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
            referencedSOP.setReferencedSOPInstanceUID(sopInstanceUID);
            referencedSOP.setReferencedSOPClassUID(sopClassUID);

            sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, referencedSOP);

            newReferenceAdded = true;
        }

        if (!newReferenceAdded) {
            return false; // UID's parameters were already referenced , skip the job
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyInstanceUID,
                seriesAndInstanceReferenceBySeriesUID = new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSerie = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

        if (referencedSerie == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSerie = new SeriesAndInstanceReference();
            referencedSerie.setSeriesInstanceUID(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesInstanceUID, referencedSerie);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
            new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

        referencedSerie.setReferencedSOPInstances(referencedSOPInstances);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom =
            hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyInstanceUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyInstanceUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        List<SeriesAndInstanceReference> referencedSeries =
            new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

        hierachicalDicom.setReferencedSeries(referencedSeries);

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();

        List<HierachicalSOPInstanceReference> referencedStudies =
            new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObject(DicomImageElement dicomImage) {
        String studyInstanceUID = TagD.getTagValue(this, Tag.StudyInstanceUID, String.class);
        String seriesInstanceUID = TagD.getTagValue(this, Tag.SeriesInstanceUID, String.class);
        String sopInstanceUID = TagD.getTagValue(this, Tag.SOPInstanceUID, String.class);
        
        return removeKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID);
    }

    public boolean removeKeyObject(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null
            || sopInstanceReferenceBySOPInstanceUID.remove(sopInstanceUID) == null) {
            // UID's parameters were not referenced, skip the job
            return false;
        }

        if (sopInstanceReferenceBySOPInstanceUID.isEmpty()) {

            sopInstanceReferenceMapBySeriesUID.remove(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.remove(seriesInstanceUID);

            if (seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyInstanceUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyInstanceUID);
            } else {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                List<SeriesAndInstanceReference> referencedSeries =
                    new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

                hierachicalDicom.setReferencedSeries(referencedSeries);
            }

        } else {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
                new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

            referencedSeries.setReferencedSOPInstances(referencedSOPInstances);
        }

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();
        List<HierachicalSOPInstanceReference> referencedStudies = null;

        if (hierachicalSOPInstanceReferenceByStudyUID.isEmpty() == false) {
            referencedStudies =
                new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());
        }

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    //
    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObjects(String studyInstanceUID, String seriesInstanceUID,
        Collection<String> sopInstanceUIDs) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null) {
            return false;
        }

        boolean referenceRemoved = false;

        for (String sopInstanceUID : sopInstanceUIDs) {
            if (sopInstanceReferenceBySOPInstanceUID.remove(sopInstanceUID) != null) {
                referenceRemoved = true;
            }
        }

        if (!referenceRemoved) {
            return false; // UID's parameters were not referenced, skip the job
        }

        if (!sopInstanceReferenceBySOPInstanceUID.isEmpty()) {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
                new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

            referencedSeries.setReferencedSOPInstances(referencedSOPInstances);
        } else {

            sopInstanceReferenceMapBySeriesUID.remove(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.remove(seriesInstanceUID);

            if (!seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                List<SeriesAndInstanceReference> referencedSeries =
                    new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

                hierachicalDicom.setReferencedSeries(referencedSeries);
            } else {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyInstanceUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyInstanceUID);
            }
        }

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();
        List<HierachicalSOPInstanceReference> referencedStudies = null;

        if (!hierachicalSOPInstanceReferenceByStudyUID.isEmpty()) {
            referencedStudies =
                new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());
        }

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Filter<DicomImageElement> getSOPInstanceUIDFilter() {
        return new Filter<DicomImageElement>() {
            @Override
            public boolean passes(DicomImageElement dicom) {
                String seriesInstanceUID = TagD.getTagValue(dicom, Tag.SeriesInstanceUID, String.class);
                if (dicom == null || seriesInstanceUID == null) {
                    return false;
                }
                String sopInstanceUID = TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class);
                Set<String> referencedSOPInstanceUIDSet = getReferencedSOPInstanceUIDSet(seriesInstanceUID);

                return referencedSOPInstanceUIDSet == null ? false
                    : referencedSOPInstanceUIDSet.contains(sopInstanceUID);
            }
        };
    }
    
    public String getDocumentTitle() {
        Attributes dcmItems = getMediaReader().getDicomObject();
        if (dcmItems != null) {
            Attributes item = dcmItems.getNestedDataset(Tag.ConceptNameCodeSequence);
            if (item != null) {
                return item.getString(Tag.CodeMeaning, null);
            }
        }
        return null;
    }

}
