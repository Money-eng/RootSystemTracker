package io.github.rocsg.rsml;

import io.github.rocsg.rsml.RsmlExpert_Plugin;
import org.openjdk.jmh.annotations.*;
import org.scijava.vecmath.Point3d;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class RsmlExpertPluginBenchmark {

    private RsmlExpert_Plugin plugin;
    private RootModel model;

    @Setup(Level.Trial)
    public void setUp() {
        plugin = new RsmlExpert_Plugin();
        model = createBenchmarkRootModel();
        plugin.setCurrentModel(model);
    }

    private RootModel createBenchmarkRootModel() {
        RootModel rm = new RootModel();
        // Initialisation du modèle racinaire avec une plus grande complexité pour le benchmark
        return rm;
    }

    @Benchmark
    public void benchmarkMovePointInModel() {
        Point3d[] points = new Point3d[]{
                new Point3d(10, 10, 0),
                new Point3d(15, 15, 0)
        };
        plugin.movePointInModel(points, model);
    }

    @Benchmark
    public void benchmarkRemovePointInModel() {
        Point3d[] points = new Point3d[]{
                new Point3d(10, 10, 0)
        };
        plugin.removePointInModel(points, model);
    }

    @Benchmark
    public void benchmarkRefineSegmentInModel() {
        Point3d[] points = new Point3d[]{
                new Point3d(10, 10, 0),
                new Point3d(12, 12, 0)
        };
        plugin.refineSegmentInModel(points, model);
    }

    // Ajouter des benchmarks pour les autres méthodes
}
