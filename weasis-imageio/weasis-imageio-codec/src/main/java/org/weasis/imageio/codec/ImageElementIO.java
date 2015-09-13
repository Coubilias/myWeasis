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
package org.weasis.imageio.codec;

import java.awt.RenderingHints;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;

public class ImageElementIO implements MediaReader<PlanarImage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageElementIO.class);

    protected URI uri;

    protected final String mimeType;

    private ImageElement image = null;

    private final Codec codec;

    public ImageElementIO(URI media, String mimeType, Codec codec) {
        if (media == null) {
            throw new IllegalArgumentException("media uri is null"); //$NON-NLS-1$
        }
        this.uri = media;
        if (mimeType == null) {
            this.mimeType = MimeInspector.UNKNOWN_MIME_TYPE;
        } else if ("image/x-ms-bmp".equals(mimeType)) { //$NON-NLS-1$
            this.mimeType = "image/bmp"; //$NON-NLS-1$
        } else {
            this.mimeType = mimeType;
        }
        this.codec = codec;
    }

    @Override
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getFile() != null) {
            ImageReader reader = getDefaultReader(mimeType);
            if (reader == null) {
                LOGGER.info("Cannot find a reader for the mime type: {}", mimeType); //$NON-NLS-1$
                return null;
            }
            PlanarImage img;
            RenderingHints hints = LayoutUtil.createTiledLayoutHints();
            ImageInputStream in = new FileImageInputStream(new RandomAccessFile(media.getFile(), "r")); //$NON-NLS-1$
            // hints.add(new RenderingHints(JAI.KEY_TILE_CACHE, null));
            ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
            pb.setParameter("Input", in); //$NON-NLS-1$
            pb.setParameter("Reader", reader); //$NON-NLS-1$
            img = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$

            // to avoid problem with alpha channel and png encoded in 24 and 32 bits
            img = PlanarImage.wrapRenderedImage(ImageFiler.getReadableImage(img));
            return img;
        }
        return null;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void reset() {

    }

    @Override
    public MediaElement<PlanarImage> getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        MediaElement element = getSingleImage();
        if (element != null) {
            return new MediaElement[] { element };
        }
        return null;
    }

    @Override
    public MediaSeries<ImageElement> getMediaSeries() {
        String sUID = null;
        MediaElement element = getSingleImage();
        if (element != null) {
            sUID = (String) element.getTagValue(TagW.SeriesInstanceUID);
        }
        if (sUID == null) {
            sUID = uri == null ? "unknown" : uri.toString(); //$NON-NLS-1$
        }
        MediaSeries<ImageElement> series = new Series<ImageElement>(TagW.SubseriesInstanceUID, sUID, TagW.FileName) { // $NON-NLS-1$

            @Override
            public String getMimeType() {
                synchronized (this) {
                    for (ImageElement img : medias) {
                        return img.getMimeType();
                    }
                }
                return null;
            }

            @Override
            public void addMedia(MediaElement media) {
                if (media instanceof ImageElement) {
                    this.add((ImageElement) media);
                    DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                    if (model != null) {
                        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                            new SeriesEvent(SeriesEvent.Action.AddImage, this, media)));
                    }
                }
            }
        };

        ImageElement img = getSingleImage();
        if (img != null) {
            series.add(getSingleImage());
            series.setTag(TagW.FileName, img.getName());
        }
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        return 1;
    }

    private ImageElement getSingleImage() {
        if (image == null) {
            image = new ImageElement(this, 0);
        }
        return image;
    }

    @Override
    public String getMediaFragmentMimeType(Object key) {
        return mimeType;
    }

    @Override
    public HashMap<TagW, Object> getMediaFragmentTags(Object key) {
        return new HashMap<TagW, Object>();
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "Image Codec: " + codec.getCodecName() }; //$NON-NLS-1$
    }

    public ImageReader getDefaultReader(String mimeType) {
        if (mimeType != null) {
            Iterator readers = ImageIO.getImageReadersByMIMEType(mimeType);
            if (readers.hasNext()) {
                return (ImageReader) readers.next();
            }
        }
        return null;
    }

    @Override
    public Object getTagValue(TagW tag) {
        MediaElement element = getSingleImage();
        if (element != null) {
            return element.getTagValue(tag);
        }
        return null;
    }

    @Override
    public void replaceURI(URI uri) {
        // TODO Auto-generated method stub

    }
}
