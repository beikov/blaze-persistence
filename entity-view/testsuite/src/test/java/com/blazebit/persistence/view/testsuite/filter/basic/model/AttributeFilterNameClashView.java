/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.filter.basic.model;

import com.blazebit.persistence.testsuite.entity.PrimitiveDocument;
import com.blazebit.persistence.view.AttributeFilter;
import com.blazebit.persistence.view.AttributeFilters;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.filter.ContainsFilter;
import com.blazebit.persistence.view.filter.ContainsIgnoreCaseFilter;

/**
 * @author Moritz Becker
 * @since 1.2.0
 */
@EntityView(PrimitiveDocument.class)
public interface AttributeFilterNameClashView {

    @IdMapping
    Long getId();

    @AttributeFilters({
        @AttributeFilter(name = "filter", value = ContainsIgnoreCaseFilter.class),
        @AttributeFilter(name = "filter", value = ContainsFilter.class)
    })
    String getName();

}
