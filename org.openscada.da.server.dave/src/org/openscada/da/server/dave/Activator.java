package org.openscada.da.server.dave;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.openscada.ca.ConfigurationAdministrator;
import org.openscada.ca.ConfigurationFactory;
import org.openscada.da.server.dave.data.VariableManager;
import org.openscada.da.server.dave.data.VariableManagerImpl;
import org.openscada.da.server.dave.factory.BeanConfigurationFactory;
import org.openscada.da.server.dave.factory.ConfigurationFactoryImpl;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{

    private ConfigurationFactoryImpl service;

    private BeanConfigurationFactory blockFactory;

    private static VariableManagerImpl variableManager;

    private ExecutorService executor;

    public static VariableManager getVariableManager ()
    {
        return variableManager;
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start ( final BundleContext context ) throws Exception
    {

        this.executor = Executors.newSingleThreadExecutor ();

        this.service = new ConfigurationFactoryImpl ( context );

        {
            final Dictionary<Object, Object> properties = new Hashtable<Object, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, "org.openscada.da.server.dave.device" );
            context.registerService ( ConfigurationFactory.class.getName (), this.service, properties );
        }

        {
            final Dictionary<Object, Object> properties = new Hashtable<Object, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, "org.openscada.da.server.dave.block" );
            this.blockFactory = new BeanConfigurationFactory ( context, BlockConfiguration.class );
            context.registerService ( ConfigurationFactory.class.getName (), this.blockFactory, properties );
        }

        {
            Activator.variableManager = new VariableManagerImpl ( this.executor );
            final Dictionary<Object, Object> properties = new Hashtable<Object, Object> ();
            properties.put ( ConfigurationAdministrator.FACTORY_ID, "org.openscada.da.server.dave.types" );
            context.registerService ( ConfigurationFactory.class.getName (), Activator.variableManager, properties );
        }
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop ( final BundleContext context ) throws Exception
    {
        this.blockFactory.dispose ();
        this.service.dispose ();

        Activator.variableManager.dispose ();
        Activator.variableManager = null;

        this.executor.shutdown ();
        this.executor = null;
    }

}