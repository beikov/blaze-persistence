/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datetime.quarter;

/**
 *
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class PostgreSQLQuarterFunction extends QuarterFunction {

    public PostgreSQLQuarterFunction() {
        super("cast(extract(quarter from ?1) as int)");
    }
}
