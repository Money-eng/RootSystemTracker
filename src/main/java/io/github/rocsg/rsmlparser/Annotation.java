package io.github.rocsg.rsmlparser;

import java.util.HashMap;
import java.util.Map;

/**
 * The Annotation class represents an annotation with a name and a set of attributes.
 */
public class Annotation {
    private final String name; // The name of the annotation
    private final Map<String, String> attributes; // The attributes of the annotation

    /**
     * Constructs an Annotation with the specified name.
     *
     * @param name The name of the annotation.
     */
    public Annotation(String name) {
        this.name = name;
        this.attributes = new HashMap<>();
    }

    /**
     * Adds an attribute to the annotation.
     *
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    /**
     * Gets the name of the annotation.
     *
     * @return The name of the annotation.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the attributes of the annotation.
     *
     * @return A map containing the attributes of the annotation.
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Returns a string representation of the annotation.
     *
     * @return A string representation of the annotation.
     */
    @Override
    public String toString() {
        return "Annotation{" +
                "name='" + name + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}