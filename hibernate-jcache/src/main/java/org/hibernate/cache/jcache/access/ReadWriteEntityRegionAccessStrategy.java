/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cache.jcache.access;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.jcache.JCacheEntityRegion;
import org.hibernate.cache.spi.access.EntityRegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;

/**
 * @author Alex Snaps
 */
public class ReadWriteEntityRegionAccessStrategy extends JCacheRegionAccessStrategy<JCacheEntityRegion>
		implements EntityRegionAccessStrategy {

	public ReadWriteEntityRegionAccessStrategy(JCacheEntityRegion region) {
		super( region );
	}

	@Override
	public boolean insert(SessionImplementor session, Object key, Object value, Object version) throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public boolean afterInsert(SessionImplementor session, Object key, Object value, Object version) throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public boolean update(SessionImplementor session, Object key, Object value, Object currentVersion, Object previousVersion)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public boolean afterUpdate(SessionImplementor session, Object key, Object value, Object currentVersion, Object previousVersion, SoftLock lock)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public Object generateCacheKey(Object id, EntityPersister persister, SessionFactoryImplementor factory, String tenantIdentifier) {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public Object getCacheKeyId(Object cacheKey) {
		throw new UnsupportedOperationException( "Implement me!" );
	}
}
