/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.dateadd.quarter;

/**
 * @author Jan-Willem Gmelig Meyling
 * @since 1.4.0
 */
public class H2QuarterAddFunction extends QuarterAddFunction {

    public H2QuarterAddFunction() {
        super("DATEADD(quarter, ?2, ?1)");
    }

}
