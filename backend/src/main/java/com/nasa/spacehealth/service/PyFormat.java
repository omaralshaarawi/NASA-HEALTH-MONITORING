package com.nasa.spacehealth.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Number rounding and formatting that reproduces Python exactly, so the Java engine emits the same numbers and the
 * same reason strings as contract/status_engine.py.
 *
 * Python rounds the exact binary value of a float half-to-even; {@code new BigDecimal(double)} gives that exact value.
 */
public final class PyFormat {

    private PyFormat() {
    }

    /** Python {@code round(x, digits)}. Keeps the sign of zero, as Python does (round(-0.001, 2) == -0.0). */
    public static double round(double x, int digits) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return x;
        }
        double r = Double.parseDouble(new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toString());
        return (r == 0.0 && isNegative(x)) ? -0.0 : r;
    }

    /** Python {@code f"{x:.Nf}"}. */
    public static String fixed(double x, int digits) {
        if (Double.isNaN(x)) {
            return "nan";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "inf" : "-inf";
        }
        BigDecimal bd = new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN);
        String s = bd.toPlainString();
        return (bd.signum() == 0 && isNegative(x)) ? "-" + s : s;
    }

    /** Python {@code f"{x:+.Nf}"}. */
    public static String signedFixed(double x, int digits) {
        String s = fixed(x, digits);
        return s.startsWith("-") ? s : "+" + s;
    }

    /** Python {@code f"{x:g}"}: 6 significant digits, trailing zeros removed, exponent form outside 1e-4 .. 1e6. */
    public static String general(double x) {
        if (Double.isNaN(x)) {
            return "nan";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "inf" : "-inf";
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

    private static boolean isNegative(double x) {
        return x < 0 || (x == 0.0 && 1.0 / x < 0);
    }
}
