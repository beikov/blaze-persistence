/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.limit.topn.model;

import java.util.List;

import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Limit;

/**
 *
 * @author Christian Beikov
 * @since 1.6.16
 */
@EntityView(Person.class)
public interface PersonLimitJoinView extends PersonLimitView {

    @Limit(limit = "2", order = {"age", "id"})
    public List<DocumentLimitView> getOwnedDocuments();

}
