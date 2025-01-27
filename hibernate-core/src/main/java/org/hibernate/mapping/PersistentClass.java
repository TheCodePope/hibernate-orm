/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.hibernate.MappingException;
import org.hibernate.boot.model.CustomSql;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.internal.FilterConfiguration;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.JoinedIterator;
import org.hibernate.internal.util.collections.SingletonIterator;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.jpa.event.spi.CallbackDefinition;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.Alias;

/**
 * Mapping for an entity.
 *
 * @author Gavin King
 */
public abstract class PersistentClass implements AttributeContainer, Serializable, Filterable, MetaAttributable, Contributable {
	private static final Alias PK_ALIAS = new Alias( 15, "PK" );

	public static final String NULL_DISCRIMINATOR_MAPPING = "null";
	public static final String NOT_NULL_DISCRIMINATOR_MAPPING = "not null";

	private final MetadataBuildingContext metadataBuildingContext;
	private final String contributor;

	private String entityName;

	private String className;
	private transient Class<?> mappedClass;

	private String proxyInterfaceName;
	private transient Class<?> proxyInterface;

	private String jpaEntityName;

	private String discriminatorValue;
	private boolean lazy;
	private java.util.List<Property> properties = new ArrayList<>();
	private java.util.List<Property> declaredProperties = new ArrayList<>();
	private final java.util.List<Subclass> subclasses = new ArrayList<>();
	private final java.util.List<Property> subclassProperties = new ArrayList<>();
	private final java.util.List<Table> subclassTables = new ArrayList<>();
	private boolean dynamicInsert;
	private boolean dynamicUpdate;
	private int batchSize = -1;
	private boolean selectBeforeUpdate;
	private java.util.Map metaAttributes;
	private java.util.List<Join> joins = new ArrayList<>();
	private final java.util.List<Join> subclassJoins = new ArrayList<>();
	private final java.util.List<FilterConfiguration> filters = new ArrayList<>();
	protected final Set<String> synchronizedTables = new HashSet<>();
	private String loaderName;
	private Boolean isAbstract;
	private boolean hasSubselectLoadableCollections;
	private Component identifierMapper;
	private java.util.List<CallbackDefinition> callbackDefinitions;

	// Custom SQL
	private String customSQLInsert;
	private boolean customInsertCallable;
	private ExecuteUpdateResultCheckStyle insertCheckStyle;
	private String customSQLUpdate;
	private boolean customUpdateCallable;
	private ExecuteUpdateResultCheckStyle updateCheckStyle;
	private String customSQLDelete;
	private boolean customDeleteCallable;
	private ExecuteUpdateResultCheckStyle deleteCheckStyle;

	private java.util.Map tuplizerImpls;

	private MappedSuperclass superMappedSuperclass;
	private Component declaredIdentifierMapper;
	private OptimisticLockStyle optimisticLockStyle;

	private boolean isCached;

	public PersistentClass(MetadataBuildingContext metadataBuildingContext) {
		this.metadataBuildingContext = metadataBuildingContext;
		this.contributor = metadataBuildingContext.getCurrentContributorName();
	}

	public String getContributor() {
		return contributor;
	}

	public ServiceRegistry getServiceRegistry() {
		return metadataBuildingContext.getBuildingOptions().getServiceRegistry();
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className == null ? null : className.intern();
		this.mappedClass = null;
	}

	public String getProxyInterfaceName() {
		return proxyInterfaceName;
	}

	public void setProxyInterfaceName(String proxyInterfaceName) {
		this.proxyInterfaceName = proxyInterfaceName;
		this.proxyInterface = null;
	}

	public Class<?> getMappedClass() throws MappingException {
		if ( className == null ) {
			return null;
		}

		try {
			if ( mappedClass == null ) {
				mappedClass = metadataBuildingContext.getBootstrapContext().getClassLoaderAccess().classForName( className );
			}
			return mappedClass;
		}
		catch (ClassLoadingException e) {
			throw new MappingException( "entity class not found: " + className, e );
		}
	}

