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

package org.openscada.net.da.handler;

import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;

import org.openscada.core.Variant;
import org.openscada.da.core.IODirection;
import org.openscada.net.base.data.ListValue;
import org.openscada.net.base.data.MapValue;
import org.openscada.net.base.data.Message;
import org.openscada.net.base.data.StringValue;
import org.openscada.net.base.data.Value;
import org.openscada.net.base.data.VoidValue;

public class Messages extends org.openscada.core.net.MessageHelper
{
	public final static int CC_CREATE_SESSION =                     0x00010001;
	public final static int CC_CLOSE_SESSION =                      0x00010002;
    
    public final static int CC_SUBSCRIBE_ITEM =                     0x00010010;
    public final static int CC_UNSUBSCRIBE_ITEM =                   0x00010011;
    public final static int CC_NOTIFY_VALUE =                       0x00010020;
    public final static int CC_NOTIFY_ATTRIBUTES =                  0x00010021;
    
    public final static int CC_WRITE_OPERATION =                    0x00010030;
    public final static int CC_WRITE_OPERATION_RESULT =             0x00010031;
    public final static int CC_READ_OPERATION =                     0x00010040;
    public final static int CC_WRITE_ATTRIBUTES_OPERATION =         0x00010050;
    public final static int CC_WRITE_ATTRIBUTES_OPERATION_RESULT =  0x00010040;
   
    public final static int CC_BROWSER_LIST_REQ =                   0x00010200;
    public final static int CC_BROWSER_LIST_RES =                   0x00010201;
    
    public final static int CC_BROWSER_EVENT =                      0x00010210;
    public final static int CC_BROWSER_SUBSCRIBE =                  0x00010211;
    public final static int CC_BROWSER_UNSUBSCRIBE =                0x00010212;
    
    public final static int CC_CANCEL_OPERATION =                   0x00010300;       
    
    public static Message createSession ( Properties props )
    {
        Message msg = new Message ( CC_CREATE_SESSION );
        
        for ( Map.Entry<Object,Object> entry : props.entrySet() )
        {
            msg.getValues().put (entry.getKey().toString(), new StringValue(entry.getValue().toString()) );
        }
        
        return msg;
    }
    
    public static Message closeSession ()
    {
        return new Message ( CC_CLOSE_SESSION );
    }
    
    public static Message subscribeItem ( String itemName, boolean initial )
    {
        Message msg = new Message ( CC_SUBSCRIBE_ITEM );
        msg.getValues().put ( "item-id", new StringValue(itemName) );
        
        if ( initial )
            msg.getValues().put ( "cache-read", new VoidValue () );
        
        return msg;
    }
    
    public static Message unsubscribeItem ( String itemName )
    {
        Message msg = new Message ( CC_UNSUBSCRIBE_ITEM );
        msg.getValues().put ( "item-id", new StringValue(itemName) );
        return msg;
    }
    
    public static Message notifyValue ( String itemName, Variant value, boolean initial )
    {
        Message msg = new Message ( CC_NOTIFY_VALUE );
        
        msg.getValues().put ( "item-id", new StringValue(itemName) );
        
        // flag if initial bit is set
        if ( initial )
            msg.getValues().put ( "cache-read", new VoidValue () );
        
        Value messageValue = variantToValue ( value );
        if ( messageValue != null )
            msg.getValues ().put ( "value", messageValue );
        
        return msg;
    }
    
    public static Message notifyAttributes ( String itemName, Map<String,Variant> attributes, boolean initial )
    {
        Message msg = new Message ( CC_NOTIFY_ATTRIBUTES );
        
        msg.getValues ().put ( "item-id", new StringValue(itemName) );
        
        // flag if initial bit is set
        if ( initial )
            msg.getValues ().put ( "cache-read", new VoidValue () );
        
        ListValue unsetEntries = new ListValue ();
        MapValue setEntries = new MapValue ();
        
        for ( Map.Entry<String,Variant> entry : attributes.entrySet() )
        {
            Value value = variantToValue ( entry.getValue () );
            if ( value == null )
            {
                unsetEntries.add ( new StringValue ( entry.getKey () ) );
            }
            else
            {
                setEntries.put ( entry.getKey (), value );
            }
        }
        
        msg.getValues ().put ( "unset", unsetEntries );
        msg.getValues ().put ( "set", setEntries );
        
        return msg;
    }
    
    public static int encodeIO ( EnumSet<IODirection> io )
    {
        int bits = 0;
        if ( io.contains ( IODirection.INPUT ) )
            bits |= 1;
        if ( io.contains ( IODirection.OUTPUT ) )
            bits |= 2;
        
       return bits;
    }
    
    public static EnumSet<IODirection> decodeIO ( int bits )
    {
        EnumSet<IODirection> ioDirection = EnumSet.noneOf ( IODirection.class );

        if ( (bits & 1) > 0 )
            ioDirection.add ( IODirection.INPUT );
        if ( (bits & 2) > 0 )
            ioDirection.add ( IODirection.OUTPUT );
        
        return ioDirection;
    }
}
