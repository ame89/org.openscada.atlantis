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

package org.openscada.hd.exporter.http;

import java.io.Serializable;
import java.util.Date;

public class DataPoint implements Serializable
{
    private static final long serialVersionUID = -3829410505213908520L;

    private Date timestamp;

    private Double value;

    private Double quality;

    private Double manual;

    public DataPoint ()
    {
    }

    public Date getTimestamp ()
    {
        return this.timestamp;
    }

    public void setTimestamp ( final Date timestamp )
    {
        this.timestamp = timestamp;
    }

    public Double getValue ()
    {
        return this.value;
    }

    public void setValue ( final Double value )
    {
        this.value = value;
    }

    public Double getQuality ()
    {
        return this.quality;
    }

    public void setQuality ( final Double quality )
    {
        this.quality = quality;
    }

    public Double getManual ()
    {
        return this.manual;
    }

    public void setManual ( final Double manual )
    {
        this.manual = manual;
    }

    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( this.manual == null ? 0 : this.manual.hashCode () );
        result = prime * result + ( this.quality == null ? 0 : this.quality.hashCode () );
        result = prime * result + ( this.timestamp == null ? 0 : this.timestamp.hashCode () );
        result = prime * result + ( this.value == null ? 0 : this.value.hashCode () );
        return result;
    }

    @Override
    public boolean equals ( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass () != obj.getClass () )
        {
            return false;
        }
        final DataPoint other = (DataPoint)obj;
        if ( this.manual == null )
        {
            if ( other.manual != null )
            {
                return false;
            }
        }
        else if ( !this.manual.equals ( other.manual ) )
        {
            return false;
        }
        if ( this.quality == null )
        {
            if ( other.quality != null )
            {
                return false;
            }
        }
        else if ( !this.quality.equals ( other.quality ) )
        {
            return false;
        }
        if ( this.timestamp == null )
        {
            if ( other.timestamp != null )
            {
                return false;
            }
        }
        else if ( !this.timestamp.equals ( other.timestamp ) )
        {
            return false;
        }
        if ( this.value == null )
        {
            if ( other.value != null )
            {
                return false;
            }
        }
        else if ( !this.value.equals ( other.value ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString ()
    {
        return "DataPoint [manual=" + this.manual + ", quality=" + this.quality + ", timestamp=" + this.timestamp + ", value=" + this.value + "]";
    }
}
