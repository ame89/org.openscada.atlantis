package org.openscada.da.master.common.sum;

import java.util.Map;

import org.openscada.da.master.common.AbstractHandlerImpl;
import org.openscada.utils.osgi.ca.factory.AbstractServiceConfigurationFactory;
import org.osgi.framework.BundleContext;

public class CommonFactoryImpl extends AbstractServiceConfigurationFactory<AbstractHandlerImpl>
{
    private final BundleContext context;

    private final String tag;

    private final int priority;

    public CommonFactoryImpl ( final BundleContext context, final String tag, final int priority )
    {
        super ( context );
        this.context = context;
        this.tag = tag;
        this.priority = priority;
    }

    @Override
    protected Entry<AbstractHandlerImpl> createService ( final String configurationId, final BundleContext context, final Map<String, String> parameters ) throws Exception
    {
        final AbstractHandlerImpl handler = new CommonSumHandler ( this.context, this.tag, this.priority );
        handler.update ( parameters );
        return new Entry<AbstractHandlerImpl> ( handler );
    }

    @Override
    protected Entry<AbstractHandlerImpl> updateService ( final String configurationId, final Entry<AbstractHandlerImpl> entry, final Map<String, String> parameters ) throws Exception
    {
        entry.getService ().update ( parameters );
        return null;
    }

    @Override
    protected void disposeService ( final AbstractHandlerImpl service )
    {
        service.dispose ();
    }

}