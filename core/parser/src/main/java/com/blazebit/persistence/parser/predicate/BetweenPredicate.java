/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.parser.predicate;

import com.blazebit.persistence.parser.expression.Expression;
import com.blazebit.persistence.parser.expression.ExpressionCopyContext;

/**
 *
 * @author Christian Beikov
 * @author Moritz Becker
 * @since 1.0.0
 */
public class BetweenPredicate extends AbstractPredicate {

    private Expression left;
    private Expression start;
    private Expression end;

    public BetweenPredicate(Expression left, Expression start, Expression end) {
        this(left, start, end, false);
    }

    public BetweenPredicate(Expression left, Expression start, Expression end, boolean negated) {
        super(negated);
        this.left = left;
        this.start = start;
        this.end = end;
    }

    @Override
    public BetweenPredicate copy(ExpressionCopyContext copyContext) {
        return new BetweenPredicate(left.copy(copyContext), start.copy(copyContext), end.copy(copyContext), negated);
    }

    public Expression getLeft() {
        return left;
    }

    public Expression getStart() {
        return start;
    }

    public Expression getEnd() {
        return end;
    }

    public void setLeft(Expression left) {
        this.left = left;
    }

    public void setStart(Expression start) {
        this.start = start;
    }

    public void setEnd(Expression end) {
        this.end = end;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <T> T accept(ResultVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BetweenPredicate)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        BetweenPredicate that = (BetweenPredicate) o;

        if (left != null ? !left.equals(that.left) : that.left != null) {
            return false;
        }
        if (start != null ? !start.equals(that.start) : that.start != null) {
            return false;
        }
        return end != null ? end.equals(that.end) : that.end == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (left != null ? left.hashCode() : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        result = 31 * result + (end != null ? end.hashCode() : 0);
        return result;
    }
}
