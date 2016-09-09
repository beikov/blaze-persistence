package com.blazebit.persistence.criteria.impl.expression;

import java.util.Collection;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import com.blazebit.persistence.criteria.impl.BlazeCriteriaBuilderImpl;
import com.blazebit.persistence.criteria.impl.ParameterVisitor;
import com.blazebit.persistence.criteria.impl.RenderContext;
import com.blazebit.persistence.criteria.impl.path.PluralAttributePath;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class MemberOfPredicate<E, C extends Collection<E>> extends AbstractSimplePredicate {

    private static final long serialVersionUID = 1L;
    
    private final Expression<E> elementExpression;
    private final PluralAttributePath<C> collectionPath;

    public MemberOfPredicate(BlazeCriteriaBuilderImpl criteriaBuilder, boolean negated, Expression<E> elementExpression, PluralAttributePath<C> collectionPath) {
        super(criteriaBuilder, negated);
        this.elementExpression = elementExpression;
        this.collectionPath = collectionPath;
    }

    public MemberOfPredicate(BlazeCriteriaBuilderImpl criteriaBuilder, boolean negated, E element, PluralAttributePath<C> collectionPath) {
        this(criteriaBuilder, negated, new LiteralExpression<E>(criteriaBuilder, element), collectionPath);
    }

    @Override
    public AbstractPredicate copyNegated() {
        return new MemberOfPredicate<E, C>(criteriaBuilder, !isNegated(), elementExpression, collectionPath);
    }

    @Override
    public void visitParameters(ParameterVisitor visitor) {
        visitor.visit(elementExpression);
        visitor.visit(collectionPath);
    }

    @Override
    public void render(RenderContext context) {
        context.apply(elementExpression);
        
        if (isNegated()) {
            context.getBuffer().append(" NOT");
        }

        context.getBuffer().append(" MEMBER OF ");
        context.apply(collectionPath);
    }

}