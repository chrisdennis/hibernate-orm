/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.cache.jcache.access;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;

import static javax.cache.configuration.FactoryBuilder.factoryOf;

public final class SoftLockEvictionLogger implements CacheEntryListener<Object, Object>, javax.cache.event. {

	public static final CacheEntryListenerConfiguration CONFIGURATION = new CacheEntryListenerConfiguration() {
		@Override
		public Factory<CacheEntryListener<?, Object>> getCacheEntryListenerFactory() {
			return factoryOf( INSTANCE );
		}

		@Override
		public boolean isOldValueRequired() {
			return false;
		}

		@Override
		public Factory<CacheEntryEventFilter<?, Object>> getCacheEntryEventFilterFactory() {
			return null;
		}

		@Override
		public boolean isSynchronous() {
			return false;
		}
	};

	public static final CacheEntryListener<?, Object> INSTANCE = new SoftLockEvictionLogger();

	private SoftLockEvictionLogger() {}
}
