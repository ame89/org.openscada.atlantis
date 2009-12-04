package org.openscada.ae.monitor.dataitem;

import java.util.HashMap;
import java.util.Map;

import org.openscada.ae.ConditionStatus;
import org.openscada.ae.ConditionStatusInformation;
import org.openscada.ae.event.EventProcessor;
import org.openscada.ae.monitor.common.AbstractConditionService;
import org.openscada.core.Variant;
import org.openscada.da.client.DataItemValue;
import org.openscada.da.client.DataItemValue.Builder;
import org.openscada.da.core.WriteAttributeResult;
import org.openscada.da.core.WriteAttributeResults;
import org.openscada.da.master.MasterItem;
import org.openscada.da.master.MasterItemHandler;
import org.openscada.da.master.WriteRequest;
import org.openscada.da.master.WriteRequestResult;
import org.openscada.utils.osgi.FilterUtil;
import org.openscada.utils.osgi.SingleServiceListener;
import org.openscada.utils.osgi.SingleServiceTracker;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDataItemMonitor extends AbstractConditionService implements DataItemMonitor
{
    private final static Logger logger = LoggerFactory.getLogger ( AbstractDataItemMonitor.class );

    private String masterId;

    private final BundleContext context;

    private SingleServiceTracker tracker;

    private MasterItem masterItem;

    private MasterItemHandler handler;

    protected final String prefix;

    private boolean requireAkn = false;

    private boolean active = true;

    private boolean akn;

    private ConditionStatus state;

    public AbstractDataItemMonitor ( final BundleContext context, final EventProcessor eventProcessor, final String id, final String prefix )
    {
        super ( eventProcessor, id );
        this.context = context;
        this.prefix = prefix;
    }

    public void dispose ()
    {
        disconnect ();
    }

    protected static boolean getBoolean ( final Map<String, String> properties, final String key, final boolean defaultValue )
    {
        final String value = properties.get ( key );
        if ( value == null )
        {
            return defaultValue;
        }
        return Boolean.parseBoolean ( value );
    }

    public synchronized void update ( final Map<String, String> properties ) throws Exception
    {
        disconnect ();
        this.masterId = properties.get ( MasterItem.MASTER_ID );
        setActive ( getBoolean ( properties, "active", true ) );
        setRequireAkn ( getBoolean ( properties, "requireAkn", false ) );
        connect ();
    }

    private synchronized void connect () throws InvalidSyntaxException
    {
        if ( this.masterId == null )
        {
            setUnsafe ();
            throw new RuntimeException ( String.format ( "'%s' is not set", MasterItem.MASTER_ID ) );
        }

        logger.debug ( "Setting up for master item: {}", this.masterId );

        final Map<String, String> parameters = new HashMap<String, String> ();
        parameters.put ( MasterItem.MASTER_ID, this.masterId );
        final Filter filter = FilterUtil.createAndFilter ( MasterItem.class.getName (), parameters );
        this.tracker = new SingleServiceTracker ( this.context, filter, new SingleServiceListener () {

            public void serviceChange ( final ServiceReference reference, final Object service )
            {
                AbstractDataItemMonitor.this.setMasterItem ( (MasterItem)service );
            }
        } );
        this.tracker.open ();
    }

    protected synchronized void setMasterItem ( final MasterItem masterItem )
    {
        logger.info ( "Setting master item:{}", masterItem );

        disconnectItem ();
        connectItem ( masterItem );
    }

    private synchronized void connectItem ( final MasterItem masterItem )
    {
        logger.debug ( "Connecting to master item: {}", masterItem );

        this.masterItem = masterItem;
        if ( this.masterItem != null )
        {
            this.masterItem.addHandler ( this.handler = new MasterItemHandler () {

                public WriteRequestResult processWrite ( final WriteRequest request )
                {
                    return AbstractDataItemMonitor.this.handleProcessWrite ( request );
                }

                public DataItemValue dataUpdate ( final DataItemValue value )
                {
                    logger.debug ( "Handle data update: {}", value );
                    return AbstractDataItemMonitor.this.handleDataUpdate ( value );
                }
            }, 0 );
        }
    }

    private synchronized void disconnectItem ()
    {
        logger.debug ( "Disconnect from master item: {}", this.masterItem );

        if ( this.masterItem != null )
        {
            this.masterItem.removeHandler ( this.handler );
            this.masterItem = null;
            this.handler = null;
        }
    }

    private synchronized void disconnect ()
    {
        if ( this.tracker != null )
        {
            this.tracker.close ();
            this.tracker = null;
        }
    }

    private DataItemValue handleDataUpdate ( final DataItemValue value )
    {
        final DataItemValue.Builder builder = new DataItemValue.Builder ( value );

        performDataUpdate ( builder );
        injectAttributes ( builder );

        final DataItemValue newValue = builder.build ();
        logger.info ( "Setting new value: {}", newValue );

        return newValue;
    }

    protected abstract void performDataUpdate ( Builder builder );

    @Override
    protected void notifyStateChange ( final ConditionStatusInformation status )
    {
        super.notifyStateChange ( status );
        this.state = status.getStatus ();
        this.akn = this.state == ConditionStatus.NOT_AKN || this.state == ConditionStatus.NOT_OK_NOT_AKN;
        reprocess ();
    }

    /**
     * Inject attributes to the value after the value update has been performed using
     * {@link #performDataUpdate(Builder)}
     * @param builder the builder to use for changing information
     */
    protected void injectAttributes ( final Builder builder )
    {
        builder.setAttribute ( this.prefix + ".active", new Variant ( this.active ) );
        builder.setAttribute ( this.prefix + ".requireAkn", new Variant ( this.requireAkn ) );

        builder.setAttribute ( this.prefix + ".akn", this.akn ? Variant.TRUE : Variant.FALSE );
        builder.setAttribute ( this.prefix + ".state", new Variant ( this.state.toString () ) );
    }

    protected WriteRequestResult handleProcessWrite ( final WriteRequest request )
    {
        if ( request.getAttributes () != null )
        {
            return handleAttributesWrite ( request );
        }
        return null;
    }

    protected WriteRequestResult handleAttributesWrite ( final WriteRequest request )
    {
        final Map<String, Variant> attributes = new HashMap<String, Variant> ( request.getAttributes () );
        final WriteAttributeResults result = new WriteAttributeResults ();
        simpleHandleAttributes ( attributes, result );

        // remove result keys from request
        for ( final String attr : result.keySet () )
        {
            attributes.remove ( attr );
        }

        return new WriteRequestResult ( request.getValue (), attributes, result );
    }

    protected void simpleHandleAttributes ( final Map<String, Variant> attributes, final WriteAttributeResults result )
    {
        final Map<String, String> configUpdate = new HashMap<String, String> ();

        handleConfigUpdate ( configUpdate, attributes, result );

        if ( !configUpdate.isEmpty () )
        {
            updateConfiguration ( configUpdate );
        }
    }

    private void updateConfiguration ( final Map<String, String> configUpdate )
    {
        logger.info ( "Request to update configuration: {}", configUpdate );
    }

    @Override
    public synchronized void setRequireAkn ( final boolean state )
    {
        super.setRequireAkn ( state );
        this.requireAkn = state;
        reprocess ();
    }

    @Override
    public synchronized void setActive ( final boolean state )
    {
        super.setActive ( state );
        this.active = state;
        reprocess ();
    }

    private void reprocess ()
    {
        final MasterItem item = this.masterItem;
        if ( item != null )
        {
            item.reprocess ();
        }
    }

    protected void handleConfigUpdate ( final Map<String, String> configUpdate, final Map<String, Variant> attributes, final WriteAttributeResults result )
    {
        final Variant active = attributes.get ( this.prefix + ".active" );
        if ( active != null )
        {
            configUpdate.put ( "active", active.asBoolean () ? "true" : "false" );
            result.put ( this.prefix + ".active", new WriteAttributeResult () );
        }

        final Variant requireAkn = attributes.get ( this.prefix + ".requireAkn" );
        if ( requireAkn != null )
        {
            configUpdate.put ( "requireAkn", requireAkn.asBoolean () ? "true" : "false" );
            result.put ( this.prefix + ".requireAkn", new WriteAttributeResult () );
        }
    }

}