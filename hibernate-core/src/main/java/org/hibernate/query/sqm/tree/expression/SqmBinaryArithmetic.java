/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import org.hibernate.metamodel.model.domain.JpaMetamodel;
import org.hibernate.query.sqm.BinaryArithmeticOperator;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.tree.select.SqmSelectableNode;

/**
 * @author Steve Ebersole
 */
public class SqmBinaryArithmetic<T> extends AbstractSqmExpression<T> implements SqmSelectableNode<T> {
	private final SqmExpression<?> lhsOperand;
	private final BinaryArithmeticOperator operator;
	private final SqmExpression<?> rhsOperand;

	public SqmBinaryArithmetic(
			BinaryArithmeticOperator operator,
			SqmExpression<?> lhsOperand,
			SqmExpression<?> rhsOperand,
			JpaMetamodel domainModel,
			NodeBuilder nodeBuilder) {
		//noinspection unchecked
		super(
				(SqmExpressible<T>) domainModel.getTypeConfiguration().resolveArithmeticType(
						lhsOperand.getNodeType(),
						rhsOperand.getNodeType(),
						operator
				),
				nodeBuilder
		);

		this.lhsOperand = lhsOperand;
		this.operator = operator;
		this.rhsOperand = rhsOperand;

		this.lhsOperand.applyInferableType( rhsOperand.getNodeType() );
		this.rhsOperand.applyInferableType( lhsOperand.getNodeType() );
	}

	public SqmBinaryArithmetic(
			BinaryArithmeticOperator operator,
			SqmExpression<?> lhsOperand,
			SqmExpression<?> rhsOperand,
			SqmExpressible<T> expressibleType,
			NodeBuilder nodeBuilder) {
		super( expressibleType, nodeBuilder );

		this.operator = operator;

		this.lhsOperand = lhsOperand;
		this.rhsOperand = rhsOperand;

		applyInferableType( expressibleType );
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitBinaryArithmeticExpression( this );
	}

	/**
	 * Get the left-hand operand.
	 *
	 * @return The left-hand operand.
	 */
	public SqmExpression<?> getLeftHandOperand() {
		return lhsOperand;
	}

	/**
	 * Get the operator
	 *
	 * @return The operator
	 */
	public BinaryArithmeticOperator getOperator() {
		return operator;
	}

	/**
	 * Get the right-hand operand.
	 *
	 * @return The right-hand operand.
	 */
	public SqmExpression<?> getRightHandOperand() {
		return rhsOperand;
	}

	@Override
	protected void internalApplyInferableType(SqmExpressible<?> type) {
		rhsOperand.applyInferableType( type );
		lhsOperand.applyInferableType( type );

		super.internalApplyInferableType( type );
	}

	@Override
	public String asLoggableText() {
		return getOperator().toLoggableText( lhsOperand.asLoggableText(), rhsOperand.asLoggableText() );
	}

	@Override
	public void appendHqlString(StringBuilder sb) {
		lhsOperand.appendHqlString( sb );
		sb.append( ' ' );
		sb.append( operator.getOperatorSqlText() );
		sb.append( ' ' );
		rhsOperand.appendHqlString( sb );
	}

}
