/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.graphql.dgs.model;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

/**
 * @author Christian Beikov
 * @since 1.6.16
 */
@Entity
public class Order {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    private OrderPos additionalPosition;
    @OneToMany(mappedBy = "order")
    private Set<OrderPos> positions = new HashSet<>();

    public Order() {
    }

    public Order(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OrderPos getAdditionalPosition() {
        return additionalPosition;
    }

    public void setAdditionalPosition(OrderPos additionalPosition) {
        this.additionalPosition = additionalPosition;
    }

    public Set<OrderPos> getPositions() {
        return positions;
    }

    public void setPositions(Set<OrderPos> positions) {
        this.positions = positions;
    }
}
