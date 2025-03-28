/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.impl;

import com.blazebit.persistence.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Christian Beikov
 * @author Moritz Becker
 * @since 1.2.0
 */
public abstract class AbstractQueryConfiguration implements QueryConfiguration {

    @Override
    public String getProperty(String name) {
        switch (name) {
            case ConfigurationProperties.COMPATIBLE_MODE: return Boolean.toString(isCompatibleModeEnabled());
            case ConfigurationProperties.RETURNING_CLAUSE_CASE_SENSITIVE: return Boolean.toString(isReturningClauseCaseSensitive());
            case ConfigurationProperties.SIZE_TO_COUNT_TRANSFORMATION: return Boolean.toString(isCountTransformationEnabled());
            case ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_SELECT: return Boolean.toString(isImplicitGroupByFromSelectEnabled());
            case ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_HAVING: return Boolean.toString(isImplicitGroupByFromHavingEnabled());
            case ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_ORDER_BY: return Boolean.toString(isImplicitGroupByFromOrderByEnabled());
            case ConfigurationProperties.EXPRESSION_OPTIMIZATION: return Boolean.toString(isExpressionOptimizationEnabled());
            case ConfigurationProperties.EXPRESSION_CACHE_CLASS: return getExpressionCacheClass();
            case ConfigurationProperties.VALUES_CLAUSE_FILTER_NULLS: return Boolean.toString(isValuesClauseFilterNullsEnabled());
            case ConfigurationProperties.OPTIMIZED_KEYSET_PREDICATE_RENDERING: return Boolean.toString(isOptimizedKeysetPredicateRenderingEnabled());
            case ConfigurationProperties.INLINE_ID_QUERY: return getInlineIdQueryEnabled() == null ? "auto" : Boolean.toString(getInlineIdQueryEnabled());
            case ConfigurationProperties.INLINE_COUNT_QUERY: return getInlineCountQueryEnabled() == null ? "auto" : Boolean.toString(getInlineCountQueryEnabled());
            case ConfigurationProperties.INLINE_CTES: return getInlineCtesEnabled() == null ? "auto" : Boolean.toString(getInlineCtesEnabled());
            case ConfigurationProperties.QUERY_PLAN_CACHE_ENABLED: return Boolean.toString(isQueryPlanCacheEnabled());
            default: return null;
        }
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>(20);
        properties.put(ConfigurationProperties.COMPATIBLE_MODE, Boolean.toString(isCompatibleModeEnabled()));
        properties.put(ConfigurationProperties.RETURNING_CLAUSE_CASE_SENSITIVE, Boolean.toString(isReturningClauseCaseSensitive()));
        properties.put(ConfigurationProperties.SIZE_TO_COUNT_TRANSFORMATION, Boolean.toString(isCountTransformationEnabled()));
        properties.put(ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_SELECT, Boolean.toString(isImplicitGroupByFromSelectEnabled()));
        properties.put(ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_HAVING, Boolean.toString(isImplicitGroupByFromHavingEnabled()));
        properties.put(ConfigurationProperties.IMPLICIT_GROUP_BY_FROM_ORDER_BY, Boolean.toString(isImplicitGroupByFromOrderByEnabled()));
        properties.put(ConfigurationProperties.EXPRESSION_OPTIMIZATION, Boolean.toString(isExpressionOptimizationEnabled()));
        properties.put(ConfigurationProperties.EXPRESSION_CACHE_CLASS, getExpressionCacheClass());
        properties.put(ConfigurationProperties.VALUES_CLAUSE_FILTER_NULLS, Boolean.toString(isValuesClauseFilterNullsEnabled()));
        properties.put(ConfigurationProperties.OPTIMIZED_KEYSET_PREDICATE_RENDERING, Boolean.toString(isOptimizedKeysetPredicateRenderingEnabled()));
        properties.put(ConfigurationProperties.INLINE_ID_QUERY, getInlineIdQueryEnabled() == null ? "auto" : Boolean.toString(getInlineIdQueryEnabled()));
        properties.put(ConfigurationProperties.INLINE_COUNT_QUERY, getInlineCountQueryEnabled() == null ? "auto" : Boolean.toString(getInlineCountQueryEnabled()));
        properties.put(ConfigurationProperties.INLINE_CTES, getInlineCtesEnabled() == null ? "auto" : Boolean.toString(getInlineCtesEnabled()));
        properties.put(ConfigurationProperties.QUERY_PLAN_CACHE_ENABLED, Boolean.toString(isQueryPlanCacheEnabled()));
        return properties;
    }

}
