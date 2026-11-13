package com.nasa.spacehealth.model;

/** Constants copied verbatim from contract/contract.ts. */
public final class Contract {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String DISCLAIMER =
            "Prototype decision-support tool using simulated data. Not a medical device and not a diagnosis. "
                    + "Follow flight-surgeon and onboard medical procedures.";
    public static final String THRESHOLDS_STATUS = "PROTOTYPE";

    private Contract() {
    }
}
