/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datetime.microsecond;

/**
 *
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class PostgreSQLMicrosecondFunction extends MicrosecondFunction {

    public PostgreSQLMicrosecondFunction() {
        super("cast(extract(microseconds from ?1) as int) % 1000000");
    }
}
