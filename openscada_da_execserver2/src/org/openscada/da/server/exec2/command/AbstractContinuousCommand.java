/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2009 inavare GmbH (http://inavare.com)
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

package org.openscada.da.server.exec2.command;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.openscada.core.Variant;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.DataItemCommand;
import org.openscada.da.server.common.chain.DataItemInputChained;
import org.openscada.da.server.common.item.factory.FolderItemFactory;
import org.openscada.da.server.exec2.Hive;
import org.openscada.da.server.exec2.splitter.SplitResult;
import org.openscada.da.server.exec2.splitter.Splitter;
import org.openscada.da.server.exec2.util.DefaultItemFactory;
import org.openscada.da.server.exec2.util.StreamProcessor;

public abstract class AbstractContinuousCommand implements ContinuousCommand, Runnable
{
    private static Logger logger = Logger.getLogger ( AbstractContinuousCommand.class );

    public enum State
    {
        STOPPED,
        STARTING,
        STARTED,
        DIED
    }

    private final ProcessConfiguration processConfiguration;

    private final int restartDelay;

    private final int maxInputBuffer;

    private final String id;

    private Thread thread;

    private boolean running;

    private long lastStartTimestamp = 0;

    protected FolderItemFactory itemFactory;

    private DataItemInputChained stateItem;

    private State state = State.STOPPED;

    private DataItemInputChained failedItem;

    private final Splitter splitter;

    private Thread readerStdThread;

    private Thread readerErrThread;

    private String inputBuffer;

    private DataItemInputChained pidItem;

    private Process process;

    private DataItemCommand killItem;

    private Thread shutdownHook;

    public AbstractContinuousCommand ( final String id, final ProcessConfiguration processConfiguration, final int restartDelay, final int maxInputBuffer, final Splitter splitter )
    {
        this.id = id;
        this.processConfiguration = processConfiguration;
        this.restartDelay = restartDelay;
        this.maxInputBuffer = maxInputBuffer;
        this.splitter = splitter;

    }

    public void start ( final Hive hive, final FolderCommon parentFolder )
    {
        this.itemFactory = new DefaultItemFactory ( hive, parentFolder, this.id, this.id );

        this.stateItem = this.itemFactory.createInput ( "state" );
        this.stateItem.updateData ( new Variant ( this.state.toString () ), null, null );
        this.failedItem = this.itemFactory.createInput ( "lastFailure" );

        this.pidItem = this.itemFactory.createInput ( "pid" );

        this.killItem = this.itemFactory.createCommand ( "kill" );
        this.killItem.addListener ( new DataItemCommand.Listener () {

            public void command ( final Variant value )
            {
                AbstractContinuousCommand.this.killProcess ();
            }
        } );

        // add the shutdown hook
        this.shutdownHook = new Thread ( new Runnable () {

            public void run ()
            {
                AbstractContinuousCommand.this.killProcess ();
            }
        }, "ProcessKiller/" + this.id );
        Runtime.getRuntime ().addShutdownHook ( this.shutdownHook );

        // start the process
        this.thread = new Thread ( this, "ProcessRunner/" + this.id );
        this.running = true;
        this.thread.setDaemon ( true );
        this.thread.start ();

    }

    public void stop ()
    {
        cancelProcess ();
        this.itemFactory.dispose ();

        // remove the shutdown hook
        Runtime.getRuntime ().removeShutdownHook ( this.shutdownHook );
        this.shutdownHook = null;
    }

    /**
     * The should cancel the process
     */
    private void cancelProcess ()
    {
        this.running = false;
        killProcess ();
    }

    private void killProcess ()
    {
        final Process process = this.process;
        if ( process != null )
        {
            logger.warn ( "Killing process" );
            process.destroy ();
        }
    }

    public void run ()
    {
        try
        {
            while ( this.running )
            {
                try
                {
                    if ( shouldStartProcess () )
                    {
                        startProcess ();
                        logger.info ( "Process terminated" );
                        processFailed ( null );
                    }
                }
                catch ( final Throwable e )
                {
                    logger.info ( "Process failed", e );
                    processFailed ( e );
                }

                // sleep
                try
                {
                    Thread.sleep ( 1000 );
                }
                catch ( final InterruptedException e )
                {
                }
            }
        }
        finally
        {
            logger.info ( "Process was stopped" );
            setCurrentState ( State.STOPPED );
        }
    }

    private boolean shouldStartProcess ()
    {
        return System.currentTimeMillis () - this.lastStartTimestamp > this.restartDelay;
    }

    protected void processFailed ( final Throwable e )
    {
        this.process = null;
        setCurrentState ( State.DIED );
        if ( e != null )
        {
            this.failedItem.updateData ( new Variant ( e.getMessage () ), null, null );
        }

        this.pidItem.updateData ( new Variant (), null, null );

    }

    protected void processStarted ( final Process process )
    {
        this.process = process;
    }

    private void startProcess () throws Exception
    {
        this.lastStartTimestamp = System.currentTimeMillis ();

        setCurrentState ( State.STARTING );

        final ProcessBuilder processBuilder = this.processConfiguration.asProcessBuilder ();

        this.inputBuffer = "";

        final Process process = processBuilder.start ();

        try
        {
            logger.info ( "Processes started..." );

            setCurrentState ( State.STARTED );

            this.pidItem.updateData ( new Variant ( process.toString () ), null, null );

            // Starting the stream reader
            processStarted ( process );
            final StreamProcessor stdProcessor = new StreamProcessor ( process.getInputStream (), this.maxInputBuffer ) {

                @Override
                protected void handleInput ( final String input )
                {
                    AbstractContinuousCommand.this.handleStdInput ( input );
                }
            };

            this.readerStdThread = new Thread ( stdProcessor, "StreamStdReader/" + this.id );
            this.readerStdThread.setDaemon ( false );
            this.readerStdThread.start ();

            final StreamProcessor errProcessor = new StreamProcessor ( process.getErrorStream (), this.maxInputBuffer ) {

                @Override
                protected void handleInput ( final String input )
                {
                    AbstractContinuousCommand.this.handleErrInput ( input );
                }
            };
            this.readerErrThread = new Thread ( errProcessor, "StreamErrReader/" + this.id );
            this.readerErrThread.setDaemon ( false );
            this.readerErrThread.start ();

            process.waitFor ();

            setCurrentState ( State.DIED );
        }
        finally
        {
            closeProcess ( process );
        }
    }

    private void closeProcess ( final Process process )
    {
        try
        {
            process.getErrorStream ().close ();
        }
        catch ( final IOException e )
        {
            logger.error ( "Failed to close error stream", e );
            e.printStackTrace ();
        }
        try
        {
            process.getInputStream ().close ();
        }
        catch ( final IOException e )
        {
            logger.error ( "Failed to close input stream", e );
        }
        try
        {
            process.getOutputStream ().close ();
        }
        catch ( final IOException e )
        {
            logger.error ( "Failed to close output stream", e );
        }
    }

    private void handleErrInput ( final String buffer )
    {
        logger.info ( "Error Input: " + buffer );
    }

    private void handleStdInput ( final String buffer )
    {
        if ( this.inputBuffer == null )
        {
            this.inputBuffer = buffer;
        }
        else
        {
            this.inputBuffer += buffer;
        }

        final SplitResult result = this.splitter.split ( this.inputBuffer );
        if ( result != null )
        {
            for ( final String line : result.getLines () )
            {
                handleStdLine ( line );
            }

            this.inputBuffer = result.getRemainingBuffer ();
            if ( this.inputBuffer == null )
            {
                this.inputBuffer = "";
            }
        }

        logger.debug ( "Input buffer size: " + this.inputBuffer.length () );
        if ( this.inputBuffer.length () > this.maxInputBuffer )
        {
            logger.warn ( "Input buffer is too big. Killing process" );
            killProcess ();
        }
    }

    protected abstract void handleStdLine ( String line );

    public void setCurrentState ( final State state )
    {
        this.state = state;
        this.stateItem.updateData ( new Variant ( state.toString () ), null, null );
    }
}
