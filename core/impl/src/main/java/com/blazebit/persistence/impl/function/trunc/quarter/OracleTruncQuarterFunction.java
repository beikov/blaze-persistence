/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.trunc.quarter;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class OracleTruncQuarterFunction extends TruncQuarterFunction {

    public OracleTruncQuarterFunction() {
        super("TRUNC(?1, 'Q')");
    }

}
