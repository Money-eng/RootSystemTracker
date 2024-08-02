package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Metadata.java
public class Metadata {
    public List<PropertyDefinition> propertiedef;
    float version;
    String unit;
    float resolution;
    LocalDate modifDate;
    String date2Use;
    String software;
    String user;
    String fileKey;
    double size;
    double[] observationHours;
    Map<String, String> image_info;

    public Metadata() {
        this.version = 0;
        this.unit = "";
        this.resolution = 0;
        this.modifDate = LocalDate.now();
        this.software = "";
        this.user = "";
        this.fileKey = "";
        this.date2Use = "";
        this.size = 0;
        this.propertiedef = new ArrayList<>();
        this.image_info = new HashMap<>();
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public float getVersion() {
        return version;
    }

    public void setVersion(float version) {
        this.version = version;
    }

    public LocalDate getModifDate() {
        return modifDate;
    }

    public void setModifDate(LocalDate modifDate) {
        this.modifDate = modifDate;
    }

    public float getResolution() {
        return resolution;
    }

    public void setResolution(float resolution) {
        this.resolution = resolution;
    }

    public String getDate2Use() {
        return date2Use;
    }

    public void setDate2Use(String date2Use) {
        this.date2Use = date2Use;
    }

    public String getSoftware() {
        return software;
    }

    public void setSoftware(String software) {
        this.software = software;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public void setObservationHours(double[] observationHours) {
        this.observationHours = new double[observationHours.length];
        System.arraycopy(observationHours, 0, this.observationHours, 0, observationHours.length);
    }

    public double[] getObservationHours() {
        return observationHours;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getSize() {
        return size;
    }

    public void addImageInfo(String key, String value) {
        this.image_info.put(key, value);
    }

    public class PropertyDefinition {
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
