/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import java.util.List;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.NaturalId;
import org.hibernate.dialect.SybaseDialect;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.query.hql.HqlTranslator;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.sql.SqmTranslation;
import org.hibernate.query.sqm.sql.SqmTranslator;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.select.SelectStatement;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Steve Ebersole, Jan Martiska
 * @author Christian Beikov
 */
@DomainModel( annotatedClasses = { EntityJoinTest.FinancialRecord.class, EntityJoinTest.User.class, EntityJoinTest.Customer.class } )
@SessionFactory
public class EntityJoinTest {

    @Test
    public void testInnerEntityJoins(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                        // this should get financial records which have a lastUpdateBy user set
                        List<Object[]> result = session.createQuery(
                                "select r.id, c.name, u.id, u.username " +
                                        "from FinancialRecord r " +
                                        "   inner join r.customer c " +
                                        "	inner join User u on r.lastUpdateBy = u.username",
                                Object[].class
                        ).list();

                        assertThat( result.size(), is( 1 ) );
                        Object[] steveAndAcme = result.get( 0 );
                        assertThat( steveAndAcme[0], is( 1 ) );
                        assertThat( steveAndAcme[1], is( "Acme" ) );
                        assertThat( steveAndAcme[3], is( "steve" ) );

            // NOTE that this leads to not really valid SQL, although some databases might support it /
//			result = session.createQuery(
//					"select r.id, r.customer.name, u.id, u.username " +
//							"from FinancialRecord r " +
//							"	inner join User u on r.lastUpdateBy = u.username"
//			).list();
//			assertThat( result.size(), is( 1 ) );

                }
        );
    }

    @Test
    public void testLeftOuterEntityJoins(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    // this should get all financial records even if their lastUpdateBy user is null
                    List<Object[]> result = session.createQuery(
                            "select r.id, u.id, u.username " +
                                    "from FinancialRecord r " +
                                    "	left join User u on r.lastUpdateBy = u.username" +
                                    "   order by r.id",
                            Object[].class
                    ).list();
                    assertThat( result.size(), is( 2 ) );

                    Object[] stevesRecord = result.get( 0 );
                    assertThat( stevesRecord[0], is( 1 ) );
                    assertThat( stevesRecord[2], is( "steve" ) );

                    Object[] noOnesRecord = result.get( 1 );
                    assertThat( noOnesRecord[0], is( 2 ) );
                    assertNull( noOnesRecord[2] );
                }
        );
    }

    @Test
    @TestForIssue(jiraKey = "HHH-11337")
    @SkipForDialect(dialectClass = SybaseDialect.class)
    public void testLeftOuterEntityJoinsWithImplicitInnerJoinInSelectClause(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    // this should get all financial records even if their lastUpdateBy user is null
                    List<Object[]> result = session.createQuery(
                            "select r.id, u.id, u.username, r.customer.name " +
                                    "from FinancialRecord r " +
                                    "	left join User u on r.lastUpdateBy = u.username" +
                                    "   order by r.id",
                            Object[].class
                    ).list();
                    assertThat( result.size(), is( 2 ) );

                    Object[] stevesRecord = result.get( 0 );
                    assertThat( stevesRecord[0], is( 1 ) );
                    assertThat( stevesRecord[2], is( "steve" ) );

                    Object[] noOnesRecord = result.get( 1 );
                    assertThat( noOnesRecord[0], is( 2 ) );
                    assertNull( noOnesRecord[2] );
                }
        );
    }

    @Test
    @TestForIssue(jiraKey = "HHH-11340")
    public void testJoinOnEntityJoinNode(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    // this should get all financial records even if their lastUpdateBy user is null
                    List<Object[]> result = session.createQuery(
                            "select u.username, c.name " +
                                    "from FinancialRecord r " +
                                    "	left join User u on r.lastUpdateBy = u.username " +
                                    "   left join u.customer c " +
                                    "   order by r.id",
                            Object[].class
                    ).list();
                    assertThat( result.size(), is( 2 ) );

                    Object[] stevesRecord = result.get( 0 );
                    assertThat( stevesRecord[0], is( "steve" ) );
                    assertThat( stevesRecord[1], is( "Acme" ) );

                    Object[] noOnesRecord = result.get( 1 );
                    assertNull( noOnesRecord[0] );
                    assertNull( noOnesRecord[1] );
                }
        );
    }

    @Test
    @TestForIssue(jiraKey = "HHH-11538")
    public void testNoImpliedJoinGeneratedForEqualityComparison(SessionFactoryScope scope) {
        final String qry = "select r.id, cust.name " +
                "from FinancialRecord r " +
                "	join Customer cust on r.customer = cust" +
                "   order by r.id";

        scope.inTransaction(
                (session) -> {
                    final SessionFactoryImplementor factory = scope.getSessionFactory();

                    final EntityMappingType customerEntityDescriptor = factory.getRuntimeMetamodels()
                            .getMappingMetamodel()
                            .findEntityDescriptor( Customer.class );

                    final QueryEngine queryEngine = factory.getQueryEngine();
                    final HqlTranslator hqlTranslator = queryEngine.getHqlTranslator();
                    final SqmTranslatorFactory sqmTranslatorFactory = queryEngine.getSqmTranslatorFactory();

                    final SqmStatement<Object> sqm = hqlTranslator.translate( qry );

                    final SqmTranslator<SelectStatement> selectTranslator = sqmTranslatorFactory.createSelectTranslator(
                            (SqmSelectStatement<?>) sqm,
                            QueryOptions.NONE,
                            DomainParameterXref.empty(),
                            QueryParameterBindings.NO_PARAM_BINDINGS,
                            LoadQueryInfluencers.NONE,
                            factory
                    );
                    final SqmTranslation<SelectStatement> sqmTranslation = selectTranslator.translate();

                    final SelectStatement sqlAst = sqmTranslation.getSqlAst();
                    final List<TableGroup> roots = sqlAst.getQuerySpec().getFromClause().getRoots();
                    assertThat( roots.size(), is( 1 ) );

                    final TableGroup rootTableGroup = roots.get( 0 );
                    assertThat( rootTableGroup.getTableGroupJoins().size(), is( 1 ) );

                    final TableGroupJoin tableGroupJoin = rootTableGroup.getTableGroupJoins().get( 0 );
                    assertThat( tableGroupJoin.getJoinedGroup().getModelPart(), is( customerEntityDescriptor ) );
                }
        );
    }

    @Test
    public void testRightOuterEntityJoins(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    // this should get all users even if they have no financial records
                    List<Object[]> result = session.createQuery(
                            "select r.id, u.id, u.username " +
                                    "from FinancialRecord r " +
                                    "	right join User u on r.lastUpdateBy = u.username" +
                                    "   order by u.id",
                            Object[].class
                    ).list();

                    assertThat( result.size(), is( 2 ) );

                    Object[] steveAndAcme = result.get( 0 );
                    assertThat( steveAndAcme[ 0 ], is( 1 ) );
                    assertThat( steveAndAcme[ 2 ], is( "steve" ) );

                    Object[] janeAndNull = result.get( 1 );
                    assertNull( janeAndNull[ 0 ] );
                    assertThat( janeAndNull[ 2 ], is( "jane" ) );
                }
        );
    }


    @BeforeEach
    public void createTestData(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    final Customer customer = new Customer( 1, "Acme" );
                    session.save( customer );
                    session.save( new User( 1, "steve", customer ) );
                    session.save( new User( 2, "jane" ) );
                    session.save( new FinancialRecord( 1, customer, "steve" ) );
                    session.save( new FinancialRecord( 2, customer, null ) );
                }
        );
    }

    @AfterEach
    public void dropTestData(SessionFactoryScope scope) {
        scope.inTransaction(
                (session) -> {
                    session.createQuery( "delete FinancialRecord" ).executeUpdate();
                    session.createQuery( "delete User" ).executeUpdate();
                    session.createQuery( "delete Customer" ).executeUpdate();
                }
        );
    }

    @Entity(name = "Customer")
    @Table(name = "`a:customer`")
    public static class Customer {
        private Integer id;
        private String name;

        public Customer() {
        }

        public Customer(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        @Id
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity(name = "FinancialRecord")
    @Table(name = "`a:financial_record`")
    public static class FinancialRecord {
        private Integer id;
        private Customer customer;
        private String lastUpdateBy;

        public FinancialRecord() {
        }

        public FinancialRecord(Integer id, Customer customer, String lastUpdateBy) {
            this.id = id;
            this.customer = customer;
            this.lastUpdateBy = lastUpdateBy;
        }

        @Id
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        @ManyToOne
        @JoinColumn
        public Customer getCustomer() {
            return customer;
        }

        public void setCustomer(Customer customer) {
            this.customer = customer;
        }

        public String getLastUpdateBy() {
            return lastUpdateBy;
        }

        public void setLastUpdateBy(String lastUpdateBy) {
            this.lastUpdateBy = lastUpdateBy;
        }
    }

    @Entity(name = "User")
    @Table(name = "`a:user`")
    public static class User {
        private Integer id;
        private String username;
        private Customer customer;

        public User() {
        }

        public User(Integer id, String username) {
            this.id = id;
            this.username = username;
        }

        public User(Integer id, String username, Customer customer) {
            this.id = id;
            this.username = username;
            this.customer = customer;
        }

        @Id
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        @NaturalId
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public Customer getCustomer() {
            return customer;
        }

        public void setCustomer(Customer customer) {
            this.customer = customer;
        }
    }


}
