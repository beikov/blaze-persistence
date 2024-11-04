/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.subview.model;

import com.blazebit.persistence.view.AttributeFilter;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.filter.ContainsFilter;
import com.blazebit.persistence.testsuite.entity.Person;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@EntityView(Person.class)
public interface SimplePersonSubView {
    
    @IdMapping
    public Long getId();

    @Mapping("UPPER(name)")
    @AttributeFilter(ContainsFilter.class)
    public String getName();
}
