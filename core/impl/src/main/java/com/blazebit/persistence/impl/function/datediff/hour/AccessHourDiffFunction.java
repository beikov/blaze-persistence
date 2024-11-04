/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.datediff.hour;

import com.blazebit.persistence.spi.FunctionRenderContext;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class AccessHourDiffFunction extends HourDiffFunction {

    public AccessHourDiffFunction() {
        super("DateDiff('h', ?1, ?2)");
    }

    @Override
    protected void renderDiff(FunctionRenderContext context) {
        renderer.start(context).addArgument(0).addArgument(1).build();
    }
}