	public Class<?> getProxyInterface() {
		if ( proxyInterfaceName == null ) {
			return null;
		}
		try {
			if ( proxyInterface == null ) {
				proxyInterface = metadataBuildingContext.getBootstrapContext().getClassLoaderAccess().classForName( proxyInterfaceName );
			}
			return proxyInterface;
		}
		catch (ClassLoadingException e) {
			throw new MappingException( "proxy class not found: " + proxyInterfaceName, e );
		}
	}

	public boolean useDynamicInsert() {
		return dynamicInsert;
	}

	abstract int nextSubclassId();

	public abstract int getSubclassId();

	public boolean useDynamicUpdate() {
		return dynamicUpdate;
	}

	public void setDynamicInsert(boolean dynamicInsert) {
		this.dynamicInsert = dynamicInsert;
	}

	public void setDynamicUpdate(boolean dynamicUpdate) {
		this.dynamicUpdate = dynamicUpdate;
	}


	public String getDiscriminatorValue() {
		return discriminatorValue;
	}

	public void addSubclass(Subclass subclass) throws MappingException {
		// inheritance cycle detection (paranoid check)
		PersistentClass superclass = getSuperclass();
		while ( superclass != null ) {
			if ( subclass.getEntityName().equals( superclass.getEntityName() ) ) {
				throw new MappingException(
						"Circular inheritance mapping detected: " +
								subclass.getEntityName() +
								" will have itself as superclass when extending " +
								getEntityName()
				);
			}
			superclass = superclass.getSuperclass();
		}
		subclasses.add( subclass );
	}

	public boolean hasSubclasses() {
		return subclasses.size() > 0;
	}

	public int getSubclassSpan() {
		int n = subclasses.size();
		for ( Subclass subclass : subclasses ) {
			n += subclass.getSubclassSpan();
		}
		return n;
	}

	/**
	 * Iterate over subclasses in a special 'order', most derived subclasses
	 * first.
	 */
	public Iterator<Subclass> getSubclassIterator() {
		Iterator<Subclass>[] iters = new Iterator[subclasses.size() + 1];
		Iterator<Subclass> iter = subclasses.iterator();
		int i = 0;
		while ( iter.hasNext() ) {
			iters[i++] = iter.next().getSubclassIterator();
		}
		iters[i] = subclasses.iterator();
		return new JoinedIterator<>( iters );
	}

	public Iterator<PersistentClass> getSubclassClosureIterator() {
		ArrayList<Iterator<PersistentClass>> iters = new ArrayList<>();
		iters.add( new SingletonIterator<>( this ) );
		Iterator<Subclass> iter = getSubclassIterator();
		while ( iter.hasNext() ) {
			PersistentClass clazz =  iter.next();
			iters.add( clazz.getSubclassClosureIterator() );
		}
		return new JoinedIterator<>( iters );
	}

	public Table getIdentityTable() {
		return getRootTable();
	}

	public Iterator<Subclass> getDirectSubclasses() {
		return subclasses.iterator();
	}

	@Override
	public void addProperty(Property p) {
		properties.add( p );
		declaredProperties.add( p );
		p.setPersistentClass( this );
	}

	public abstract Table getTable();

	public String getEntityName() {
		return entityName;
	}

	public abstract boolean isMutable();

	public abstract boolean hasIdentifierProperty();

	public abstract Property getIdentifierProperty();

	public abstract Property getDeclaredIdentifierProperty();

	public abstract KeyValue getIdentifier();

	public abstract Property getVersion();

	public abstract Property getDeclaredVersion();

	public abstract Value getDiscriminator();

	public abstract boolean isInherited();

	public abstract boolean isPolymorphic();

	public abstract boolean isVersioned();


	public boolean isCached() {
		return isCached;
	}

	public void setCached(boolean cached) {
		isCached = cached;
	}

	/**
	 * @deprecated Use {@link #isCached} instead
	 */
	@Deprecated
	public boolean isCachingExplicitlyRequested() {
		return isCached();
	}

