/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2012 TH4 SYSTEMS GmbH (http://th4-systems.com)
 *
 * OpenSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.datasource.movingaverage;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.openscada.ca.ConfigurationAdministrator;
import org.openscada.ca.ConfigurationFactory;
import org.openscada.utils.concurrent.NamedThreadFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public class Activator implements BundleActivator
{
    private ScheduledExecutorService scheduler;

    private ExecutorService executor;

    private MovingAverageDataSourceFactory factory;

    private static BundleContext context;

    static BundleContext getContext ()
    {
        return context;
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start ( final BundleContext context ) throws Exception
    {
        Activator.context = context;
        this.executor = Executors.newSingleThreadExecutor ( new NamedThreadFactory ( context.getBundle ().getSymbolicName () + ".executor" ) );
        this.scheduler = Executors.newSingleThreadScheduledExecutor ( new NamedThreadFactory ( context.getBundle ().getSymbolicName () + ".scheduler" ) );
        this.factory = new MovingAverageDataSourceFactory ( context, this.executor, this.scheduler );

        final Dictionary<String, String> properties = new Hashtable<String, String> ();
        properties.put ( Constants.SERVICE_DESCRIPTION, "An averaging data source over time" );
        properties.put ( Constants.SERVICE_VENDOR, "TH4 SYSTEMS GmbH" );
        properties.put ( ConfigurationAdministrator.FACTORY_ID, context.getBundle ().getSymbolicName () );

        context.registerService ( ConfigurationFactory.class.getName (), this.factory, properties );
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop ( final BundleContext context ) throws Exception
    {
        this.factory.dispose ();
        this.executor.shutdown ();
        this.scheduler.shutdown ();
        Activator.context = null;
    }
}
