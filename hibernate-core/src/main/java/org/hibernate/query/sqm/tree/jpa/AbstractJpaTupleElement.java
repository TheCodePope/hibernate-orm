/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.jpa;

import org.hibernate.query.criteria.JpaTupleElement;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.tree.AbstractSqmNode;
import org.hibernate.query.sqm.tree.SqmVisitableNode;

/**
 * Base support for {@link JpaTupleElement} impls
 *
 * @author Steve Ebersole
 */
public abstract class AbstractJpaTupleElement<T>
		extends AbstractSqmNode
		implements SqmVisitableNode, JpaTupleElement<T> {

	private SqmExpressible<T> expressibleType;
	private String alias;

	protected AbstractJpaTupleElement(SqmExpressible<? extends T> expressibleType, NodeBuilder criteriaBuilder) {
		super( criteriaBuilder );

		setExpressibleType(expressibleType);
	}

	@Override
	public String getAlias() {
		return alias;
	}

	/**
	 * Protected access to set the alias.
	 */
	protected void setAlias(String alias) {
		this.alias = alias;
	}

	public SqmExpressible<T> getNodeType() {
		return expressibleType;
	}

	protected final void setExpressibleType(SqmExpressible<?> expressibleType) {
		//noinspection unchecked
		this.expressibleType = (SqmExpressible<T>) expressibleType;
	}

}
