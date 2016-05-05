/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.cache;

import java.util.Properties;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.Session;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.H2Dialect;

import org.hibernate.testing.RequiresDialect;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Chris Dennis
 */
@TestForIssue(jiraKey = "HHH-10707")
@RequiresDialect(value = {H2Dialect.class})
public class SharedCacheRegionAccessStrategyTest extends BaseCoreFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] {
			NonStrictReadWriteCacheableItem.class,
			ReadWriteCacheableItem.class,
		};
	}

	@Override
	protected void configure(Configuration cfg) {
		super.configure( cfg );
		Properties properties = Environment.getProperties();
		if ( H2Dialect.class.getName().equals( properties.get( Environment.DIALECT ) ) ) {
			cfg.setProperty( Environment.URL, "jdbc:h2:mem:db-mvcc;MVCC=true" );
		}
		cfg.setProperty( Environment.CACHE_REGION_PREFIX, "" );
		cfg.setProperty( Environment.GENERATE_STATISTICS, "true" );
		cfg.setProperty( Environment.USE_SECOND_LEVEL_CACHE, "true" );
		cfg.setProperty( Environment.CACHE_PROVIDER_CONFIG, "true" );
	}

	@Test
	public void testUpdateAndFlushThenRefresh() {
		// prepare data
		Session s = openSession();
		s.beginTransaction();

		NonStrictReadWriteCacheableItem nonStrictReadWriteCacheableItem = new NonStrictReadWriteCacheableItem( "before" );
		s.persist( nonStrictReadWriteCacheableItem );

		ReadWriteCacheableItem readWriteCacheableItem = new ReadWriteCacheableItem( "before" );
		s.persist( readWriteCacheableItem );

		s.getTransaction().commit();
		s.close();

		Session s1 = openSession();
		s1.beginTransaction();

		NonStrictReadWriteCacheableItem nonStrictReadWriteCacheableItem1 = s1.get( NonStrictReadWriteCacheableItem.class, nonStrictReadWriteCacheableItem.getId() );
		nonStrictReadWriteCacheableItem1.setName( "after" );

		ReadWriteCacheableItem readWriteCacheableItem1 = s1.get( ReadWriteCacheableItem.class, readWriteCacheableItem.getId() );
		readWriteCacheableItem1.setName( "after" );

		s1.flush();
		s1.refresh( nonStrictReadWriteCacheableItem1 );
		s1.refresh( readWriteCacheableItem1 );

		assertThat( nonStrictReadWriteCacheableItem1.getName(), is("after") );
		assertThat( readWriteCacheableItem1.getName(), is("after") );

		// open another session
		Session s2 = sessionFactory().openSession();
		try {
			s2.beginTransaction();
			NonStrictReadWriteCacheableItem nonStrictReadWriteCacheableItem2 = s2.get( NonStrictReadWriteCacheableItem.class, nonStrictReadWriteCacheableItem.getId() );
			ReadWriteCacheableItem readWriteCacheableItem2 = s2.get( ReadWriteCacheableItem.class, readWriteCacheableItem.getId() );

			assertThat( nonStrictReadWriteCacheableItem2.getName(), is("after") );

			assertThat( readWriteCacheableItem2.getName(), is("before") );

			s2.getTransaction().commit();
		}
		finally {
			if ( s2.getTransaction().getStatus().canRollback() ) {
				s2.getTransaction().rollback();
			}
			s2.close();
		}

		s1.getTransaction().rollback();
		s1.close();

		s = openSession();
		s.beginTransaction();
		s.delete( nonStrictReadWriteCacheableItem );
		s.delete( readWriteCacheableItem );
		s.getTransaction().commit();
		s.close();
	}

	@Entity(name = "ReadWriteCacheableItem")
	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "item")
	public static class ReadWriteCacheableItem {

		@Id
		@GeneratedValue
		private Long id;

		private String name;

		public ReadWriteCacheableItem() {
		}

		public ReadWriteCacheableItem(String name) {
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Entity(name = "NonStrictReadWriteCacheableItem")
	@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "item")
	public static class NonStrictReadWriteCacheableItem {

		@Id
		@GeneratedValue
		private Long id;

		private String name;

		public NonStrictReadWriteCacheableItem() {
		}

		public NonStrictReadWriteCacheableItem(String name) {
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
