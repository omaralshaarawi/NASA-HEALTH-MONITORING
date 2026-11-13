package com.nasa.spacehealth.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// expected values come from CPython 3.12
class PyFormatTest {

    @Test
    void generalMatchesPythonG() {
        assertThat(PyFormat.general(118.0)).isEqualTo("118");
        assertThat(PyFormat.general(36.85)).isEqualTo("36.85");
        assertThat(PyFormat.general(0.4)).isEqualTo("0.4");
        assertThat(PyFormat.general(30.0)).isEqualTo("30");
        assertThat(PyFormat.general(100)).isEqualTo("100");
        assertThat(PyFormat.general(123456.7)).isEqualTo("123457");
        assertThat(PyFormat.general(1234567.0)).isEqualTo("1.23457e+06");
        assertThat(PyFormat.general(999999.5)).isEqualTo("1e+06");
        assertThat(PyFormat.general(99999.95)).isEqualTo("99999.9");
        assertThat(PyFormat.general(0.0001)).isEqualTo("0.0001");
        assertThat(PyFormat.general(0.00001234)).isEqualTo("1.234e-05");
        assertThat(PyFormat.general(1e22)).isEqualTo("1e+22");
        assertThat(PyFormat.general(-0.0)).isEqualTo("-0");
    }

    @Test
    void roundMatchesPythonRound() {
        assertThat(PyFormat.round(2.675, 2)).isEqualTo(2.67);      // binary value is just below the tie
        assertThat(PyFormat.round(0.125, 2)).isEqualTo(0.12);      // exact tie: half to even
        assertThat(PyFormat.round(2.5, 0)).isEqualTo(2.0);
        assertThat(PyFormat.round(61.185714, 3)).isEqualTo(61.186);
        assertThat(Double.doubleToLongBits(PyFormat.round(-0.001, 2))).isEqualTo(Double.doubleToLongBits(-0.0));
        assertThat(Double.doubleToLongBits(PyFormat.round(1e-9, 3))).isEqualTo(Double.doubleToLongBits(0.0));
    }

    @Test
    void fixedMatchesPythonF() {
        assertThat(PyFormat.fixed(2.675, 2)).isEqualTo("2.67");
        assertThat(PyFormat.fixed(-0.04, 1)).isEqualTo("-0.0");
        assertThat(PyFormat.fixed(19.05, 1)).isEqualTo("19.1");
        assertThat(PyFormat.signedFixed(5.81, 2)).isEqualTo("+5.81");
        assertThat(PyFormat.signedFixed(-0.3, 2)).isEqualTo("-0.30");
        assertThat(PyFormat.signedFixed(-0.001, 2)).isEqualTo("-0.00");
        assertThat(PyFormat.signedFixed(1e-9, 3)).isEqualTo("+0.000");
    }
}
