/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.dynamic;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.query.results.FetchBuilder;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractFetchBuilderContainer<T extends AbstractFetchBuilderContainer<T>>
		implements DynamicFetchBuilderContainer {
	private Map<String, DynamicFetchBuilder> fetchBuilderMap;

	protected AbstractFetchBuilderContainer() {
	}

	protected AbstractFetchBuilderContainer(AbstractFetchBuilderContainer<T> original) {
		if ( original.fetchBuilderMap != null ) {
			final Map<String, DynamicFetchBuilder> fetchBuilderMap = new HashMap<>( original.fetchBuilderMap.size() );
			for ( Map.Entry<String, DynamicFetchBuilder> entry : original.fetchBuilderMap.entrySet() ) {
				final DynamicFetchBuilder fetchBuilder;
				if ( entry.getValue() instanceof DynamicFetchBuilderStandard ) {
					fetchBuilder = ( (DynamicFetchBuilderStandard) entry.getValue() ).cacheKeyInstance( this );
				}
				else {
					fetchBuilder = entry.getValue().cacheKeyInstance();
				}
				fetchBuilderMap.put( entry.getKey(), fetchBuilder );
			}
			this.fetchBuilderMap = fetchBuilderMap;
		}
	}

	protected abstract String getPropertyBase();

	@Override
	public DynamicFetchBuilder findFetchBuilder(String fetchableName) {
		return fetchBuilderMap == null ? null : fetchBuilderMap.get( fetchableName );
	}

	@Override
	public T addProperty(String propertyName, String columnAlias) {
		final DynamicFetchBuilder fetchBuilder = addProperty( propertyName );
		fetchBuilder.addColumnAlias( columnAlias );

		return (T) this;
	}

	@Override
	public T addProperty(String propertyName, String... columnAliases) {
		final DynamicFetchBuilder fetchBuilder = addProperty( propertyName );
		ArrayHelper.forEach( columnAliases, fetchBuilder::addColumnAlias );

		return (T) this;
	}

	@Override
	public DynamicFetchBuilder addProperty(String propertyName) {
		if ( fetchBuilderMap == null ) {
			fetchBuilderMap = new HashMap<>();
		}
		else {
			final FetchBuilder existing = fetchBuilderMap.get( propertyName );
			if ( existing != null ) {
				throw new IllegalArgumentException(
						String.format(
								Locale.ROOT,
								"Fetch was already defined for %s.%s : %s",
								getPropertyBase(),
								propertyName,
								existing
						)
				);
			}
		}

		final DynamicFetchBuilderStandard fetchBuilder = new DynamicFetchBuilderStandard(
				this,
				propertyName
		);

		fetchBuilderMap.put( propertyName, fetchBuilder );

		return fetchBuilder;
	}

	public void addFetchBuilder(String propertyName, DynamicFetchBuilder fetchBuilder) {
		if ( fetchBuilderMap == null ) {
			fetchBuilderMap = new HashMap<>();
		}
		fetchBuilderMap.put( propertyName, fetchBuilder );
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		final AbstractFetchBuilderContainer<?> that = (AbstractFetchBuilderContainer<?>) o;
		return Objects.equals( fetchBuilderMap, that.fetchBuilderMap );
	}

	@Override
	public int hashCode() {
		return fetchBuilderMap != null ? fetchBuilderMap.hashCode() : 0;
	}
}
