/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.hibernate;

import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.ast.HqlSqlWalker;
import org.hibernate.hql.internal.ast.tree.FromElement;
import org.hibernate.hql.internal.ast.tree.UpdateStatement;
import org.hibernate.hql.spi.id.MultiTableBulkIdStrategy;
import org.hibernate.hql.spi.id.TableBasedUpdateHandlerImpl;
import org.hibernate.persister.entity.AbstractEntityPersister;

import java.util.logging.Logger;

/**
 * @author Christian Beikov
 * @since 1.3.0
 */
public class CustomMultiTableBulkIdStrategy implements MultiTableBulkIdStrategy {

    private static final Logger LOG = Logger.getLogger(CustomMultiTableBulkIdStrategy.class.getName());
    private final MultiTableBulkIdStrategy delegate;

    public CustomMultiTableBulkIdStrategy(MultiTableBulkIdStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public UpdateHandler buildUpdateHandler(SessionFactoryImplementor factory, HqlSqlWalker walker) {
        UpdateHandler updateHandler = delegate.buildUpdateHandler(factory, walker);

        final UpdateStatement updateStatement = (UpdateStatement) walker.getAST();
        final FromElement fromElement = updateStatement.getFromClause().getFromElement();
        final AbstractEntityPersister targetedPersister = (AbstractEntityPersister) fromElement.getQueryable();

        // Only do this when we have secondary tables
        if (targetedPersister.getConstraintOrderedTableNameClosure().length > 1) {
            if (updateHandler instanceof TableBasedUpdateHandlerImpl) {
                return new CustomTableBasedUpdateHandlerImpl((TableBasedUpdateHandlerImpl) updateHandler, walker);
            } else {
                LOG.warning("Unsupported update handler that can't be adapted to support updates to secondary tables: " + updateHandler);
            }
        }

        return updateHandler;
    }

    @Override
    public DeleteHandler buildDeleteHandler(SessionFactoryImplementor factory, HqlSqlWalker walker) {
        return delegate.buildDeleteHandler(factory, walker);
    }

    @Override
    public void prepare(JdbcServices jdbcServices, JdbcConnectionAccess connectionAccess, MetadataImplementor metadata, SessionFactoryOptions sessionFactoryOptions) {
        delegate.prepare(jdbcServices, connectionAccess, metadata, sessionFactoryOptions);
    }

    @Override
    public void release(JdbcServices jdbcServices, JdbcConnectionAccess connectionAccess) {
        delegate.release(jdbcServices, connectionAccess);
    }
}
