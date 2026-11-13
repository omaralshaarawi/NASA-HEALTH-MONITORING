package com.nasa.spacehealth.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

// Python-compatible round() and number formatting, so reason strings and numbers match
// status_engine.py exactly. Python rounds the exact binary value half-to-even, which is
// what new BigDecimal(double) + HALF_EVEN gives us.
public final class PyFormat {

    private PyFormat() {
    }

    // round(x, digits); keeps -0.0 like Python does
    public static double round(double x, int digits) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return x;
        }
        double r = Double.parseDouble(new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toString());
        return r == 0.0 && isNegative(x) ? -0.0 : r;
    }

    // f"{x:.Nf}"
    public static String fixed(double x, int digits) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return special(x);
        }
        BigDecimal bd = new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN);
        String s = bd.toPlainString();
        return bd.signum() == 0 && isNegative(x) ? "-" + s : s;
    }

    // f"{x:+.Nf}"
    public static String signedFixed(double x, int digits) {
        String s = fixed(x, digits);
        return s.startsWith("-") ? s : "+" + s;
    }

    // f"{x:g}": 6 significant digits, no trailing zeros, exponent form outside 1e-4..1e6
    public static String general(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return special(x);
        }
        if (x == 0.0) {
            return isNegative(x) ? "-0" : "0";
        }
        BigDecimal bd = new BigDecimal(x).round(new MathContext(6, RoundingMode.HALF_EVEN));
        int exponent = bd.precision() - bd.scale() - 1;
        if (exponent < -4 || exponent >= 6) {
            String mantissa = bd.movePointLeft(exponent).stripTrailingZeros().toPlainString();
            return mantissa + "e" + (exponent < 0 ? "-" : "+") + String.format("%02d", Math.abs(exponent));
        }
        return bd.stripTrailingZeros().toPlainString();
    }

    private static String special(double x) {
        if (Double.isNaN(x)) {
            return "nan";
        }
        return x > 0 ? "inf" : "-inf";
    }

    private static boolean isNegative(double x) {
        return x < 0 || (x == 0.0 && 1.0 / x < 0);
    }
}
