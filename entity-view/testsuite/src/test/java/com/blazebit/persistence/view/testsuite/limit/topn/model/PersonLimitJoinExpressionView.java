/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.limit.topn.model;

import java.util.List;

import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Limit;
import com.blazebit.persistence.view.Mapping;

/**
 *
 * @author Christian Beikov
 * @since 1.6.16
 */
@EntityView(Person.class)
public interface PersonLimitJoinExpressionView extends PersonLimitView {

    @Limit(limit = "2", order = {"age", "id"})
    @Mapping("ownedDocuments[owner.name = VIEW(name)]")
    public List<DocumentLimitView> getOwnedDocuments();

}
