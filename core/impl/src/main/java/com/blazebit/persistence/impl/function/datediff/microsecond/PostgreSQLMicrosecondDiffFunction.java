/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datediff.microsecond;

/**

 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class PostgreSQLMicrosecondDiffFunction extends MicrosecondDiffFunction {

    public PostgreSQLMicrosecondDiffFunction() {
        super("-cast(trunc(date_part('epoch', cast(?1 as timestamp) - cast(?2 as timestamp)) * 1000000) as bigint)");
    }
}
