package org.openscada.spring.client.value;

import org.openscada.core.Variant;
import org.openscada.da.client.DataItemValue;

public abstract class AbstractBaseValueSource implements ValueSource
{
    public Value<Variant> getVariantValue () throws ValueSourceException
    {
        final DataItemValue value = getValue ();

        if ( value == null )
        {
            throw new ValueSourceException ( "No value from source" );
        }

        if ( !value.isConnected () )
        {
            throw new ValueSourceException ( "Value is not connected" );
        }

        if ( value.isError () )
        {
            throw new ValueSourceException ( "Error flag is set for value" );
        }

        return new Value<Variant> ( value.getValue (), value.isManual (), value.isAlarm (), value.getTimestamp () );
    }

    // boolean

    public Value<Boolean> getBooleanValue ( final Boolean defaultValue )
    {
        try
        {
            return getBooleanValue ();
        }
        catch ( final ValueSourceException e )
        {
            return Value.createDefault ( defaultValue );
        }
    }

    public Value<Boolean> getBooleanValue () throws ValueSourceException
    {
        final Value<Variant> value = getVariantValue ();

        final Boolean data = value.getValue ().asBoolean ( null );

        if ( data == null )
        {
            throw new ValueSourceException ( "Null value" );
        }

        return new Value<Boolean> ( data, value );
    }

    public boolean getBoolean () throws ValueSourceException
    {
        return getBooleanValue ().getValue ();
    }

    public Boolean getBoolean ( final Boolean defaultValue )
    {
        return getBooleanValue ( defaultValue ).getValue ();
    }

    // double

    public Value<Double> getDoubleValue ( final Double defaultValue )
    {
        try
        {
            return getDoubleValue ();
        }
        catch ( final ValueSourceException e )
        {
            return Value.createDefault ( defaultValue );
        }
    }

    public Value<Double> getDoubleValue () throws ValueSourceException
    {
        final Value<Variant> value = getVariantValue ();

        try
        {
            final Double data = value.getValue ().asDouble ();
            return new Value<Double> ( data, value );
        }
        catch ( final Exception e )
        {
            throw new ValueSourceException ( "Failed to convert value", e );
        }

    }

    public double getDouble () throws ValueSourceException
    {
        return getDoubleValue ().getValue ();
    }

    public Double getDouble ( final Double defaultValue )
    {
        return getDoubleValue ( defaultValue ).getValue ();
    }
}