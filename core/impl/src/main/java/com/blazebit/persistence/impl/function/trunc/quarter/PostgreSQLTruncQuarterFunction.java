/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.trunc.quarter;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class PostgreSQLTruncQuarterFunction extends TruncQuarterFunction {

    public PostgreSQLTruncQuarterFunction() {
        super("DATE_TRUNC('quarter', ?1)");
    }

}
