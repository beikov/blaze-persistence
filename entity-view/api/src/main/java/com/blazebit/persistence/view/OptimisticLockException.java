/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view;

/**
 * Thrown when an optimistic lock conflict has been detected.
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class OptimisticLockException extends javax.persistence.OptimisticLockException {

    /**
     * The entity view object that caused the exception.
     */
    private final Object entityView;

    /**
     * Constructs a new <code>OptimisticLockException</code> with given entity and entity view objects.
     *
     * @param entity The entity that caused the exception
     * @param entityView The entity view that caused the exception
     */
    public OptimisticLockException(Object entity, Object entityView) {
        super(null, null, entity);
        this.entityView = entityView;
    }

    /**
     * Constructs a new <code>OptimisticLockException</code> with given entity and entity view objects.
     *
     * @param message The exception message
     * @param entity The entity that caused the exception
     * @param entityView The entity view that caused the exception
     */
    public OptimisticLockException(String message, Object entity, Object entityView) {
        super(message, null, entity);
        this.entityView = entityView;
    }

    /**
     * Returns the entity view object that caused this exception.
     *
     * @return The entity view
     */
    public Object getEntityView() {
        return entityView;
    }
}
