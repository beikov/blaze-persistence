/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.inheritance.subview.correlated.model;

import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.EntityViewInheritance;

/**
 *
 * @author Christian Beikov
 * @since 1.3.0
 */
@EntityView(Person.class)
@EntityViewInheritance({ YoungPersonView1.class, OldPersonView1.class })
public interface PersonBaseView1 extends PersonBaseView {

    String getName();
}
