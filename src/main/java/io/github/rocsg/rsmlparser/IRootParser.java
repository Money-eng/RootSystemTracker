package io.github.rocsg.rsmlparser;

import io.github.rocsg.fijiyama.registration.ItkTransform;

import java.util.List;

/**
 * The IRootParser interface defines the methods required for parsing root models.
 */
public interface IRootParser {
    /**
     * Gets the ID of the root.
     *
     * @return The ID of the root.
     */
    String getId();

    /**
     * Gets the label of the root.
     *
     * @return The label of the root.
     */
    String getLabel();

    /**
     * Gets the PO accession of the root.
     *
     * @return The PO accession of the root.
     */
    String getPoAccession();

    /**
     * Gets the order of the root.
     *
     * @return The order of the root.
     */
    int getOrder();

    /**
     * Gets the properties of the root.
     *
     * @return A list of properties of the root.
     */
    List<Property> getProperties();

    /**
     * Gets the functions of the root.
     *
     * @return A list of functions of the root.
     */
    List<Function> getFunctions();

    /**
     * Gets the geometry of the root.
     *
     * @return The geometry of the root.
     */
    Geometry getGeometry();

    /**
     * Gets the children of the root.
     *
     * @return A list of child roots.
     */
    List<IRootParser> getChildren();

    /**
     * Adds a child root to the current root.
     *
     * @param child The child root to add.
     * @param rootModel The root model parser.
     */
    void addChild(IRootParser child, IRootModelParser rootModel);

    /**
     * Gets the parent of the root.
     *
     * @return The parent root.
     */
    IRootParser getParent();

    /**
     * Gets the ID of the parent root.
     *
     * @return The ID of the parent root.
     */
    String getParentId();

    /**
     * Gets the label of the parent root.
     *
     * @return The label of the parent root.
     */
    String getParentLabel();

    /**
     * Applies a transformation to the geometry of the root.
     *
     * @param transform The transformation to apply.
     * @param i The time index for the transformation.
     */
    void applyTransformToGeometry(ItkTransform transform, int i);

    /**
     * Sets the ID of the root.
     *
     * @param id The ID to set.
     */
    void setId(String id);
}