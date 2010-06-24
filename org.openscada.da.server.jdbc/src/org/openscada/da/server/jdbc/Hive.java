/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2010 inavare GmbH (http://inavare.com)
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

package org.openscada.da.server.jdbc;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Timer;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.openscada.da.jdbc.configuration.ConnectionType;
import org.openscada.da.jdbc.configuration.QueryType;
import org.openscada.da.jdbc.configuration.RootDocument;
import org.openscada.da.jdbc.configuration.UpdateMappingType;
import org.openscada.da.jdbc.configuration.UpdateType;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.ValidationStrategy;
import org.openscada.da.server.common.impl.HiveCommon;
import org.openscada.da.server.jdbc.Update.Mapping;
import org.w3c.dom.Node;

public class Hive extends HiveCommon
{
    private static Logger logger = Logger.getLogger ( Hive.class );

    private FolderCommon rootFolder = null;

    private final Collection<Connection> connections = new LinkedList<Connection> ();

    private final Timer timer;

    public Hive () throws XmlException, IOException
    {
        this ( RootDocument.Factory.parse ( new File ( "configuration.xml" ) ) );
    }

    public Hive ( final Node node ) throws XmlException
    {
        this ( RootDocument.Factory.parse ( node ) );
    }

    protected Hive ( final RootDocument doc )
    {
        // create root folder
        this.rootFolder = new FolderCommon ();
        setRootFolder ( this.rootFolder );

        setValidatonStrategy ( ValidationStrategy.GRANT_ALL );

        this.timer = new Timer ( "JdbcHiveTimer", true );

        configure ( doc );

        register ();
    }

    public void register ()
    {
        for ( final Connection connection : this.connections )
        {
            connection.register ( this, this.rootFolder, this.timer );
        }
    }

    public void unregister ()
    {
        for ( final Connection connection : this.connections )
        {
            connection.unregister ( this );
        }
    }

    private void configure ( final RootDocument doc )
    {
        for ( final ConnectionType connectionType : doc.getRoot ().getConnectionList () )
        {
            createConnection ( connectionType );
        }
    }

    private void createConnection ( final ConnectionType connectionType )
    {
        final Connection connection = new Connection ( connectionType.getId (), connectionType.getTimeout (), connectionType.getConnectionClass (), connectionType.getUri (), connectionType.getUsername (), connectionType.getPassword () );

        for ( final QueryType queryType : connectionType.getQueryList () )
        {
            createQuery ( connection, queryType );
        }

        for ( final UpdateType updateType : connectionType.getUpdateList () )
        {
            createUpdate ( connection, updateType );
        }

        this.connections.add ( connection );
    }

    private void createUpdate ( final Connection connection, final UpdateType updateType )
    {
        String sql = updateType.getSql ();
        if ( sql == null || sql.length () == 0 )
        {
            sql = updateType.getSql2 ();
        }

        logger.info ( "Create update:" + sql );

        final Update update = new Update ( updateType.getId (), sql, connection );

        for ( final UpdateMappingType mappingValue : updateType.getMappingList () )
        {
            update.addMapping ( new Mapping ( mappingValue.getName (), mappingValue.getNamedParameter () ) );
        }

        connection.add ( update );
    }

    private void createQuery ( final Connection connection, final QueryType queryType )
    {
        String sql = queryType.getSql ();
        if ( sql == null || sql.length () == 0 )
        {
            sql = queryType.getSql2 ();
        }

        logger.info ( "Creating new query: " + sql );

        connection.add ( new Query ( queryType.getId (), queryType.getPeriod (), sql, connection ) );
    }
}
