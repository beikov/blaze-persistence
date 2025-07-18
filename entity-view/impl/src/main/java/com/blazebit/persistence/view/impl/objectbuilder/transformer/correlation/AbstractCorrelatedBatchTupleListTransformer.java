/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.impl.objectbuilder.transformer.correlation;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.FullQueryBuilder;
import com.blazebit.persistence.ObjectBuilder;
import com.blazebit.persistence.parser.expression.Expression;
import com.blazebit.persistence.parser.expression.ExpressionFactory;
import com.blazebit.persistence.view.CorrelationProvider;
import com.blazebit.persistence.view.impl.BatchCorrelationMode;
import com.blazebit.persistence.view.CorrelationProviderFactory;
import com.blazebit.persistence.view.impl.EntityViewConfiguration;
import com.blazebit.persistence.view.impl.macro.CorrelatedSubqueryEmbeddingViewJpqlMacro;
import com.blazebit.persistence.view.impl.macro.CorrelatedSubqueryViewRootJpqlMacro;
import com.blazebit.persistence.view.impl.macro.MutableViewJpqlMacro;
import com.blazebit.persistence.view.impl.metamodel.ManagedViewTypeImplementor;
import com.blazebit.persistence.view.impl.objectbuilder.ContainerAccumulator;
import com.blazebit.persistence.view.impl.objectbuilder.LateAdditionalObjectBuilder;
import com.blazebit.persistence.view.impl.objectbuilder.Limiter;
import com.blazebit.persistence.view.impl.objectbuilder.TupleReuse;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.metamodel.ViewType;

