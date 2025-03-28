/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite;

import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToOne;
import java.io.Serializable;

/**
 * @author Christian Beikov
 * @since 1.4.1
 */
public class Issue969Test extends AbstractCoreTest {

    @Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[]{BasicEntity.class, IdClassEntity.class };
    }

    @Entity(name = "BasicEntity")
    public static class BasicEntity {
        @Id
        Long key1;
    }

    @Entity(name = "IdClassEntity")
    @IdClass(IdClassEntity.IdClassEntityId.class)
    public static class IdClassEntity {
        @Id
        @ManyToOne
        BasicEntity basicEntity;
        @Id
        Long key2;

        public static class IdClassEntityId implements Serializable {
            Long basicEntity;
            Long key2;
        }
    }

    @Test
    public void test1() {
        cbf.create(em, IdClassEntity.class)
                .orderByAsc("basicEntity.key1")
                .orderByAsc("key2")
                .page(0, 1)
                .getResultList();
    }

    @Test
    public void test2() {
        cbf.create(em, IdClassEntity.class)
                .orderByAsc("basicEntity.key1")
                .orderByAsc("key2")
                .page(0, 1)
                .getResultList();
    }

    @Test
    public void test3() {
        cbf.create(em, IdClassEntity.class)
                .where("basicEntity").eq(em.getReference(BasicEntity.class, 1L))
                .orderByAsc("key2")
                .orderByAsc("basicEntity.key1")
                .page(0, 1)
                .getResultList();
    }

    @Test
    public void test4() {
        cbf.create(em, IdClassEntity.class)
                .where("basicEntity").in(em.getReference(BasicEntity.class, 1L))
                .orderByAsc("key2")
                .orderByAsc("basicEntity.key1")
                .page(0, 1)
                .getResultList();
    }
}