	/**
	 * @deprecated Use {@link #setCached} instead
	 */
	@Deprecated
	public void setCachingExplicitlyRequested(boolean cached) {
		setCached( cached );
	}

	public abstract String getCacheConcurrencyStrategy();

	public abstract String getNaturalIdCacheRegionName();

	public abstract PersistentClass getSuperclass();

	public abstract boolean isExplicitPolymorphism();

	public abstract boolean isDiscriminatorInsertable();

	public abstract Iterator<Property> getPropertyClosureIterator();

	public abstract Iterator<Table> getTableClosureIterator();

	public abstract Iterator<KeyValue> getKeyClosureIterator();

	protected void addSubclassProperty(Property prop) {
		subclassProperties.add( prop );
	}

	protected void addSubclassJoin(Join join) {
		subclassJoins.add( join );
	}

	protected void addSubclassTable(Table subclassTable) {
		subclassTables.add( subclassTable );
	}

	public Iterator<Property> getSubclassPropertyClosureIterator() {
		ArrayList<Iterator<Property>> iters = new ArrayList<>();
		iters.add( getPropertyClosureIterator() );
		iters.add( subclassProperties.iterator() );
		for ( int i = 0; i < subclassJoins.size(); i++ ) {
			Join join = subclassJoins.get( i );
			iters.add( join.getPropertyIterator() );
		}
		return new JoinedIterator<>( iters );
	}

	public Iterator<Join> getSubclassJoinClosureIterator() {
		return new JoinedIterator<>( getJoinClosureIterator(), subclassJoins.iterator() );
	}

	public Iterator<Table> getSubclassTableClosureIterator() {
		return new JoinedIterator<>( getTableClosureIterator(), subclassTables.iterator() );
	}

	public boolean isClassOrSuperclassJoin(Join join) {
		return joins.contains( join );
	}

	public boolean isClassOrSuperclassTable(Table closureTable) {
		return getTable() == closureTable;
	}

	public boolean isLazy() {
		return lazy;
	}

	public void setLazy(boolean lazy) {
		this.lazy = lazy;
	}

	public abstract boolean hasEmbeddedIdentifier();

	public abstract Class<? extends EntityPersister> getEntityPersisterClass();

	public abstract void setEntityPersisterClass(Class<? extends EntityPersister> classPersisterClass);

	public abstract Table getRootTable();

	public abstract RootClass getRootClass();

	public abstract KeyValue getKey();

	public void setDiscriminatorValue(String discriminatorValue) {
		this.discriminatorValue = discriminatorValue;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName == null ? null : entityName.intern();
	}

	public void createPrimaryKey() {
		//Primary key constraint
		final Table table = getTable();
		PrimaryKey pk = new PrimaryKey( table );
		pk.setName( PK_ALIAS.toAliasString( table.getName() ) );
		table.setPrimaryKey( pk );

		pk.addColumns( getKey().getColumnIterator() );
	}

	public abstract String getWhere();

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public boolean hasSelectBeforeUpdate() {
		return selectBeforeUpdate;
	}

	public void setSelectBeforeUpdate(boolean selectBeforeUpdate) {
		this.selectBeforeUpdate = selectBeforeUpdate;
	}

	/**
	 * Build an iterator of properties which may be referenced in association mappings.
	 * <p>
	 * Includes properties defined in superclasses of the mapping inheritance.
	 * Includes all properties defined as part of a join.
	 *
	 * @see #getReferencedProperty for a discussion of "referenceable"
	 * @return The referenceable property iterator.
	 */
	public Iterator<Property> getReferenceablePropertyIterator() {
		return getPropertyClosureIterator();
	}

