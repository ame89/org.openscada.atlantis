/*
 * This file is part of the openSCADA project
 * 
 * Copyright (C) 2006-2010 TH4 SYSTEMS GmbH (http://th4-systems.com)
 *
 * openSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * openSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with openSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.server.modbus.driver;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.xmlbeans.XmlException;
import org.openscada.da.modbus.configuration.DeviceType;
import org.openscada.da.modbus.configuration.ItemType;
import org.openscada.da.modbus.configuration.ModbusSlave;
import org.openscada.da.modbus.configuration.RootDocument;
import org.openscada.da.server.browser.common.FolderCommon;
import org.openscada.da.server.common.chain.storage.ChainStorageServiceHelper;
import org.openscada.da.server.common.impl.HiveCommon;
import org.openscada.da.server.modbus.ModbusConstants;
import org.openscada.da.server.modbus.ModbusDeviceType;
import org.openscada.da.server.modbus.ModbusRegisterType;
import org.openscada.da.server.modbus.ModbusType;
import org.openscada.da.server.modbus.Rs232Information;
import org.openscada.da.server.modbus.Rs232Information.Parity;
import org.openscada.utils.concurrent.NamedThreadFactory;
import org.w3c.dom.Node;

public class Hive extends HiveCommon
{
    private final ScheduledExecutorService scheduler;

    public Hive () throws XmlException, IOException
    {
        this ( RootDocument.Factory.parse ( new File ( "modbusConfiguration.xml" ) ) );
    }

    public Hive ( final Node node ) throws XmlException
    {
        this ( RootDocument.Factory.parse ( node ) );
    }

    public Hive ( final RootDocument document )
    {
        super ();

        ChainStorageServiceHelper.registerDefaultPropertyService ( this );

        this.scheduler = Executors.newSingleThreadScheduledExecutor ( new NamedThreadFactory ( "modbusScheduler" ) );

        final FolderCommon rootFolder = new FolderCommon ();
        setRootFolder ( rootFolder );

        for ( final DeviceType device : document.getRoot ().getDevices ().getDeviceList () )
        {
            final InetSocketAddress address = new InetSocketAddress ( device.getHost (), device.getPort () );
            final Rs232Information rs232Information = new Rs232Information ( device.getBaudRate (), Parity.valueOf ( device.getParity ().toString () ), device.getDataBits (), device.getStopBits () );
            // convert both values to nanoseconds! 
            final long interFrameDelay;
            if ( device.getInterFrameDelay () > 10 )
            {
                interFrameDelay = new Double ( device.getInterFrameDelay () ).longValue () * 1000;
            }
            else
            {
                interFrameDelay = new Double ( rs232Information.getCharLengthAsNano () * device.getInterFrameDelay () ).longValue ();
            }
            final long interCharacterTimeout;
            if ( device.getInterCharacterTimeout () > 10 )
            {
                interCharacterTimeout = new Double ( device.getInterCharacterTimeout () ).longValue () * 1000;
            }
            else
            {
                interCharacterTimeout = new Double ( rs232Information.getCharLengthAsNano () * device.getInterCharacterTimeout () ).longValue ();
            }
            final Map<Byte, SlaveDevice> slaves = toSlaveList ( device.getSlaveList () );
            ModbusDeviceType deviceType = ModbusDeviceType.RTU;
            for ( final ModbusDeviceType t : ModbusDeviceType.values () )
            {
                if ( t.name ().equals ( device.getProtocol ().toString () ) )
                {
                    deviceType = t;
                }
            }
            new DeviceWrapper ( this, device.getId (), this.scheduler, rootFolder, address, deviceType, rs232Information, interFrameDelay, interCharacterTimeout, slaves );
        }
    }

    @Override
    public String getHiveId ()
    {
        return "org.openscada.da.server.modbus";
    }

    private Map<Byte, SlaveDevice> toSlaveList ( final List<ModbusSlave> slaveList ) throws IllegalArgumentException
    {
        final Map<Byte, SlaveDevice> result = new HashMap<Byte, SlaveDevice> ();
        for ( final ModbusSlave modbusSlave : slaveList )
        {
            if ( modbusSlave.getId () < ModbusConstants.MIN_SLAVE_ID || modbusSlave.getId () > ModbusConstants.MAX_SLAVE_ID )
            {
                throw new IllegalArgumentException ( "id of slave must be between " + ModbusConstants.MIN_SLAVE_ID + "  and " + ModbusConstants.MAX_SLAVE_ID );
            }
            final SlaveDevice slaveDevice = new SlaveDevice ( (byte) ( modbusSlave.getId () & 0xFF ), modbusSlave.getName (), modbusSlave.getDiscreteInputOffset (), modbusSlave.getCoilOffset (), modbusSlave.getInputRegisterOffset (), modbusSlave.getHoldingRegisterOffset () );
            if ( result.containsValue ( slaveDevice ) )
            {
                throw new IllegalArgumentException ( "slave with id " + slaveDevice + " is already configured" );
            }

            for ( final ItemType item : modbusSlave.getDiscreteInputList () )
            {
                configureItems ( ModbusRegisterType.DiscreteInputs, slaveDevice, item );
            }
            for ( final ItemType item : modbusSlave.getCoilList () )
            {
                configureItems ( ModbusRegisterType.Coils, slaveDevice, item );
            }
            for ( final ItemType item : modbusSlave.getInputRegisterList () )
            {
                configureItems ( ModbusRegisterType.InputRegisters, slaveDevice, item );
            }
            for ( final ItemType item : modbusSlave.getHoldingRegisterList () )
            {
                configureItems ( ModbusRegisterType.HoldingRegisters, slaveDevice, item );
            }

            result.put ( (byte) ( modbusSlave.getId () & 0xFF ), slaveDevice );
        }
        return result;
    }

    private void configureItems ( final ModbusRegisterType registerType, final SlaveDevice slaveDevice, final ItemType config )
    {
        final int startAddress = parseAddress ( config );
        final ModbusType type;
        if ( "DEFAULT".equals ( config.getType ().toString () ) )
        {
            switch ( registerType )
            {
                case InputRegisters:
                case HoldingRegisters:
                    type = ModbusType.INT16;
                    break;
                default:
                    type = ModbusType.BOOLEAN;
            }
        }
        else
        {
            type = ModbusType.valueOf ( String.valueOf ( config.getType () ) );
        }
        final int baseOffset;
        switch ( registerType )
        {
            case DiscreteInputs:
                baseOffset = slaveDevice.getDiscreteInputOffset ();
                break;
            case Coils:
                baseOffset = slaveDevice.getCoilOffset ();
                break;
            case InputRegisters:
                baseOffset = slaveDevice.getInputRegisterOffset ();
                break;
            case HoldingRegisters:
                baseOffset = slaveDevice.getHoldingRegisterOffset ();
                break;
            default:
                baseOffset = 0;
                break;
        }
        checkConstraints ( config, startAddress + baseOffset, type, registerType );
        int currentAddress = startAddress;
        for ( int offset = 0; offset < config.getQuantity (); offset++ )
        {
            final String name = config.getQuantity () > 1 ? config.getName () + String.format ( ".%04d", currentAddress ) : config.getName ();
            slaveDevice.addTag ( registerType, name, currentAddress, type, config.getPriority () );
            currentAddress = currentAddress + type.getSize ();
        }
    }

    private int parseAddress ( final ItemType config )
    {
        final int startAddress;
        if ( config.getStartAddress ().startsWith ( "0x" ) )
        {
            startAddress = Integer.parseInt ( config.getStartAddress ().replace ( "0x", "" ), 16 );
        }
        else
        {
            startAddress = Integer.parseInt ( config.getStartAddress () );
        }
        return startAddress;
    }

    private void checkConstraints ( final ItemType config, final int startAddress, final ModbusType type, final ModbusRegisterType registerType ) throws IllegalArgumentException
    {
        if ( startAddress < 0 || startAddress > 0xffff )
        {
            throw new IllegalArgumentException ( "address not in range" );
        }
        if ( config.getPriority () < 1 )
        {
            throw new IllegalArgumentException ( "priority must be larger than 0" );
        }
        if ( config.getQuantity () < 1 )
        {
            throw new IllegalArgumentException ( "quantity must be larger than 0" );
        }
        if ( ( registerType == ModbusRegisterType.Coils || registerType == ModbusRegisterType.DiscreteInputs ) && type != ModbusType.BOOLEAN )
        {
            throw new IllegalArgumentException ( "type must be BOOLEAN" );
        }
    }
}
