package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IRootModelParser {

    IRootModelParser createRootModel(IRootModelParser rootModel, float time);

    IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor);
}


// Metadata.java
class Metadata {
    public List<PropertyDefinition> propertiedef;
    float version;
    String unit;
    float resolution;
    LocalDate modifDate;
    String date2Use;
    String software;
    String user;
    String fileKey;

    public Metadata() {
        this.version = 0;
        this.unit = "";
        this.resolution = 0;
        this.modifDate = LocalDate.now();
        this.software = "";
        this.user = "";
        this.fileKey = "";
        this.date2Use = "";
        this.propertiedef = new ArrayList<>();
    }

    class PropertyDefinition {
        // Mapping label - type - unit
        public String label;
        public String type;
        public String unit;

        public PropertyDefinition() {
            this.label = "";
            this.type = "";
            this.unit = "";
        }

        public PropertyDefinition(String label, String type, String unit) {
            this.label = label;
            this.type = type;
            this.unit = unit;
        }
    }
}

