/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datetime.hour;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class SybaseHourFunction extends HourFunction {

    public SybaseHourFunction() {
        super("datepart(hh, ?1)");
    }
}
