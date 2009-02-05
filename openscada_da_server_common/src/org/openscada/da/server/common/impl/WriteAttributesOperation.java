/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.openscada.da.server.common.impl;

import java.util.Map;

import org.openscada.core.Variant;
import org.openscada.da.core.WriteAttributeResults;
import org.openscada.da.core.server.WriteAttributesOperationListener;
import org.openscada.da.server.common.DataItem;
import org.openscada.utils.jobqueue.RunnableCancelOperation;

public class WriteAttributesOperation extends RunnableCancelOperation
{

    private DataItem _item = null;
    private WriteAttributesOperationListener _listener = null;
    private Map<String, Variant> _attributes = null;
    
    public WriteAttributesOperation ( DataItem item, WriteAttributesOperationListener listener, Map<String, Variant> attributes )
    {
        _item = item;
        _listener = listener;
        _attributes = attributes;
    }
    
    public void run ()
    {
        try
        {
            WriteAttributeResults writeAttributeResults = _item.setAttributes ( _attributes );
            synchronized ( this )
            {
                if ( !isCanceled () )
                {
                    _listener.complete ( writeAttributeResults );
                }
            }
        }
        catch ( Exception e )
        {
            synchronized ( this )
            {
                if ( !isCanceled () )
                {
                    _listener.failed ( e );
                }
            }
        }
        
        
    }

}
