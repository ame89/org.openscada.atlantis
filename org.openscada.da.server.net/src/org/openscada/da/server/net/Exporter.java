/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2010 TH4 SYSTEMS GmbH (http://th4-systems.com)
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

package org.openscada.da.server.net;

import java.io.IOException;
import java.util.Collection;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.openscada.core.ConnectionInformation;
import org.openscada.core.server.net.Server;
import org.openscada.da.core.server.Hive;
import org.openscada.da.server.common.impl.ExporterBase;
import org.openscada.utils.lifecycle.LifecycleAware;

public class Exporter extends ExporterBase implements LifecycleAware
{
    private Server server;

    private Collection<ConnectionInformation> startedConnections;

    public Exporter ( final Hive hive, final ConnectionInformation connectionInformation ) throws Exception
    {
        super ( hive, connectionInformation );
    }

    private Collection<ConnectionInformation> createServer () throws IOException
    {
        this.server = new Server ( this.connectionInformation );
        return this.server.start ( createFactory () );
    }

    private SingleSessionIoHandlerDelegate createFactory ()
    {
        return new SingleSessionIoHandlerDelegate ( new SingleSessionIoHandlerFactory () {

            @Override
            public SingleSessionIoHandler getHandler ( final IoSession session ) throws Exception
            {
                return new ServerConnectionHandler ( Exporter.this.hive, session, Exporter.this.connectionInformation );
            }
        } );
    }

    @Override
    public void start () throws Exception
    {
        this.startedConnections = createServer ();
    }

    public Collection<ConnectionInformation> getStartedConnections ()
    {
        return this.startedConnections;
    }

    @Override
    public void stop () throws Exception
    {
        destroyServer ();
    }

    private void destroyServer ()
    {
        if ( this.server != null )
        {
            this.server.dispose ();
            this.server = null;
        }
    }
}
