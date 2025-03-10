package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.KeyManipulator;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.SessionParser;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Utility methods that assist with working with IBaseDataObject's.
 */
public final class IBaseDataObjectHelper {
    /**
     * A logger instance.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IBaseDataObjectHelper.class);

    private IBaseDataObjectHelper() {}

    /**
     * Clones an IBaseDataObject equivalently to emissary.core.BaseDataObject.clone(), which duplicates some attributes.
     * 
     * A "fullClone" duplicates all attributes.
     * 
     * @param iBaseDataObject the IBaseDataObject to be cloned.
     * @param fullClone specifies if all fields should be cloned.
     * @return the clone of the IBaseDataObject passed in.
     */
    public static IBaseDataObject clone(final IBaseDataObject iBaseDataObject, final boolean fullClone) {
        Validate.notNull(iBaseDataObject, "Required: iBaseDataObject not null");

        final BaseDataObject bdo = new BaseDataObject();

        final SeekableByteChannelFactory sbcf = iBaseDataObject.getChannelFactory();
        if (sbcf != null) {
            bdo.setChannelFactory(sbcf);
        }

        bdo.replaceCurrentForm(null);
        final List<String> allCurrentForms = iBaseDataObject.getAllCurrentForms();
        for (int i = 0; i < allCurrentForms.size(); i++) {
            bdo.enqueueCurrentForm(allCurrentForms.get(i));
        }
        bdo.setHistory(iBaseDataObject.getTransformHistory());
        bdo.putParameters(iBaseDataObject.getParameters());
        for (final Map.Entry<String, byte[]> entry : iBaseDataObject.getAlternateViews().entrySet()) {
            bdo.addAlternateView(entry.getKey(), entry.getValue());
        }
        bdo.setPriority(iBaseDataObject.getPriority());
        bdo.setCreationTimestamp((Date) iBaseDataObject.getCreationTimestamp().clone());
        if (iBaseDataObject.getExtractedRecords() != null) {
            bdo.setExtractedRecords(iBaseDataObject.getExtractedRecords());
        }
        if (iBaseDataObject.getFilename() != null) {
            bdo.setFilename(iBaseDataObject.getFilename());
        }

        if (fullClone) {
            try {
                setPrivateFieldValue(bdo, "internalId", iBaseDataObject.getInternalId());
            } catch (IllegalAccessException | NoSuchFieldException e) {
                // Ignore any problems setting the internal id.
            }
            final String processingError = iBaseDataObject.getProcessingError();
            if (processingError != null) {
                bdo.addProcessingError(processingError.substring(0, processingError.length() - 1));
            }
            bdo.setFontEncoding(iBaseDataObject.getFontEncoding());
            bdo.setNumChildren(iBaseDataObject.getNumChildren());
            bdo.setNumSiblings(iBaseDataObject.getNumSiblings());
            bdo.setBirthOrder(iBaseDataObject.getBirthOrder());
            bdo.setHeader(iBaseDataObject.header() == null ? null : iBaseDataObject.header().clone());
            bdo.setFooter(iBaseDataObject.footer() == null ? null : iBaseDataObject.footer().clone());
            bdo.setHeaderEncoding(iBaseDataObject.getHeaderEncoding());
            bdo.setClassification(iBaseDataObject.getClassification());
            bdo.setBroken(iBaseDataObject.getBroken());
            bdo.setOutputable(iBaseDataObject.isOutputable());
            bdo.setId(iBaseDataObject.getId());
            bdo.setWorkBundleId(iBaseDataObject.getWorkBundleId());
            bdo.setTransactionId(iBaseDataObject.getTransactionId());
        }

        return bdo;
    }

    /**
     * This method reflectively sets a private method that is not normally accessible. This method should only be used when
     * the field must be set and there is no other way to do it. Ideally the class would be modified so that this method
     * call would not be necessary.
     * 
     * @param bdo the BaseDataObject to set the field on.
     * @param fieldName the name of the field to be set.
     * @param object the object that the field is to be set to.
     * @throws IllegalAccessException if this {@code Field} object is enforcing Java language access control and the
     *         underlying field is either inaccessible or final.
     * @throws NoSuchFieldException if a field with the specified name is not found.
     */
    public static void setPrivateFieldValue(final BaseDataObject bdo, final String fieldName, final Object object)
            throws IllegalAccessException, NoSuchFieldException {
        Validate.notNull(bdo, "Required: bdo not null");
        Validate.notNull(fieldName, "Required: fieldName not null");

        final Field field = bdo.getClass().getDeclaredField(fieldName);

        field.setAccessible(true); // NOSONAR intentional visibility change
        field.set(bdo, object); // NOSONAR intentional visibility change
    }

