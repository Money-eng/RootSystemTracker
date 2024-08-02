package io.github.rocsg.rsmlparser;

import java.util.HashMap;
import java.util.Map;

public class Annotation {
    private final String name;
    private final Map<String, String> attributes;

    public Annotation(String name) {
        this.name = name;
        this.attributes = new HashMap<>();
    }

    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "Annotation{" +
                "name='" + name + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}
