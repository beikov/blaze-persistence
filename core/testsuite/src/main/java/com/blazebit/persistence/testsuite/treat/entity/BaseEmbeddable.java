
/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite.treat.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BaseEmbeddable<T extends Base<T, ?>> {
    
    public T getParent();

    public void setParent(T parent);

    public List<? extends T> getList();

    public void setList(List<? extends T> list);

    public Set<? extends T> getChildren();

    public void setChildren(Set<? extends T> children);

    public Map<? extends T, ? extends T> getMap();

    public void setMap(Map<? extends T, ? extends T> map);
    
}