    /**
     * Used to propagate needed parent information to a sprouted child. NOTE: This is taken from
     * emissary.place.MultiFileServerPlace.
     * 
     * @param parentIBaseDataObject the source of parameters to be copied
     * @param childIBaseDataObject the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     * @param alwaysCopyMetadataKeys set of metadata keys to always copy from parent to child.
     * @param placeKey the place key to be added to the transform history.
     * @param kffDataObjectHandler the kffDataObjectHandler to use to create the kff hashes.
     */
    public static void addParentInformationToChild(final IBaseDataObject parentIBaseDataObject,
            final IBaseDataObject childIBaseDataObject, final boolean nullifyFileType,
            final Set<String> alwaysCopyMetadataKeys, final String placeKey,
            final KffDataObjectHandler kffDataObjectHandler) {
        Validate.notNull(parentIBaseDataObject, "Required: parentIBaseDataObject not null");
        Validate.notNull(childIBaseDataObject, "Required: childIBaseDataObject not null");
        Validate.notNull(alwaysCopyMetadataKeys, "Required: alwaysCopyMetadataKeys not null");
        Validate.notNull(placeKey, "Required: placeKey not null");
        Validate.notNull(kffDataObjectHandler, "Required: kffDataObjectHandler not null");

        // Copy over the classification
        if (parentIBaseDataObject.getClassification() != null) {
            childIBaseDataObject.setClassification(parentIBaseDataObject.getClassification());
        }

        // And some other things we configure to be always copied
        for (final String meta : alwaysCopyMetadataKeys) {
            final List<Object> parentVals = parentIBaseDataObject.getParameter(meta);

            if (parentVals != null) {
                childIBaseDataObject.putParameter(meta, parentVals);
            }
        }

        // Copy over the transform history up to this point
        childIBaseDataObject.setHistory(parentIBaseDataObject.getTransformHistory());
        childIBaseDataObject.appendTransformHistory(KeyManipulator.makeSproutKey(placeKey));
        try {
            childIBaseDataObject.putParameter(SessionParser.ORIG_DOC_SIZE_KEY,
                    Long.toString(childIBaseDataObject.getChannelSize()));
        } catch (IOException e) {
            // Do not add the ORIG_DOC_SIZE_KEY parameter.
        }

        // start over with no FILETYPE if so directed
        if (nullifyFileType) {
            childIBaseDataObject.setFileType(null);
        }

        // Set up the proper KFF/HASH information for the child
        // Change parent hit so it doesn't look like hit on the child
        KffDataObjectHandler.parentToChild(childIBaseDataObject);

        // Hash the new child data, overwrites parent hashes if any
        try {
            kffDataObjectHandler.hash(childIBaseDataObject, true);
        } catch (NoSuchAlgorithmException | IOException e) {
            // Do not add the hash parameters
        }
    }

    /**
     * Used to propagate needed parent information to a sprouted child. NOTE: This is taken from
     * emissary.place.MultiFileServerPlace.
     * 
     * @param parent the source of parameters to be copied
     * @param children the destination for parameters to be copied
     * @param nullifyFileType if true the child fileType is nullified after the copy
     * @param alwaysCopyMetadataKeys set of metadata keys to always copy from parent to child.
     * @param placeKey the place key to be added to the transform history.
     * @param kffDataObjectHandler the kffDataObjectHandler to use to create the kff hashes.
     */
    public static void addParentInformationToChildren(final IBaseDataObject parent, @Nullable final List<IBaseDataObject> children,
            final boolean nullifyFileType, final Set<String> alwaysCopyMetadataKeys, final String placeKey,
            final KffDataObjectHandler kffDataObjectHandler) {
        Validate.notNull(parent, "Required: parent not null");
        Validate.notNull(alwaysCopyMetadataKeys, "Required: alwaysCopyMetadataKeys not null");
        Validate.notNull(placeKey, "Required: placeKey not null");
        Validate.notNull(kffDataObjectHandler, "Required: kffDataObjectHandler not null");

        if (children != null) {
            int birthOrder = 1;

            final int totalNumSiblings = children.size();
            for (final IBaseDataObject child : children) {
                if (child == null) {
                    LOGGER.warn("addParentInformation with null child");
                    continue;
                }
                addParentInformationToChild(parent, child, nullifyFileType, alwaysCopyMetadataKeys, placeKey,
                        kffDataObjectHandler);
                child.setBirthOrder(birthOrder++);
                child.setNumSiblings(totalNumSiblings);
            }
        }
    }
}
