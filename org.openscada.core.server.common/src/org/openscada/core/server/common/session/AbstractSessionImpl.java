/*
 * This file is part of the OpenSCADA project
 * 
 * Copyright (C) 2006-2012 TH4 SYSTEMS GmbH (http://th4-systems.com)
 * Copyright (C) 2013 Jens Reimann (ctron@dentrassi.de)
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

package org.openscada.core.server.common.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.openscada.core.server.Session;
import org.openscada.sec.UserInformation;
import org.openscada.sec.callback.Callback;
import org.openscada.sec.callback.CallbackHandler;
import org.openscada.utils.concurrent.NotifyFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSessionImpl implements Session
{
    private final static Logger logger = LoggerFactory.getLogger ( AbstractSessionImpl.class );

    private final UserInformation userInformation;

    private final Map<String, String> properties;

    private volatile Set<String> privileges = new HashSet<String> ();

    private final Set<SessionListener> listeners = new CopyOnWriteArraySet<Session.SessionListener> ();

    private final Set<DisposeListener> disposeListeners = new LinkedHashSet<DisposeListener> ();

    private boolean disposed;

    private final Set<SessionCallbackHandler> sessionCallbackHandlers = new HashSet<AbstractSessionImpl.SessionCallbackHandler> ();

    public interface DisposeListener
    {
        public void disposed ();
    }

    public AbstractSessionImpl ( final UserInformation userInformation, final Map<String, String> properties )
    {
        this.userInformation = userInformation;
        this.properties = properties != null ? new HashMap<String, String> ( properties ) : Collections.<String, String> emptyMap ();
    }

    @Override
    public Map<String, String> getProperties ()
    {
        return Collections.unmodifiableMap ( this.properties );
    }

    public UserInformation getUserInformation ()
    {
        return this.userInformation;
    }

    public void dispose ()
    {
        if ( this.disposed )
        {
            return;
        }

        this.disposed = true;

        final HashSet<SessionCallbackHandler> handlers;
        synchronized ( this.sessionCallbackHandlers )
        {
            handlers = new HashSet<SessionCallbackHandler> ( this.sessionCallbackHandlers );
            this.sessionCallbackHandlers.clear ();
        }

        for ( final SessionCallbackHandler handler : handlers )
        {
            handler.dispose ();
        }

        for ( final DisposeListener listener : this.disposeListeners )
        {
            try
            {
                listener.disposed ();
            }
            catch ( final Exception e )
            {
                logger.warn ( "Failed to handle dispose", e );
            }
        }
    }

    public void addDisposeListener ( final DisposeListener disposeListener )
    {
        this.disposeListeners.add ( disposeListener );
    }

    public void removeDisposeListener ( final DisposeListener disposeListener )
    {
        this.disposeListeners.remove ( disposeListener );
    }

    @Override
    public void addSessionListener ( final SessionListener listener )
    {
        if ( this.listeners.add ( listener ) )
        {
            listener.privilegeChange ();
        }
    }

    @Override
    public void removeSessionListener ( final SessionListener listener )
    {
        this.listeners.remove ( listener );
    }

    protected void firePrivilegeChange ()
    {
        for ( final SessionListener listener : this.listeners )
        {
            listener.privilegeChange ();
        }
    }

    public void setPrivileges ( final Set<String> privileges )
    {
        this.privileges = privileges;
        firePrivilegeChange ();
    }

    @Override
    public Set<String> getPrivileges ()
    {
        return this.privileges;
    }

    private class SessionCallbackHandler implements CallbackHandler
    {
        private final CallbackHandler callbackHandler;

        private NotifyFuture<Callback[]> future;

        public SessionCallbackHandler ( final CallbackHandler callbackHandler )
        {
            this.callbackHandler = callbackHandler;
        }

        @Override
        public NotifyFuture<Callback[]> performCallback ( final Callback[] callbacks )
        {
            this.future = this.callbackHandler.performCallback ( callbacks );
            this.future.addListener ( new Runnable () {

                @Override
                public void run ()
                {
                    removeCallbackHandler ( SessionCallbackHandler.this );
                }
            } );
            return this.future;
        }

        public void dispose ()
        {
            if ( this.future != null )
            {
                this.future.cancel ( false );
            }
        }

    }

    /**
     * @param callbackHandler
     * @since 1.1
     */
    public CallbackHandler wrapCallbackHandler ( final CallbackHandler callbackHandler )
    {
        if ( callbackHandler == null || this.disposed )
        {
            return null;
        }

        final SessionCallbackHandler sch = new SessionCallbackHandler ( callbackHandler );
        addCallbackHandler ( sch );
        return sch;
    }

    private void addCallbackHandler ( final SessionCallbackHandler sessionCallbackHandler )
    {
        synchronized ( this.sessionCallbackHandlers )
        {
            this.sessionCallbackHandlers.add ( sessionCallbackHandler );
        }
    }

    private void removeCallbackHandler ( final SessionCallbackHandler sessionCallbackHandler )
    {
        synchronized ( this.sessionCallbackHandlers )
        {
            this.sessionCallbackHandlers.remove ( sessionCallbackHandler );
        }
    }
}
