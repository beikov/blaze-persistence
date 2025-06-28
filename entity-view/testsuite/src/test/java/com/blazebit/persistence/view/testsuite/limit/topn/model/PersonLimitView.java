/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.limit.topn.model;

import java.util.List;

import com.blazebit.persistence.view.testsuite.basic.model.IdHolderView;

/**
 *
 * @author Christian Beikov
 * @since 1.6.16
 */
public interface PersonLimitView extends IdHolderView<Long> {

    public String getName();

    public List<DocumentLimitView> getOwnedDocuments();

}