	/**
	 * Given a property path, locate the appropriate referenceable property reference.
	 * <p/>
	 * A referenceable property is a property  which can be a target of a foreign-key
	 * mapping (e.g. {@code @ManyToOne}, {@code @OneToOne}).
	 *
	 * @param propertyPath The property path to resolve into a property reference.
	 *
	 * @return The property reference (never null).
	 *
	 * @throws MappingException If the property could not be found.
	 */
	public Property getReferencedProperty(String propertyPath) throws MappingException {
		try {
			return getRecursiveProperty( propertyPath, getReferenceablePropertyIterator() );
		}
		catch (MappingException e) {
			throw new MappingException(
					"property-ref [" + propertyPath + "] not found on entity [" + getEntityName() + "]", e
			);
		}
	}

	public Property getRecursiveProperty(String propertyPath) throws MappingException {
		try {
			return getRecursiveProperty( propertyPath, getPropertyClosureIterator() );
		}
		catch (MappingException e) {
			throw new MappingException(
					"property [" + propertyPath + "] not found on entity [" + getEntityName() + "]", e
			);
		}
	}

	private Property getRecursiveProperty(String propertyPath, Iterator<Property> iter) throws MappingException {
		Property property = null;
		StringTokenizer st = new StringTokenizer( propertyPath, ".", false );
		try {
			while ( st.hasMoreElements() ) {
				final String element = (String) st.nextElement();
				if ( property == null ) {
					Property identifierProperty = getIdentifierProperty();
					if ( identifierProperty != null && identifierProperty.getName().equals( element ) ) {
						// we have a mapped identifier property and the root of
						// the incoming property path matched that identifier
						// property
						property = identifierProperty;
					}
					else if ( identifierProperty == null && getIdentifierMapper() != null ) {
						// we have an embedded composite identifier
						try {
							identifierProperty = getProperty( element, getIdentifierMapper().getPropertyIterator() );
							if ( identifierProperty != null ) {
								// the root of the incoming property path matched one
								// of the embedded composite identifier properties
								property = identifierProperty;
							}
						}
						catch (MappingException ignore) {
							// ignore it...
						}
					}

					if ( property == null ) {
						property = getProperty( element, iter );
					}
				}
				else {
					//flat recursive algorithm
					property = ( (Component) property.getValue() ).getProperty( element );
				}
			}
		}
		catch (MappingException e) {
			throw new MappingException( "property [" + propertyPath + "] not found on entity [" + getEntityName() + "]" );
		}

		return property;
	}

	private Property getProperty(String propertyName, Iterator<Property> iterator) throws MappingException {
		if ( iterator.hasNext() ) {
			String root = StringHelper.root( propertyName );
			while ( iterator.hasNext() ) {
				Property prop = iterator.next();
				if ( prop.getName().equals( root )
						|| (prop instanceof Backref || prop instanceof IndexBackref)
						&& prop.getName().equals( propertyName ) ) {
					return prop;
				}
			}
		}
		throw new MappingException( "property [" + propertyName + "] not found on entity [" + getEntityName() + "]" );
	}

	public Property getProperty(String propertyName) throws MappingException {
		Property identifierProperty = getIdentifierProperty();
		if ( identifierProperty != null
				&& identifierProperty.getName().equals( StringHelper.root( propertyName ) ) ) {
			return identifierProperty;
		}
		else {
			Iterator<Property> iter = getPropertyClosureIterator();
			Component identifierMapper = getIdentifierMapper();
			if ( identifierMapper != null ) {
				iter = new JoinedIterator<>( identifierMapper.getPropertyIterator(), iter );
			}
			return getProperty( propertyName, iter );
		}
	}

	public Property getSubclassProperty(String propertyName) throws MappingException {
		Property identifierProperty = getIdentifierProperty();
		if ( identifierProperty != null
				&& identifierProperty.getName().equals( StringHelper.root( propertyName ) ) ) {
			return identifierProperty;
		}
		else {
			Iterator<Property> iter = getSubclassPropertyClosureIterator();
			Component identifierMapper = getIdentifierMapper();
			if ( identifierMapper != null ) {
				iter = new JoinedIterator<>( identifierMapper.getPropertyIterator(), iter );
			}
			return getProperty( propertyName, iter );
		}
	}

