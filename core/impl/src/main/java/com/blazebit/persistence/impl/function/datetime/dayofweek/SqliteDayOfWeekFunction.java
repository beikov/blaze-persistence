/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datetime.dayofweek;

/**
 *
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class SqliteDayOfWeekFunction extends DayOfWeekFunction {

    public SqliteDayOfWeekFunction() {
        super("(cast(strftime('%w',?1) as integer) + 1)");
    }
}
