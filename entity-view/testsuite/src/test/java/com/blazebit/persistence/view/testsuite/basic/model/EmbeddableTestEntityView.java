/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.basic.model;

import java.util.Map;
import java.util.Set;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.testsuite.entity.EmbeddableTestEntity2;
import com.blazebit.persistence.view.testsuite.entity.EmbeddableTestEntityId2;
import com.blazebit.persistence.testsuite.entity.IntIdEntity;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@EntityView(EmbeddableTestEntity2.class)
public interface EmbeddableTestEntityView extends IdHolderView<EmbeddableTestEntityId2> {

    @Mapping("id.intIdEntity")
    public IntIdEntity getIdIntIdEntity();
    
    @Mapping("id.intIdEntity.id")
    public Integer getIdIntIdEntityId();
    
    @Mapping("id.intIdEntity.name")
    public String getIdIntIdEntityName();
    
    @Mapping("id.key")
    public String getIdKey();

    @Mapping("embeddable")
    public EmbeddableTestEntityEmbeddableSubView getEmbeddable();

    @Mapping("embeddableSet")
    public Set<EmbeddableTestEntitySimpleEmbeddableSubView> getEmbeddableSet();

    @Mapping("embeddableMap")
    public Map<String, EmbeddableTestEntitySimpleEmbeddableSubView> getEmbeddableMap();

    @Mapping("embeddable.manyToOne")
    public EmbeddableTestEntity2 getEmbeddableManyToOne();

    @Mapping("embeddable.oneToMany")
    public Set<EmbeddableTestEntity2> getEmbeddableOneToMany();

    @Mapping("embeddable.elementCollection")
    public Map<String, IntIdEntity> getEmbeddableElementCollection();
}
