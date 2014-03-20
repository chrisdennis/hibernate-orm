/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cache.jcache;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.hibernate.boot.spi.SessionFactoryOptions;

import org.jboss.logging.Logger;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.CollectionRegion;
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;
import org.hibernate.cache.spi.access.AccessType;

/**
 * @author Alex Snaps
 */
public class JCacheRegionFactory implements RegionFactory {

	private static final String PROP_PREFIX = "hibernate.javax.cache";

	public static final String  PROVIDER    = PROP_PREFIX + ".provider";
	public static final String  CONFIG_URI  = PROP_PREFIX + ".uri";

	private static final JCacheMessageLogger LOG = Logger.getMessageLogger(
			JCacheMessageLogger.class,
			JCacheRegionFactory.class.getName()
	);

	private final AtomicBoolean started = new AtomicBoolean( false );
	private volatile CacheManager cacheManager;

	@Override
	public void start(final SessionFactoryOptions options, final Properties properties) throws CacheException {
		if ( started.compareAndSet( false, true ) ) {
			synchronized ( this ) {
				try {
					final CachingProvider cachingProvider;
					final String provider = getProp( properties, PROVIDER );
					if ( provider != null ) {
						cachingProvider = Caching.getCachingProvider( provider );
					}
					else {
						cachingProvider = Caching.getCachingProvider();
					}
					final CacheManager cacheManager;
					final String cacheManagerUri = getProp( properties, CONFIG_URI );
					if ( cacheManagerUri != null ) {
						URI uri;
						try {
							uri = new URI( cacheManagerUri );
						}
						catch ( URISyntaxException e ) {
							throw new CacheException( "Couldn't create URI from " + cacheManagerUri, e );
						}
						cacheManager = cachingProvider.getCacheManager( uri, cachingProvider.getDefaultClassLoader() );
					}
					else {
						cacheManager = cachingProvider.getCacheManager();
					}
					this.cacheManager = cacheManager;
				}
				finally {
					if ( this.cacheManager == null ) {
						started.set( false );
					}
				}
			}
		}
		else {
			LOG.attemptToRestartAlreadyStartedJCacheProvider();
		}
	}

	@Override
	public void stop() {
		if ( started.compareAndSet( true, false ) ) {
			synchronized ( this ) {
				cacheManager.close();
				cacheManager = null;
			}
		}
		else {
			LOG.attemptToRestopAlreadyStoppedJCacheProvider();
		}
	}

	@Override
	public boolean isMinimalPutsEnabledByDefault() {
		return true;
	}

	@Override
	public AccessType getDefaultAccessType() {
		return AccessType.READ_WRITE;
	}

	@Override
	public long nextTimestamp() {
		return System.currentTimeMillis() / 100;
	}

	@Override
	public EntityRegion buildEntityRegion(final String regionName, final Properties properties, final CacheDataDescription metadata)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public NaturalIdRegion buildNaturalIdRegion(final String regionName, final Properties properties, final CacheDataDescription metadata)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public CollectionRegion buildCollectionRegion(final String regionName, final Properties properties, final CacheDataDescription metadata)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public QueryResultsRegion buildQueryResultsRegion(final String regionName, final Properties properties)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	@Override
	public TimestampsRegion buildTimestampsRegion(final String regionName, final Properties properties)
			throws CacheException {
		throw new UnsupportedOperationException( "Implement me!" );
	}

	boolean isStarted() {
		return started.get();
	}

	CacheManager getCacheManager() {
		return cacheManager;
	}

	private String getProp(Properties properties, String prop) {
		return properties != null ? properties.getProperty( prop ) : null;
	}

}
