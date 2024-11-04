/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.examples.deltaspike.data.rest.view;

import com.blazebit.persistence.examples.deltaspike.data.rest.model.Cat;
import com.blazebit.persistence.view.CreatableEntityView;
import com.blazebit.persistence.view.EntityView;

/**
 * @author Christian Beikov
 * @since 1.4.0
 */
@CreatableEntityView
@EntityView(Cat.class)
public interface CatCreateView extends CatUpdateView {

    PersonIdView getOwner();

    void setOwner(PersonIdView owner);
}
