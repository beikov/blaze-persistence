/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.dateadd.milliseconds;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class DB2MillisecondsAddFunction extends MillisecondsAddFunction {

    public DB2MillisecondsAddFunction() {
        super("cast(?1 as timestamp) + (?2 * 1000) MICROSECONDS");
    }

}
