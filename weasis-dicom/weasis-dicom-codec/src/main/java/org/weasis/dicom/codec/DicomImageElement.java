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

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.renderable.ParameterBlock;
import java.lang.ref.Reference;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.OpImage;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.LookupDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomImageUtils;
import org.weasis.dicom.codec.utils.LutParameters;

public class DicomImageElement extends ImageElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomImageElement.class);

    private static final SoftHashMap<LutParameters, LookupTableJAI> LUT_Cache =
        new SoftHashMap<LutParameters, LookupTableJAI>() {

            public Reference<? extends LookupTableJAI> getReference(LutParameters key) {
                return hash.get(key);
            }

            @Override
            public void removeElement(Reference<? extends LookupTableJAI> soft) {
                LutParameters key = reverseLookup.remove(soft);
                if (key != null) {
                    hash.remove(key);
                }
            }
        };

    private volatile List<PresetWindowLevel> windowingPresetCollection = null;
    private volatile Collection<LutShape> lutShapeCollection = null;

    public DicomImageElement(DcmMediaReader mediaIO, Object key) {
        super(mediaIO, key);

        initPixelConfiguration();
    }

    public void initPixelConfiguration() {
        this.pixelSizeX = 1.0;
        this.pixelSizeY = 1.0;
        this.pixelSpacingUnit = Unit.PIXEL;

        double[] val = null;
        String modality = (String) mediaIO.getTagValue(TagW.Modality);
        if (!"SC".equals(modality) && !"OT".equals(modality)) { //$NON-NLS-1$ //$NON-NLS-2$
            // Physical distance in mm between the center of each pixel (ratio in mm)
            val = (double[]) mediaIO.getTagValue(TagW.PixelSpacing);
            if (val == null || val.length != 2) {
                val = (double[]) mediaIO.getTagValue(TagW.ImagerPixelSpacing);
                // Follows D. Clunie recommendations
                pixelSizeCalibrationDescription = val == null ? null : Messages.getString("DicomImageElement.detector"); //$NON-NLS-1$

            } else {
                pixelSizeCalibrationDescription = (String) mediaIO.getTagValue(TagW.PixelSpacingCalibrationDescription);
            }
            if (val != null && val.length == 2 && val[0] > 0.0 && val[1] > 0.0) {
                /*
                 * Pixel Spacing = Row Spacing \ Column Spacing => (Y,X) The first value is the row spacing in mm, that
                 * is the spacing between the centers of adjacent rows, or vertical spacing. Pixel Spacing must be
                 * always positive, but some DICOMs have negative values
                 */
                setPixelSize(val[1], val[0]);
                pixelSpacingUnit = Unit.MILLIMETER;
            }

            // DICOM $C.11.1.1.2 Modality LUT and Rescale Type
            // Specifies the units of the output of the Modality LUT or rescale operation.
            // Defined Terms:
            // OD = The number in the LUT represents thousands of optical density. That is, a value of
            // 2140 represents an optical density of 2.140.
            // HU = Hounsfield Units (CT)
            // US = Unspecified
            // Other values are permitted, but are not defined by the DICOM Standard.
            pixelValueUnit = (String) getTagValue(TagW.RescaleType);
            if (pixelValueUnit == null) {
                // For some other modalities like PET
                pixelValueUnit = (String) getTagValue(TagW.Units);
            }
            if (pixelValueUnit == null && "CT".equals(modality)) { //$NON-NLS-1$
                pixelValueUnit = "HU"; //$NON-NLS-1$
            }

        }

        if (val == null) {
            int[] aspects = (int[]) mediaIO.getTagValue(TagW.PixelAspectRatio);
            if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
                /*
                 * Set the Pixel Aspect Ratio to the pixel size of the image to stretch the rendered image (for having
                 * square pixel on the display image)
                 */
                if (aspects[1] < aspects[0]) {
                    setPixelSize(1.0, (double) aspects[0] / (double) aspects[1]);
                } else {
                    setPixelSize((double) aspects[1] / (double) aspects[0], 1.0);
                }
            }
        }
    }

    /**
     * @return return the min value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    @Override
    public float getMinValue(HashMap<TagW, Object> params, boolean pixelPadding) {
        // Computes min and max as slope can be negative
        return Math.min(pixel2mLUT(super.getMinValue(params, pixelPadding), params, pixelPadding),
            pixel2mLUT(super.getMaxValue(params, pixelPadding), params, pixelPadding));
    }

    /**
     * @return return the max value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    @Override
    public float getMaxValue(HashMap<TagW, Object> params, boolean pixelPadding) {
        // Computes min and max as slope can be negative
        return Math.max(pixel2mLUT(super.getMinValue(params, pixelPadding), params, pixelPadding),
            pixel2mLUT(super.getMaxValue(params, pixelPadding), params, pixelPadding));
    }

    @Override
    protected boolean isGrayImage(RenderedImage source) {
        Boolean val = (Boolean) getTagValue(TagW.MonoChrome);
        return val == null ? true : val;
    }

    /**
     * Data representation of the pixel samples. Each sample shall have the same pixel representation. Enumerated
     * Values: 0000H = unsigned integer. 0001H = 2's complement
     *
     * @return true if Tag exist and if explicitly defined a signed
     * @see DICOM standard PS 3.3 - §C.7.6.3 - Image Pixel Module
     */

    public boolean isPixelRepresentationSigned() {
        Integer pixelRepresentation = (Integer) getTagValue(TagW.PixelRepresentation);
        return (pixelRepresentation != null) && (pixelRepresentation != 0);
    }

    public boolean isPhotometricInterpretationInverse(HashMap<TagW, Object> params) {
        String prLUTShape = (String) (params == null ? null : params.get(TagW.PresentationLUTShape));
        if (prLUTShape == null) {
            prLUTShape = (String) getTagValue(TagW.PresentationLUTShape);
        }
        boolean inverseLUT = prLUTShape != null ? "INVERSE".equals(prLUTShape) : "MONOCHROME1" //$NON-NLS-1$ //$NON-NLS-2$
            .equalsIgnoreCase(getPhotometricInterpretation());
        return inverseLUT;
    }

    /**
     * In the case where Rescale Slope and Rescale Intercept are used for modality pixel transformation, the output
     * ranges may be signed even if Pixel Representation is unsigned.
     *
     * @param pixelPadding
     *
     * @return
     */
    public boolean isModalityLutOutSigned(HashMap<TagW, Object> params, boolean pixelPadding) {
        boolean signed = isPixelRepresentationSigned();
        return getMinValue(params, pixelPadding) < 0 ? true : signed;
    }

    public int getBitsStored() {
        return (Integer) getTagValue(TagW.BitsStored);
    }

    public int getBitsAllocated() {
        return (Integer) getTagValue(TagW.BitsAllocated);
    }

    public float getRescaleIntercept(HashMap<TagW, Object> params) {
        Float prIntercept = (Float) (params != null ? params.get(TagW.RescaleIntercept) : null);
        Float intercept = prIntercept == null ? (Float) getTagValue(TagW.RescaleIntercept) : prIntercept;
        return (intercept == null) ? 0.0f : intercept.floatValue();
    }

    public float getRescaleSlope(HashMap<TagW, Object> params) {
        Float prSlope = (Float) (params != null ? params.get(TagW.RescaleSlope) : null);
        Float slope = prSlope == null ? (Float) getTagValue(TagW.RescaleSlope) : prSlope;
        return (slope == null) ? 1.0f : slope.floatValue();
    }

    public float pixel2mLUT(float pixelValue, HashMap<TagW, Object> params, boolean pixelPadding) {
        LookupTableJAI lookup = getModalityLookup(params, pixelPadding);
        if (lookup != null) {
            if (pixelValue >= lookup.getOffset() && pixelValue < lookup.getOffset() + lookup.getNumEntries()) {
                return lookup.lookup(0, (int) pixelValue);
            }
        }
        return pixelValue;
    }

    public int getMinAllocatedValue(HashMap<TagW, Object> params, boolean pixelPadding) {
        boolean signed = isModalityLutOutSigned(params, pixelPadding);
        int bitsAllocated = getBitsAllocated();
        int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
        return (signed ? -(maxValue + 1) : 0);
    }

    public int getMaxAllocatedValue(HashMap<TagW, Object> params, boolean pixelPadding) {
        boolean signed = isModalityLutOutSigned(params, pixelPadding);
        int bitsAllocated = getBitsAllocated();
        return signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
    }

    public int getAllocatedOutRangeSize() {
        int bitsAllocated = getBitsAllocated();
        return (1 << bitsAllocated) - 1;
    }

    /**
     * The value of Photometric Interpretation specifies the intended interpretation of the image pixel data.
     *
     * @return following values (MONOCHROME1 , MONOCHROME2 , PALETTE COLOR ....) Other values are permitted but the
     *         meaning is not defined by this Standard.
     */
    public String getPhotometricInterpretation() {
        return (String) getTagValue(TagW.PhotometricInterpretation);
    }

    public boolean isPhotometricInterpretationMonochrome() {
        String photometricInterpretation = getPhotometricInterpretation();

        return (photometricInterpretation != null && //
            ("MONOCHROME1".equalsIgnoreCase(photometricInterpretation) || "MONOCHROME2" //$NON-NLS-1$ //$NON-NLS-2$
                .equalsIgnoreCase(photometricInterpretation)));
    }

    /**
     *
     * Pixel Padding Value is used to pad grayscale images (those with a Photometric Interpretation of MONOCHROME1 or
     * MONOCHROME2)<br>
     * Pixel Padding Value specifies either a single value of this padding value, or when combined with Pixel Padding
     * Range Limit, a range of values (inclusive) that are padding.<br>
     * <br>
     * <b>Note :</b> It is explicitly described in order to prevent display applications from taking it into account
     * when determining the dynamic range of an image, since the Pixel Padding Value will be outside the range between
     * the minimum and maximum values of the pixels in the native image
     *
     * @see DICOM standard PS 3.3 - §C.7.5.1.1.2 - Pixel Padding Value and Pixel Padding Range Limit
     */

    public Integer getPaddingValue() {
        return (Integer) getTagValue(TagW.PixelPaddingValue);
    }

    /**
     * @see getPaddingValue()
     */
    public Integer getPaddingLimit() {
        return (Integer) getTagValue(TagW.PixelPaddingRangeLimit);

    }

    public LutParameters getLutParameters(HashMap<TagW, Object> params, boolean pixelPadding, LookupTableJAI mLUTSeq,
        boolean inversePaddingMLUT) {
        Integer paddingValue = getPaddingValue();

        boolean isSigned = isPixelRepresentationSigned();
        int bitsStored = getBitsStored();
        float intercept = getRescaleIntercept(params);
        float slope = getRescaleSlope(params);

        // No need to have a modality lookup table
        if (bitsStored > 16 || (slope == 1.0f && intercept == 0.0f && paddingValue == null)) {
            return null;
        }

        Integer paddingLimit = getPaddingLimit();
        boolean outputSigned = false;
        int bitsOutputLut;
        if (mLUTSeq == null) {
            float minValue = super.getMinValue(params, pixelPadding) * slope + intercept;
            float maxValue = super.getMaxValue(params, pixelPadding) * slope + intercept;
            bitsOutputLut = Integer.SIZE - Integer.numberOfLeadingZeros(Math.round(maxValue - minValue));
            outputSigned = minValue < 0 ? true : isSigned;
            if (outputSigned && bitsOutputLut <= 8) {
                // Allows to handle negative values with 8-bit image
                bitsOutputLut = 9;
            }
        } else {
            bitsOutputLut = mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE ? 8 : 16;
        }
        return new LutParameters(intercept, slope, pixelPadding, paddingValue, paddingLimit, bitsStored, isSigned,
            outputSigned, bitsOutputLut, inversePaddingMLUT);

    }

    public LookupTableJAI getModalityLookup(HashMap<TagW, Object> params, boolean pixelPadding) {
        return getModalityLookup(params, pixelPadding, false);
    }

    /**
     * DICOM PS 3.3 $C.11.1 Modality LUT Module
     *
     * The LUT Data contains the LUT entry values.
     *
     * The output range of the Modality LUT Module depends on whether or not Rescale Slope (0028,1053) and Rescale
     * Intercept (0028,1052) or the Modality LUT Sequence (0028,3000) are used. In the case where Rescale Slope and
     * Rescale Intercept are used, the output ranges from (minimum pixel value*Rescale Slope+Rescale Intercept) to
     * (maximum pixel value*Rescale - Slope+Rescale Intercept), where the minimum and maximum pixel values are
     * determined by Bits Stored and Pixel Representation. Note: This range may be signed even if Pixel Representation
     * is unsigned.
     *
     * In the case where the Modality LUT Sequence is used, the output range is from 0 to 2n-1 where n is the third
     * value of LUT Descriptor. This range is always unsigned.
     *
     * @param pixelPadding
     * @param inverseLUT
     * @return the modality lookup table
     */
    protected LookupTableJAI getModalityLookup(HashMap<TagW, Object> params, boolean pixelPadding,
        boolean inverseLUTAction) {
        Integer paddingValue = getPaddingValue();
        LookupTableJAI prModLut = (LookupTableJAI) (params != null ? params.get(TagW.ModalityLUTData) : null);
        final LookupTableJAI mLUTSeq = prModLut == null ? (LookupTableJAI) getTagValue(TagW.ModalityLUTData) : prModLut;
        if (mLUTSeq != null) {
            if (!pixelPadding || paddingValue == null) {
                if (super.getMinValue(params, false) >= mLUTSeq.getOffset()
                    && super.getMaxValue(params, false) < mLUTSeq.getOffset() + mLUTSeq.getNumEntries()) {
                    return mLUTSeq;
                } else if (prModLut == null) {
                    // Remove MLut as it cannot be used.
                    tags.remove(TagW.ModalityLUTData);
                    LOGGER.warn(
                        "Pixel values doesn't match to Modality LUT sequence table. So the Modality LUT is not applied."); //$NON-NLS-1$
                }
            } else {
                LOGGER.warn("Cannot apply Modality LUT sequence and Pixel Padding"); //$NON-NLS-1$
            }
        }

        boolean inverseLut = isPhotometricInterpretationInverse(params);
        if (pixelPadding) {
            inverseLut ^= inverseLUTAction;
        }
        LutParameters lutparams = getLutParameters(params, pixelPadding, mLUTSeq, inverseLut);
        // Not required to have a modality lookup table
        if (lutparams == null) {
            return null;
        }
        LookupTableJAI modalityLookup = LUT_Cache.get(lutparams);

        if (modalityLookup != null) {
            return modalityLookup;
        }

        if (modalityLookup == null) {
            if (mLUTSeq != null) {
                if (mLUTSeq.getNumBands() == 1) {
                    if (mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE) {
                        byte[] data = mLUTSeq.getByteData(0);
                        if (data != null) {
                            modalityLookup = new LookupTableJAI(data, mLUTSeq.getOffset(0));
                        }
                    } else {
                        short[] data = mLUTSeq.getShortData(0);
                        if (data != null) {
                            modalityLookup = new LookupTableJAI(data, mLUTSeq.getOffset(0),
                                mLUTSeq.getData() instanceof DataBufferUShort);
                        }
                    }
                }
                if (modalityLookup == null) {
                    modalityLookup = mLUTSeq;
                }
            } else {
                modalityLookup = DicomImageUtils.createRescaleRampLut(lutparams);
            }
        }

        if (isPhotometricInterpretationMonochrome()) {
            DicomImageUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutparams);
        }
        LUT_Cache.put(lutparams, modalityLookup);
        return modalityLookup;
    }

    /**
     *
     * @param window
     * @param level
     * @param shape
     * @param fillLutOutside
     * @param pixelPadding
     *
     * @return 8 bits unsigned Lookup Table
     */
    public LookupTableJAI getVOILookup(HashMap<TagW, Object> params, Float window, Float level, Float minLevel,
        Float maxLevel, LutShape shape, boolean fillLutOutside, boolean pixelPadding) {

        if (window == null || level == null || shape == null || minLevel == null || maxLevel == null) {
            return null;
        }

        if (getPaddingValue() != null && isPhotometricInterpretationMonochrome()) {
            /*
             * When pixel padding is activated, VOI LUT must extend to the min bit stored value when MONOCHROME2 and to
             * the max bit stored value when MONOCHROME1.
             *
             * C.7.5.1.1.2 Pixel Padding Value and Pixel Padding Range Limit If Photometric Interpretation
             *
             * (0028,0004) is MONOCHROME2, Pixel Padding Value (0028,0120) shall be less than (closer to or equal to the
             * minimum possible pixel value) or equal to Pixel Padding Range Limit (0028,0121). If Photometric
             * Interpretation (0028,0004) is MONOCHROME1, Pixel Padding Value (0028,0120) shall be greater than (closer
             * to or equal to the maximum possible pixel value) or equal to Pixel Padding Range Limit (0028,0121).
             */

            // Create a VOI LUT with pixel padding values at the extremity of the allocated values.
            fillLutOutside = true;
        }
        int minValue;
        int maxValue;
        if (fillLutOutside) {
            minValue = getMinAllocatedValue(params, pixelPadding);
            maxValue = getMaxAllocatedValue(params, pixelPadding);
        } else {
            minValue = minLevel.intValue();
            maxValue = maxLevel.intValue();
        }

        return DicomImageUtils.createWindowLevelLut(shape, window, level, minValue, maxValue, 8, false,
            isPhotometricInterpretationInverse(params));
    }

    /**
     * @return default as first element of preset List <br>
     *         Note : null should never be returned since auto is at least one preset
     */
    public PresetWindowLevel getDefaultPreset(boolean pixelPadding) {
        List<PresetWindowLevel> presetList = getPresetList(pixelPadding);
        return (presetList != null && presetList.size() > 0) ? presetList.get(0) : null;
    }

    public List<PresetWindowLevel> getPresetList(boolean pixelPadding) {
        if (windowingPresetCollection == null && isImageAvailable()) {
            windowingPresetCollection = PresetWindowLevel.getPresetCollection(this, this.tags, pixelPadding);
        }
        return windowingPresetCollection;
    }

    public boolean containsPreset(PresetWindowLevel preset) {
        if (preset != null) {
            List<PresetWindowLevel> collection = getPresetList(false);
            if (collection != null) {
                return collection.contains(preset);
            }
        }
        return false;
    }

    public Collection<LutShape> getLutShapeCollection(boolean pixelPadding) {
        if (lutShapeCollection != null) {
            return lutShapeCollection;
        }

        lutShapeCollection = new LinkedHashSet<LutShape>();
        List<PresetWindowLevel> presetList = getPresetList(pixelPadding);
        if (presetList != null) {
            for (PresetWindowLevel preset : presetList) {
                lutShapeCollection.add(preset.getLutShape());
            }
        }
        lutShapeCollection.addAll(LutShape.DEFAULT_FACTORY_FUNCTIONS);

        return lutShapeCollection;
    }

    /**
     *
     * @param imageSource
     * @param pixelPadding
     * @return Histogram of the image source after modality lookup rescaled
     */

    public Histogram getHistogram(RenderedImage imageSource, HashMap<TagW, Object> params, boolean pixelPadding) {
        LookupTableJAI lookup = getModalityLookup(params, pixelPadding);
        if (imageSource == null || lookup == null) {
            return null;
        }
        // TODO instead of computing histo from image get Dicom attribute if present. Handle pixel padding!

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(imageSource);
        pb.add(lookup);
        final RenderedImage imageModalityTransformed = JAI.create("lookup", pb, null); //$NON-NLS-1$

        pb.removeSources();
        pb.removeParameters();

        pb.addSource(imageModalityTransformed);
        pb.add(null); // No ROI
        pb.add(1); // Sampling
        pb.add(1); // periods
        pb.add(new int[] { getAllocatedOutRangeSize() }); // Num. bins.
        pb.add(new double[] { getMinAllocatedValue(params, pixelPadding) }); // Min value to be considered.
        pb.add(new double[] { getMaxAllocatedValue(params, pixelPadding) }); // Max value to be considered.

        RenderedOp op = JAI.create("histogram", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        return (Histogram) op.getProperty("histogram"); //$NON-NLS-1$
    }

    @Override
    protected void findMinMaxValues(RenderedImage img, boolean exclude8bitImage) {
        /*
         * This function can be called several times from the inner class Load. min and max will be computed only once.
         */

        if (img != null && !isImageAvailable()) {
            // Cannot trust min and max values!
            // Integer min = (Integer) getTagValue(TagW.SmallestImagePixelValue);
            // Integer max = (Integer) getTagValue(TagW.LargestImagePixelValue);
            int bitsStored = getBitsStored();
            int bitsAllocated = getBitsAllocated();

            minPixelValue = null;
            maxPixelValue = null;

            boolean monochrome = isPhotometricInterpretationMonochrome();
            if (monochrome) {
                Integer paddingValue = getPaddingValue();
                if (paddingValue != null) {
                    Integer paddingLimit = getPaddingLimit();
                    int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
                    findMinMaxValues(img, paddingValueMin, paddingValueMax);
                }
            }

            if (!isImageAvailable()) {
                super.findMinMaxValues(img, !monochrome);
            }

            if (bitsStored < bitsAllocated && isImageAvailable()) {
                boolean isSigned = isPixelRepresentationSigned();
                int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
                int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;
                if (minPixelValue < minInValue || maxPixelValue > maxInValue) {
                    /*
                     *
                     *
                     * When the image contains values outside the bits stored values, the bits stored is replaced by the
                     * bits allocated for having a LUT which handles all the values.
                     *
                     * Overlays in pixel data should be masked before finding min and max.
                     */
                    setTag(TagW.BitsStored, bitsAllocated);
                }
            }
            /*
             * Lazily compute image pixel transformation here since inner class Load is called from a separate and
             * dedicated worker Thread. Also, it will be computed only once
             *
             * Considering that the default pixel padding option is true and Inverse LUT action is false
             */
            getModalityLookup(null, true);
        }
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     *
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     */
    private void findMinMaxValues(RenderedImage img, double paddingValueMin, double paddingValueMax) {
        if (img != null) {
            int datatype = img.getSampleModel().getDataType();
            if (datatype == DataBuffer.TYPE_BYTE) {
                this.minPixelValue = 0.0f;
                this.maxPixelValue = 255.0f;
            } else {
                RenderedOp dst = ImageStatisticsDescriptor.create(img, (ROI) null, 1, 1, new Double(paddingValueMin),
                    new Double(paddingValueMax), null);
                // To ensure this image won't be stored in tile cache
                ((OpImage) dst.getRendering()).setTileCache(null);

                double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                int numBands = dst.getSampleModel().getNumBands();

                for (int i = 0; i < numBands; i++) {
                    min = Math.min(min, extrema[0][i]);
                    max = Math.max(max, extrema[1][i]);
                }
                this.minPixelValue = Double.valueOf(min).floatValue();
                this.maxPixelValue = Double.valueOf(max).floatValue();
                // Handle special case when min and max are equal, ex. black image
                // + 1 to max enables to display the correct value
                if (this.minPixelValue.equals(this.maxPixelValue)) {
                    this.maxPixelValue += 1.0f;
                }
            }
        }
    }

    public double[] getDisplayPixelSize() {
        return new double[] { pixelSizeX, pixelSizeY };
    }

    public float getFullDynamicWidth(HashMap<TagW, Object> params, boolean pixelPadding) {
        return getMaxValue(params, pixelPadding) - getMinValue(params, pixelPadding);
    }

    public float getFullDynamicCenter(HashMap<TagW, Object> params, boolean pixelPadding) {
        float minValue = getMinValue(params, pixelPadding);
        float maxValue = getMaxValue(params, pixelPadding);
        return minValue + (maxValue - minValue) / 2.f;
    }

    @Override
    public LutShape getDefaultShape(boolean pixelPadding) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding);
        return (defaultPreset != null) ? defaultPreset.getLutShape() : super.getDefaultShape(pixelPadding);
    }

    @Override
    public float getDefaultWindow(boolean pixelPadding) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding);
        return (defaultPreset != null) ? defaultPreset.getWindow() : super.getDefaultWindow(pixelPadding);
    }

    @Override
    public float getDefaultLevel(boolean pixelPadding) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding);
        return (defaultPreset != null) ? defaultPreset.getLevel() : super.getDefaultLevel(pixelPadding);

    }

    /**
     * @param imageSource
     *            is the RenderedImage upon which transformation is done
     * @param window
     *            is width from low to high input values around level. If null, getDefaultWindow() value is used
     * @param level
     *            is center of window values. If null, getDefaultLevel() value is used
     * @param lutShape
     *            defines the shape of applied lookup table transformation. If null getDefaultLutShape() is used
     * @param pixPadding
     *            indicates if some padding values defined in ImageElement should be applied or not. If null, TRUE is
     *            considered
     * @return
     */
    @Override
    public RenderedImage getRenderedImage(final RenderedImage imageSource, HashMap<String, Object> params) {
        if (imageSource == null) {
            return null;
        }

        SampleModel sampleModel = imageSource.getSampleModel();
        if (sampleModel == null) {
            return null;
        }

        Float window = null;
        Float level = null;
        Float levelMin = null;
        Float levelMax = null;
        LutShape lutShape = null;
        Boolean pixelPadding = null;
        Boolean inverseLUT = null;
        Boolean fillLutOutside = null;
        Boolean wlOnColorImage = null;
        PRSpecialElement pr = null;
        LookupTableJAI prLutData = null;
        HashMap<TagW, Object> prTags = null;

        if (params != null) {
            window = (Float) params.get(ActionW.WINDOW.cmd());
            level = (Float) params.get(ActionW.LEVEL.cmd());
            levelMin = (Float) params.get(ActionW.LEVEL_MIN.cmd());
            levelMax = (Float) params.get(ActionW.LEVEL_MAX.cmd());
            lutShape = (LutShape) params.get(ActionW.LUT_SHAPE.cmd());
            pixelPadding = (Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd());
            inverseLUT = (Boolean) params.get(PseudoColorOp.P_LUT_INVERSE);
            fillLutOutside = (Boolean) params.get(WindowOp.P_FILL_OUTSIDE_LUT);
            wlOnColorImage = (Boolean) params.get(WindowOp.P_APPLY_WL_COLOR);
            pr = (PRSpecialElement) params.get(WindowAndPresetsOp.P_PR_ELEMENT);
            if (pr != null) {
                prLutData = (LookupTableJAI) pr.getTagValue(TagW.PRLUTsData);
                prTags = pr.getTags();
            }

        }

        boolean pixPadding = JMVUtils.getNULLtoTrue(pixelPadding);
        boolean invLUT = JMVUtils.getNULLtoFalse(inverseLUT);
        float windowValue = (window == null) ? getDefaultWindow(pixPadding) : window;
        float levelValue = (level == null) ? getDefaultLevel(pixPadding) : level;
        LutShape lut = (lutShape == null) ? getDefaultShape(pixPadding) : lutShape;
        float minLevel;
        float maxLevel;
        if (levelMin == null || levelMax == null) {
            minLevel = Math.min(levelValue - windowValue / 2.0f, getMinValue(prTags, pixPadding));
            maxLevel = Math.max(levelValue + windowValue / 2.0f, getMaxValue(prTags, pixPadding));
        } else {
            minLevel = Math.min(levelMin, getMinValue(prTags, pixPadding));
            maxLevel = Math.max(levelMax, getMaxValue(prTags, pixPadding));
        }

        int datatype = sampleModel.getDataType();

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            LookupTableJAI modalityLookup = getModalityLookup(prTags, pixPadding, invLUT);

            // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, new ImageLayout(imageSource));
            RenderedImage imageModalityTransformed =
                modalityLookup == null ? imageSource : LookupDescriptor.create(imageSource, modalityLookup, null);

            /*
             * C.11.2.1.2 Window center and window width
             *
             * Theses Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
             * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
             */
            if (!JMVUtils.getNULLtoFalse(wlOnColorImage) && !isPhotometricInterpretationMonochrome()) {
                /*
                 * If photometric interpretation is not monochrome do not apply VOILUT. It is necessary for
                 * PALETTE_COLOR.
                 */
                return imageModalityTransformed;
            }

            LookupTableJAI voiLookup = null;
            if (prLutData == null || lut.getLookup() != null) {
                voiLookup = getVOILookup(prTags, windowValue, levelValue, minLevel, maxLevel, lut,
                    JMVUtils.getNULLtoFalse(fillLutOutside), pixPadding);
            }

            if (prLutData == null) {
                // BUG fix: for some images the color model is null. Creating 8 bits gray model layout fixes this issue.
                return LookupDescriptor.create(imageModalityTransformed, voiLookup,
                    LayoutUtil.createGrayRenderedImage());
            }

            RenderedImage imageVoiTransformed = voiLookup == null ? imageModalityTransformed
                : LookupDescriptor.create(imageModalityTransformed, voiLookup, null);
            // BUG fix: for some images the color model is null. Creating 8 bits gray model layout fixes this issue.
            return LookupDescriptor.create(imageVoiTransformed, prLutData, LayoutUtil.createGrayRenderedImage());

        } else if (datatype == DataBuffer.TYPE_INT || datatype == DataBuffer.TYPE_FLOAT
            || datatype == DataBuffer.TYPE_DOUBLE) {
            double low = minLevel;
            double high = maxLevel;
            double range = high - low;
            if (range < 1.0) {
                range = 1.0;
            }
            double slope = 255.0 / range;
            double y_int = 255.0 - slope * high;

            ParameterBlock pb = new ParameterBlock();
            pb.addSource(imageSource);
            pb.add(new double[] { slope });
            pb.add(new double[] { y_int });
            RenderedOp rescale = JAI.create("rescale", pb, null); //$NON-NLS-1$

            // produce a byte image
            pb = new ParameterBlock();
            pb.addSource(rescale);
            pb.add(DataBuffer.TYPE_BYTE);
            return JAI.create("format", pb, null); //$NON-NLS-1$
        }
        return null;
    }

    public GeometryOfSlice getDispSliceGeometry() {
        // The geometry is adapted to get square pixel as all the images are displayed with square pixel.
        double[] imgOr = (double[]) getTagValue(TagW.ImageOrientationPatient);
        if (imgOr != null && imgOr.length == 6) {
            double[] pos = (double[]) getTagValue(TagW.ImagePositionPatient);
            if (pos != null && pos.length == 3) {
                Double sliceTickness = (Double) getTagValue(TagW.SliceThickness);
                if (sliceTickness == null) {
                    sliceTickness = getPixelSize();
                }
                double[] spacing = { getPixelSize(), getPixelSize(), sliceTickness };
                Integer rows = (Integer) getTagValue(TagW.Rows);
                Integer columns = (Integer) getTagValue(TagW.Columns);
                if (rows != null && columns != null && rows > 0 && columns > 0) {
                    // SliceTickness is only use in IntersectVolume
                    // Multiply rows and columns by getZoomScale() to have square pixel image size
                    return new GeometryOfSlice(new double[] { imgOr[0], imgOr[1], imgOr[2] },
                        new double[] { imgOr[3], imgOr[4], imgOr[5] }, pos, spacing, sliceTickness,
                        new double[] { rows * getRescaleY(), columns * getRescaleX(), 1 });
                }
            }
        }
        return null;
    }

    public GeometryOfSlice getSliceGeometry() {
        double[] imgOr = (double[]) getTagValue(TagW.ImageOrientationPatient);
        if (imgOr != null && imgOr.length == 6) {
            double[] pos = (double[]) getTagValue(TagW.ImagePositionPatient);
            if (pos != null && pos.length == 3) {
                Double sliceTickness = (Double) getTagValue(TagW.SliceThickness);
                if (sliceTickness == null) {
                    sliceTickness = getPixelSize();
                }
                double[] pixSize = getDisplayPixelSize();
                double[] spacing = { pixSize[0], pixSize[1], sliceTickness };
                Integer rows = (Integer) getTagValue(TagW.Rows);
                Integer columns = (Integer) getTagValue(TagW.Columns);
                if (rows != null && columns != null && rows > 0 && columns > 0) {
                    return new GeometryOfSlice(new double[] { imgOr[0], imgOr[1], imgOr[2] },
                        new double[] { imgOr[3], imgOr[4], imgOr[5] }, pos, spacing, sliceTickness,
                        new double[] { rows, columns, 1 });
                }
            }
        }
        return null;
    }

}
