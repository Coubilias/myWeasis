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
package org.weasis.dicom.codec;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;

public class DicomSeries extends Series<DicomImageElement> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomSeries.class);
    private static volatile PreloadingTask preloadingTask;

    public DicomSeries(String subseriesInstanceUID) {
        this(TagW.SubseriesInstanceUID, subseriesInstanceUID, null);
    }

    public DicomSeries(TagW displayTag, String subseriesInstanceUID, List<DicomImageElement> c) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, displayTag, c, SortSeriesStack.instanceNumber);
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagW.SubseriesInstanceUID);
    }

    public boolean[] getImageInMemoryList() {
        boolean[] list;
        synchronized (this) {
            list = new boolean[medias.size()];
            for (int i = 0; i < medias.size(); i++) {
                if (medias.get(i).isImageInCache()) {
                    list[i] = true;
                }
            }
        }
        return list;
    }

    @Override
    public <T extends MediaElement<?>> void addMedia(T media) {
        if (media != null && media.getMediaReader() instanceof DcmMediaReader) {
            if (media instanceof DicomImageElement) {
                DicomImageElement dcm = (DicomImageElement) media;
                int insertIndex;
                synchronized (this) {
                    // add image or multi-frame sorted by Instance Number (0020,0013) order
                    int index = Collections.binarySearch(medias, dcm, SortSeriesStack.instanceNumber);
                    if (index < 0) {
                        insertIndex = -(index + 1);
                    } else {
                        // Should not happen because the instance number must be unique
                        insertIndex = index + 1;
                    }
                    if (insertIndex < 0 || insertIndex > medias.size()) {
                        insertIndex = medias.size();
                    }
                    add(insertIndex, dcm);
                }
                DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                if (model != null) {
                    model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                        new SeriesEvent(SeriesEvent.Action.AddImage, this, media)));
                }
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagW.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagW.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagW.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagW.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagW.SeriesDescription); //$NON-NLS-1$
        toolTips.append(Messages.getString("DicomSeries.date")); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(TagW.formatDateTime((Date) getTagValue(TagW.SeriesDate)));
        toolTips.append("<br>"); //$NON-NLS-1$
        if (getFileSize() > 0.0) {
            toolTips.append(Messages.getString("DicomSeries.size")); //$NON-NLS-1$
            toolTips.append(StringUtil.COLON_AND_SPACE);
            toolTips.append(FileUtil.formatSize(getFileSize()));
            toolTips.append("<br>"); //$NON-NLS-1$
        }
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public String getSeriesNumber() {
        Integer splitNb = (Integer) getTagValue(TagW.SplitSeriesNumber);
        Integer val = (Integer) getTagValue(TagW.SeriesNumber);
        String result = val == null ? "" : val.toString(); //$NON-NLS-1$
        return splitNb == null ? result : result + "-" + splitNb.toString(); //$NON-NLS-1$
    }

    @Override
    public String getMimeType() {
        String modality = (String) getTagValue(TagW.Modality);
        DicomSpecialElementFactory factory = DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality);
        if (factory != null) {
            return factory.getSeriesMimeType();
        }
        // Type for the default 2D viewer
        return DicomMediaIO.SERIES_MIMETYPE;
    }

    @Override
    public void dispose() {
        stopPreloading(this);
        super.dispose();
    }

    @Override
    public DicomImageElement getNearestImage(double location, int offset, Filter<DicomImageElement> filter,
        Comparator<DicomImageElement> sort) {
        Iterable<DicomImageElement> mediaList = getMedias(filter, sort);
        DicomImageElement nearest = null;
        int index = 0;
        int bestIndex = -1;
        synchronized (this) {
            double bestDiff = Double.MAX_VALUE;
            for (Iterator<DicomImageElement> iter = mediaList.iterator(); iter.hasNext();) {
                DicomImageElement dcm = iter.next();
                double[] val = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        nearest = dcm;
                        bestIndex = index;
                        if (diff == 0.0) {
                            break;
                        }
                    }
                }
                index++;
            }
        }
        if (offset > 0) {
            return getMedia(bestIndex + offset, filter, sort);
        }
        return nearest;
    }

    @Override
    public int getNearestImageIndex(double location, int offset, Filter<DicomImageElement> filter,
        Comparator<DicomImageElement> sort) {
        Iterable<DicomImageElement> mediaList = getMedias(filter, sort);
        int index = 0;
        int bestIndex = -1;
        synchronized (this) {
            double bestDiff = Double.MAX_VALUE;
            for (Iterator<DicomImageElement> iter = mediaList.iterator(); iter.hasNext();) {
                DicomImageElement dcm = iter.next();
                double[] val = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestIndex = index;
                        if (diff == 0.0) {
                            break;
                        }
                    }
                }
                index++;
            }
        }

        return (offset > 0) ? (bestIndex + offset) : bestIndex;
    }

    public static synchronized void startPreloading(DicomSeries series, List<DicomImageElement> imageList,
        int currentIndex) {
        if (series != null && imageList != null) {
            if (preloadingTask != null) {
                if (preloadingTask.getSeries() == series) {
                    return;
                }
                stopPreloading(preloadingTask.getSeries());
            }
            preloadingTask = new PreloadingTask(series, imageList, currentIndex);
            preloadingTask.start();
        }
    }

    public static synchronized void stopPreloading(DicomSeries series) {
        if (preloadingTask != null && preloadingTask.getSeries() == series) {
            PreloadingTask moribund = preloadingTask;
            preloadingTask = null;
            if (moribund != null) {
                moribund.setPreloading(false);
                moribund.interrupt();
            }
        }
    }

    static class PreloadingTask extends Thread {
        private volatile boolean preloading = true;
        private final int index;
        private final List<DicomImageElement> imageList;
        private final DicomSeries series;

        public PreloadingTask(DicomSeries series, List<DicomImageElement> imageList, int currentIndex) {
            this.series = series;
            this.imageList = imageList;
            this.index = currentIndex;
        }

        public synchronized boolean isPreloading() {
            return preloading;
        }

        public DicomSeries getSeries() {
            return series;
        }

        public List<DicomImageElement> getImageList() {
            return imageList;
        }

        public synchronized void setPreloading(boolean preloading) {
            this.preloading = preloading;
        }

        private void freeMemory() {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
            }
        }

        private long evaluateImageSize(DicomImageElement image) {
            Integer allocated = (Integer) image.getTagValue(TagW.BitsAllocated);
            Integer sample = (Integer) image.getTagValue(TagW.SamplesPerPixel);
            Integer rows = (Integer) image.getTagValue(TagW.Rows);
            Integer columns = (Integer) image.getTagValue(TagW.Columns);
            if (allocated != null && sample != null && rows != null && columns != null) {
                return (rows * columns * sample * allocated) / 8;
            }
            return 0L;
        }

        private void loadArrays(DicomImageElement img, DataExplorerModel model) {
            // Do not load an image if another process already loading it
            if (preloading && !img.isLoading()) {
                Boolean cache = (Boolean) img.getTagValue(TagW.ImageCache);
                if (cache == null || !cache) {
                    long start = System.currentTimeMillis();
                    PlanarImage i = img.getImage();
                    if (i != null) {
                        int tymin = i.getMinTileY();
                        int tymax = i.getMaxTileY();
                        int txmin = i.getMinTileX();
                        int txmax = i.getMaxTileX();
                        for (int tj = tymin; tj <= tymax; tj++) {
                            for (int ti = txmin; ti <= txmax; ti++) {
                                try {
                                    i.getTile(ti, tj);
                                } catch (OutOfMemoryError e) {
                                    LOGGER.error("Out of memory when loading image: {}", img); //$NON-NLS-1$
                                    freeMemory();
                                    return;
                                }
                            }
                        }
                    }
                    long stop = System.currentTimeMillis();
                    LOGGER.debug("Reading time: {} ms of image: {}", (stop - start), img.getMediaURI()); //$NON-NLS-1$
                    if (model != null) {
                        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                            new SeriesEvent(SeriesEvent.Action.loadImageInMemory, series, img)));
                    }
                }
            }
        }

        @Override
        public void run() {
            if (imageList != null) {
                DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
                int size = imageList.size();
                if (model == null || index < 0 || index >= size) {
                    return;
                }
                long imgSize = evaluateImageSize(imageList.get(index)) * size + 5000;
                long heapSize = Runtime.getRuntime().totalMemory();
                long heapFreeSize = Runtime.getRuntime().freeMemory();
                if (imgSize > heapSize / 3) {
                    if (imgSize > heapFreeSize) {
                        freeMemory();
                    }
                    double val = (double) heapFreeSize / imgSize;
                    int ajustSize = (int) (size * val) / 2;
                    int start = index - ajustSize;
                    if (start < 0) {
                        ajustSize -= start;
                        start = 0;
                    }
                    if (ajustSize > size) {
                        ajustSize = size;
                    }
                    for (int i = start; i < ajustSize; i++) {
                        loadArrays(imageList.get(i), model);
                    }
                } else {
                    if (imgSize > heapFreeSize) {
                        freeMemory();
                    }
                    for (DicomImageElement img : imageList) {
                        loadArrays(img, model);
                    }
                }
            }
        }
    }
}