import javax.persistence.EntityManager;
import javax.persistence.Parameter;
import javax.persistence.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public abstract class AbstractCorrelatedBatchTupleListTransformer extends AbstractCorrelatedTupleListTransformer {

    public static final String CORRELATION_KEY_ALIAS = "correlationKey";
    private static final String CORRELATION_PARAM_PREFIX = "correlationParam_";

    protected final int batchSize;
    protected final boolean correlatesThis;
    protected final BatchCorrelationMode expectBatchCorrelationMode;
    protected final int valueIndex;
    protected final int keyIndex;

    protected String correlationParamName;
    protected String correlationSelectExpression;
    protected CriteriaBuilder<?> criteriaBuilder;
    protected CorrelatedSubqueryViewRootJpqlMacro viewRootJpqlMacro;
    protected CorrelatedSubqueryEmbeddingViewJpqlMacro embeddingViewJpqlMacro;
    protected Query query;

    public AbstractCorrelatedBatchTupleListTransformer(ExpressionFactory ef, Correlator correlator, ContainerAccumulator<?> containerAccumulator, ManagedViewTypeImplementor<?> viewRootType, ManagedViewTypeImplementor<?> embeddingViewType, Expression correlationResult, CorrelationProviderFactory correlationProviderFactory, String attributePath, String[] fetches, String[] indexFetches,
                                                       Expression indexExpression, Correlator indexCorrelator, boolean correlatesThis, int viewRootIndex, int embeddingViewIndex, int tupleIndex, int defaultBatchSize, Class<?> correlationBasisType, Class<?> correlationBasisEntity, Limiter limiter, EntityViewConfiguration entityViewConfiguration) {
        super(ef, correlator, containerAccumulator, viewRootType, embeddingViewType, correlationResult, correlationProviderFactory, attributePath, fetches, indexFetches, indexExpression, indexCorrelator, viewRootIndex, embeddingViewIndex, tupleIndex, correlationBasisType, correlationBasisEntity, limiter, entityViewConfiguration);
        this.batchSize = entityViewConfiguration.getBatchSize(attributePath, defaultBatchSize);
        this.correlatesThis = correlatesThis;
        this.expectBatchCorrelationMode = entityViewConfiguration.getExpectBatchCorrelationValues(attributePath);
        this.valueIndex = correlator.getElementOffset();
        this.keyIndex = valueIndex + 1;
    }

    private String generateCorrelationParamName() {
        final FullQueryBuilder<?, ?> queryBuilder = entityViewConfiguration.getCriteriaBuilder();
        final Map<String, Object> optionalParameters = entityViewConfiguration.getOptionalParameters();
        int paramNumber = 0;
        String paramName;
        while (true) {
            paramName = CORRELATION_PARAM_PREFIX + paramNumber;
            if (queryBuilder.getParameter(paramName) != null) {
                paramNumber++;
            } else if (optionalParameters.containsKey(paramName)) {
                paramNumber++;
            } else {
                return paramName;
            }
        }
    }

    private String applyAndGetCorrelationRoot(BatchCorrelationMode batchCorrelationMode) {
        Class<?> viewRootEntityClass = viewRootType.getEntityClass();
        Class<?> embeddingViewEntityClass = embeddingViewType.getEntityClass();
        String viewRootIdAttributePath = getEntityIdName(viewRootEntityClass);
        String embeddingViewIdAttributePath = getEntityIdName(embeddingViewEntityClass);

        FullQueryBuilder<?, ?> queryBuilder = entityViewConfiguration.getCriteriaBuilder();
        Map<String, Object> optionalParameters = entityViewConfiguration.getOptionalParameters();

        Class<?> correlationBasisEntityType;
        String viewRootExpression;
        String embeddingViewExpression;
        boolean batchedIdValues = false;
        if (batchCorrelationMode == BatchCorrelationMode.VALUES) {
            correlationBasisEntityType = correlationBasisEntity;
            viewRootExpression = null;
            batchedIdValues = correlatesThis && correlationBasisEntity == null;
            embeddingViewExpression = correlatesThis ? CORRELATION_KEY_ALIAS : null;
        } else if (batchCorrelationMode == BatchCorrelationMode.VIEW_ROOTS) {
            correlationBasisEntityType = viewRootEntityClass;
            viewRootExpression = CORRELATION_KEY_ALIAS;
            embeddingViewExpression = null;
        } else {
            correlationBasisEntityType = embeddingViewEntityClass;
            viewRootExpression = null;
            embeddingViewExpression = CORRELATION_KEY_ALIAS;
        }

        this.criteriaBuilder = queryBuilder.getCriteriaBuilderFactory().create(queryBuilder.getEntityManager(), Object[].class);
        this.viewRootJpqlMacro = new CorrelatedSubqueryViewRootJpqlMacro(criteriaBuilder, optionalParameters, viewRootExpression != null, viewRootEntityClass, viewRootIdAttributePath, viewRootExpression);
        this.embeddingViewJpqlMacro = new CorrelatedSubqueryEmbeddingViewJpqlMacro(criteriaBuilder, optionalParameters, embeddingViewExpression != null, embeddingViewEntityClass, embeddingViewIdAttributePath, embeddingViewExpression, batchedIdValues, viewRootJpqlMacro);
        this.criteriaBuilder.registerMacro("view", new MutableViewJpqlMacro(correlationResult));
        this.criteriaBuilder.registerMacro("view_root", viewRootJpqlMacro);
        this.criteriaBuilder.registerMacro("embedding_view", embeddingViewJpqlMacro);

        String joinBase = CORRELATION_KEY_ALIAS;
        SubqueryCorrelationBuilder correlationBuilder = new SubqueryCorrelationBuilder(queryBuilder, optionalParameters, criteriaBuilder, correlationAlias, correlationExternalAlias, correlationResult, correlationBasisType, correlationBasisEntityType, joinBase, attributePath, batchSize, limiter, false);
        CorrelationProvider provider = correlationProviderFactory.create(entityViewConfiguration.getCriteriaBuilder(), entityViewConfiguration.getOptionalParameters());

        String correlationKeyExpression;
        if (batchSize > 1) {
            if (batchCorrelationMode == BatchCorrelationMode.VALUES) {
                this.correlationParamName = CORRELATION_KEY_ALIAS;
                // TODO: when using EMBEDDING_VIEW, we could make use of correlationBasis instead of binding parameters separately
            } else {
                this.correlationParamName = generateCorrelationParamName();
            }
            if (correlationBasisEntityType != null) {
                correlationKeyExpression = CORRELATION_KEY_ALIAS;
                if (batchCorrelationMode == BatchCorrelationMode.VALUES) {
                    correlationSelectExpression = CORRELATION_KEY_ALIAS + '.' + getEntityIdName(correlationBasisEntityType);
                } else {
                    correlationSelectExpression = CORRELATION_KEY_ALIAS + '.' + viewRootIdAttributePath;
                }
            } else {
                // The correlation key is basic type
                correlationSelectExpression = correlationKeyExpression = CORRELATION_KEY_ALIAS;
            }
        } else {
            this.correlationParamName = generateCorrelationParamName();
            this.correlationSelectExpression = correlationKeyExpression = null;
        }

        int originalFirstResult = criteriaBuilder.getFirstResult();
        int originalMaxResults = criteriaBuilder.getMaxResults();
        if (batchSize > 1 && batchCorrelationMode == BatchCorrelationMode.VALUES) {
            provider.applyCorrelation(correlationBuilder, correlationKeyExpression);
        } else {
            provider.applyCorrelation(correlationBuilder, ':' + correlationParamName);
        }
        if (batchSize > 1 && (originalFirstResult != criteriaBuilder.getFirstResult()
                || originalMaxResults != criteriaBuilder.getMaxResults())) {
            throw new IllegalArgumentException("Correlation provider '" + provider + "' wrongly uses setFirstResult() or setMaxResults() on the query builder which might lead to wrong results. Use SELECT fetching with batch size 1 or reformulate the correlation provider to use the limit/offset in a subquery!");
        }

        if (fetches.length != 0) {
            for (int i = 0; i < fetches.length; i++) {
                criteriaBuilder.fetch(fetches[i]);
            }
        }

        if (indexFetches.length != 0) {
            for (int i = 0; i < indexFetches.length; i++) {
                criteriaBuilder.fetch(indexFetches[i]);
            }
        }

        return correlationBuilder.getCorrelationRoot();
    }

    @Override
    public List<Object[]> transform(List<Object[]> tuples) {
        FixedArrayList correlationParams = new FixedArrayList(batchSize);
        // We have the correlation key on the first position if we do batching
        final int tupleOffset = (batchSize > 1 ? 1 : 0) + (indexCorrelator == null && indexExpression == null ? 0 : 1);

        final String correlationRoot = applyAndGetCorrelationRoot(expectBatchCorrelationMode);
        // Add select items so that macros are properly used and we can query usage
        ObjectBuilder<?> objectBuilder = correlator.finish(criteriaBuilder, entityViewConfiguration, 0, tupleOffset, correlationRoot, embeddingViewJpqlMacro, true);
        if (batchSize > 1) {
            criteriaBuilder.select(correlationSelectExpression);
        }
        if (indexCorrelator != null) {
            ObjectBuilder<?> indexBuilder = indexCorrelator.finish(criteriaBuilder, entityViewConfiguration, tupleOffset, 0, indexExpression, embeddingViewJpqlMacro, true);
            if (indexBuilder != null) {
                criteriaBuilder.selectNew(new LateAdditionalObjectBuilder(objectBuilder, indexBuilder, false));
            }
        }

        // If a view macro is used, we have to decide whether we do batches for each view id or correlation param
        if (embeddingViewJpqlMacro.usesViewMacroNonId() || !correlatesThis && embeddingViewJpqlMacro.usesViewMacro()) {
            if (!(embeddingViewType instanceof ViewType<?>)) {
                throw new IllegalStateException("The use of EMBEDDING_VIEW in the correlation for '" + embeddingViewType.getJavaType().getName() + "." + attributePath.substring(attributePath.lastIndexOf('.') + 1) + "' is illegal because the embedding view type '" + embeddingViewType.getJavaType().getName() + "' does not declare a @IdMapping!");
            }
            transformViewMacroAware(tuples, correlationParams, tupleOffset, correlationRoot, embeddingViewJpqlMacro, BatchCorrelationMode.EMBEDDING_VIEWS, embeddingViewType, embeddingViewIndex);
        } else if (viewRootJpqlMacro.usesViewMacro()) {
            if (!(viewRootType instanceof ViewType<?>)) {
                throw new IllegalStateException("The use of VIEW_ROOT in the correlation for '" + embeddingViewType.getJavaType().getName() + "." + attributePath.substring(attributePath.lastIndexOf('.') + 1) + "' is illegal because the view root type '" + viewRootType.getJavaType().getName() + "' does not declare a @IdMapping!");
            }
            transformViewMacroAware(tuples, correlationParams, tupleOffset, correlationRoot, viewRootJpqlMacro, BatchCorrelationMode.VIEW_ROOTS, viewRootType, viewRootIndex);
        } else {
            EntityManager em = criteriaBuilder.getEntityManager();
            Iterator<Object[]> tupleListIter = tuples.iterator();
            if (batchSize > 1) {
                // If the expectation was wrong, we have to create a new criteria builder
                if (expectBatchCorrelationMode != BatchCorrelationMode.VALUES) {
                    applyAndGetCorrelationRoot(BatchCorrelationMode.VALUES);
                    objectBuilder = correlator.finish(criteriaBuilder, entityViewConfiguration, 0, tupleOffset, correlationRoot, embeddingViewJpqlMacro, true);
                    criteriaBuilder.select(correlationSelectExpression);
                    if (indexCorrelator != null) {
                        ObjectBuilder<?> indexBuilder = indexCorrelator.finish(criteriaBuilder, entityViewConfiguration, tupleOffset, 0, indexExpression, embeddingViewJpqlMacro, true);
                        if (indexBuilder != null) {
                            criteriaBuilder.selectNew(new LateAdditionalObjectBuilder(objectBuilder, indexBuilder, false));
                        }
                    }
                }
            }
            populateParameters(criteriaBuilder);
            query = criteriaBuilder.getQuery();

            Map<Object, TuplePromise> correlationValues = new HashMap<>(tuples.size());
            while (tupleListIter.hasNext()) {
                Object[] tuple = tupleListIter.next();
                Object correlationValue = tuple[startIndex];

                TuplePromise tupleIndexValue = correlationValues.get(correlationValue);

                if (tupleIndexValue == null) {
                    tupleIndexValue = new TuplePromise(startIndex);
                    tupleIndexValue.add(tuple);
                    correlationValues.put(correlationValue, tupleIndexValue);

                    // Can't correlate null
                    if (correlationValue != null) {
                        if (correlationBasisEntity != null) {
                            correlationParams.add(em.getReference(correlationBasisEntity, correlationValue));
                        } else {
                            correlationParams.add(correlationValue);
                        }

                        if (batchSize == correlationParams.realSize()) {
                            Object defaultKey;
                            if (correlationBasisEntity != null) {
                                defaultKey = jpaProvider.getIdentifier(correlationParams.get(0));
                            } else {
                                defaultKey = correlationParams.get(0);
                            }
                            batchLoad(correlationValues, correlationParams, null, defaultKey, viewRootJpqlMacro, BatchCorrelationMode.VALUES);
                        }
                    }
                } else {
                    tupleIndexValue.add(tuple);
                }
            }

            if (correlationParams.realSize() > 0) {
                batchLoad(correlationValues, correlationParams, null, null, viewRootJpqlMacro, BatchCorrelationMode.VALUES);
            }

            fillDefaultValues(Collections.singletonMap(null, correlationValues));
        }

        consumeTupleMacroViewValues(tuples);
        return tuples;
    }

    @Override
    protected void populateParameters(FullQueryBuilder<?, ?> queryBuilder) {
        FullQueryBuilder<?, ?> mainBuilder = entityViewConfiguration.getCriteriaBuilder();
        for (Parameter<?> paramEntry : mainBuilder.getParameters()) {
            if (!paramEntry.getName().equals(correlationParamName) && queryBuilder.containsParameter(paramEntry.getName()) && !queryBuilder.isParameterSet(paramEntry.getName())) {
                queryBuilder.setParameter(paramEntry.getName(), mainBuilder.getParameterValue(paramEntry.getName()));
            }
        }
        for (Map.Entry<String, Object> paramEntry : entityViewConfiguration.getOptionalParameters().entrySet()) {
            if (!paramEntry.getKey().equals(correlationParamName) && queryBuilder.containsParameter(paramEntry.getKey()) && !queryBuilder.isParameterSet(paramEntry.getKey())) {
                queryBuilder.setParameter(paramEntry.getKey(), paramEntry.getValue());
            }
        }
    }

    private void consumeTupleMacroViewValues(List<Object[]> tuples) {
        int totalSize = tuples.size();
        if (embeddingViewIndex > startIndex && viewRootIndex > startIndex) {
            for (int i = 0; i < totalSize; i++) {
                Object[] tuple = tuples.get(i);
                tuple[viewRootIndex] = TupleReuse.CONSUMED;
                tuple[embeddingViewIndex] = TupleReuse.CONSUMED;
            }
        } else if (embeddingViewIndex > startIndex) {
            for (int i = 0; i < totalSize; i++) {
                Object[] tuple = tuples.get(i);
                tuple[embeddingViewIndex] = TupleReuse.CONSUMED;
            }
        } else if (viewRootIndex > startIndex) {
            for (int i = 0; i < totalSize; i++) {
                Object[] tuple = tuples.get(i);
                tuple[viewRootIndex] = TupleReuse.CONSUMED;
            }
        }
    }

    private void transformViewMacroAware(List<Object[]> tuples, FixedArrayList correlationParams, int tupleOffset, String correlationRoot, CorrelatedSubqueryViewRootJpqlMacro macro, BatchCorrelationMode correlationMode, ManagedViewType<?> viewType, int viewIndex) {
        EntityManager em = criteriaBuilder.getEntityManager();
        int totalSize = tuples.size();
        Map<Object, Map<Object, TuplePromise>> viewRoots = new HashMap<>(totalSize);
        Map<Object, Map<Object, TuplePromise>> correlationValues = new HashMap<>(totalSize);

        // Group tuples by view roots and correlation values and create tuple promises
        for (int i = 0; i < totalSize; i++) {
            Object[] tuple = tuples.get(i);
            Object viewRootKey = tuple[viewIndex];
            Object correlationValueKey = tuple[startIndex];

            if (viewRootKey != null && correlationValueKey != null) {
                Map<Object, TuplePromise> viewRootCorrelationValues = viewRoots.get(viewRootKey);
                if (viewRootCorrelationValues == null) {
                    viewRootCorrelationValues = new HashMap<>();
                    viewRoots.put(viewRootKey, viewRootCorrelationValues);
                }
                TuplePromise viewRootPromise = viewRootCorrelationValues.get(correlationValueKey);
                if (viewRootPromise == null) {
                    viewRootPromise = new TuplePromise(startIndex);
                    viewRootCorrelationValues.put(correlationValueKey, viewRootPromise);
                }
                viewRootPromise.add(tuple);

                Map<Object, TuplePromise> correlationValueViewRoots = correlationValues.get(correlationValueKey);
                if (correlationValueViewRoots == null) {
                    correlationValueViewRoots = new HashMap<>();
                    correlationValues.put(correlationValueKey, correlationValueViewRoots);
                }
                TuplePromise correlationValuePromise = correlationValueViewRoots.get(viewRootKey);
                if (correlationValuePromise == null) {
                    correlationValuePromise = new TuplePromise(startIndex);
                    correlationValueViewRoots.put(viewRootKey, correlationValuePromise);
                }
                correlationValuePromise.add(tuple);
            }
        }

        boolean batchCorrelationValues = !macro.usesViewMacro() && viewRoots.size() <= correlationValues.size();
        FixedArrayList viewRootIds = new FixedArrayList(batchSize);

        if (batchCorrelationValues) {
            if (batchSize > 1) {
                // If the expectation was wrong, we have to create a new criteria builder
                if (expectBatchCorrelationMode != BatchCorrelationMode.VALUES) {
                    applyAndGetCorrelationRoot(BatchCorrelationMode.VALUES);
                    macro = BatchCorrelationMode.VIEW_ROOTS == correlationMode ? viewRootJpqlMacro : embeddingViewJpqlMacro;
                    ObjectBuilder<?> objectBuilder = correlator.finish(criteriaBuilder, entityViewConfiguration, 0, tupleOffset, correlationRoot, embeddingViewJpqlMacro, true);
                    criteriaBuilder.select(correlationSelectExpression);
                    if (indexCorrelator != null) {
                        ObjectBuilder<?> indexBuilder = indexCorrelator.finish(criteriaBuilder, entityViewConfiguration, tupleOffset, 0, indexExpression, embeddingViewJpqlMacro, true);
                        if (indexBuilder != null) {
                            criteriaBuilder.selectNew(new LateAdditionalObjectBuilder(objectBuilder, indexBuilder, false));
                        }
                    }
                }
                macro.addBatchPredicate(criteriaBuilder);
            } else {
                // We have to bind the view id value, otherwise we might get wrong results
                macro.addIdParamPredicate(criteriaBuilder);
            }
            populateParameters(criteriaBuilder);
            query = criteriaBuilder.getQuery();

            for (Map.Entry<Object, Map<Object, TuplePromise>> batchEntry : viewRoots.entrySet()) {
                Map<Object, TuplePromise> batchValues = batchEntry.getValue();
                for (Map.Entry<Object, TuplePromise> batchValueEntry : batchValues.entrySet()) {
                    if (correlationBasisEntity != null) {
                        correlationParams.add(em.getReference(correlationBasisEntity, batchValueEntry.getKey()));
                    } else {
                        correlationParams.add(batchValueEntry.getKey());
                    }

                    if (batchSize == correlationParams.realSize()) {
                        viewRootIds.add(batchEntry.getKey());
                        Object defaultKey;
                        if (correlationBasisEntity != null) {
                            defaultKey = jpaProvider.getIdentifier(correlationParams.get(0));
                        } else {
                            defaultKey = correlationParams.get(0);
                        }
                        batchLoad(batchValues, correlationParams, viewRootIds, defaultKey, macro, correlationMode);
                    }
                }

                if (correlationParams.realSize() > 0) {
                    viewRootIds.add(batchEntry.getKey());
                    batchLoad(batchValues, correlationParams, viewRootIds, null, macro, correlationMode);
                }
            }

            fillDefaultValues(viewRoots);
        } else {
            if (batchSize > 1) {
                // If the expectation was wrong, we have to create a new criteria builder
                if (expectBatchCorrelationMode != correlationMode) {
                    applyAndGetCorrelationRoot(correlationMode);
                    macro = BatchCorrelationMode.VIEW_ROOTS == correlationMode ? viewRootJpqlMacro : embeddingViewJpqlMacro;
                    ObjectBuilder<?> objectBuilder = correlator.finish(criteriaBuilder, entityViewConfiguration, 0, tupleOffset, correlationRoot, embeddingViewJpqlMacro, true);
                    criteriaBuilder.select(correlationSelectExpression);
                    if (indexCorrelator != null) {
                        ObjectBuilder<?> indexBuilder = indexCorrelator.finish(criteriaBuilder, entityViewConfiguration, tupleOffset, 0, indexExpression, embeddingViewJpqlMacro, true);
                        if (indexBuilder != null) {
                            criteriaBuilder.selectNew(new LateAdditionalObjectBuilder(objectBuilder, indexBuilder, false));
                        }
                    }
                }
                macro.addBatchPredicate(criteriaBuilder);
            } else {
                // We have to bind the view id value, otherwise we might get wrong results
                macro.addIdParamPredicate(criteriaBuilder);
            }
            populateParameters(criteriaBuilder);
            query = criteriaBuilder.getQuery();

            for (Map.Entry<Object, Map<Object, TuplePromise>> batchEntry : correlationValues.entrySet()) {
                Map<Object, TuplePromise> batchValues = batchEntry.getValue();
                for (Map.Entry<Object, TuplePromise> batchValueEntry : batchValues.entrySet()) {
                    viewRootIds.add(batchValueEntry.getKey());

                    if (batchSize == viewRootIds.realSize()) {
                        if (correlationBasisEntity != null) {
                            correlationParams.add(em.getReference(correlationBasisEntity, batchEntry.getKey()));
                        } else {
                            correlationParams.add(batchEntry.getKey());
                        }
                        Object defaultKey = viewRootIds.get(0);
                        batchLoad(batchValues, correlationParams, viewRootIds, defaultKey, macro, correlationMode);
                    }
                }

                if (viewRootIds.realSize() > 0) {
                    if (correlationBasisEntity != null) {
                        correlationParams.add(em.getReference(correlationBasisEntity, batchEntry.getKey()));
                    } else {
                        correlationParams.add(batchEntry.getKey());
                    }
                    batchLoad(batchValues, correlationParams, viewRootIds, null, macro, correlationMode);
                }
            }

            fillDefaultValues(correlationValues);
        }
    }

    private void batchLoad(Map<Object, TuplePromise> correlationValues, FixedArrayList batchParameters, FixedArrayList viewRootIds, Object defaultKey, CorrelatedSubqueryViewRootJpqlMacro macro, BatchCorrelationMode batchCorrelationMode) {
        batchParameters.clearRest();
        if (criteriaBuilder.containsParameter(correlationParamName)) {
            if (batchSize > 1 && batchCorrelationMode == BatchCorrelationMode.VALUES) {
                criteriaBuilder.setParameter(correlationParamName, batchParameters);
                query.setParameter(correlationParamName, batchParameters);
            } else {
                criteriaBuilder.setParameter(correlationParamName, batchParameters.get(0));
                query.setParameter(correlationParamName, batchParameters.get(0));
            }
        }

        if (viewRootIds != null) {
            viewRootIds.clearRest();
            if (viewRootIds.size() == 1) {
                macro.setParameters(criteriaBuilder, query, viewRootIds.get(0));
            } else {
                macro.setParameters(criteriaBuilder, query, viewRootIds);
            }
        }

        populateResult(correlationValues, defaultKey, (List<Object>) query.getResultList());

        batchParameters.reset();
        if (viewRootIds != null) {
            viewRootIds.reset();
        }
    }

    protected void populateResult(Map<Object, TuplePromise> correlationValues, Object defaultKey, List<Object> list) {
        if (batchSize == 1) {
            if (indexCorrelator == null && indexExpression == null) {
                correlationValues.get(defaultKey).onResult(createContainer(list), this);
            } else {
                Object result = createDefaultResult();
                for (int i = 0; i < list.size(); i++) {
                    Object[] element = (Object[]) list.get(i);
                    Object indexObject = element[keyIndex];
                    containerAccumulator.add(result, indexObject, element[valueIndex], isRecording());
                }
                correlationValues.get(defaultKey).onResult(result, this);
            }
            return;
        }
        Map<Object, Object> collections = new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object[] element = (Object[]) list.get(i);
            Object result = collections.get(element[keyIndex]);
            if (result == null) {
                result = createDefaultResult();
                collections.put(element[keyIndex], result);
            }

            Object indexObject = null;
            if (indexCorrelator != null || indexExpression != null) {
                indexObject = element[keyIndex + 1];
            }
            containerAccumulator.add(result, indexObject, element[valueIndex], isRecording());
        }

        for (Map.Entry<Object, Object> entry : collections.entrySet()) {
            TuplePromise tuplePromise = correlationValues.get(entry.getKey());
            if (tuplePromise != null) {
                tuplePromise.onResult(postConstruct(entry.getValue()), this);
            }
        }
    }

}
