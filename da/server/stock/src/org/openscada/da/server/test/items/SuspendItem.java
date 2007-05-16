/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openscada.da.server.test.items;

import org.apache.log4j.Logger;
import org.openscada.da.server.common.DataItemInputCommon;
import org.openscada.da.server.common.ItemListener;

public class SuspendItem extends DataItemInputCommon
{
    private static Logger _log = Logger.getLogger ( SuspendItem.class );
    
    public SuspendItem ( String name )
    {
        super ( name );
    }
    
    @Override
    public void setListener ( ItemListener listener )
    {
        super.setListener ( listener );
        if ( listener != null )
        {
            wakeup ();
        }
        else
        {
            suspend ();
        }
    }

    public void suspend ()
    {
       _log.warn ( String.format ( "Item %1$s suspended", getInformation ().getName () ) );
    }

    public void wakeup ()
    {
        _log.warn ( String.format ( "Item %1$s woken up", getInformation ().getName () ) );
    }

}
