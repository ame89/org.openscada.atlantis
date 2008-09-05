/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2008 inavare GmbH (http://inavare.com)
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

import java.util.EnumSet;
import java.util.Map;

import org.openscada.core.Variant;
import org.openscada.da.core.IODirection;
import org.openscada.da.server.common.DataItemInformationBase;
import org.openscada.da.server.common.HiveServiceRegistry;
import org.openscada.da.server.common.chain.AttributeBinder;
import org.openscada.da.server.common.chain.BaseChainItemCommon;
import org.openscada.da.server.common.chain.ChainItem;
import org.openscada.da.server.common.chain.ChainProcessEntry;
import org.openscada.da.server.common.chain.DataItemInputChained;
import org.openscada.da.server.common.chain.MemoryItemChained;
import org.openscada.da.server.common.chain.item.LevelAlarmChainItem;
import org.openscada.da.server.common.chain.item.ManualErrorOverrideChainItem;
import org.openscada.da.server.common.chain.item.ManualOverrideChainItem;
import org.openscada.da.server.common.chain.item.ScaleInputItem;
import org.openscada.da.server.common.chain.item.SumAlarmChainItem;
import org.openscada.da.server.common.chain.item.SumErrorChainItem;
import org.openscada.da.server.common.factory.FactoryHelper;
import org.openscada.da.server.common.impl.HiveCommon;

public class MemoryChainedItem extends MemoryItemChained
{

    private class AddClassAttributeBinder implements AttributeBinder
    {
        private MemoryChainedItem _item = null;

        private IODirection _direction = null;

        public AddClassAttributeBinder ( final MemoryChainedItem item, final IODirection direction )
        {
            super ();
            this._item = item;
            this._direction = direction;
        }

        public void bind ( final Variant value ) throws Exception
        {
            if ( value != null )
            {
                if ( !value.isNull () )
                {
                    this._item.addChainElement ( this._direction, value.asString () );
                }
            }
        }

        public Variant getAttributeValue ()
        {
            return null;
        }

    }

    private class RemoveClassAttributeBinder implements AttributeBinder
    {
        private MemoryChainedItem _item = null;

        private IODirection _direction = null;

        public RemoveClassAttributeBinder ( final MemoryChainedItem item, final IODirection direction )
        {
            super ();
            this._item = item;
            this._direction = direction;
        }

        public void bind ( final Variant value ) throws Exception
        {
            if ( value != null )
            {
                if ( !value.isNull () )
                {
                    this._item.removeChainElement ( this._direction, value.asString () );
                }
            }
        }

        public Variant getAttributeValue ()
        {
            return null;
        }
    }

    private class InjectChainItem extends BaseChainItemCommon
    {
        private MemoryChainedItem _item = null;

        public InjectChainItem ( final HiveServiceRegistry serviceRegistry, final MemoryChainedItem item )
        {
            super ( serviceRegistry );
            this._item = item;

            addBinder ( "org.openscada.da.test.chain.input.add", new AddClassAttributeBinder ( item, IODirection.INPUT ) );
            addBinder ( "org.openscada.da.test.chain.input.remove", new RemoveClassAttributeBinder ( item, IODirection.INPUT ) );
            addBinder ( "org.openscada.da.test.chain.outpt.add", new AddClassAttributeBinder ( item, IODirection.OUTPUT ) );
            addBinder ( "org.openscada.da.test.chain.output.remove", new RemoveClassAttributeBinder ( item, IODirection.OUTPUT ) );
            setReservedAttributes ( "org.openscada.da.test.chain.value" );
        }

        @Override
        public boolean isPersistent ()
        {
            return false;
        }

        public void process ( final Variant value, final Map<String, Variant> attributes )
        {
            int i = 0;
            final StringBuilder str = new StringBuilder ();
            for ( final ChainProcessEntry item : this._item.getChainCopy () )
            {
                if ( i > 0 )
                {
                    str.append ( ", " );
                }

                str.append ( item.getWhat ().getClass ().getCanonicalName () );
                str.append ( "(" );
                str.append ( item.getWhen ().toString () );
                str.append ( ")" );

                i++;
            }
            attributes.put ( "org.openscada.da.test.chain.value", new Variant ( str.toString () ) );
        }

    }

    private final HiveCommon hive;

    public static void applyDefaultInputChain ( final HiveCommon hive, final DataItemInputChained item )
    {
        item.addChainElement ( IODirection.INPUT, new SumErrorChainItem ( hive ) );
        item.addChainElement ( IODirection.INPUT, new ScaleInputItem ( hive ) );
        item.addChainElement ( IODirection.INPUT, new ManualOverrideChainItem ( hive ) );
        item.addChainElement ( IODirection.INPUT, new ManualErrorOverrideChainItem () );
        item.addChainElement ( IODirection.INPUT, new LevelAlarmChainItem ( hive ) );
        item.addChainElement ( IODirection.INPUT, new SumAlarmChainItem ( hive ) );
    }

    public MemoryChainedItem ( final HiveCommon hive, final String id )
    {
        super ( new DataItemInformationBase ( id, EnumSet.of ( IODirection.INPUT, IODirection.OUTPUT ) ) );
        this.hive = hive;
        addChainElement ( IODirection.INPUT, new InjectChainItem ( hive, this ) );

        applyDefaultInputChain ( hive, this );
    }

    public void addChainElement ( final IODirection direction, final String className ) throws Exception
    {
        final Class<?> itemClass = Class.forName ( className );
        final Object o = itemClass.newInstance ();

        FactoryHelper.createChainItem ( this.hive, Class.forName ( className ) );

        addChainElement ( direction, (ChainItem)o );
    }

    synchronized public void removeChainElement ( final IODirection direction, final String className ) throws Exception
    {
        for ( final ChainProcessEntry entry : getChainCopy () )
        {
            if ( entry.getWhat ().getClass ().getCanonicalName ().equals ( className ) )
            {
                if ( entry.getWhen ().equals ( EnumSet.of ( direction ) ) )
                {
                    removeChainElement ( entry.getWhen (), entry.getWhat () );
                }
                return;
            }
        }
        throw new Exception ( "Item not found" );
    }

}
