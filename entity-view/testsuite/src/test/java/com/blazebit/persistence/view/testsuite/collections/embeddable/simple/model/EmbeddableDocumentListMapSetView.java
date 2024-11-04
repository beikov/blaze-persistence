/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.collections.embeddable.simple.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.testsuite.collections.entity.simple.DocumentForElementCollections;
import com.blazebit.persistence.view.testsuite.collections.entity.simple.PersonForElementCollections;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@EntityView(DocumentForElementCollections.class)
public abstract class EmbeddableDocumentListMapSetView implements EmbeddableDocumentCollectionsView {

    @Mapping("personList")
    public abstract List<PersonForElementCollections> getA();

    @Mapping("contacts")
    public abstract Map<Integer, PersonForElementCollections> getB();

    @Mapping("partners")
    public abstract Set<PersonForElementCollections> getC();

    @Override
    public Map<Integer, PersonForElementCollections> getContacts() {
        return getB();
    }

    @Override
    public Set<PersonForElementCollections> getPartners() {
        return getC();
    }

    @Override
    public List<PersonForElementCollections> getPersonList() {
        return getA();
    }
}
