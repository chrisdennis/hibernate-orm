/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.persistence.PersistenceException;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.CacheDataDescriptionImpl;
import org.hibernate.cache.internal.StandardQueryCache;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.CollectionRegion;
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.QueryCache;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;
import org.hibernate.cache.spi.UpdateTimestampsCache;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionRegionAccessStrategy;
import org.hibernate.cache.spi.access.EntityRegionAccessStrategy;
import org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy;
import org.hibernate.engine.spi.CacheImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.pretty.MessageHelper;

/**
 * @author Steve Ebersole
 * @author Strong Liu
 */
public class CacheImpl implements CacheImplementor {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( CacheImpl.class );

	private final SessionFactoryImplementor sessionFactory;
	private final SessionFactoryOptions settings;
	private final transient RegionFactory regionFactory;
	private final String cacheRegionPrefix;

	private final transient ConcurrentHashMap<String, EntityRegion> entityRegions = new ConcurrentHashMap<>();
	private final transient ConcurrentHashMap<String, CollectionRegion> collectionRegions = new ConcurrentHashMap<>();
	private final transient ConcurrentHashMap<String, NaturalIdRegion> naturalIdRegions = new ConcurrentHashMap<>();

	private final transient UpdateTimestampsCache updateTimestampsCache;
	private final transient QueryCache defaultQueryCache;
	private final transient ConcurrentMap<String, QueryCache> queryCaches;

	public CacheImpl(SessionFactoryImplementor sessionFactory) {
		this.sessionFactory = sessionFactory;
		this.settings = sessionFactory.getSessionFactoryOptions();
		this.regionFactory = settings.getServiceRegistry().getService( RegionFactory.class );
		this.regionFactory.start( settings, sessionFactory.getProperties() );

		this.cacheRegionPrefix = StringHelper.isEmpty( sessionFactory.getSessionFactoryOptions().getCacheRegionPrefix() )
				? ""
				: sessionFactory.getSessionFactoryOptions().getCacheRegionPrefix() + ".";

		if ( settings.isQueryCacheEnabled() ) {
			final TimestampsRegion timestampsRegion = regionFactory.buildTimestampsRegion(
					qualifyRegionName( UpdateTimestampsCache.REGION_NAME ),
					sessionFactory.getProperties()
			);
			updateTimestampsCache = new UpdateTimestampsCache( sessionFactory, timestampsRegion );
			final QueryResultsRegion queryResultsRegion = regionFactory.buildQueryResultsRegion(
					StandardQueryCache.class.getName(),
					sessionFactory.getProperties()
			);
			defaultQueryCache = settings.getQueryCacheFactory().buildQueryCache( queryResultsRegion, this );
			queryCaches = new ConcurrentHashMap<>();
		}
		else {
			updateTimestampsCache = null;
			defaultQueryCache = null;
			queryCaches = null;
		}
	}

	@Override
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	@Override
	public RegionFactory getRegionFactory() {
		return regionFactory;
	}

	@Override
	public String qualifyRegionName(String regionName) {
		return StringHelper.isEmpty( regionName )
				? null
				: cacheRegionPrefix + regionName;
	}

	@Override
	public boolean containsEntity(Class entityClass, Serializable identifier) {
		return containsEntity( entityClass.getName(), identifier );
	}

	@Override
	public boolean containsEntity(String entityName, Serializable identifier) {
		EntityPersister p = sessionFactory.getMetamodel().entityPersister( entityName );
		if ( p.hasCache() ) {
			EntityRegionAccessStrategy cache = p.getCacheAccessStrategy();
			Object key = cache.generateCacheKey( identifier, p, sessionFactory, null ); // have to assume non tenancy
			return cache.getRegion().contains( key );
		}
		else {
			return false;
		}
	}

	@Override
	public void evictEntity(Class entityClass, Serializable identifier) {
		evictEntity( entityClass.getName(), identifier );
	}

