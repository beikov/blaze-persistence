/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.hibernate.base;

import antlr.collections.AST;
import com.blazebit.apt.service.ServiceProvider;
import com.blazebit.persistence.ConfigurationProperties;
import com.blazebit.persistence.ReturningResult;
import com.blazebit.persistence.spi.ConfigurationSource;
import com.blazebit.persistence.spi.CteQueryWrapper;
import com.blazebit.persistence.spi.DbmsDialect;
import com.blazebit.persistence.spi.DbmsStatementType;
import com.blazebit.persistence.spi.ExtendedQuerySupport;
import com.blazebit.reflection.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.TypeMismatchException;
import org.hibernate.engine.query.spi.HQLQueryPlan;
import org.hibernate.engine.query.spi.QueryPlanCache;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.engine.spi.RowSelection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.TypedValue;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EventType;
import org.hibernate.hql.internal.QueryExecutionRequestException;
import org.hibernate.hql.internal.antlr.SqlTokenTypes;
import org.hibernate.hql.internal.ast.exec.BasicExecutor;
import org.hibernate.hql.internal.ast.exec.DeleteExecutor;
import org.hibernate.hql.internal.ast.exec.StatementExecutor;
import org.hibernate.hql.internal.ast.tree.AbstractStatement;
import org.hibernate.hql.internal.ast.tree.DotNode;
import org.hibernate.hql.internal.ast.tree.FromElement;
import org.hibernate.hql.internal.ast.tree.InsertStatement;
import org.hibernate.hql.internal.ast.tree.QueryNode;
import org.hibernate.hql.internal.ast.tree.SelectClause;
import org.hibernate.hql.spi.ParameterTranslations;
import org.hibernate.hql.spi.QueryTranslator;
import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.hibernate.loader.hql.QueryLoader;
import org.hibernate.param.ParameterSpecification;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.Type;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * @author Christian Beikov
 * @since 1.2.0
 */
@ServiceProvider(ExtendedQuerySupport.class)
public class HibernateExtendedQuerySupport implements ExtendedQuerySupport {

    private static final Logger LOG = Logger.getLogger(HibernateExtendedQuerySupport.class.getName());
    private static final String[] KNOWN_STATEMENTS = { "select ", "insert ", "update ", "delete " };
    
    private final ConcurrentMap<SessionFactoryImplementor, BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue>> queryPlanCachesCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();
    private final HibernateAccess hibernateAccess;
    
    public HibernateExtendedQuerySupport() {
        Iterator<HibernateAccess> serviceIter = ServiceLoader.load(HibernateAccess.class).iterator();
        if (!serviceIter.hasNext()) {
            throw new IllegalStateException("Hibernate integration was not found on the class path!");
        }
        this.hibernateAccess = serviceIter.next();
    }

    @Override
    public boolean supportsAdvancedSql() {
        return true;
    }

    @Override
    public boolean needsExampleQueryForAdvancedDml() {
        return false;
    }

    @Override
    public boolean applyFirstResultMaxResults(Query query, int firstResult, int maxResults) {
//        boolean changed = firstResult == 0 && query.getFirstResult() != 0 || firstResult != 0 && query.getFirstResult() == 0
//                || maxResults == Integer.MAX_VALUE && query.getMaxResults() != Integer.MAX_VALUE || maxResults != Integer.MAX_VALUE && query.getMaxResults() == Integer.MAX_VALUE;
        query.setFirstResult(firstResult);
        query.setMaxResults(maxResults);
        // Since getSqlContainsLimit returns false, we don't consider any limit/offset change as "dirty"
        return false;
    }

    @Override
    public String getSql(EntityManager em, Query query) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        HQLQueryPlan queryPlan = getOriginalQueryPlan(session, query);

        if (queryPlan.getTranslators().length > 1) {
            throw new IllegalArgumentException("No support for multiple translators yet!");
        }
        QueryTranslator queryTranslator = queryPlan.getTranslators()[0];

        String[] sqls;
        if (queryTranslator.isManipulationStatement()) {
            StatementExecutor executor = getStatementExecutor(queryTranslator);
            if (!(executor instanceof BasicExecutor)) {
                throw new IllegalArgumentException("Using polymorphic deletes/updates with CTEs is not yet supported");
            }
            sqls = executor.getSqlStatements();
        } else {
            sqls = queryPlan.getSqlStrings();
        }
        // TODO: have to handle multiple sql strings which happens when having e.g. a polymorphic UPDATE/DELETE
        for (int i = 0; i < sqls.length; i++) {
            if (sqls[i] != null) {
                return sqls[i];
            }
        }

