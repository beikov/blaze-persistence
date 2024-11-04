/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.trunc.milliseconds;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class DB2TruncMillisecondsFunction extends TruncMillisecondsFunction {

    public DB2TruncMillisecondsFunction() {
        super("DATE_TRUNC('milliseconds', ?1)");
    }

}
