/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.quarkus.runtime;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManagerFactory;

/**
 * @author Moritz Becker
 * @since 1.5.0
 */
@Dependent
public class DefaultCriteriaBuilderFactoryProducer {

    @DefaultBean
    @Produces
    @ApplicationScoped
    public CriteriaBuilderFactory produceCriteriaBuilderFactory(Instance<EntityManagerFactory> entityManagerFactory,
                                                                CriteriaBuilderConfiguration criteriaBuilderConfiguration) {
        return criteriaBuilderConfiguration.createCriteriaBuilderFactory(entityManagerFactory.get());
    }
}
