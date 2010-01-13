package org.openscada.ae.monitor.dataitem;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.openscada.ae.event.EventProcessor;
import org.openscada.ae.monitor.dataitem.monitor.internal.bit.BooleanAlarmMonitor;
import org.openscada.ae.monitor.dataitem.monitor.internal.bit.MonitorFactoryImpl;
import org.openscada.ae.monitor.dataitem.monitor.internal.level.LevelMonitorFactoryImpl;
import org.openscada.ae.monitor.dataitem.monitor.internal.remote.RemoteBooleanAttributeAlarmMonitor;
import org.openscada.ae.monitor.dataitem.monitor.internal.remote.RemoteAttributeMonitorFactoryImpl;
import org.openscada.ae.server.common.akn.AknHandler;
import org.openscada.ca.ConfigurationAdministrator;
import org.openscada.ca.ConfigurationFactory;
import org.openscada.da.master.MasterItem;
import org.openscada.utils.concurrent.NamedThreadFactory;
import org.openscada.utils.osgi.pool.ObjectPoolTracker;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator
{
    private final static Logger logger = Logger.getLogger ( Activator.class );

    private static Activator instance;

    private EventProcessor eventProcessor;

    private ServiceTracker configAdminTracker;

    private final Collection<AbstractMonitorFactory> factories = new LinkedList<AbstractMonitorFactory> ();

    private ObjectPoolTracker poolTracker;

    private ExecutorService executor;

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start ( final BundleContext context ) throws Exception
    {
        logger.info ( "Starting up..." );

        this.executor = Executors.newSingleThreadExecutor ( new NamedThreadFactory ( context.getBundle ().getSymbolicName () ) );

        this.eventProcessor = new EventProcessor ( context );
        this.eventProcessor.open ();

        this.configAdminTracker = new ServiceTracker ( context, ConfigurationAdministrator.class.getName (), null );
        this.configAdminTracker.open ();

        Dictionary<Object, Object> properties;

        this.poolTracker = new ObjectPoolTracker ( context, MasterItem.class.getName () );
        this.poolTracker.open ();

        // monitor service
        {
            final MonitorFactoryImpl factory = new MonitorFactoryImpl ( context, this.poolTracker, this.eventProcessor );
            properties = new Hashtable<Object, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, BooleanAlarmMonitor.FACTORY_ID );
            properties.put ( Constants.SERVICE_DESCRIPTION, "Boolean alarms" );
            context.registerService ( new String[] { ConfigurationFactory.class.getName (), AknHandler.class.getName () }, factory, properties );
            this.factories.add ( factory );
        }

        // remote monitor service
        {
            final RemoteAttributeMonitorFactoryImpl factory = new RemoteAttributeMonitorFactoryImpl ( context, this.executor, this.poolTracker, this.eventProcessor );
            properties = new Hashtable<Object, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, RemoteBooleanAttributeAlarmMonitor.FACTORY_ID );
            properties.put ( Constants.SERVICE_DESCRIPTION, "Remote Boolean alarms" );
            context.registerService ( new String[] { ConfigurationFactory.class.getName (), AknHandler.class.getName () }, factory, properties );
            this.factories.add ( factory );
        }

        makeLevelFactory ( context, "ceil", "MAX", true, 0, true );
        makeLevelFactory ( context, "highhigh", "HH", true, 1000, false );
        makeLevelFactory ( context, "high", "H", true, 1000, false );
        makeLevelFactory ( context, "low", "L", false, 1000, false );
        makeLevelFactory ( context, "lowlow", "LL", false, 1000, false );
        makeLevelFactory ( context, "floor", "MIN", false, 0, true );

        logger.info ( "Starting up...done" );

        Activator.instance = this;
    }

    private void makeLevelFactory ( final BundleContext context, final String type, final String defaultMonitorType, final boolean lowerOk, final int priority, final boolean cap )
    {
        Dictionary<Object, Object> properties;
        final LevelMonitorFactoryImpl factory = new LevelMonitorFactoryImpl ( context, this.poolTracker, this.eventProcessor, type, defaultMonitorType, lowerOk, priority, cap );
        properties = new Hashtable<Object, Object> ();
        properties.put ( ConfigurationAdministrator.FACTORY_ID, LevelMonitorFactoryImpl.FACTORY_PREFIX + "." + type );
        properties.put ( Constants.SERVICE_DESCRIPTION, type + " Alarms" );
        context.registerService ( new String[] { ConfigurationFactory.class.getName (), AknHandler.class.getName () }, factory, properties );
        this.factories.add ( factory );
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop ( final BundleContext context ) throws Exception
    {
        this.executor.shutdown ();

        Activator.instance = null;

        for ( final AbstractMonitorFactory factory : this.factories )
        {
            factory.dispose ();
        }

        this.eventProcessor.close ();
    }

    public static ConfigurationAdministrator getConfigAdmin ()
    {
        return (ConfigurationAdministrator)Activator.instance.configAdminTracker.getService ();
    }
}
