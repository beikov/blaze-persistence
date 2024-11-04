/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.trunc.microseconds;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class PostgreSQLTruncMicrosecondsFunction extends TruncMicrosecondsFunction {

    public PostgreSQLTruncMicrosecondsFunction() {
        super("DATE_TRUNC('microseconds', ?1)");
    }

}
