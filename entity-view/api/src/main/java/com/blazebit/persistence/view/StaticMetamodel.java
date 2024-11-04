/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@linkplain StaticMetamodel} annotation specifies that the class is the metamodel class
 * for the entity view class designated by the value element.
 *
 * @author Christian Beikov
 * @since 1.5.0
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface StaticMetamodel {

    /**
     * The entity view class.
     *
     * @return the entity view class
     */
    Class<?> value();

}
