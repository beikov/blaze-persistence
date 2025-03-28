/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite.entity;

import javax.persistence.AssociationOverride;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.JoinColumn;
import java.io.Serializable;

@Embeddable
public class NameObjectContainer2 implements Serializable {

    private String name;
    private NameObject nameObject = new NameObject();

    public NameObjectContainer2() {
    }

    public NameObjectContainer2(String name, NameObject nameObject) {
        this.name = name;
        this.nameObject = nameObject;
    }

    @Column(name = "container_name", length = 30)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "primaryName", column = @Column(name = "container_primary_name", length = 30)),
        @AttributeOverride(name = "secondaryName", column = @Column(name = "container_secondary_name", length = 30))
    })
    @AssociationOverride(name = "intIdEntity", joinColumns = @JoinColumn(name = "container_int_id_entity"))
    public NameObject getNameObject() {
        return nameObject;
    }

    public void setNameObject(NameObject nameObject) {
        this.nameObject = nameObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NameObjectContainer2)) {
            return false;
        }

        NameObjectContainer2 that = (NameObjectContainer2) o;

        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
            return false;
        }
        return getNameObject() != null ? getNameObject().equals(that.getNameObject()) : that.getNameObject() == null;
    }

    @Override
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getNameObject() != null ? getNameObject().hashCode() : 0);
        return result;
    }
}
