/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.jdbc.pool.dbcp2.impl;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.transaction.TransactionManager;

import org.ops4j.pax.jdbc.pool.dbcp2.impl.ds.PooledDataSourceFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches for DataSourceFactory services and creates/destroys a PooledDataSourceFactory for each
 * existing DataSourceFactory
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DataSourceFactoryTracker implements ServiceTrackerCustomizer {

    private Logger LOG = LoggerFactory.getLogger(DataSourceFactoryTracker.class);
    private BundleContext context;

    private Map<ServiceReference, ServiceRegistration> serviceRegs;
    private TransactionManager tm;

    public DataSourceFactoryTracker(BundleContext context) {
        this(context, null);
    }

    public DataSourceFactoryTracker(BundleContext context, TransactionManager tm) {
        this.tm = tm;
        this.context = context;
        this.serviceRegs = new HashMap<ServiceReference, ServiceRegistration>();
    }

    @Override
    public Object addingService(ServiceReference reference) {
        if (reference.getProperty("pooled") != null) {
            // Make sure we do not react on our own service for the pooled factory
            return null;
        }
        ServiceRegistration reg = createAndregisterPooledFactory(reference);
        serviceRegs.put(reference, reg);
        return null;
    }

    private ServiceRegistration createAndregisterPooledFactory(ServiceReference reference) {
        LOG.debug("Registering PooledDataSourceFactory");
        DataSourceFactory dsf = (DataSourceFactory) context.getService(reference);
        PooledDataSourceFactory pdsf = new PooledDataSourceFactory(dsf, tm);
        Dictionary props = createPropsForPoolingDataSourceFactory(reference);
        ServiceRegistration reg = context.registerService(DataSourceFactory.class.getName(), pdsf,
            props);
        return reg;
    }

    private Properties createPropsForPoolingDataSourceFactory(ServiceReference reference) {
        Properties props = new Properties();
        for (String key : reference.getPropertyKeys()) {
            if (!"service.id".equals(key)) {
                props.put(key, reference.getProperty(key));
            }
        }
        props.put("pooled", "true");
        if (tm != null) {
            props.put("xa", "true");
        }
        props.put(DataSourceFactory.OSGI_JDBC_DRIVER_NAME, getPoolDriverName(reference));
        return props;
    }

    private String getPoolDriverName(ServiceReference reference) {
        String origName = (String) reference.getProperty(DataSourceFactory.OSGI_JDBC_DRIVER_NAME);
        if (origName == null) {
            origName = (String) reference.getProperty(DataSourceFactory.OSGI_JDBC_DRIVER_CLASS);
        }
        return origName + "-pool" + ((tm != null) ? "-xa" : "");
    }

    @Override
    public void modifiedService(ServiceReference reference, Object service) {

    }

    @Override
    public void removedService(ServiceReference reference, Object service) {
        ServiceRegistration reg = serviceRegs.get(reference);
        if (reg != null) {
            LOG.warn("Unregistering PooledDataSourceFactory");
            reg.unregister();
        }
    }

}