package dev.solomillo.ml;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Random;

/** Solo el dataset sintetico del modelo de rendimiento de jugador (regresion). */
public final class DatasetGenerator {

    private DatasetGenerator() {}

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
