package io.github.rocsg.rsmlparser;

/**
 * The Property class represents a property with a name and a value.
 */
public class Property {
    private final String name; // The name of the property
    private final double value; // The value of the property

    /**
     * Constructs a Property with the specified name and value.
     *
     * @param name The name of the property.
     * @param value The value of the property as a String.
     */
    public Property(String name, String value) {
        this.name = name;
        this.value = Double.parseDouble(value);
    }

    /**
     * Gets the name of the property.
     *
     * @return The name of the property.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the value of the property.
     *
     * @return The value of the property.
     */
    public double getValue() {
        return value;
    }

    /**
     * Returns a string representation of the Property object.
     *
     * @return A string representation of the Property object.
     */
    @Override
    public String toString() {
        return "Property{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}