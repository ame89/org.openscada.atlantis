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

package org.openscada.ae.client.test.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;

public class QueryDataModel extends Observable
{
    public class UpdateData
    {
        public Set<EventData> added = new HashSet<EventData> ();

        public Set<EventData> removed = new HashSet<EventData> ();

        public Set<EventData> modified = new HashSet<EventData> ();
    }

    private String _unsubscribed = null;

    private Set<EventData> _events = new HashSet<EventData> ();

    public Set<EventData> getEvents ()
    {
        return Collections.unmodifiableSet ( this._events );
    }

    public void setEvents ( final Set<EventData> events )
    {
        setChanged ();
        this._events = events;
    }

    public void setUnsubscribed ( final String reason )
    {
        this._unsubscribed = reason;
    }

    public String getUnsubscribed ()
    {
        return this._unsubscribed;
    }

    public boolean isUnsubscribed ()
    {
        return this._unsubscribed != null;
    }

    public void addEvent ( final EventData event )
    {
        if ( this._events.add ( event ) )
        {
            setChanged ();
        }
    }

    public void removeEvent ( final EventData event )
    {
        if ( this._events.remove ( event ) )
        {
            setChanged ();
        }
    }

    public void notifyUpdates ( final UpdateData updateData )
    {
        notifyObservers ( updateData );
    }
}
