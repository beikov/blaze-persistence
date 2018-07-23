/*
 * Copyright 2014 - 2018 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.view.materialization.impl;

import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.sql.validate.SqlValidatorImpl;

/**
 * @author Christian Beikov
 * @since 1.2.0
 */
public class CalciteSqlValidator extends SqlValidatorImpl {

    public CalciteSqlValidator(SqlOperatorTable opTab, CalciteCatalogReader catalogReader, JavaTypeFactory typeFactory, SqlConformance conformance) {
        super(opTab, catalogReader, typeFactory, conformance);
    }

    @Override
    protected RelDataType getLogicalSourceRowType(RelDataType sourceRowType, SqlInsert insert) {
        final RelDataType superType = super.getLogicalSourceRowType(sourceRowType, insert);
        return ((JavaTypeFactory) typeFactory).toSql(superType);
    }

    @Override
    protected RelDataType getLogicalTargetRowType(RelDataType targetRowType, SqlInsert insert) {
        final RelDataType superType = super.getLogicalTargetRowType(targetRowType, insert);
        return ((JavaTypeFactory) typeFactory).toSql(superType);
    }
}