	/**
	 * Check to see if this PersistentClass defines a property with the given name.
	 *
	 * @param name The property name to check
	 *
	 * @return {@code true} if a property with that name exists; {@code false} if not
	 */
	public boolean hasProperty(String name) {
		final Property identifierProperty = getIdentifierProperty();
		if ( identifierProperty != null && identifierProperty.getName().equals( name ) ) {
			return true;
		}

		final Iterator<Property> itr = getPropertyClosureIterator();
		while ( itr.hasNext() ) {
			final Property property = itr.next();
			if ( property.getName().equals( name ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Check to see if a property with the given name exists in the super hierarchy
	 * of this PersistentClass.  Does not check this PersistentClass, just up the
	 * hierarchy
	 *
	 * @param name The property name to check
	 *
	 * @return {@code true} if a property with that name exists; {@code false} if not
	 */
	public boolean isPropertyDefinedInSuperHierarchy(String name) {
		return getSuperclass() != null && getSuperclass().isPropertyDefinedInHierarchy( name );

	}

	/**
	 * Check to see if a property with the given name exists in this PersistentClass
	 * or in any of its super hierarchy.  Unlike {@link #isPropertyDefinedInSuperHierarchy},
	 * this method does check this PersistentClass
	 *
	 * @param name The property name to check
	 *
	 * @return {@code true} if a property with that name exists; {@code false} if not
	 */
	public boolean isPropertyDefinedInHierarchy(String name) {
		if ( hasProperty( name ) ) {
			return true;
		}

		if ( getSuperMappedSuperclass() != null
				&& getSuperMappedSuperclass().isPropertyDefinedInHierarchy( name ) ) {
			return true;
		}

		if ( getSuperclass() != null
				&& getSuperclass().isPropertyDefinedInHierarchy( name ) ) {
			return true;
		}

		return false;
	}

	/**
	 * @deprecated prefer {@link #getOptimisticLockStyle}
	 */
	@Deprecated
	public int getOptimisticLockMode() {
		return getOptimisticLockStyle().getOldCode();
	}

	/**
	 * @deprecated prefer {@link #setOptimisticLockStyle}
	 */
	@Deprecated
	public void setOptimisticLockMode(int optimisticLockMode) {
		setOptimisticLockStyle( OptimisticLockStyle.interpretOldCode( optimisticLockMode ) );
	}

	public OptimisticLockStyle getOptimisticLockStyle() {
		return optimisticLockStyle;
	}

	public void setOptimisticLockStyle(OptimisticLockStyle optimisticLockStyle) {
		this.optimisticLockStyle = optimisticLockStyle;
	}

	public void validate(Mapping mapping) throws MappingException {
		Iterator<Property> iter = getPropertyIterator();
		while ( iter.hasNext() ) {
			Property prop = iter.next();
			if ( !prop.isValid( mapping ) ) {
				throw new MappingException(
						"property mapping has wrong number of columns: " +
								StringHelper.qualify( getEntityName(), prop.getName() ) +
								" type: " +
								prop.getType().getName()
				);
			}
		}
		checkPropertyDuplication();
		checkColumnDuplication();
	}

	private void checkPropertyDuplication() throws MappingException {
		HashSet<String> names = new HashSet<>();
		Iterator<Property> iter = getPropertyIterator();
		while ( iter.hasNext() ) {
			Property prop = iter.next();
			if ( !names.add( prop.getName() ) ) {
				throw new MappingException( "Duplicate property mapping of " + prop.getName() + " found in " + getEntityName() );
			}
		}
	}

	public boolean isDiscriminatorValueNotNull() {
		return NOT_NULL_DISCRIMINATOR_MAPPING.equals( getDiscriminatorValue() );
	}

	public boolean isDiscriminatorValueNull() {
		return NULL_DISCRIMINATOR_MAPPING.equals( getDiscriminatorValue() );
	}

	public java.util.Map getMetaAttributes() {
		return metaAttributes;
	}

	public void setMetaAttributes(java.util.Map metas) {
		this.metaAttributes = metas;
	}

	public MetaAttribute getMetaAttribute(String name) {
		return metaAttributes == null
				? null
				: (MetaAttribute) metaAttributes.get( name );
	}

	@Override
	public String toString() {
		return getClass().getName() + '(' + getEntityName() + ')';
	}

	public Iterator<Join> getJoinIterator() {
		return joins.iterator();
	}

	public Iterator<Join> getJoinClosureIterator() {
		return joins.iterator();
	}

	public void addJoin(Join join) {
		joins.add( join );
		join.setPersistentClass( this );
	}

	public int getJoinClosureSpan() {
		return joins.size();
	}

	public int getPropertyClosureSpan() {
		int span = properties.size();
		for ( Join join : joins ) {
			span += join.getPropertySpan();
		}
		return span;
	}

	public int getJoinNumber(Property prop) {
		int result = 1;
		Iterator<Join> iter = getSubclassJoinClosureIterator();
		while ( iter.hasNext() ) {
			Join join = iter.next();
			if ( join.containsProperty( prop ) ) {
				return result;
			}
			result++;
		}
		return 0;
	}

	/**
	 * Build an iterator over the properties defined on this class.  The returned
	 * iterator only accounts for "normal" properties (i.e. non-identifier
	 * properties).
	 * <p/>
	 * Differs from {@link #getUnjoinedPropertyIterator} in that the returned iterator
	 * will include properties defined as part of a join.
	 * <p/>
	 * Differs from {@link #getReferenceablePropertyIterator} in that the properties
	 * defined in superclasses of the mapping inheritance are not included.
	 *
	 * @return An iterator over the "normal" properties.
	 */
	public Iterator<Property> getPropertyIterator() {
		ArrayList<Iterator<Property>> iterators = new ArrayList<>();
		iterators.add( properties.iterator() );
		for ( int i = 0; i < joins.size(); i++ ) {
			Join join = joins.get( i );
			iterators.add( join.getPropertyIterator() );
		}
		return new JoinedIterator<>( iterators );
	}

	/**
	 * Build an iterator over the properties defined on this class <b>which
	 * are not defined as part of a join</b>.  As with {@link #getPropertyIterator},
	 * the returned iterator only accounts for non-identifier properties.
	 *
	 * @return An iterator over the non-joined "normal" properties.
	 */
	public Iterator<Property> getUnjoinedPropertyIterator() {
		return properties.iterator();
	}

	public void setCustomSqlInsert(CustomSql customSql) {
		if ( customSql != null ) {
			setCustomSQLInsert(
					customSql.getSql(),
					customSql.isCallable(),
					customSql.getCheckStyle()
			);
		}
	}

	public void setCustomSQLInsert(String customSQLInsert, boolean callable, ExecuteUpdateResultCheckStyle checkStyle) {
		this.customSQLInsert = customSQLInsert;
		this.customInsertCallable = callable;
		this.insertCheckStyle = checkStyle;
	}

	public String getCustomSQLInsert() {
		return customSQLInsert;
	}

	public boolean isCustomInsertCallable() {
		return customInsertCallable;
	}

	public ExecuteUpdateResultCheckStyle getCustomSQLInsertCheckStyle() {
		return insertCheckStyle;
	}

	public void setCustomSqlUpdate(CustomSql customSql) {
		if ( customSql != null ) {
			setCustomSQLUpdate(
					customSql.getSql(),
					customSql.isCallable(),
					customSql.getCheckStyle()
			);
		}
	}

	public void setCustomSQLUpdate(String customSQLUpdate, boolean callable, ExecuteUpdateResultCheckStyle checkStyle) {
		this.customSQLUpdate = customSQLUpdate;
		this.customUpdateCallable = callable;
		this.updateCheckStyle = checkStyle;
	}

	public String getCustomSQLUpdate() {
		return customSQLUpdate;
	}

	public boolean isCustomUpdateCallable() {
		return customUpdateCallable;
	}

	public ExecuteUpdateResultCheckStyle getCustomSQLUpdateCheckStyle() {
		return updateCheckStyle;
	}

	public void setCustomSqlDelete(CustomSql customSql) {
		if ( customSql != null ) {
			setCustomSQLDelete(
					customSql.getSql(),
					customSql.isCallable(),
					customSql.getCheckStyle()
			);
		}
	}

	public void setCustomSQLDelete(String customSQLDelete, boolean callable, ExecuteUpdateResultCheckStyle checkStyle) {
		this.customSQLDelete = customSQLDelete;
		this.customDeleteCallable = callable;
		this.deleteCheckStyle = checkStyle;
	}

	public String getCustomSQLDelete() {
		return customSQLDelete;
	}

	public boolean isCustomDeleteCallable() {
		return customDeleteCallable;
	}

	public ExecuteUpdateResultCheckStyle getCustomSQLDeleteCheckStyle() {
		return deleteCheckStyle;
	}

	public void addFilter(
			String name,
			String condition,
			boolean autoAliasInjection,
			java.util.Map<String, String> aliasTableMap,
			java.util.Map<String, String> aliasEntityMap) {
		filters.add(
				new FilterConfiguration(
						name,
						condition,
						autoAliasInjection,
						aliasTableMap,
						aliasEntityMap,
						this
				)
		);
	}

	public java.util.List<FilterConfiguration> getFilters() {
		return filters;
	}

	public boolean isForceDiscriminator() {
		return false;
	}

	public abstract boolean isJoinedSubclass();

	public String getLoaderName() {
		return loaderName;
	}

	public void setLoaderName(String loaderName) {
		this.loaderName = loaderName == null ? null : loaderName.intern();
	}

	public abstract Set<String> getSynchronizedTables();

	public void addSynchronizedTable(String table) {
		synchronizedTables.add( table );
	}

	public Boolean isAbstract() {
		return isAbstract;
	}

	public void setAbstract(Boolean isAbstract) {
		this.isAbstract = isAbstract;
	}

	protected void checkColumnDuplication(Set<String> distinctColumns, Iterator<Selectable> columns)
			throws MappingException {
		while ( columns.hasNext() ) {
			Selectable columnOrFormula = columns.next();
			if ( !columnOrFormula.isFormula() ) {
				Column col = (Column) columnOrFormula;
				if ( !distinctColumns.add( col.getName() ) ) {
					throw new MappingException(
							"Repeated column in mapping for entity: " +
									getEntityName() +
									" column: " +
									col.getName() +
									" (should be mapped with insert=\"false\" update=\"false\")"
					);
				}
			}
		}
	}

	protected void checkPropertyColumnDuplication(Set distinctColumns, Iterator properties)
			throws MappingException {
		while ( properties.hasNext() ) {
			Property prop = (Property) properties.next();
			if ( prop.getValue() instanceof Component ) { //TODO: remove use of instanceof!
				Component component = (Component) prop.getValue();
				checkPropertyColumnDuplication( distinctColumns, component.getPropertyIterator() );
			}
			else {
				if ( prop.isUpdateable() || prop.isInsertable() ) {
					checkColumnDuplication( distinctColumns, prop.getColumnIterator() );
				}
			}
		}
	}

	protected Iterator<Property> getNonDuplicatedPropertyIterator() {
		return getUnjoinedPropertyIterator();
	}

	protected Iterator<Selectable> getDiscriminatorColumnIterator() {
		return Collections.emptyIterator();
	}

	protected void checkColumnDuplication() {
		HashSet<String> cols = new HashSet<>();
		if ( getIdentifierMapper() == null ) {
			//an identifier mapper => getKey will be included in the getNonDuplicatedPropertyIterator()
			//and checked later, so it needs to be excluded
			checkColumnDuplication( cols, getKey().getColumnIterator() );
		}
		checkColumnDuplication( cols, getDiscriminatorColumnIterator() );
		checkPropertyColumnDuplication( cols, getNonDuplicatedPropertyIterator() );
		Iterator<Join> iter = getJoinIterator();
		while ( iter.hasNext() ) {
			cols.clear();
			Join join = iter.next();
			checkColumnDuplication( cols, join.getKey().getColumnIterator() );
			checkPropertyColumnDuplication( cols, join.getPropertyIterator() );
		}
	}

	public abstract Object accept(PersistentClassVisitor mv);

	public String getJpaEntityName() {
		return jpaEntityName;
	}

	public void setJpaEntityName(String jpaEntityName) {
		this.jpaEntityName = jpaEntityName;
	}

	public boolean hasPojoRepresentation() {
		return getClassName() != null;
	}

	public boolean hasSubselectLoadableCollections() {
		return hasSubselectLoadableCollections;
	}

	public void setSubselectLoadableCollections(boolean hasSubselectCollections) {
		this.hasSubselectLoadableCollections = hasSubselectCollections;
	}

	public Component getIdentifierMapper() {
		return identifierMapper;
	}

	public Component getDeclaredIdentifierMapper() {
		return declaredIdentifierMapper;
	}

	public void setDeclaredIdentifierMapper(Component declaredIdentifierMapper) {
		this.declaredIdentifierMapper = declaredIdentifierMapper;
	}

	public boolean hasIdentifierMapper() {
		return identifierMapper != null;
	}

	public void addCallbackDefinitions(java.util.List<CallbackDefinition> callbackDefinitions) {
		if ( callbackDefinitions == null || callbackDefinitions.isEmpty() ) {
			return;
		}
		if ( this.callbackDefinitions == null ) {
			this.callbackDefinitions = new ArrayList<>();
		}
		this.callbackDefinitions.addAll( callbackDefinitions );
	}

	public java.util.List<CallbackDefinition> getCallbackDefinitions() {
		if ( callbackDefinitions == null ) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList( callbackDefinitions );
	}

	public void setIdentifierMapper(Component handle) {
		this.identifierMapper = handle;
	}

	public String getTuplizerImplClassName(RepresentationMode mode) {
		if ( tuplizerImpls == null ) {
			return null;
		}
		return (String) tuplizerImpls.get( mode );
	}

	public java.util.Map getTuplizerMap() {
		if ( tuplizerImpls == null ) {
			return null;
		}
		return Collections.unmodifiableMap( tuplizerImpls );
	}

	private Boolean hasNaturalId;

	public boolean hasNaturalId() {
		if ( hasNaturalId == null ) {
			hasNaturalId = determineIfNaturalIdDefined();
		}
		return hasNaturalId;
	}

	private boolean determineIfNaturalIdDefined() {
		final Iterator<Property> props = getRootClass().getPropertyIterator();
		while ( props.hasNext() ) {
			if ( props.next().isNaturalIdentifier() ) {
				return true;
			}
		}
		return false;
	}

	// The following methods are added to support @MappedSuperclass in the metamodel
	public Iterator<Property> getDeclaredPropertyIterator() {
		ArrayList<Iterator<Property>> iterators = new ArrayList<>();
		iterators.add( declaredProperties.iterator() );
		for ( int i = 0; i < joins.size(); i++ ) {
			Join join = joins.get( i );
			iterators.add( join.getDeclaredPropertyIterator() );
		}
		return new JoinedIterator<>( iterators );
	}

	public void addMappedsuperclassProperty(Property p) {
		properties.add( p );
		p.setPersistentClass( this );
	}

	public MappedSuperclass getSuperMappedSuperclass() {
		return superMappedSuperclass;
	}

	public void setSuperMappedSuperclass(MappedSuperclass superMappedSuperclass) {
		this.superMappedSuperclass = superMappedSuperclass;
	}

	// End of @MappedSuperclass support

	public void prepareForMappingModel() {
		properties.sort( Comparator.comparing( Property::getName ) );
	}

}
