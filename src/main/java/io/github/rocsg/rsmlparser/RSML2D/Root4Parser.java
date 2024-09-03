package io.github.rocsg.rsmlparser.RSML2D;

import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsmlparser.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Root4Parser class implements the IRootParser interface and represents a root in the RSML2D format.
 */
public class Root4Parser implements IRootParser {
    public static int numFunctions; // Number of functions associated with the root
    public final LocalDateTime currentTime; // The current time associated with the root
    protected String id; // The ID of the root
    final String poAccession; // The Plant Ontology accession of the root
    final List<Function> functions; // List of functions associated with the root
    final List<Annotation> annotations; // List of annotations associated with the root
    private final String label; // The label of the root
    private final List<Property> properties; // List of properties associated with the root
    private final int order; // The order of the root
    public List<IRootParser> children; // List of child roots
    protected IRootParser parent; // The parent root
    private Geometry geometry; // The geometry of the root

    /**
     * Constructor initializing the root with the provided parameters.
     * @param id The ID of the root.
     * @param label The label of the root.
     * @param poAccession The Plant Ontology accession of the root.
     * @param parent The parent root.
     * @param order The order of the root.
     * @param currentTime The current time associated with the root.
     */
    public Root4Parser(String id, String label, String poAccession, Root4Parser parent, int order, LocalDateTime currentTime) {
        this.id = id;
        this.label = label;
        this.poAccession = poAccession;
        this.properties = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.annotations = new ArrayList<>();
        this.order = order;
        this.parent = parent;
        this.children = new ArrayList<>();
        if (parent != null) {
            parent.addChild(this, null);
        }
        numFunctions = 2;
        this.currentTime = currentTime;
    }

    /**
     * Gets the total list of children for the provided roots.
     * @param roots The list of roots.
     * @return The total list of children.
     */
    public static List<Root4Parser> getTotalChildrenList(List<Root4Parser> roots) {
        List<Root4Parser> totalChildren = new ArrayList<>();
        for (int i = roots.size() - 1; i >= 0; i--) {
            Root4Parser root = roots.get(i);
            for (IRootParser child : root.getChildren()) {
                if (totalChildren.stream().noneMatch(r -> r.getId().equals(child.getId())))
                    totalChildren.add((Root4Parser) child);
            }
        }
        return totalChildren;
    }

    /**
     * Checks if this root is equal to another object.
     * @param o The object to compare with.
     * @return True if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Root4Parser that = (Root4Parser) o;
        return Objects.equals(id, that.id);
    }

    /**
     * Returns the hash code of this root.
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Adds a property to the root.
     * @param property The property to add.
     */
    public void addProperty(Property property) {
        this.properties.add(property);
    }

    /**
     * Adds a function to the root.
     * @param function The function to add.
     */
    public void addFunction(Function function) {
        this.functions.add(function);
    }

    /**
     * Adds an annotation to the root.
     * @param annotation The annotation to add.
     */
    public void addAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
    }

    /**
     * Gets the list of annotations associated with the root.
     * @return The list of annotations.
     */
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * Gets the ID of the root.
     * @return The ID.
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Gets the label of the root.
     * @return The label.
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * Gets the Plant Ontology accession of the root.
     * @return The Plant Ontology accession.
     */
    @Override
    public String getPoAccession() {
        return poAccession;
    }

    /**
     * Gets the order of the root.
     * @return The order.
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * Gets the list of child roots.
     * @return The list of child roots.
     */
    @Override
    public List<IRootParser> getChildren() {
        return children;
    }

    /**
     * Adds a child root.
     * @param child The child root to add.
     * @param rootModel The root model parser.
     */
    @Override
    public void addChild(IRootParser child, IRootModelParser rootModel) {
        if (child instanceof Root4Parser) children.add(child);
        else {
            System.out.println("Only Root4Parser can be added as a child");
        }
    }

    /**
     * Gets the parent root.
     * @return The parent root.
     */
    @Override
    public IRootParser getParent() {
        return parent;
    }

    /**
     * Gets the ID of the parent root.
     * @return The parent ID.
     */
    @Override
    public String getParentId() {
        return parent == null ? null : parent.getId();
    }

    /**
     * Gets the label of the parent root.
     * @return The parent label.
     */
    @Override
    public String getParentLabel() {
        return parent == null ? null : parent.getLabel();
    }

    /**
     * Applies a transformation to the geometry of the root.
     * @param transform The transformation to apply.
     * @param i The time index for the transformation.
     */
    @Override
    public void applyTransformToGeometry(ItkTransform transform, int i) {
        if (geometry != null) {
            geometry.applyTransform(transform, i);
        }
    }

    /**
     * Sets the ID of the root.
     * @param id The ID to set.
     */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the list of properties associated with the root.
     * @return The list of properties.
     */
    @Override
    public List<Property> getProperties() {
        return properties;
    }

    /**
     * Gets the list of functions associated with the root.
     * @return The list of functions.
     */
    @Override
    public List<Function> getFunctions() {
        return functions;
    }

    /**
     * Gets the geometry of the root.
     * @return The geometry.
     */
    @Override
    public Geometry getGeometry() {
        return geometry;
    }

    /**
     * Sets the geometry of the root.
     * @param geometry The geometry to set.
     */
    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    /**
     * Returns a string representation of the Root4Parser.
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        String indent = "";
        int order = this.order;
        for (indent = ""; order > 0; order--) {
            indent += "\t";
        }
        return "\n" + indent + "Root4Parser{" +
                "\n" + indent + "\tid='" + id + '\'' +
                "\n" + indent + "\tlabel='" + label + '\'' +
                "\n" + indent + "\tproperties=" + properties +
                "\n" + indent + "\tgeometry=" + geometry +
                "\n" + indent + "\tfunctions=" + functions +
                "\n" + indent + "\tparent=" + parent +
                childIDandLbel2String("\n" + indent + "\t\t") +
                "\n" + indent + "\torder=" + order +
                '}';
    }

    /**
     * Returns a string representation of the child IDs and labels.
     * @param indent The indentation to use.
     * @return A string representation of the child IDs and labels.
     */
    private String childIDandLbel2String(String indent) {
        StringBuilder childID = new StringBuilder();
        if (children != null) {
            for (IRootParser child : children) {
                childID.append(indent).append(child.getId()).append(" : ").append(child.getLabel());
            }
        }
        return childID.toString();
    }

    /**
     * Scales the geometry of the root by the given scale factor.
     * @param scaleFactor The scale factor to apply.
     */
    public void scaleGeometry(double scaleFactor) {
        if (geometry != null) {
            geometry.scale(scaleFactor);
        }
    }

    /**
     * Checks if this root is equal to another root.
     * @param root The root to compare with.
     * @return True if the roots are equal, false otherwise.
     */
    public boolean equals(Root4Parser root) {
        if (!(this.id.equals(root.id))) return false;
        if (!(this.label.equals(root.label))) return false;
        if (!(this.poAccession.equals(root.poAccession))) return false;
        if (!(this.properties.equals(root.properties))) return false;
        if (!(this.functions.equals(root.functions))) return false;
        if (!(this.annotations.equals(root.annotations))) return false;
        if (this.order != root.order) return false;
        if (!(this.children.equals(root.children))) return false;
        if (!(this.parent.equals(root.parent))) return false;
        if (!(this.geometry.equals(root.geometry))) return false;
        return true;
    }
}