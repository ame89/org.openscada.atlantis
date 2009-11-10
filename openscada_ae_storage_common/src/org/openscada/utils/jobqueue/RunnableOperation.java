/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2009 inavare GmbH (http://inavare.com)
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

package org.openscada.utils.jobqueue;

import org.openscada.utils.jobqueue.OperationManager.Handle;

public class RunnableOperation implements Operation
{
    private Runnable runnable = null;

    private Thread thread = null;

    public RunnableOperation ( Runnable runnable )
    {
        this.runnable = runnable;
    }

    public void cancel () throws CancelNotSupportedException
    {
        throw new CancelNotSupportedException ();
    }

    public void start ( final Handle handle )
    {
        if ( runnable == null )
            return;

        thread = new Thread ( new Runnable () {

            public void run ()
            {
                try
                {
                    runnable.run ();
                }
                finally
                {
                    handle.completed ();
                }
            }
        }, "RunableOperationThread" );
        thread.start ();
    }

}