package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

/**
 * The Function class represents a function with a name and a list of samples.
 */
public class Function {
    private final String name; // The name of the function
    private final List<Double> samples; // The list of samples

    /**
     * Constructs a Function with the specified name.
     *
     * @param name The name of the function.
     */
    public Function(String name) {
        this.name = name;
        this.samples = new ArrayList<>();
    }

    /**
     * Adds a sample to the function.
     *
     * @param sample The sample to add, as a String.
     */
    public void addSample(String sample) {
        this.samples.add(Double.parseDouble(sample));
    }

    /**
     * Gets the name of the function.
     *
     * @return The name of the function.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the list of samples.
     *
     * @return The list of samples.
     */
    public List<Double> getSamples() {
        return samples;
    }

    /**
     * Returns a string representation of the function.
     *
     * @return A string representation of the function.
     */
    @Override
    public String toString() {
        return "Function{" +
                "name='" + name + '\'' +
                ", samples=" + samples +
                '}';
    }
}