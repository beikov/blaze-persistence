/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite;

import java.util.List;

import org.junit.Test;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.testsuite.tx.TxVoidWork;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class EntityJoinTest extends AbstractCoreTest {

    @Override
    public void setUpOnce() {
        cleanDatabase();
        transactional(new TxVoidWork() {
            @Override
            public void work(EntityManager em) {
                Document doc1 = new Document("doc1", 2);
                Document doc2 = new Document("doc2", 3);
                Document doc3 = new Document("doc3", 5);

                Person o1 = new Person("pers1", 1);
                Person o2 = new Person("pers2", 4);
                Person o3 = new Person("doc1", 0);

                doc1.setOwner(o1);
                doc2.setOwner(o1);
                doc3.setOwner(o1);

                doc1.getContacts().put(1, o1);
                doc1.getContacts().put(2, o2);

                em.persist(o1);
                em.persist(o2);
                em.persist(o3);

                em.persist(doc1);
                em.persist(doc2);
                em.persist(doc3);
            }
        });
    }

    @Test
    public void testEntityInnerJoin() {
        CriteriaBuilder<Tuple> crit = cbf.create(em, Tuple.class)
                .from(Document.class, "d")
                .innerJoinOn(Person.class, "p")
                    .on("p.age").geExpression("d.age")
                .end()
                .select("d.name").select("p.name")
                .orderByAsc("d.name");
        assertEquals("SELECT d.name, p.name FROM Document d JOIN Person p" +
                onClause("p.age >= d.age")
                + " ORDER BY d.name ASC", crit.getQueryString());
        List<Tuple> results = crit.getResultList();

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).get(0));
        assertEquals("pers2", results.get(0).get(1));

        assertEquals("doc2", results.get(1).get(0));
        assertEquals("pers2", results.get(1).get(1));
    }

    @Test
    public void testEntityLeftJoin() {
        CriteriaBuilder<Tuple> crit = cbf.create(em, Tuple.class)
                .from(Document.class, "d")
                .leftJoinOn(Person.class, "p")
                    .on("p.name").eqExpression("d.name")
                .end()
                .select("d.name").select("p.name")
                .orderByAsc("d.name");
        assertEquals("SELECT d.name, p.name FROM Document d LEFT JOIN Person p" +
                onClause("p.name = d.name") +
                " ORDER BY d.name ASC", crit.getQueryString());
        List<Tuple> results = crit.getResultList();

        assertEquals(3, results.size());
        assertEquals("doc1", results.get(0).get(0));
        assertEquals("doc1", results.get(0).get(1));

        assertEquals("doc2", results.get(1).get(0));
        assertNull(results.get(1).get(1));

        assertEquals("doc3", results.get(2).get(0));
        assertNull(results.get(2).get(1));
    }
}