        return null;
    }

    @Override
    public boolean getSqlContainsLimit() {
        return false;
    }

    @Override
    public List<String> getCascadingDeleteSql(EntityManager em, Query query) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        HQLQueryPlan queryPlan = getOriginalQueryPlan(session, query);
        if (queryPlan.getTranslators().length > 1) {
            throw new IllegalArgumentException("No support for multiple translators yet!");
        }
        // TODO: check if this is actually a delete statement
        QueryTranslator queryTranslator = queryPlan.getTranslators()[0];
        StatementExecutor executor = getStatementExecutor(queryTranslator);
        if (executor == null || !(executor instanceof DeleteExecutor)) {
            return Collections.EMPTY_LIST;
        }

        List<String> deletes = getField(executor, "deletes");
        if (deletes == null) {
            return Collections.EMPTY_LIST;
        }
        return deletes;
    }
    
    private HQLQueryPlan getOriginalQueryPlan(SessionImplementor session, Query query) {
        SessionFactoryImplementor sfi = session.getFactory();
        org.hibernate.Query hibernateQuery = query.unwrap(org.hibernate.Query.class);
        
        Map<String, TypedValue> namedParams = new HashMap<String, TypedValue>(hibernateAccess.getNamedParams(hibernateQuery));
        String queryString = hibernateAccess.expandParameterLists(session, hibernateQuery, namedParams);
        return sfi.getQueryPlanCache().getHQLQueryPlan(queryString, false, Collections.EMPTY_MAP);
    }

    @Override
    public String getSqlAlias(EntityManager em, Query query, String alias, int queryPartNumber) {
        FromElement fromElement = getSqlFromElement(em, query, alias, queryPartNumber);

        if (fromElement == null) {
            throw new IllegalArgumentException("The alias " + alias + " could not be found in the query: " + query);
        }

        return fromElement.getTableAlias();
    }

    private FromElement getSqlFromElement(EntityManager em, Query query, String alias, int queryPartNumber) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        HQLQueryPlan plan = getOriginalQueryPlan(session, query);
        if (plan.getTranslators().length > 1) {
            throw new IllegalArgumentException("No support for multiple translators yet!");
        }
        QueryTranslator translator = plan.getTranslators()[0];
        
        AbstractStatement statement = getField(translator, "sqlAst");
        QueryNode queryNode;

        if (statement instanceof QueryNode) {
            queryNode = (QueryNode) statement;
        } else if (statement instanceof InsertStatement) {
            queryNode = (QueryNode) ((InsertStatement) statement).getIntoClause().getNextSibling();
        } else {
            queryNode = (QueryNode) statement.getNextSibling();
        }
        return queryNode.getFromClause().getFromElement(alias);
    }

    @Override
    public SqlFromInfo getSqlFromInfo(EntityManager em, Query query, String alias, int queryPartNumber) {
        FromElement fromElement = getSqlFromElement(em, query, alias, queryPartNumber);

        if (fromElement == null) {
            throw new IllegalArgumentException("The alias " + alias + " could not be found in the query: " + query);
        }
        String sql = getSql(em, query);
        String text = fromElement.getText();
        int joinIndex = text.indexOf(" join ");
        if (joinIndex != -1) {
            text = text.substring(joinIndex + " join ".length());
        }
        String tableAlias = fromElement.getTableAlias();
        int startIndex = sql.indexOf(text);
        return new SqlFromInfoImpl(tableAlias, startIndex, text);
    }

    @Override
    public int getSqlSelectAliasPosition(EntityManager em, Query query, String alias) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        HQLQueryPlan plan = getOriginalQueryPlan(session, query);
        if (plan.getTranslators().length > 1) {
            throw new IllegalArgumentException("No support for multiple translators yet!");
        }
        QueryTranslator translator = plan.getTranslators()[0];

        try {
            QueryNode queryNode = getField(translator, "sqlAst");
            
            String[] aliases = queryNode.getSelectClause().getQueryReturnAliases();

            for (int i = 0; i < aliases.length; i++) {
                if (alias.equals(aliases[i])) {
                    // the ordinal is 1 based
                    return i + 1;
                }
            }
            
            return -1;
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }
    
    @Override
    public int getSqlSelectAttributePosition(EntityManager em, Query query, String expression) {
        if (expression.contains(".")) {
            // TODO: implement
            throw new UnsupportedOperationException("Embeddables are not yet supported!");
        }
        
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        HQLQueryPlan plan = getOriginalQueryPlan(session, query);
        if (plan.getTranslators().length > 1) {
            throw new IllegalArgumentException("No support for multiple translators yet!");
        }
        QueryTranslator translator = plan.getTranslators()[0];

        try {
            QueryNode queryNode = getField(translator, "sqlAst");
            SelectClause selectClause = queryNode.getSelectClause();
            Type[] queryReturnTypes = selectClause.getQueryReturnTypes();
            
            boolean found = false;
            // The ordinal is 1 based
            int position = 1;
            for (int i = 0; i < queryReturnTypes.length; i++) {
                Type t = queryReturnTypes[i];
                if (t instanceof ManyToOneType) {
                    ManyToOneType manyToOneType = (ManyToOneType) t;
                    AbstractEntityPersister persister = (AbstractEntityPersister) session.getFactory().getEntityPersister(manyToOneType.getAssociatedEntityName());
                    
                    int propertyIndex = persister.getPropertyIndex(expression);
                    found = true;
                    for (int j = 0; j < propertyIndex; j++) {
                        position += persister.getPropertyColumnNames(j).length;
                    }
                    // Increment to the actual property position
                    position++;
                } else {
                    position++;
                }
            }
            
            if (found) {
                return position;
            }

            AST selectItem = selectClause.getFirstChild();

            while (selectItem != null && (selectItem.getType() == SqlTokenTypes.DISTINCT || selectItem.getType() == SqlTokenTypes.ALL)) {
                selectItem = selectItem.getNextSibling();
            }

            position = 1;
            for (AST n = selectItem; n != null; n = n.getNextSibling()) {
                if (n instanceof DotNode) {
                    DotNode dot = (DotNode) n;
                    if (expression.equals(dot.getPropertyPath())) {
                        // Check if the property is an embeddable
                        if (dot.getText().contains(",")) {
                            throw new IllegalStateException("Can't order by the embeddable: " + expression);
                        }
                        found = true;
                        break;
                    }
                }
                position++;
            }

            if (found) {
                return position;
            }
            
            return -1;
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getResultList(com.blazebit.persistence.spi.ServiceProvider serviceProvider, List<Query> participatingQueries, Query query, String sqlOverride, boolean queryPlanCacheEnabled) {
        EntityManager em = serviceProvider.getService(EntityManager.class);
        try {
            return list(serviceProvider, em, participatingQueries, query, sqlOverride, queryPlanCacheEnabled);
        } catch (QueryExecutionRequestException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalStateException(he);
        } catch (TypeMismatchException e) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalArgumentException(e);
        } catch (HibernateException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw hibernateAccess.convert(em, he);
        }
    }

    @Override
    public Object getResultStream(com.blazebit.persistence.spi.ServiceProvider serviceProvider, List<Query> participatingQueries, Query query, String sqlOverride, boolean queryPlanCacheEnabled) {
        EntityManager em = serviceProvider.getService(EntityManager.class);
        try {
            return stream(serviceProvider, em, participatingQueries, query, sqlOverride, queryPlanCacheEnabled);
        } catch (QueryExecutionRequestException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalStateException(he);
        } catch (TypeMismatchException e) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalArgumentException(e);
        } catch (HibernateException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw hibernateAccess.convert(em, he);
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object getSingleResult(com.blazebit.persistence.spi.ServiceProvider serviceProvider, List<Query> participatingQueries, Query query, String sqlOverride, boolean queryPlanCacheEnabled) {
        EntityManager em = serviceProvider.getService(EntityManager.class);
        try {
            final List result = list(serviceProvider, em, participatingQueries, query, sqlOverride, queryPlanCacheEnabled);

            if (result.size() == 0) {
                NoResultException nre = new NoResultException("No entity found for query");
                hibernateAccess.handlePersistenceException(em, nre);
                throw nre;
            } else if (result.size() > 1) {
                final Set uniqueResult = new HashSet(result);
                if (uniqueResult.size() > 1) {
                    NonUniqueResultException nure = new NonUniqueResultException("result returns more than one element");
                    hibernateAccess.handlePersistenceException(em, nure);
                    throw nure;
                } else {
                    return uniqueResult.iterator().next();
                }
            } else {
                return result.get(0);
            }
        } catch (QueryExecutionRequestException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalStateException(he);
        } catch (TypeMismatchException e) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw new IllegalArgumentException(e);
        } catch (HibernateException he) {
            LOG.severe("Could not execute the following SQL query: " + sqlOverride);
            throw hibernateAccess.convert(em, he);
        }
    }

    @SuppressWarnings("rawtypes")
    private List list(com.blazebit.persistence.spi.ServiceProvider serviceProvider, EntityManager em, List<Query> participatingQueries, Query query, String finalSql, boolean queryPlanCacheEnabled) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        SessionFactoryImplementor sfi = session.getFactory();

        if (session.isClosed()) {
            throw new PersistenceException("Entity manager is closed!");
        }

        // Create combined query parameters
        List<String> queryStrings = new ArrayList<>(participatingQueries.size());
        Set<String> querySpaces = new HashSet<>();
        QueryParamEntry queryParametersEntry = createQueryParameters(em, query, participatingQueries, queryStrings, querySpaces);
        QueryParameters queryParameters = queryParametersEntry.queryParameters;

        QueryPlanCacheKey cacheKey = queryPlanCacheEnabled ? createCacheKey(finalSql, participatingQueries, queryStrings) : null;
        CacheEntry<QueryPlanCacheValue> queryPlanEntry = getQueryPlan(sfi, query, cacheKey);
        QueryPlanCacheValue queryPlanCacheValue = queryPlanEntry.getValue();
        HQLQueryPlan queryPlan = queryPlanCacheValue.getQueryPlan();
        
        if (!queryPlanEntry.isFromCache()) {
            prepareQueryPlan(queryPlan, queryParametersEntry.specifications, finalSql, session, null, false, serviceProvider.getService(DbmsDialect.class));
            if (queryPlanCacheEnabled) {
                putQueryPlanIfAbsent(sfi, cacheKey, queryPlanCacheValue);
            }
        }

        autoFlush(querySpaces, session);
        return hibernateAccess.performList(queryPlan, session, queryParameters);
    }

    @SuppressWarnings("rawtypes")
    private Object stream(com.blazebit.persistence.spi.ServiceProvider serviceProvider, EntityManager em, List<Query> participatingQueries, Query query, String finalSql, boolean queryPlanCacheEnabled) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        SessionFactoryImplementor sfi = session.getFactory();

        if (session.isClosed()) {
            throw new PersistenceException("Entity manager is closed!");
        }

        // Create combined query parameters
        List<String> queryStrings = new ArrayList<>(participatingQueries.size());
        Set<String> querySpaces = new HashSet<>();
        QueryParamEntry queryParametersEntry = createQueryParameters(em, query, participatingQueries, queryStrings, querySpaces);
        QueryParameters queryParameters = queryParametersEntry.queryParameters;

        QueryPlanCacheKey cacheKey = queryPlanCacheEnabled ? createCacheKey(finalSql, participatingQueries, queryStrings) : null;
        CacheEntry<QueryPlanCacheValue> queryPlanEntry = getQueryPlan(sfi, query, cacheKey);
        QueryPlanCacheValue queryPlanCacheValue = queryPlanEntry.getValue();
        HQLQueryPlan queryPlan = queryPlanCacheValue.getQueryPlan();

        if (!queryPlanEntry.isFromCache()) {
            prepareQueryPlan(queryPlan, queryParametersEntry.specifications, finalSql, session, null, false, serviceProvider.getService(DbmsDialect.class));
            if (queryPlanCacheEnabled) {
                putQueryPlanIfAbsent(sfi, cacheKey, queryPlanCacheValue);
            }
        }

        autoFlush(querySpaces, session);
        return hibernateAccess.performStream(queryPlan, session, queryParameters);
    }

    @Override
    public int executeUpdate(com.blazebit.persistence.spi.ServiceProvider serviceProvider, List<Query> participatingQueries, Query baseQuery, Query query, String finalSql, boolean queryPlanCacheEnabled) {
        DbmsDialect dbmsDialect = serviceProvider.getService(DbmsDialect.class);
        EntityManager em = serviceProvider.getService(EntityManager.class);
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        SessionFactoryImplementor sfi = session.getFactory();

        if (session.isClosed()) {
            throw new PersistenceException("Entity manager is closed!");
        }

        Integer firstResult = null;
        Integer maxResults = null;

        if (query.getFirstResult() > 0) {
            firstResult = query.getFirstResult();
        }
        if (query.getMaxResults() != Integer.MAX_VALUE) {
            maxResults = query.getMaxResults();
        }

        // Create combined query parameters
        List<String> queryStrings = new ArrayList<>(participatingQueries.size());
        Set<String> querySpaces = new HashSet<>();
        QueryParamEntry queryParametersEntry = createQueryParameters(em, baseQuery, participatingQueries, queryStrings, querySpaces);
        QueryParameters queryParameters = queryParametersEntry.queryParameters;

        QueryPlanCacheKey cacheKey = queryPlanCacheEnabled ? createCacheKey(finalSql, participatingQueries, queryStrings, firstResult, maxResults) : null;
        CacheEntry<QueryPlanCacheValue> queryPlanEntry = getQueryPlan(sfi, query, cacheKey);
        QueryPlanCacheValue queryPlanCacheValue = queryPlanEntry.getValue();
        HQLQueryPlan queryPlan = queryPlanCacheValue.getQueryPlan();

        String[][] returningColumns;
        int[] returningColumnTypes;
        if (queryPlan.getReturnMetadata() == null) {
            returningColumns = null;
            returningColumnTypes = null;
        } else {
            if (queryPlanEntry.isFromCache()) {
                returningColumns = queryPlanCacheValue.getReturningColumns();
                returningColumnTypes = queryPlanCacheValue.getReturningColumnTypes();
            } else {
                String exampleQuerySql = queryPlanCacheValue.getQueryPlan().getSqlStrings()[0];
                boolean caseInsensitive = !Boolean.valueOf(serviceProvider.getService(ConfigurationSource.class).getProperty(ConfigurationProperties.RETURNING_CLAUSE_CASE_SENSITIVE));
                returningColumns = getReturningColumns(caseInsensitive, exampleQuerySql);
                returningColumnTypes = dbmsDialect.needsReturningSqlTypes() ? getReturningColumnTypes(queryPlan, sfi) : null;
            }
        }

        if (!queryPlanEntry.isFromCache()) {
            prepareQueryPlan(queryPlan, queryParametersEntry.specifications, finalSql, session, baseQuery, true, dbmsDialect);
            if (queryPlanCacheEnabled) {
                putQueryPlanIfAbsent(sfi, cacheKey, new QueryPlanCacheValue(queryPlan, returningColumns, returningColumnTypes));
            }
        }

        autoFlush(querySpaces, session);

        if (queryPlan.getReturnMetadata() == null) {
            return hibernateAccess.performExecuteUpdate(queryPlan, session, queryParameters);
        }

        try {
            @SuppressWarnings("unchecked")
            List<Object> results = hibernateAccess.performList(queryPlan, wrapSession(session, dbmsDialect, returningColumns, returningColumnTypes, null), queryParameters);
            
            if (results.size() != 1) {
                throw new IllegalArgumentException("Expected size 1 but was: " + results.size());
            }
            
            Number count = (Number) results.get(0);
            return count.intValue();
        } catch (QueryExecutionRequestException he) {
            LOG.severe("Could not execute the following SQL query: " + finalSql);
            throw new IllegalStateException(he);
        } catch (TypeMismatchException e) {
            LOG.severe("Could not execute the following SQL query: " + finalSql);
            throw new IllegalArgumentException(e);
        } catch (HibernateException he) {
            LOG.severe("Could not execute the following SQL query: " + finalSql);
            hibernateAccess.throwPersistenceException(em, he);
            return 0;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReturningResult<Object[]> executeReturning(com.blazebit.persistence.spi.ServiceProvider serviceProvider, List<Query> participatingQueries, Query modificationBaseQuery, Query exampleQuery, String sqlOverride, boolean queryPlanCacheEnabled) {
        DbmsDialect dbmsDialect = serviceProvider.getService(DbmsDialect.class);
        EntityManager em = serviceProvider.getService(EntityManager.class);
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        SessionFactoryImplementor sfi = session.getFactory();

        if (session.isClosed()) {
            throw new PersistenceException("Entity manager is closed!");
        }

        // Create combined query parameters
        List<String> queryStrings = new ArrayList<>(participatingQueries.size());
        Set<String> querySpaces = new HashSet<>();
        QueryParamEntry queryParametersEntry = createQueryParameters(em, modificationBaseQuery, participatingQueries, queryStrings, querySpaces);
        QueryParameters queryParameters = queryParametersEntry.queryParameters;
        
        // Create plan for example query
        QueryPlanCacheKey cacheKey = queryPlanCacheEnabled ? createCacheKey(sqlOverride, participatingQueries, queryStrings) : null;
        CacheEntry<QueryPlanCacheValue> queryPlanEntry = getQueryPlan(sfi, exampleQuery, cacheKey);
        QueryPlanCacheValue queryPlanCacheValue = queryPlanEntry.getValue();
        HQLQueryPlan queryPlan = queryPlanCacheValue.getQueryPlan();

        String[][] returningColumns;
        int[] returningColumnTypes;
        if (queryPlanEntry.isFromCache()) {
            returningColumns = queryPlanCacheValue.getReturningColumns();
            returningColumnTypes = queryPlanCacheValue.getReturningColumnTypes();
        } else {
            String exampleQuerySql = queryPlan.getSqlStrings()[0];
            boolean caseInsensitive = !Boolean.valueOf(serviceProvider.getService(ConfigurationSource.class).getProperty(ConfigurationProperties.RETURNING_CLAUSE_CASE_SENSITIVE));
            returningColumns = getReturningColumns(caseInsensitive, exampleQuerySql);
            returningColumnTypes = dbmsDialect.needsReturningSqlTypes() ? getReturningColumnTypes(queryPlan, sfi) : null;
        }

        StringBuilder sqlSb = new StringBuilder(sqlOverride.length() + 100);
        sqlSb.append(sqlOverride);
        String finalSql = sqlSb.toString();
        
        try {
            HibernateReturningResult<Object[]> returningResult = new HibernateReturningResult<Object[]>();
            if (!queryPlanEntry.isFromCache()) {
                prepareQueryPlan(queryPlan, queryParametersEntry.specifications, finalSql, session, modificationBaseQuery, true, dbmsDialect);
                if (queryPlanCacheEnabled) {
                    putQueryPlanIfAbsent(sfi, cacheKey, new QueryPlanCacheValue(queryPlan, returningColumns, returningColumnTypes));
                }
            }

            if (queryPlan.getTranslators().length > 1) {
                throw new IllegalArgumentException("No support for multiple translators yet!");
            }

            QueryTranslator queryTranslator = queryPlan.getTranslators()[0];

            // If the DBMS doesn't support inclusion of cascading deletes in a with clause, we have to execute them manually
            StatementExecutor executor = getExecutor(queryTranslator, session, modificationBaseQuery);
            List<String> originalDeletes = Collections.emptyList();

            if (executor != null && executor instanceof DeleteExecutor) {
                originalDeletes = getField(executor, "deletes");
            }

            // Extract query loader for native listing
            QueryLoader queryLoader = getField(queryTranslator, "queryLoader");
            
            // Do the native list operation with custom session and combined parameters
            
            /*
             * NATIVE LIST START
             */
            hibernateAccess.checkTransactionSynchStatus(session);
            queryParameters.validateParameters();
            autoFlush(querySpaces, session);

            List<Object[]> results = Collections.EMPTY_LIST;
            boolean success = false;

            try {
                for (String delete : originalDeletes) {
                    hibernateAccess.doExecute(executor, delete, queryParameters, session, queryParametersEntry.specifications);
                }

                results = hibernateAccess.list(queryLoader, wrapSession(session, dbmsDialect, returningColumns, returningColumnTypes, returningResult), queryParameters);
                success = true;
            } catch (QueryExecutionRequestException he) {
                LOG.severe("Could not execute the following SQL query: " + finalSql);
                throw new IllegalStateException(he);
            } catch (TypeMismatchException e) {
                LOG.severe("Could not execute the following SQL query: " + finalSql);
                throw new IllegalArgumentException(e);
            } catch (HibernateException he) {
                LOG.severe("Could not execute the following SQL query: " + finalSql);
                throw hibernateAccess.convert(em, he);
            } finally {
                hibernateAccess.afterTransaction(session, success);
            }
            /*
             * NATIVE LIST END
             */
            
            returningResult.setResultList(results);
            return returningResult;
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    public void autoFlush(Set<String> querySpaces, SessionImplementor sessionImplementor) {
        AutoFlushEvent event = new AutoFlushEvent(querySpaces, (EventSource) sessionImplementor);
        for (AutoFlushEventListener listener : sessionImplementor.getFactory().getServiceRegistry().getService(EventListenerRegistry.class).getEventListenerGroup(EventType.AUTO_FLUSH).listeners()) {
            listener.onAutoFlush(event);
        }
    }

    private static String[][] getReturningColumns(boolean caseInsensitive, String exampleQuerySql) {
        int fromIndex = exampleQuerySql.indexOf("from");
        int selectIndex = exampleQuerySql.indexOf("select");
        String[] selectItems = splitSelectItems(exampleQuerySql.subSequence(selectIndex + "select".length() + 1, fromIndex));
        String[][] returningColumns = new String[selectItems.length][2];
        
        for (int i = 0; i < selectItems.length; i++) {
            String selectItemWithAlias = selectItems[i].substring(selectItems[i].lastIndexOf('.') + 1);
            if (caseInsensitive) {
                returningColumns[i][0] = selectItemWithAlias.substring(0, selectItemWithAlias.indexOf(' ')).toLowerCase();
            } else {
                returningColumns[i][0] = selectItemWithAlias.substring(0, selectItemWithAlias.indexOf(' '));
            }
            returningColumns[i][1] = selectItemWithAlias.substring(selectItemWithAlias.lastIndexOf(' ') + 1);
        }
        
        return returningColumns;
    }

    private static int[] getReturningColumnTypes(HQLQueryPlan queryPlan, SessionFactoryImplementor sfi) {
        List<Integer> sqlTypes = new ArrayList<>();
        Type[] types = queryPlan.getReturnMetadata().getReturnTypes();

        for (int i = 0; i < types.length; i++) {
            int[] sqlTypeArray = types[i].sqlTypes(sfi);
            for (int j = 0; j < sqlTypeArray.length; j++) {
                sqlTypes.add(sqlTypeArray[j]);
            }
        }

        int[] returningColumnTypes = new int[sqlTypes.size()];
        for (int i = 0; i < sqlTypes.size(); i++) {
            returningColumnTypes[i] = sqlTypes.get(i);
        }

        return returningColumnTypes;
    }
    
    private static String[] splitSelectItems(CharSequence itemsString) {
        List<String> selectItems = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        int parenthesis = 0;
        boolean text = false;
        
        int i = 0;
        int length = itemsString.length();
        while (i < length) {
            char c = itemsString.charAt(i);
            
            if (text) {
                if (c == '(') {
                    parenthesis++;
                } else if (c == ')') {
                    parenthesis--;
                } else if (parenthesis == 0 && c == ',') {
                    selectItems.add(trim(sb));
                    sb.setLength(0);
                    text = false;
                    
                    i++;
                    continue;
                }
                
                sb.append(c);
            } else {
                if (Character.isWhitespace(c)) {
                    // skip whitespace
                } else {
                    sb.append(c);
                    text = true;
                }
            }
            
            i++;
        }
        
        if (text) {
            selectItems.add(trim(sb));
        }
        
        return selectItems.toArray(new String[selectItems.size()]);
    }

    private static String trim(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i >= 0) {
            if (!Character.isWhitespace(sb.charAt(i))) {
                break;
            } else {
                i--;
            }
        }
        
        return sb.substring(0, i + 1);
    }
    
    private QueryParamEntry createQueryParameters(EntityManager em, Query query, List<Query> participatingQueries, List<String> queryStrings, Set<String> querySpaces) {
        org.hibernate.Query hibernateQuery = query.unwrap(org.hibernate.Query.class);
        List<ParameterSpecification> parameterSpecifications = new ArrayList<ParameterSpecification>();
        
        List<Type> types = new ArrayList<Type>();
        List<Object> values = new ArrayList<Object>();
        Map<String, TypedValue> namedParams = new LinkedHashMap<String, TypedValue>();
        Serializable collectionKey = null;
        LockOptions lockOptions = new LockOptions();
        RowSelection originalRowSelection = hibernateAccess.getQueryParameters(query.unwrap(org.hibernate.Query.class), hibernateAccess.getNamedParams(hibernateQuery)).getRowSelection();
        RowSelection rowSelection = new RowSelection();
        rowSelection.setTimeout(originalRowSelection.getTimeout());
        rowSelection.setFetchSize(originalRowSelection.getFetchSize());
        if (originalRowSelection.getFirstRow() != null && originalRowSelection.getFirstRow() != 0) {
            rowSelection.setFirstRow(originalRowSelection.getFirstRow());
        }
        if (originalRowSelection.getMaxRows() != null && originalRowSelection.getMaxRows() != Integer.MAX_VALUE) {
            rowSelection.setMaxRows(originalRowSelection.getMaxRows());
        }
        boolean readOnly = false;
        boolean cacheable = false;
        String cacheRegion = null;
        String comment = null;
        List<String> queryHints = null;

        for (Query participatingQuery : participatingQueries) {
            org.hibernate.Query hibernateParticipatingQuery = participatingQuery.unwrap(org.hibernate.Query.class);
            readOnly = readOnly || hibernateParticipatingQuery.isReadOnly();
            cacheable = cacheable || hibernateAccess.getQueryParameters(hibernateParticipatingQuery, namedParams).isCacheable();
            comment = comment != null ? comment : hibernateAccess.getQueryParameters(hibernateParticipatingQuery, namedParams).getComment();
        }

        for (QueryParamEntry queryParamEntry : getQueryParamEntries(em, participatingQueries, querySpaces)) {
            queryStrings.add(queryParamEntry.queryString);

            QueryParameters participatingQueryParameters = queryParamEntry.queryParameters;
            // Merge parameters
            Collections.addAll(types, participatingQueryParameters.getPositionalParameterTypes());
            Collections.addAll(values, participatingQueryParameters.getPositionalParameterValues());
            namedParams.putAll(participatingQueryParameters.getNamedParameters());
            parameterSpecifications.addAll(queryParamEntry.specifications);

            // Merge lock options
            LockOptions originalLockOptions = participatingQueryParameters.getLockOptions();
            if (originalLockOptions.getScope()) {
                lockOptions.setScope(true);
            }
            if (originalLockOptions.getLockMode() != LockMode.NONE) {
                if (lockOptions.getLockMode() != LockMode.NONE && lockOptions.getLockMode() != originalLockOptions.getLockMode()) {
                    throw new IllegalStateException("Multiple different lock modes!");
                }
                lockOptions.setLockMode(originalLockOptions.getLockMode());
            }
            if (originalLockOptions.getTimeOut() != -1) {
                if (lockOptions.getTimeOut() != -1 && lockOptions.getTimeOut() != originalLockOptions.getTimeOut()) {
                    throw new IllegalStateException("Multiple different lock timeouts!");
                }
                lockOptions.setTimeOut(originalLockOptions.getTimeOut());
            }
            @SuppressWarnings("unchecked")
            Iterator<Map.Entry<String, LockMode>> aliasLockIter = participatingQueryParameters.getLockOptions().getAliasLockIterator();
            while (aliasLockIter.hasNext()) {
                Map.Entry<String, LockMode> entry = aliasLockIter.next();
                lockOptions.setAliasSpecificLockMode(entry.getKey(), entry.getValue());
            }
        }
        
        QueryParameters queryParameters = hibernateAccess.createQueryParameters(
                  types.toArray(new Type[types.size()]),
                  values.toArray(new Object[values.size()]),
                  namedParams,
                  lockOptions,
                  rowSelection,
                  true,
                  readOnly,
                  cacheable,
                  cacheRegion,
                  comment,
                  queryHints,
                  collectionKey == null ? null : new Serializable[] { collectionKey }
        );
        
        return new QueryParamEntry(null, queryParameters, parameterSpecifications);
    }

    private SessionImplementor wrapSession(SessionImplementor session, DbmsDialect dbmsDialect, String[][] columns, int[] returningSqlTypes, HibernateReturningResult<?> returningResult) {
        // We do all this wrapping to change the StatementPreparer that is returned by the JdbcCoordinator
        // Instead of calling executeQuery, we delegate to executeUpdate and then return the generated keys in the prepared statement wrapper that we apply
        return hibernateAccess.wrapSession(session, dbmsDialect, columns, returningSqlTypes, returningResult);
    }
    
    private List<QueryParamEntry> getQueryParamEntries(EntityManager em, List<Query> queries, Set<String> querySpaces) {
        SessionImplementor session = em.unwrap(SessionImplementor.class);
        SessionFactoryImplementor sfi = session.getFactory();
        List<QueryParamEntry> result = new ArrayList<QueryParamEntry>(queries.size());
        Deque<Query> queryQueue = new LinkedList<Query>(queries);
        
        while (queryQueue.size() > 0) {
            Query q = queryQueue.remove();
            if (q instanceof CteQueryWrapper) {
                List<Query> participatingQueries = ((CteQueryWrapper) q).getParticipatingQueries();
                for (int i = participatingQueries.size() - 1; i > -1; i--) {
                    queryQueue.addFirst(participatingQueries.get(i));
                }
                continue;
            }
            
            org.hibernate.Query hibernateQuery = q.unwrap(org.hibernate.Query.class);

            Map<String, TypedValue> namedParams = new HashMap<String, TypedValue>(hibernateAccess.getNamedParams(hibernateQuery));
            String queryString = hibernateAccess.expandParameterLists(session, hibernateQuery, namedParams);
            
            HQLQueryPlan queryPlan = sfi.getQueryPlanCache().getHQLQueryPlan(queryString, false, Collections.EMPTY_MAP);

            if (queryPlan.getTranslators().length > 1) {
                throw new IllegalArgumentException("No support for multiple translators yet!");
            }

            querySpaces.addAll(queryPlan.getQuerySpaces());

            QueryTranslator queryTranslator = queryPlan.getTranslators()[0];
            QueryParameters queryParameters;
            List<ParameterSpecification> specifications;
            
            try {
                queryParameters = hibernateAccess.getQueryParameters(hibernateQuery, namedParams);
                specifications = getField(queryTranslator, "collectedParameterSpecifications");
                
                // This only happens for modification queries
                if (specifications == null) {
                    StatementExecutor executor = getStatementExecutor(queryTranslator);
                    if (!(executor instanceof BasicExecutor)) {
                        throw new IllegalArgumentException("Using polymorphic deletes/updates with CTEs is not yet supported");
                    }
                    specifications = getField(executor, "parameterSpecifications");
                }
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
            
            result.add(new QueryParamEntry(queryString, queryParameters, specifications));
        }
        
        return result;
    }
    
    private StatementExecutor getStatementExecutor(QueryTranslator queryTranslator) {
        return getField(queryTranslator, "statementExecutor");
    }
    
    private void prepareQueryPlan(HQLQueryPlan queryPlan, List<ParameterSpecification> queryParameterSpecifications, String finalSql, SessionImplementor session, Query modificationBaseQuery, boolean isModification, DbmsDialect dbmsDialect) {
        try {
            if (queryPlan.getTranslators().length > 1) {
                throw new IllegalArgumentException("No support for multiple translators yet!");
            }

            QueryTranslator queryTranslator = queryPlan.getTranslators()[0];
            // Override the sql in the query plan
            setField(queryTranslator, "sql", finalSql);

            QueryLoader queryLoader = getField(queryTranslator, "queryLoader");
            // INSERT statement does not have a query loader
            if (queryLoader != null) {
                setField(queryLoader, "factory", hibernateAccess.wrapSessionFactory(queryLoader.getFactory(), dbmsDialect));
            }

            // Modification queries keep the sql in the executor
            StatementExecutor executor = null;
            
            Field statementExecutor = null;
            boolean madeAccessible = false;
            
            try {
                statementExecutor = ReflectionUtils.getField(queryTranslator.getClass(), "statementExecutor");
                madeAccessible = !statementExecutor.isAccessible();
                
                if (madeAccessible) {
                    statementExecutor.setAccessible(true);
                }
                
                executor = (StatementExecutor) statementExecutor.get(queryTranslator);
                
                if (executor == null && isModification) {
                    // We have to set an executor

                    // The last query is our DML statement that contains the executor we need to alter
                    org.hibernate.Query lastHibernateQuery = modificationBaseQuery.unwrap(org.hibernate.Query.class);
                    Map<String, TypedValue> namedParams = new HashMap<String, TypedValue>(hibernateAccess.getNamedParams(lastHibernateQuery));
                    String queryString = hibernateAccess.expandParameterLists(session, lastHibernateQuery, namedParams);
                    
                    // Create a fresh query to extract the executor which we can use
                    HQLQueryPlan lastQueryPlan = new HQLQueryPlan(queryString, false, Collections.EMPTY_MAP, session.getFactory());
                    if (lastQueryPlan.getTranslators().length > 1) {
                        throw new IllegalArgumentException("No support for multiple translators yet!");
                    }
                    QueryTranslator lastQueryTranslator = lastQueryPlan.getTranslators()[0];
                    executor = (StatementExecutor) statementExecutor.get(lastQueryTranslator);
                    // Now we use this executor for our example query
                    statementExecutor.set(queryTranslator, executor);
                }
            } finally {
                if (madeAccessible) {
                    statementExecutor.setAccessible(false);
                }
            }

            if (executor != null) {
                setField(executor, "sql", finalSql);
                setField(executor, BasicExecutor.class, "parameterSpecifications", queryParameterSpecifications);
                
                if (executor instanceof DeleteExecutor) {
                    int withIndex;
                    if (dbmsDialect.supportsModificationQueryInWithClause()) {
                        setField(executor, "deletes", new ArrayList<String>());
                    } else if ((withIndex = finalSql.indexOf("with ")) != -1) {
                        int end = getCTEEnd(finalSql, withIndex);

                        List<String> originalDeletes = getField(executor, "deletes");
                        int maxLength = 0;

                        for (String s : originalDeletes) {
                            maxLength = Math.max(maxLength, s.length());
                        }

                        List<String> deletes = new ArrayList<String>(originalDeletes.size());
                        StringBuilder newSb = new StringBuilder(end + maxLength);
                        // Prefix properly with cte
                        StringBuilder withClauseSb = new StringBuilder(end - withIndex);
                        withClauseSb.append(finalSql, withIndex, end);

                        for (String s : originalDeletes) {
                            // TODO: The strings should also receive the simple CTE name instead of the complex one
                            newSb.append(s);
                            dbmsDialect.appendExtendedSql(newSb, DbmsStatementType.DELETE, false, false, withClauseSb, null, null, null, null, null);
                            deletes.add(newSb.toString());
                            newSb.setLength(0);
                        }

                        setField(executor, "deletes", deletes);
                    }
                }
            }
            
            // Prepare queryTranslator for aggregated parameters
            ParameterTranslations translations = hibernateAccess.createParameterTranslations(queryParameterSpecifications);
            setField(queryTranslator, "paramTranslations", translations);
            setField(queryTranslator, "collectedParameterSpecifications", queryParameterSpecifications);
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    private StatementExecutor getExecutor(QueryTranslator queryTranslator, SessionImplementor session, Query lastQuery) {
        // Modification queries keep the sql in the executor
        StatementExecutor executor = null;

        Field statementExectuor = null;
        boolean madeAccessible = false;

        try {
            statementExectuor = ReflectionUtils.getField(queryTranslator.getClass(), "statementExecutor");
            madeAccessible = !statementExectuor.isAccessible();

            if (madeAccessible) {
                statementExectuor.setAccessible(true);
            }

            executor = (StatementExecutor) statementExectuor.get(queryTranslator);

            if (executor == null) {
                // We have to set an executor
                org.hibernate.Query lastHibernateQuery = lastQuery.unwrap(org.hibernate.Query.class);

                Map<String, TypedValue> namedParams = new HashMap<String, TypedValue>(hibernateAccess.getNamedParams(lastHibernateQuery));
                String queryString = hibernateAccess.expandParameterLists(session, lastHibernateQuery, namedParams);

                // Extract the executor from the last query which is the actual main query
                HQLQueryPlan lastQueryPlan = session.getFactory().getQueryPlanCache().getHQLQueryPlan(queryString, false, Collections.EMPTY_MAP);
                if (lastQueryPlan.getTranslators().length > 1) {
                    throw new IllegalArgumentException("No support for multiple translators yet!");
                }
                QueryTranslator lastQueryTranslator = lastQueryPlan.getTranslators()[0];
                executor = (StatementExecutor) statementExectuor.get(lastQueryTranslator);
            }
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        } finally {
            if (madeAccessible) {
                statementExectuor.setAccessible(false);
            }
        }

        return executor;
    }

    private int getCTEEnd(String sql, int start) {
        int parenthesis = 0;
        QuoteMode mode = QuoteMode.NONE;
        boolean started = false;

        int i = start;
        int end = sql.length();
        OUTER: while (i < end) {
            final char c = sql.charAt(i);
            mode = mode.onChar(c);

            if (mode == QuoteMode.NONE) {
                if (c == '(') {
                    started = true;
                    parenthesis++;
                } else if (c == ')') {
                    parenthesis--;
                } else if (started && parenthesis == 0 && c != ',' && !Character.isWhitespace(c)) {
                    for (String statementType : KNOWN_STATEMENTS) {
                        if (sql.regionMatches(true, i, statementType, 0, statementType.length())) {
                            break OUTER;
                        }
                    }
                }
            }

            i++;
        }

        return i;
    }

    private CacheEntry<QueryPlanCacheValue> getQueryPlan(SessionFactoryImplementor sfi, Query query, QueryPlanCacheKey cacheKey) {
        QueryPlanCacheValue queryPlan;
        boolean fromCache;
        if (cacheKey == null) {
            fromCache = false;
            queryPlan = createQueryPlan(sfi, query);
        } else {
            BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue> queryPlanCache = getQueryPlanCache(sfi);
            queryPlan = queryPlanCache.get(cacheKey);
            if (queryPlan == null) {
                fromCache = false;
                queryPlan = createQueryPlan(sfi, query);
            } else {
                fromCache = true;
            }
        }
        
        return new CacheEntry<>(queryPlan, fromCache);
    }
    
    private QueryPlanCacheValue putQueryPlanIfAbsent(SessionFactoryImplementor sfi, QueryPlanCacheKey cacheKey, QueryPlanCacheValue queryPlan) {
        BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue> queryPlanCache = getQueryPlanCache(sfi);
        return queryPlanCache.putIfAbsent(cacheKey, queryPlan);
    }
    
    private QueryPlanCacheValue createQueryPlan(SessionFactoryImplementor sfi, Query query) {
        org.hibernate.Query hibernateQuery = query.unwrap(org.hibernate.Query.class);
        String queryString = hibernateQuery.getQueryString();
        return new QueryPlanCacheValue(new HQLQueryPlan(queryString, false, Collections.EMPTY_MAP, sfi), null, null);
    }
    
    private BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue> getQueryPlanCache(SessionFactoryImplementor sfi) {
        BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue> queryPlanCache = queryPlanCachesCache.get(sfi);
        if (queryPlanCache == null) {
            queryPlanCache = new BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue>(QueryPlanCache.DEFAULT_QUERY_PLAN_MAX_COUNT, 20, BoundedConcurrentHashMap.Eviction.LIRS);
            BoundedConcurrentHashMap<QueryPlanCacheKey, QueryPlanCacheValue> oldQueryPlanCache = queryPlanCachesCache.putIfAbsent(sfi, queryPlanCache);
            if (oldQueryPlanCache != null) {
                queryPlanCache = oldQueryPlanCache;
            }
        }
        
        return queryPlanCache;
    }

    private QueryPlanCacheKey createCacheKey(String sql, List<Query> queries, List<String> queryStrings) {
        return createCacheKey(sql, queries, queryStrings, null, null);
    }
    
    private QueryPlanCacheKey createCacheKey(String sql, List<Query> queries, List<String> queryStrings, Integer firstResult, Integer maxResults) {
        List<QueryPlanCacheKeyComponent> cacheKeyComponents = new ArrayList<>(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            Query query = queries.get(i);
            String queryString = queryStrings.get(i);
            cacheKeyComponents.add(new QueryPlanCacheKeyComponent(queryString, query.getFirstResult(), query.getMaxResults()));
        }
        return new QueryPlanCacheKey(sql, cacheKeyComponents, firstResult, maxResults);
    }
    
    private void addAll(List<Query> queries, List<String> parts) {
        for (int i = 0; i < queries.size(); i++) {
            Query query = queries.get(i);
            
            if (query instanceof CteQueryWrapper) {
                addAll(((CteQueryWrapper) query).getParticipatingQueries(), parts);
            } else {
                parts.add(query.unwrap(org.hibernate.Query.class).getQueryString());
            }
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    private static class QueryParamEntry {
        final String queryString;
        final QueryParameters queryParameters;
        final List<ParameterSpecification> specifications;
        
        public QueryParamEntry(String queryString, QueryParameters queryParameters, List<ParameterSpecification> specifications) {
            this.queryString = queryString;
            this.queryParameters = queryParameters;
            this.specifications = specifications;
        }
    }

    /**
     * @author Moritz Becker
     * @since 1.5.0
     */
    private static class QueryPlanCacheKey {
        final String sql;
        final List<QueryPlanCacheKeyComponent> cacheKeyComponents;
        final Integer firstResult;
        final Integer maxResults;

        public QueryPlanCacheKey(String sql, List<QueryPlanCacheKeyComponent> cacheKeyComponents) {
            this.sql = sql;
            this.cacheKeyComponents = cacheKeyComponents;
            this.firstResult = null;
            this.maxResults = null;
        }

        public QueryPlanCacheKey(String sql, List<QueryPlanCacheKeyComponent> cacheKeyComponents, Integer firstResult, Integer maxResults) {
            this.sql = sql;
            this.cacheKeyComponents = cacheKeyComponents;
            this.firstResult = firstResult;
            this.maxResults = maxResults;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QueryPlanCacheKey that = (QueryPlanCacheKey) o;
            return Objects.equals(sql, that.sql) &&
                    cacheKeyComponents.equals(that.cacheKeyComponents) &&
                    Objects.equals(firstResult, that.firstResult) &&
                    Objects.equals(maxResults, that.maxResults);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sql, cacheKeyComponents, firstResult, maxResults);
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    private static class QueryPlanCacheKeyComponent {
        final String query;
        final Integer firstResult;
        final Integer maxResults;

        public QueryPlanCacheKeyComponent(String query) {
            this.query = query;
            this.firstResult = null;
            this.maxResults = null;
        }

        public QueryPlanCacheKeyComponent(String query, Integer firstResult, Integer maxResults) {
            this.query = query;
            this.firstResult = firstResult;
            this.maxResults = maxResults;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof QueryPlanCacheKeyComponent)) {
                return false;
            }

            QueryPlanCacheKeyComponent that = (QueryPlanCacheKeyComponent) o;

            if (!query.equals(that.query)) {
                return false;
            }
            if (firstResult != null ? !firstResult.equals(that.firstResult) : that.firstResult != null) {
                return false;
            }
            return maxResults != null ? maxResults.equals(that.maxResults) : that.maxResults == null;

        }

        @Override
        public int hashCode() {
            int result = query.hashCode();
            result = 31 * result + (firstResult != null ? firstResult.hashCode() : 0);
            result = 31 * result + (maxResults != null ? maxResults.hashCode() : 0);
            return result;
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    private static class CacheEntry<T> {
        
        private final T value;
        private final boolean fromCache;
        
        public CacheEntry(T value, boolean fromCache) {
            this.value = value;
            this.fromCache = fromCache;
        }

        public T getValue() {
            return value;
        }

        public boolean isFromCache() {
            return fromCache;
        }
    }

    /**
     * @author Moritz Becker
     * @since 1.6.0
     */
    private static class QueryPlanCacheValue {
        private final HQLQueryPlan queryPlan;
        private final String[][] returningColumns;
        private final int[] returningColumnTypes;

        private QueryPlanCacheValue(HQLQueryPlan queryPlan, String[][] returningColumns, int[] returningColumnTypes) {
            this.queryPlan = queryPlan;
            this.returningColumns = returningColumns;
            this.returningColumnTypes = returningColumnTypes;
        }

        public HQLQueryPlan getQueryPlan() {
            return queryPlan;
        }

        public String[][] getReturningColumns() {
            return returningColumns;
        }

        public int[] getReturningColumnTypes() {
            return returningColumnTypes;
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.5.0
     */
    private static class FieldKey {

        private final Class<?> clazz;
        private final Class<?> expectClazz;
        private final String fieldName;

        public FieldKey(Class<?> clazz, Class<?> expectClazz, String fieldName) {
            this.clazz = clazz;
            this.expectClazz = expectClazz;
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldKey)) {
                return false;
            }

            FieldKey fieldKey = (FieldKey) o;
            return clazz.equals(fieldKey.clazz)
                    && expectClazz.equals(fieldKey.expectClazz)
                    && fieldName.equals(fieldKey.fieldName);
        }

        @Override
        public int hashCode() {
            int result = clazz.hashCode();
            result = 31 * result + expectClazz.hashCode();
            result = 31 * result + fieldName.hashCode();
            return result;
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getField(Object object, String field) {
        FieldKey key = new FieldKey(object.getClass(), object.getClass(), field);
        Field f = fieldCache.get(key);
        try {
            if (f == null) {
                f = ReflectionUtils.getField(object.getClass(), field);
                f.setAccessible(true);
                fieldCache.put(key, f);
            }
            return (T) f.get(object);
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    private void setField(Object object, String field, Object value) {
        setField(object, object.getClass(), field, value);
    }
    
    private void setField(Object object, Class<?> clazz, String field, Object value) {
        FieldKey key = new FieldKey(object.getClass(), clazz, field);
        Field f = fieldCache.get(key);
        try {
            if (f == null) {
                f = ReflectionUtils.getField(clazz, field);
                if (f == null) {
                    f = ReflectionUtils.getField(object.getClass(), field);
                }
                f.setAccessible(true);
                fieldCache.put(key, f);
            }
            f.set(object, value);
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.6.15
     */
    private static class SqlFromInfoImpl implements SqlFromInfo {
        private final String tableAlias;
        private final int startIndex;
        private final String text;

        public SqlFromInfoImpl(String tableAlias, int startIndex, String text) {
            this.tableAlias = tableAlias;
            this.startIndex = startIndex;
            this.text = text;
        }

        @Override
        public String getAlias() {
            return tableAlias;
        }

        @Override
        public int getFromStartIndex() {
            return startIndex;
        }

        @Override
        public int getFromEndIndex() {
            return startIndex + text.length();
        }
    }
}
