/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.basic;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import com.blazebit.persistence.view.EntityViews;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.blazebit.persistence.view.testsuite.AbstractEntityViewTest;
import com.blazebit.persistence.view.testsuite.basic.model.InvalidJoinOrderPersonView;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.Person;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Moritz Becker
 * @since 1.2.0
 */
public class JoinOrderTest extends AbstractEntityViewTest {

    private EntityViewManager evm;

    @Before
    public void initEvm() {
        evm = build(InvalidJoinOrderPersonView.class);
    }

    @Test
    @Ignore("Ignored for now since it's a hibernate bug")
    public void testInvalidJoinOrder() {
        Person p1 = new Person("p1");
        Person p2 = new Person("p3");
        p2.getLocalized().put(1, "Loc1");
        p2.getLocalized().put(2, "Loc2");

        Document d1 = new Document();
        d1.setOwner(p1);
        d1.getContacts().put(1, p1);
        d1.getContacts().put(2, p2);

        Document d2 = new Document();
        d2.setOwner(p1);

        p1.setFriend(p2);

        em.persist(p2);
        em.persist(p1);
        em.persist(d1);
        em.persist(d2);

        p1.setPartnerDocument(d1);

        em.flush();
        em.clear();

        EntityViewSetting<InvalidJoinOrderPersonView, CriteriaBuilder<InvalidJoinOrderPersonView>> setting = EntityViewSetting.create(InvalidJoinOrderPersonView.class);
        setting.addOptionalParameter("contactPersonNumber", 2);
        List<InvalidJoinOrderPersonView> persons = evm.applySetting(setting, cbf.create(em, Person.class))
                .getResultList();

        assertEquals(3, persons.size());
    }
}
