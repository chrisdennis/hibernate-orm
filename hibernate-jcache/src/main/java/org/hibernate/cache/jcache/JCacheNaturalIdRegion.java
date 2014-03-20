/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cache.jcache;

import javax.cache.Cache;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy;

/**
 * @author Alex Snaps
 */
public class JCacheNaturalIdRegion extends JCacheTransactionalDataRegion implements NaturalIdRegion {

	public JCacheNaturalIdRegion(Cache<Object, Object> cache) {
		super( cache );
	}

	@Override
	public NaturalIdRegionAccessStrategy buildAccessStrategy(AccessType accessType) throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}
}
