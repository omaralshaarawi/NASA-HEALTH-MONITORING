package com.nasa.spacehealth.engine;

import java.util.List;
import java.util.function.Predicate;

// Small numeric helpers. Sums go left to right, same as numpy on these short arrays,
// so results match the Python engine bit for bit.
final class Stats {

    private Stats() {
    }

    static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    // sample standard deviation (numpy std with ddof=1)
    static double sampleStd(double[] values) {
        double m = mean(values);
        double sum = 0.0;
        for (double v : values) {
            sum += (v - m) * (v - m);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    // least-squares slope of y over x
    static double slope(double[] x, double[] y) {
        double xMean = mean(x);
        double yMean = mean(y);
        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - xMean;
            num += dx * (y[i] - yMean);
            den += dx * dx;
        }
        return num / den;
    }

    // how many items at the end of the list match, counting backwards until the first miss
    static <T> int trailingRun(List<T> items, Predicate<T> matches) {
        int count = 0;
        for (int i = items.size() - 1; i >= 0 && matches.test(items.get(i)); i--) {
            count++;
        }
        return count;
    }
}