	@Override
	public void evictEntity(String entityName, Serializable identifier) {
		EntityPersister p = sessionFactory.getMetamodel().entityPersister( entityName );
		if ( p.hasCache() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf(
						"Evicting second-level cache: %s",
						MessageHelper.infoString( p, identifier, sessionFactory )
				);
			}
			EntityRegionAccessStrategy cache = p.getCacheAccessStrategy();
			Object key = cache.generateCacheKey( identifier, p, sessionFactory, null ); // have to assume non tenancy
			cache.evict( key );
		}
	}

	@Override
	public void evictEntityRegion(Class entityClass) {
		evictEntityRegion( entityClass.getName() );
	}

	@Override
	public void evictEntityRegion(String entityName) {
		EntityPersister p = sessionFactory.getMetamodel().entityPersister( entityName );
		if ( p.hasCache() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf( "Evicting second-level cache: %s", p.getEntityName() );
			}
			p.getCacheAccessStrategy().evictAll();
		}
	}

	@Override
	public void evictEntityRegions() {
		sessionFactory.getMetamodel().entityPersisters().keySet().forEach( this::evictEntityRegion );
	}

	@Override
	public void evictNaturalIdRegion(Class entityClass) {
		evictNaturalIdRegion( entityClass.getName() );
	}

	@Override
	public void evictNaturalIdRegion(String entityName) {
		EntityPersister p = sessionFactory.getMetamodel().entityPersister( entityName );
		if ( p.hasNaturalIdCache() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf( "Evicting natural-id cache: %s", p.getEntityName() );
			}
			p.getNaturalIdCacheAccessStrategy().evictAll();
		}
	}

	@Override
	public void evictNaturalIdRegions() {
		sessionFactory.getMetamodel().entityPersisters().keySet().forEach( this::evictNaturalIdRegion );
	}

	@Override
	public boolean containsCollection(String role, Serializable ownerIdentifier) {
		CollectionPersister p = sessionFactory.getMetamodel().collectionPersister( role );
		if ( p.hasCache() ) {
			CollectionRegionAccessStrategy cache = p.getCacheAccessStrategy();
			Object key = cache.generateCacheKey( ownerIdentifier, p, sessionFactory, null ); // have to assume non tenancy
			return cache.getRegion().contains( key );
		}
		else {
			return false;
		}
	}

	@Override
	public void evictCollection(String role, Serializable ownerIdentifier) {
		CollectionPersister p = sessionFactory.getMetamodel().collectionPersister( role );
		if ( p.hasCache() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf(
						"Evicting second-level cache: %s",
						MessageHelper.collectionInfoString( p, ownerIdentifier, sessionFactory )
				);
			}
			CollectionRegionAccessStrategy cache = p.getCacheAccessStrategy();
			Object key = cache.generateCacheKey( ownerIdentifier, p, sessionFactory, null ); // have to assume non tenancy
			cache.evict( key );
		}
	}

	@Override
	public void evictCollectionRegion(String role) {
		CollectionPersister p = sessionFactory.getMetamodel().collectionPersister( role );
		if ( p.hasCache() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf( "Evicting second-level cache: %s", p.getRole() );
			}
			p.getCacheAccessStrategy().evictAll();
		}
	}

	@Override
	public void evictCollectionRegions() {
		sessionFactory.getMetamodel().collectionPersisters().keySet().forEach( this::evictCollectionRegion );
	}

	@Override
	public boolean containsQuery(String regionName) {
		return sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled()
				&& queryCaches.containsKey( regionName );
	}

	@Override
	public void evictDefaultQueryRegion() {
		if ( sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled() ) {
			if ( LOG.isDebugEnabled() ) {
				LOG.debug( "Evicting default query region cache." );
			}
			getDefaultQueryCache().clear();
		}
	}

	@Override
	public void evictQueryRegion(String regionName) {
		if ( regionName == null ) {
			throw new NullPointerException(
					"Region-name cannot be null (use Cache#evictDefaultQueryRegion to evict the default query cache)"
			);
		}
		if ( sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled() ) {
			QueryCache namedQueryCache = queryCaches.get( regionName );
			// TODO : cleanup entries in queryCaches + allCacheRegions ?
			if ( namedQueryCache != null ) {
				if ( LOG.isDebugEnabled() ) {
					LOG.debugf( "Evicting query cache, region: %s", regionName );
				}
				namedQueryCache.clear();
			}
		}
	}

	@Override
	public void evictQueryRegions() {
		evictDefaultQueryRegion();

		if ( CollectionHelper.isEmpty( queryCaches ) ) {
			return;
		}
		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Evicting cache of all query regions." );
		}

		queryCaches.values().forEach( QueryCache::clear );
	}

	@Override
	public void close() {
		for ( EntityRegion region : entityRegions.values() ) {
			region.destroy();
		}

		for ( CollectionRegion region : collectionRegions.values() ) {
			region.destroy();
		}

		for ( NaturalIdRegion region : naturalIdRegions.values() ) {
			region.destroy();
		}

		if ( settings.isQueryCacheEnabled() ) {
			defaultQueryCache.destroy();

			for ( QueryCache cache : queryCaches.values() ) {
				cache.destroy();
			}
			updateTimestampsCache.destroy();
		}

		regionFactory.stop();
	}

	@Override
	public QueryCache getDefaultQueryCache() {
		return defaultQueryCache;
	}

	@Override
	public QueryCache getQueryCache(String regionName) throws HibernateException {
		if ( !settings.isQueryCacheEnabled() ) {
			return null;
		}

		if ( regionName == null ) {
			return getDefaultQueryCache();
		}

		QueryCache queryCache = queryCaches.get( regionName );
		if ( queryCache == null ) {
			synchronized (queryCaches) {
				queryCache = queryCaches.get( regionName );
				if ( queryCache == null ) {
					final QueryResultsRegion region = regionFactory.buildQueryResultsRegion(
							qualifyRegionName( regionName ),
							sessionFactory.getProperties()
					);

					queryCache = settings.getQueryCacheFactory().buildQueryCache( region, this );
					queryCaches.put( regionName, queryCache );
				}
			}
		}
		return queryCache;
	}

	@Override
	public UpdateTimestampsCache getUpdateTimestampsCache() {
		return updateTimestampsCache;
	}

	@Override
	public void evictQueries() throws HibernateException {
		if ( settings.isQueryCacheEnabled() ) {
			defaultQueryCache.clear();
		}
	}

	@Override
	public String[] getSecondLevelCacheRegionNames() {
		final Set<String> names = new HashSet<>();
		names.addAll( entityRegions.keySet() );
		names.addAll( collectionRegions.keySet() );
		names.addAll( naturalIdRegions.keySet() );
		if ( settings.isQueryCacheEnabled() ) {
			names.add( updateTimestampsCache.getRegion().getName() );
			names.addAll( queryCaches.keySet() );
		}
		return ArrayHelper.toStringArray( names );
	}

	@Override
	public EntityRegion getEntityRegion(String regionName) {
		return entityRegions.get(regionName);
	}

	@Override
	public CollectionRegion getCollectionRegion(String regionName) {
		return collectionRegions.get(regionName);
	}

	@Override
	public NaturalIdRegion getNaturalIdCacheRegion(String regionName) {
		return naturalIdRegions.get(regionName);
	}

	@Override
	public void evictAllRegions() {
		evictCollectionRegions();
		evictDefaultQueryRegion();
		evictEntityRegions();
		evictQueryRegions();
		evictNaturalIdRegions();
	}

	@Override
	public boolean contains(Class cls, Object primaryKey) {
		return containsEntity( cls, (Serializable) primaryKey );
	}

	@Override
	public void evict(Class cls, Object primaryKey) {
		evictEntity( cls, (Serializable) primaryKey );
	}

	@Override
	public void evict(Class cls) {
		evictEntityRegion( cls );
	}

	@Override
	public void evictAll() {
		// Evict only the "JPA cache", which is purely defined as the entity regions.
		evictEntityRegions();
		// TODO : if we want to allow an optional clearing of all cache data, the additional calls would be:
//			evictCollectionRegions();
//			evictQueryRegions();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(Class<T> cls) {
		if ( org.hibernate.Cache.class.isAssignableFrom( cls ) ) {
			return (T) this;
		}

		if ( RegionFactory.class.isAssignableFrom( cls ) ) {
			return (T) regionFactory;
		}

		throw new PersistenceException( "Hibernate cannot unwrap Cache as " + cls.getName() );
	}

	@Override
	public EntityRegionAccessStrategy determineEntityRegionAccessStrategy(PersistentClass model) {
		final String cacheRegionName = cacheRegionPrefix + model.getRootClass().getCacheRegionName();
		if (settings.isSecondLevelCacheEnabled()) {
			final AccessType accessType = AccessType.fromExternalName( model.getCacheConcurrencyStrategy() );
			if ( accessType != null ) {
				return getEntityRegion(
						cacheRegionName,
						CacheDataDescriptionImpl.decode( model )
				).buildAccessStrategy(accessType);
			}
		}
		return null;
	}


	@Override
	public NaturalIdRegionAccessStrategy determineNaturalIdRegionAccessStrategy(PersistentClass model) {
		if ( settings.isSecondLevelCacheEnabled() && model.hasNaturalId() && model.getNaturalIdCacheRegionName() != null ) {
			final String naturalIdCacheRegionName = cacheRegionPrefix + model.getNaturalIdCacheRegionName();
			try {
				return getNaturalIdRegion(
						naturalIdCacheRegionName,
						CacheDataDescriptionImpl.decode( model )
				).buildAccessStrategy( regionFactory.getDefaultAccessType() );
			}
			catch ( UnsupportedOperationException e ) {
				LOG.warnf(
						"Shared cache region factory [%s] does not support natural id caching; " +
								"shared NaturalId caching will be disabled for not be enabled for %s",
						regionFactory.getClass().getName(),
						model.getEntityName()
				);
				return null;
			}
		}
		return null;
	}

	@Override
	public CollectionRegionAccessStrategy determineCollectionRegionAccessStrategy(Collection model) {
		final String cacheRegionName = cacheRegionPrefix + model.getCacheRegionName();
		if (settings.isSecondLevelCacheEnabled()) {
			final AccessType accessType = AccessType.fromExternalName(model.getCacheConcurrencyStrategy());
			if (accessType != null) {
				return getCollectionRegion(
						cacheRegionName,
						CacheDataDescriptionImpl.decode( model )
				).buildAccessStrategy(accessType);
			}
		}
		return null;
	}

	private NaturalIdRegion getNaturalIdRegion(String cacheRegionName, CacheDataDescription cacheDataDescription) {
		NaturalIdRegion region = naturalIdRegions.get(cacheRegionName);
		if (region == null) {
			LOG.tracef( "Building natural id cache region for data [%s]", cacheDataDescription );
			NaturalIdRegion naturalIdRegion = regionFactory.buildNaturalIdRegion(
					cacheRegionName,
					sessionFactory.getProperties(),
					cacheDataDescription
			);
			naturalIdRegions.put(cacheRegionName, naturalIdRegion);
			return naturalIdRegion;
		}
		else {
			checkCacheDataDescriptions( region.getCacheDataDescription(), cacheDataDescription);
			return region;
		}
	}

	private EntityRegion getEntityRegion(String cacheRegionName, CacheDataDescription cacheDataDescription) {
		EntityRegion region = entityRegions.get(cacheRegionName);
		if (region == null) {
			LOG.tracef( "Building shared cache region for entity data [%s]", cacheDataDescription );
			EntityRegion entityRegion = regionFactory.buildEntityRegion(
					cacheRegionName,
					sessionFactory.getProperties(),
					cacheDataDescription
			);
			entityRegions.put(cacheRegionName, entityRegion);
			return entityRegion;
		}
		else {
			checkCacheDataDescriptions( region.getCacheDataDescription(), cacheDataDescription );
			return region;
		}
	}

	private CollectionRegion getCollectionRegion(String cacheRegionName, CacheDataDescription cacheDataDescription) {
		CollectionRegion region = collectionRegions.get(cacheRegionName);
		if (region == null) {
			LOG.tracev("Building shared cache region for collection data [{0}]", cacheDataDescription);
			CollectionRegion collectionRegion = regionFactory.buildCollectionRegion(
					cacheRegionName,
					sessionFactory.getProperties(),
					cacheDataDescription
			);
			collectionRegions.put(cacheRegionName, collectionRegion);
			return collectionRegion;
		}
		else {
			checkCacheDataDescriptions(region.getCacheDataDescription(), cacheDataDescription);
			return region;
		}
	}

    /*
     * TODO: This method needs more attention, right now it's pretty pessimistic.
     */
	private void checkCacheDataDescriptions(CacheDataDescription a, CacheDataDescription b) {
		if (a.isMutable() ^ b.isMutable()) {
			throw new IllegalArgumentException("Incompatible cache data cannot share regions (" + a + " & " + b + ")");
		}
		if (a.isVersioned() ^ b.isVersioned()) {
			throw new IllegalArgumentException("Incompatible cache data cannot share regions (" + a + " & " + b + ")");
		}
		if (a.isVersioned() && a.getVersionComparator() != b.getVersionComparator()) {
			throw new IllegalArgumentException("Incompatible cache data cannot share regions (" + a + " & " + b + ")");
		}
		if (a.getKeyType() != b.getKeyType()) {
			throw new IllegalArgumentException("Incompatible cache data cannot share regions (" + a + " & " + b + ")");
		}
	}

}
