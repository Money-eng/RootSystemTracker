package io.github.rocsg.rsmlparser;

import io.github.rocsg.fijiyama.registration.ItkTransform;

import java.util.List;

// Define the interface
public interface IRootParser {
    String getId();

    String getLabel();

    String getPoAccession();

    int getOrder();

    List<Property> getProperties();

    List<Function> getFunctions();

    Geometry getGeometry();

    List<IRootParser> getChildren();

    void addChild(IRootParser child, IRootModelParser rootModel);

    IRootParser getParent();

    String getParentId();

    String getParentLabel();

    void applyTransformToGeometry(ItkTransform transform, int i);

    void setId(String id);
}

