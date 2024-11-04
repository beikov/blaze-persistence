/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence;

/**
 * An interface for builders that support set operators.
 *
 * @param <T> The builder result type
 * @param <X> The concrete builder type
 * @author Christian Beikov
 * @since 1.1.0
 */
public interface BaseFinalSetOperationBuilder<T, X extends BaseFinalSetOperationBuilder<T, X>> extends OrderByBuilder<X>, LimitBuilder<X> {

}
