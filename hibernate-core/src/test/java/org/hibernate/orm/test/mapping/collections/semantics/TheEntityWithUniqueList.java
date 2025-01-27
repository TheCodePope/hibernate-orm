/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.collections.semantics;

import java.util.List;

import org.hibernate.annotations.CollectionType;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * @author Steve Ebersole
 */
@Table(name = "unique_list_owners")
//tag::ex-collections-custom-type-model[]
@Entity
public class TheEntityWithUniqueList {
	//end::ex-collections-custom-type-model[]
	@Id
	private Integer id;
	@Basic
	private String name;

	@CollectionTable(name = "unique_list_contents")
	//tag::ex-collections-custom-type-model[]
	@ElementCollection
	@CollectionType(type = UniqueListType.class)
	private List<String> strings;

	// ...
	//end::ex-collections-custom-type-model[]

	private TheEntityWithUniqueList() {
		// for use by Hibernate
	}

	public TheEntityWithUniqueList(Integer id, String name) {
		this.id = id;
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getStrings() {
		return strings;
	}

	public void setStrings(List<String> strings) {
		this.strings = strings;
	}
//tag::ex-collections-custom-type-model[]
}
//end::ex-collections-custom-type-model[]

