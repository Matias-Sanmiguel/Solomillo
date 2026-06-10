package dev.solomillo.ml;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DatasetGenerator {

    private DatasetGenerator() {}

    public static Instances buildHeader() {
        var attrs = new ArrayList<Attribute>();
        attrs.add(new Attribute("gf_local"));
        attrs.add(new Attribute("gc_local"));
        attrs.add(new Attribute("gf_visit"));
        attrs.add(new Attribute("gc_visit"));
        attrs.add(new Attribute("resultado", List.of("0", "1", "2")));
        var data = new Instances("partidos", attrs, 0);
        data.setClassIndex(4);
        return data;
    }

    public static Instances generarPartidos(int n, long seed) {
        Instances data = buildHeader();
        var rng = new Random(seed);
        for (int i = 0; i < n; i++) {
            int gfl = rng.nextInt(30), gcl = rng.nextInt(30);
            int gfv = rng.nextInt(30), gcv = rng.nextInt(30);
            double margen = (gfl - gcl) - (gfv - gcv) + rng.nextGaussian() * 3;
            double label = margen > 2 ? 0 : margen < -2 ? 2 : 1;
            data.add(new DenseInstance(1.0, new double[]{gfl, gcl, gfv, gcv, label}));
        }
        return data;
    }

    public static Instances buildRegressionHeader() {
        var attrs = new ArrayList<Attribute>();
        attrs.add(new Attribute("goles"));
        attrs.add(new Attribute("rating"));
        var data = new Instances("rendimiento", attrs, 0);
        data.setClassIndex(1);
        return data;
    }

    public static Instances generarRendimiento(int n, long seed) {
        Instances data = buildRegressionHeader();
        var rng = new Random(seed);
        for (int i = 0; i < n; i++) {
            int goles = rng.nextInt(12);
            double rating = Math.min(10, Math.max(1, 5 + 0.7 * goles + rng.nextGaussian() * 0.6));
            data.add(new DenseInstance(1.0, new double[]{goles, rating}));
        }
        return data;
    }
}
