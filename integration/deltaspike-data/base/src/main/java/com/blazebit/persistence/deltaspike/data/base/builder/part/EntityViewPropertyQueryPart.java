/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.deltaspike.data.base.builder.part;

import com.blazebit.persistence.deltaspike.data.Specification;
import com.blazebit.persistence.deltaspike.data.base.builder.EntityViewQueryBuilderContext;

import java.text.MessageFormat;

import static org.apache.deltaspike.data.impl.util.QueryUtils.uncapitalize;

/**
 * Implementation is similar to {@link org.apache.deltaspike.data.impl.builder.part.PropertyQueryPart} but was modified to
 * work with entity views.
 *
 * @author Moritz Becker
 * @since 1.2.0
 */
public class EntityViewPropertyQueryPart extends EntityViewBasePropertyQueryPart {

    private String name;
    private QueryOperator comparator;

    @Override
    public EntityViewQueryPart build(String queryPart, String method, Class<?> repositoryClass, Class<?> entityClass) {
        comparator = QueryOperator.Equal;
        name = uncapitalize(queryPart);
        for (QueryOperator comp : QueryOperator.values()) {
            if (queryPart.endsWith(comp.getExpression())) {
                comparator = comp;
                name = uncapitalize(queryPart.substring(0, queryPart.indexOf(comp.getExpression())));
                break;
            }
        }
        validate(name, method, repositoryClass, entityClass);
        name = EntityViewBasePropertyQueryPart.rewriteSeparator(name);
        return this;
    }

    @Override
    protected EntityViewQueryPart buildQuery(EntityViewQueryBuilderContext ctx) {
        String[] args = new String[comparator.getParamNum() + 1];
        args[0] = name;
        for (int i = 1; i < args.length; i++) {
            args[i] = getAndIncrementParamName(ctx);
        }
        ctx.getWhereExpressionBuilder().append(MessageFormat.format(comparator.getJpql(), (Object[]) args));
        return this;
    }

    @Override
    protected Specification<?> buildSpecification(EntityViewQueryBuilderContext ctx) {
        String param1 = comparator.getParamNum() < 1 ? null : getAndIncrementParamName(ctx);
        String param2 = comparator.getParamNum() < 2 ? null : getAndIncrementParamName(ctx);
        return new QueryOperatorSpecification<>(name, comparator, param1, param2);
    }

    private String getAndIncrementParamName(EntityViewQueryBuilderContext ctx) {
        return "?" + ctx.increment();
    }
}