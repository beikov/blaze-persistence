/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl.function.window.nth;

import com.blazebit.persistence.impl.function.window.AbstractWindowFunction;
import com.blazebit.persistence.spi.DbmsDialect;

/**
 *
 * @author Jan-Willem Gmelig Meyling
 * @author Sayra Ranjha
 * @since 1.4.0
 */
public class NthValueFunction extends AbstractWindowFunction {

    public static final String FUNCTION_NAME = "NTH_VALUE";

    public NthValueFunction(DbmsDialect dbmsDialect) {
        super(FUNCTION_NAME, dbmsDialect.isNullSmallest(), dbmsDialect.supportsWindowNullPrecedence(), dbmsDialect.supportsFilterClause(), true);
    }

    @Override
    protected boolean requiresOver() {
        return true;
    }

}
