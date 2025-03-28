/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.impl.objectbuilder.transformator;

import com.blazebit.persistence.ParameterHolder;
import com.blazebit.persistence.view.impl.EntityViewConfiguration;
import com.blazebit.persistence.view.impl.objectbuilder.ConstrainedTupleList;
import com.blazebit.persistence.view.impl.objectbuilder.transformer.NullListTupleTransformer;
import com.blazebit.persistence.view.impl.objectbuilder.transformer.TupleListTransformer;
import com.blazebit.persistence.view.impl.objectbuilder.transformer.TupleListTransformerFactory;
import com.blazebit.persistence.view.impl.objectbuilder.transformer.TupleTransformer;
import com.blazebit.persistence.view.impl.objectbuilder.transformer.TupleTransformerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class TupleTransformatorFactory {

    private final List<TupleTransformatorFactoryLevel> transformatorLevels = new ArrayList<TupleTransformatorFactoryLevel>();
    private int currentLevel = 0;

    public TupleTransformatorFactory() {
        transformatorLevels.add(new TupleTransformatorFactoryLevel());
    }

    public boolean hasTransformers() {
        return transformatorLevels.get(0).tupleListTransformer != null
            || transformatorLevels.get(0).tupleListTransformerFactory != null
            || transformatorLevels.get(0).tupleTransformerFactories.size() > 0;
    }

    public boolean hasListTransformers() {
        return transformatorLevels.get(0).tupleListTransformer != null
                || transformatorLevels.get(0).tupleListTransformerFactory != null;
    }

    public void add(TupleTransformatorFactory tupleTransformator) {
        if (!tupleTransformator.hasTransformers()) {
            return;
        }

        for (int i = 0; i < tupleTransformator.transformatorLevels.size(); i++) {
            if (i != 0) {
                incrementLevel();
            }

            TupleTransformatorFactoryLevel thisLevel = transformatorLevels.get(currentLevel);
            TupleTransformatorFactoryLevel otherLevel = tupleTransformator.transformatorLevels.get(i);
            thisLevel.tupleTransformerFactories.addAll(otherLevel.tupleTransformerFactories);
            thisLevel.tupleListTransformer = otherLevel.tupleListTransformer;
            thisLevel.tupleListTransformerFactory = otherLevel.tupleListTransformerFactory;
        }
    }

    public void add(Set<Integer> consumableIndexes, int classMappingIndex, int[] subtypeIndexes, TupleTransformatorFactory tupleTransformator) {
        if (!tupleTransformator.hasTransformers()) {
            return;
        }

        for (int i = 0; i < tupleTransformator.transformatorLevels.size(); i++) {
            if (i != 0) {
                incrementLevel();
            }

            TupleTransformatorFactoryLevel thisLevel = transformatorLevels.get(currentLevel);
            TupleTransformatorFactoryLevel otherLevel = tupleTransformator.transformatorLevels.get(i);
            for (TupleTransformerFactory tupleTransformerFactory : otherLevel.tupleTransformerFactories) {
                int consumeEndIndex = tupleTransformerFactory.getConsumeEndIndex();
                for (int j = tupleTransformerFactory.getConsumeStartIndex(); j < consumeEndIndex; j++) {
                    consumableIndexes.add(j);
                }
                thisLevel.tupleTransformerFactories.add(new ConstrainedTupleTransformerFactory(classMappingIndex, subtypeIndexes, tupleTransformerFactory));
            }

            if (otherLevel.tupleListTransformer != null) {
                thisLevel.tupleListTransformer = new ConstrainedTupleListTransformer(classMappingIndex, subtypeIndexes, otherLevel.tupleListTransformer);
                consumableIndexes.add(otherLevel.tupleListTransformer.getConsumableIndex());
            }
            if (otherLevel.tupleListTransformerFactory != null) {
                thisLevel.tupleListTransformerFactory = new ConstrainedTupleListTransformerFactory(classMappingIndex, subtypeIndexes, otherLevel.tupleListTransformerFactory);
                consumableIndexes.add(otherLevel.tupleListTransformerFactory.getConsumableIndex());
            }
        }
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.3.0
     */
    private static class ConstrainedTupleTransformer implements TupleTransformer {

        private final int classMappingIndex;
        private final int[] subtypeIndexes;
        private final TupleTransformer delegate;

        public ConstrainedTupleTransformer(int classMappingIndex, int[] subtypeIndexes, TupleTransformer delegate) {
            this.classMappingIndex = classMappingIndex;
            this.subtypeIndexes = subtypeIndexes;
            this.delegate = delegate;
        }

        @Override
        public int getConsumeStartIndex() {
            return delegate.getConsumeStartIndex();
        }

        @Override
        public int getConsumeEndIndex() {
            return delegate.getConsumeEndIndex();
        }

        @Override
        public Object[] transform(Object[] tuple, UpdatableViewMap updatableViewMap) {
            final Number classIndex = (Number) tuple[classMappingIndex];
            if (classIndex != null && Arrays.binarySearch(subtypeIndexes, classIndex.intValue()) >= 0) {
                return delegate.transform(tuple, updatableViewMap);
            } else {
                return tuple;
            }
        }
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.3.0
     */
    private static class ConstrainedTupleTransformerFactory implements TupleTransformerFactory {

        private final int classMappingIndex;
        private final int[] subtypeIndexes;
        private final TupleTransformerFactory delegate;

        public ConstrainedTupleTransformerFactory(int classMappingIndex, int[] subtypeIndexes, TupleTransformerFactory delegate) {
            this.classMappingIndex = classMappingIndex;
            this.subtypeIndexes = subtypeIndexes;
            this.delegate = delegate;
        }

        @Override
        public int getConsumeStartIndex() {
            return delegate.getConsumeStartIndex();
        }

        @Override
        public int getConsumeEndIndex() {
            return delegate.getConsumeEndIndex();
        }

        @Override
        public TupleTransformer create(ParameterHolder<?> parameterHolder, Map<String, Object> optionalParameters, EntityViewConfiguration entityViewConfiguration) {
            return new ConstrainedTupleTransformer(classMappingIndex, subtypeIndexes, delegate.create(parameterHolder, optionalParameters, entityViewConfiguration));
        }
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.3.0
     */
    private static class ConstrainedTupleListTransformer extends TupleListTransformer {

        private final int classMappingIndex;
        private final int[] subtypeIndexes;
        private final TupleListTransformer delegate;

        public ConstrainedTupleListTransformer(int classMappingIndex, int[] subtypeIndexes, TupleListTransformer delegate) {
            super(-1);
            this.classMappingIndex = classMappingIndex;
            this.subtypeIndexes = subtypeIndexes;
            this.delegate = delegate;
        }

        @Override
        public int getConsumableIndex() {
            return delegate.getConsumableIndex();
        }

        @Override
        public List<Object[]> transform(List<Object[]> tuples) {
            ConstrainedTupleList tupleList = new ConstrainedTupleList(classMappingIndex, subtypeIndexes, tuples);
            if (!tupleList.isEmpty()) {
                delegate.transform(tupleList);
            }
            return tuples;
        }
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.3.0
     */
    private static class ConstrainedTupleListTransformerFactory implements TupleListTransformerFactory {

        private final int classMappingIndex;
        private final int[] subtypeIndexes;
        private final TupleListTransformerFactory delegate;

        public ConstrainedTupleListTransformerFactory(int classMappingIndex, int[] subtypeIndexes, TupleListTransformerFactory delegate) {
            this.classMappingIndex = classMappingIndex;
            this.subtypeIndexes = subtypeIndexes;
            this.delegate = delegate;
        }

        @Override
        public int getConsumableIndex() {
            return delegate.getConsumableIndex();
        }

        @Override
        public TupleListTransformer create(ParameterHolder<?> parameterHolder, Map<String, Object> optionalParameters, EntityViewConfiguration entityViewConfiguration) {
            TupleListTransformer delegateTupleListTransformer = delegate.create(parameterHolder, optionalParameters, entityViewConfiguration);
            if (delegateTupleListTransformer instanceof NullListTupleTransformer) {
                // No need to constraint anything here since the results are empty
                return delegateTupleListTransformer;
            }
            return new ConstrainedTupleListTransformer(classMappingIndex, subtypeIndexes, delegateTupleListTransformer);
        }
    }

    private void incrementLevel() {
        currentLevel++;
        transformatorLevels.add(new TupleTransformatorFactoryLevel());
    }

    public void add(TupleListTransformer tupleListTransformer) {
        transformatorLevels.get(currentLevel).tupleListTransformer = tupleListTransformer;
        incrementLevel();
    }

    public void add(TupleListTransformerFactory tupleListTransformerFactory) {
        transformatorLevels.get(currentLevel).tupleListTransformerFactory = tupleListTransformerFactory;
        incrementLevel();
    }

    public void add(TupleTransformerFactory tupleTransformerFactory) {
        transformatorLevels.get(currentLevel).tupleTransformerFactories.add(tupleTransformerFactory);
    }

    public TupleTransformator create(ParameterHolder<?> parameterHolder, Map<String, Object> optionalParameters, EntityViewConfiguration entityViewConfiguration) {
        TupleTransformatorLevel[] newTransformatorLevels = new TupleTransformatorLevel[transformatorLevels.size()];
        // We create the tuple transformers and level in the inverse order as deeper nested objects come first, yet we want to initialize stuff top-down to properly support nested join correlations
        for (int i = transformatorLevels.size() - 1; i >= 0; i--) {
            TupleTransformatorFactoryLevel thisLevel = transformatorLevels.get(i);
            List<TupleTransformerFactory> tupleTransformerFactories = thisLevel.tupleTransformerFactories;
            final TupleTransformer[] tupleTransformers = new TupleTransformer[tupleTransformerFactories.size()];
            // No need to copy this, because TupleListTransformer are not context sensitive
            final TupleListTransformer tupleListTransformer;

            if (thisLevel.tupleListTransformerFactory != null) {
                tupleListTransformer = thisLevel.tupleListTransformerFactory.create(parameterHolder, optionalParameters, entityViewConfiguration);
            } else {
                tupleListTransformer = thisLevel.tupleListTransformer;
            }

            for (int j = tupleTransformerFactories.size() - 1; j >= 0; j--) {
                TupleTransformerFactory tupleTransformerFactory = tupleTransformerFactories.get(j);
                tupleTransformers[j] = tupleTransformerFactory.create(parameterHolder, optionalParameters, entityViewConfiguration);
            }

            newTransformatorLevels[i] = new TupleTransformatorLevel(tupleTransformers, tupleListTransformer);
        }
        
        return new TupleTransformator(Arrays.asList(newTransformatorLevels));
    }
}
