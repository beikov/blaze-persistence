/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.hibernate;

import com.blazebit.persistence.spi.DbmsDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.engine.spi.RowSelection;

/**
 * @author Christian Beikov
 * @since 1.2.0
 */
public class Hibernate43LimitHandlingDialect extends Hibernate43DelegatingDialect {

    private final DbmsDialect dbmsDialect;

    public Hibernate43LimitHandlingDialect(Dialect delegate, DbmsDialect dbmsDialect) {
        super(delegate);
        this.dbmsDialect = dbmsDialect;
    }

    @Override
    public LimitHandler buildLimitHandler(String sql, RowSelection selection) {
        return new Hibernate43LimitHandler(this, dbmsDialect, sql, selection);
    }
}
