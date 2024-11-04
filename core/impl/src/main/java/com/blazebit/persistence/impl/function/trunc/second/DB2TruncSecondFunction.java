/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.trunc.second;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class DB2TruncSecondFunction extends TruncSecondFunction {

    public DB2TruncSecondFunction() {
        super("DATE_TRUNC('second', ?1)");
    }

}
