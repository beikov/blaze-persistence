/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.dateadd.month;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class MySQLMonthAddFunction extends MonthAddFunction {

    public MySQLMonthAddFunction() {
        super("DATE_ADD(?1, INTERVAL ?2 MONTH)");
    }

}
