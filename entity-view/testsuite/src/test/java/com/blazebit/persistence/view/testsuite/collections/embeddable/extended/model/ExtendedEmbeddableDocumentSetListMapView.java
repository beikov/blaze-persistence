/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.collections.embeddable.extended.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.testsuite.collections.entity.extended.ExtendedDocumentForElementCollections;
import com.blazebit.persistence.view.testsuite.collections.entity.extended.ExtendedPersonForElementCollections;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@EntityView(ExtendedDocumentForElementCollections.class)
public abstract class ExtendedEmbeddableDocumentSetListMapView implements ExtendedEmbeddableDocumentCollectionsView {

    @Mapping("partners")
    public abstract Set<ExtendedPersonForElementCollections> getA();

    @Mapping("personList")
    public abstract List<ExtendedPersonForElementCollections> getB();

    @Mapping("contacts")
    public abstract Map<Integer, ExtendedPersonForElementCollections> getC();

    @Override
    public Map<Integer, ExtendedPersonForElementCollections> getContacts() {
        return getC();
    }

    @Override
    public Set<ExtendedPersonForElementCollections> getPartners() {
        return getA();
    }

    @Override
    public List<ExtendedPersonForElementCollections> getPersonList() {
        return getB();
    }
}
