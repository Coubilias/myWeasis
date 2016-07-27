package org.weasis.acquire.explorer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.Global;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-13 - ylar - Creation
 */
public class AcquireManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireManager.class);
    private static final AcquireManager instance = new AcquireManager();

    public static final Global GLOBAL = new Global();

    private final Map<URI, AcquireImageInfo> images = new HashMap<>();
    private AcquireImageInfo currentAcquireImageInfo = null;

    private AcquireManager() {
    }

    public static AcquireManager getInstance() {
        return instance;
    }

    public static AcquireImageInfo getCurrentAcquireImageInfo() {
        return getInstance().currentAcquireImageInfo;
    }

    public static void setCurrentAcquireImageInfo(AcquireImageInfo imageInfo) {
        getInstance().currentAcquireImageInfo = imageInfo;
    }

    public static Collection<AcquireImageInfo> getAllAcquireImageInfo() {
        return getInstance().images.values();
    }

    public static AcquireImageInfo findById(String uid) {
        return getInstance().images.get(uid);
    }

    public static AcquireImageInfo findByImage(ImageElement image) {
        return getInstance().getAcquireImageInfo(image);
    }

    public static List<AcquireImageInfo> findbySerie(Serie serie) {
        return getInstance()._findbySerie(serie);
    }

    public static List<Serie> getBySeries() {
        return getInstance().images.entrySet().stream().map(e -> e.getValue().getSerie()).distinct().sorted()
            .collect(Collectors.toList());
    }

    public static Map<Serie, List<AcquireImageInfo>> groupBySeries() {
        return getInstance()._groupBySeries();
    }

    public static Serie getSerie(Serie searched) {
        return getInstance()._getSerie(searched);
    }

    public static void remove(ImageElement element) {
        Optional.ofNullable(element).ifPresent(e -> getInstance().images.remove(e.getMediaURI()));
    }

    public static void importImages(Serie searched, List<ImageElement> selected) {
        getInstance()._importImages(searched, selected);
    }

    public static List<ImageElement> toImageElement(List<? extends MediaElement<?>> medias) {
        return medias.stream().filter(m -> m instanceof ImageElement).map(ImageElement.class::cast)
            .collect(Collectors.toList());
    }

    /* ===================================== PRIVATE METHODS ===================================== */

    private AcquireImageInfo getAcquireImageInfo(ImageElement image) {
        if (image == null) {
            return null;
        }
        TagW tagUid = TagD.getUID(Level.INSTANCE);
        String UUID = (String) image.getTagValue(tagUid);
        if (UUID == null) {
            UUID = UIDUtils.createUID();
            image.setTag(tagUid, UUID);
        }

        AcquireImageInfo info = images.get(image.getMediaURI());
        if (info == null) {
            readTags(image);
            info = new AcquireImageInfo(image);
            images.put(image.getMediaURI(), info);
        }
        return info;
    }

    private void readTags(ImageElement element) {
        Optional<File> file = element.getFileCache().getOriginalFile();
        if (file.isPresent()) {
            Date date = null;
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(file.get());
                if (metadata != null) {
                    ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                    if (directory != null) {
                        date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                        if (date == null) {
                            date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME);
                        }

                        element.setTagNoNull(TagD.get(Tag.DateOfSecondaryCapture),
                            directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED));

                    }
                    ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                    if (ifd0 != null) {
                        element.setTagNoNull(TagD.get(Tag.Manufacturer), ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                        element.setTagNoNull(TagD.get(Tag.ManufacturerModelName),
                            ifd0.getString(ExifIFD0Directory.TAG_MODEL));

                        // try {
                        // int orientation = ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                        // } catch (MetadataException e) {
                        // e.printStackTrace();
                        // }

                        // AffineTransform affineTransform = new AffineTransform();
                        //
                        // switch (orientation) {
                        // case 1:
                        // break;
                        // case 2: // Flip X
                        // affineTransform.scale(-1.0, 1.0);
                        // affineTransform.translate(-width, 0);
                        // break;
                        // case 3: // PI rotation
                        // affineTransform.translate(width, height);
                        // affineTransform.rotate(Math.PI);
                        // break;
                        // case 4: // Flip Y
                        // affineTransform.scale(1.0, -1.0);
                        // affineTransform.translate(0, -height);
                        // break;
                        // case 5: // - PI/2 and Flip X
                        // affineTransform.rotate(-Math.PI / 2);
                        // affineTransform.scale(-1.0, 1.0);
                        // break;
                        // case 6: // -PI/2 and -width
                        // affineTransform.translate(height, 0);
                        // affineTransform.rotate(Math.PI / 2);
                        // break;
                        // case 7: // PI/2 and Flip
                        // affineTransform.scale(-1.0, 1.0);
                        // affineTransform.translate(-height, 0);
                        // affineTransform.translate(0, width);
                        // affineTransform.rotate(3 * Math.PI / 2);
                        // break;
                        // case 8: // PI / 2
                        // affineTransform.translate(0, width);
                        // affineTransform.rotate(3 * Math.PI / 2);
                        // break;
                        // default:
                        // break;
                        // }
                        //
                        // AffineTransformOp affineTransformOp = new AffineTransformOp(affineTransform,
                        // AffineTransformOp.TYPE_BILINEAR);
                        // BufferedImage destinationImage = new BufferedImage(originalImage.getHeight(),
                        // originalImage.getWidth(), originalImage.getType());
                        // destinationImage = affineTransformOp.filter(originalImage, destinationImage);
                    }
                }
            } catch (ImageProcessingException | IOException e) {
                LOGGER.error("Error when reading exif tags", e);
            }
            LocalDateTime dateTime = date == null ? LocalDateTime.from(Instant.ofEpochMilli(element.getLastModified()).atZone(ZoneId.systemDefault())) : TagUtil.toLocalDateTime(date);
            element.setTagNoNull(TagD.get(Tag.ContentDate), dateTime.toLocalDate());
            element.setTagNoNull(TagD.get(Tag.ContentTime), dateTime.toLocalTime());
        }
    }

    private List<Serie> _getBySeries() {
        return images.entrySet().stream().map(e -> e.getValue().getSerie()).distinct().sorted()
            .collect(Collectors.toList());
    }

    private List<AcquireImageInfo> _getAcquireImageInfoList() {
        return images.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());

    }

    private Map<Serie, List<AcquireImageInfo>> _groupBySeries() {
        return _getAcquireImageInfoList().stream().collect(Collectors.groupingBy(AcquireImageInfo::getSerie));
    }

    private Serie _getSerie(Serie searched) {
        Optional<Serie> serie = _getBySeries().stream().filter((s) -> s.equals(searched)).findFirst();
        if (serie.isPresent()) {
            return serie.get();
        }
        return searched;
    }

    public List<AcquireImageInfo> _findbySerie(Serie serie) {
        return _getAcquireImageInfoList().stream().filter(i -> i.getSerie().equals(serie)).collect(Collectors.toList());
    }

    private void _importImages(Serie searched, List<ImageElement> selected) {
        Serie serie = null;

        if (!Serie.Type.DATE.equals(searched.getType())) {
            serie = AcquireManager.getSerie(searched);
        }

        for (ImageElement element : selected) {
            AcquireImageInfo info = AcquireManager.findByImage(element);
            if (info == null) {
                continue;
            }

            if (Serie.Type.DATE.equals(searched.getType())) {
                LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage());
                serie = AcquireManager.getSerie(new Serie(date));
            }
            info.setSerie(serie);
        }
    }
}
