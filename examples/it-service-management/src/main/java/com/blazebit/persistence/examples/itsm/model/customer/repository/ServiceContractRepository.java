/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.examples.itsm.model.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.blazebit.persistence.examples.itsm.model.customer.entity.ServiceContract;

/**
 * @author Giovanni Lovato
 * @since 1.4.0
 */
public interface ServiceContractRepository
        extends JpaRepository<ServiceContract, String>,
        JpaSpecificationExecutor<ServiceContract> {

}
