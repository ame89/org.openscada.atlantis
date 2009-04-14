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

import java.util.Collection;

import org.apache.log4j.Logger;
import org.openscada.core.Variant;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.chain.DataItemInputChained;
import org.openscada.da.server.exec2.Hive;
import org.openscada.da.server.exec2.extractor.Extractor;
import org.openscada.da.server.exec2.splitter.Splitter;

public class ExtractorContinuousCommand extends AbstractContinuousCommand
{
    private static final Logger logger = Logger.getLogger ( ExtractorContinuousCommand.class );

    private int currentLineCount;

    private final Collection<Extractor> extrators;

    private final int ignoreStartLines;

    private DataItemInputChained lastInput;

    public ExtractorContinuousCommand ( final String id, final ProcessConfiguration processConfiguration, final int restartDelay, final int maxInputBuffer, final int ignoreStartLines, final Splitter splitter, final Collection<Extractor> extractors )
    {
        super ( id, processConfiguration, restartDelay, maxInputBuffer, splitter );
        this.extrators = extractors;
        this.ignoreStartLines = ignoreStartLines;
    }

    @Override
    public void start ( final Hive hive, final FolderCommon parentFolder )
    {
        super.start ( hive, parentFolder );

        this.lastInput = this.itemFactory.createInput ( "lastInput" );

        for ( final Extractor extractor : this.extrators )
        {
            extractor.register ( hive, this.itemFactory );
        }
    }

    @Override
    public void stop ()
    {
        for ( final Extractor extractor : this.extrators )
        {
            extractor.unregister ();
        }

        super.stop ();
    }

    @Override
    protected void processFailed ( final Throwable e )
    {
        super.processFailed ( e );
        final ExecutionResult result = new ExecutionResult ();
        result.setExecutionError ( new RuntimeException ( "Process failed", e ) );
        for ( final Extractor extractor : this.extrators )
        {
            extractor.process ( result );
        }

    }

    @Override
    protected void handleStdLine ( final String line )
    {
        logger.debug ( "Got line: " + line );
        this.lastInput.updateData ( new Variant ( line ), null, null );

        this.currentLineCount++;
        if ( this.currentLineCount > this.ignoreStartLines )
        {
            final ExecutionResult result = new ExecutionResult ();
            result.setOutput ( line );
            for ( final Extractor extractor : this.extrators )
            {
                extractor.process ( result );
            }
        }
    }

    @Override
    protected void processStarted ( final Process process )
    {
        this.currentLineCount = 0;
        super.processStarted ( process );
    }

}
