/*
 * This file is part of the openSCADA project
 * 
 * Copyright (C) 2013 Jürgen Rose (cptmauli@googlemail.com)
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

package org.openscada.ae.server.storage.postgres.internal;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.openscada.ae.server.storage.postgres.NotSupportedException;
import org.openscada.core.Variant;
import org.openscada.core.VariantEditor;
import org.openscada.utils.filter.Assertion;
import org.openscada.utils.filter.Filter;
import org.openscada.utils.filter.FilterAssertion;
import org.openscada.utils.filter.FilterExpression;
import org.openscada.utils.filter.Operator;
import org.openscada.utils.str.StringHelper;

public class SqlConverter
{
    public static class SqlCondition
    {

        public String condition = "";

        public List<Serializable> parameters = new ArrayList<Serializable> ();

        @Override
        public String toString ()
        {
            return this.condition + " (params = " + this.parameters + ")";
        }
    }

    private static final DateFormat isoDateFormat = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss.S" );

    private static final Map<String, String> fixedFields = new HashMap<String, String> ( 3 );

    static
    {
        fixedFields.put ( "id", "ID" );
        fixedFields.put ( "sourceTimestamp", "SOURCE_TIMESTAMP" );
        fixedFields.put ( "entryTimestamp", "ENTRY_TIMESTAMP" );
    }

    public static SqlCondition toSql ( final String schema, final Filter filter ) throws NotSupportedException
    {
        final AtomicInteger j = new AtomicInteger ( 0 );
        final SqlCondition condition = new SqlCondition ();
        if ( filter.isEmpty () )
        {
            // pass
        }
        else if ( filter.isExpression () )
        {
            final SqlCondition expression = toSql ( schema, (FilterExpression)filter, j );
            condition.condition += " AND " + expression.condition;
            condition.parameters.addAll ( expression.parameters );
        }
        else if ( filter.isAssertion () )
        {
            final SqlCondition assertion = toSql ( schema, (FilterAssertion)filter, j );
            condition.condition += " AND " + assertion.condition;
            condition.parameters.addAll ( assertion.parameters );
        }
        else
        {
            // pass
        }
        return condition;
    }

    private static SqlCondition toSql ( final String schema, final FilterAssertion assertion, final AtomicInteger j ) throws NotSupportedException
    {
        SqlCondition result = null;
        if ( assertion.getAssertion () == Assertion.EQUALITY )
        {
            result = toSql ( schema, assertion.getAttribute (), "=", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.GREATEREQ )
        {
            result = toSql ( schema, assertion.getAttribute (), ">=", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.GREATERTHAN )
        {
            result = toSql ( schema, assertion.getAttribute (), ">", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.LESSEQ )
        {
            result = toSql ( schema, assertion.getAttribute (), "<=", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.LESSTHAN )
        {
            result = toSql ( schema, assertion.getAttribute (), "<", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.APPROXIMATE )
        {
            result = toSql ( schema, assertion.getAttribute (), "approximate", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.SUBSTRING )
        {
            result = toSql ( schema, assertion.getAttribute (), "like", assertion.getValue (), j );
        }
        else if ( assertion.getAssertion () == Assertion.PRESENCE )
        {
            result = toSql ( schema, assertion.getAttribute (), "presence", assertion.getValue (), j );
        }
        else
        {
            throw new NotSupportedException ();
        }
        return result;
    }

    private static SqlCondition toSql ( final String schema, final String attribute, final String op, final Object value, final AtomicInteger j ) throws NotSupportedException
    {
        final SqlCondition condition = new SqlCondition ();
        final Variant v = toVariant ( value );

        if ( fixedFields.keySet ().contains ( attribute ) )
        {
            final String column = fixedFields.get ( attribute );
            Serializable param = null;
            // convert parameters
            if ( "sourceTimestamp".equals ( attribute ) || "entryTimestamp".equals ( attribute ) )
            {
                try
                {
                    param = new Timestamp ( isoDateFormat.parse ( v.asString ( "" ) ).getTime () );
                }
                catch ( final ParseException e )
                {
                    param = v.asString ( "" );
                }
            }
            else if ( "id".equals ( attribute ) )
            {
                try
                {
                    param = UUID.fromString ( v.asString ( "" ) );
                }
                catch ( final IllegalArgumentException e )
                {
                    param = v.asString ( "" );
                }
            }
            // now build sql expression
            if ( "approximate".equals ( op ) )
            {
                throw new NotSupportedException ( String.format ( "approximate query for %s doesn't make any sense", attribute ) );
            }
            else if ( "like".equals ( op ) && ( param instanceof String ) )
            {
                condition.condition = String.format ( " lower(%s::TEXT) ilike lower(?::TEXT)", column );
                condition.parameters.add ( v.asString ( "" ) );
            }
            else if ( "presence".equals ( op ) )
            {
                throw new NotSupportedException ( String.format ( "test for null query for %s doesn't make any sense", attribute ) );
            }
            else if ( v.isInteger () || v.isLong () )
            {
                condition.condition = String.format ( " %s::BIGINT %s ?::BIGINT", column, op );
                condition.parameters.add ( v.asLong ( 0L ) );
            }
            else if ( v.isDouble () )
            {
                condition.condition = String.format ( " %s::DOUBLE %s ?::DOUBLE", column, op );
                condition.parameters.add ( v.asDouble ( 0.0 ) );
            }
            else if ( v.isBoolean () )
            {
                condition.condition = String.format ( " %s::BOOLEAN %s ?::BOOLEAN", column, op );
                condition.parameters.add ( v.asBoolean ( false ) );
            }
            else if ( v.isNull () )
            {
                throw new NotSupportedException ( String.format ( "test for null query for %s doesn't make any sense", attribute ) );
            }
            else if ( param instanceof String )
            {
                condition.condition = String.format ( " %s::TEXT %s ?::TEXT", column, op );
                condition.parameters.add ( v.asString ( "" ) );
            }
            else
            {
                condition.condition = String.format ( " %s %s ?", column, op );
                condition.parameters.add ( param );
            }
        }
        else
        {
            // now build sql expression
            if ( "approximate".equals ( op ) )
            {
                condition.condition = String.format ( " dmetaphone(openscada_variant_to_string(openscada_ae_extract_field(DATA, ?))) = dmetaphone(?) OR dmetaphone_alt(openscada_variant_to_string(openscada_ae_extract_field(DATA, ?))) = dmetaphone_alt(?)", attribute );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asString ( "" ) );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asString ( "" ) );
            }
            else if ( "like".equals ( op ) )
            {
                condition.condition = String.format ( " lower(openscada_variant_to_string(openscada_ae_extract_field(DATA, ?))) ilike lower(?)" );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asString ( "" ) );
            }
            else if ( "presence".equals ( op ) )
            {
                condition.condition = String.format ( " openscada_ae_extract_field(DATA, ?) IS NOT NULL" );
                condition.parameters.add ( attribute );
            }
            else if ( v.isInteger () || v.isLong () )
            {
                condition.condition = String.format ( "  openscada_variant_to_long(openscada_ae_extract_field(DATA, ?)) %s ?", op );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asLong ( 0L ) );
            }
            else if ( v.isDouble () )
            {
                condition.condition = String.format ( "  openscada_variant_to_double(openscada_ae_extract_field(DATA, ?)) %s ?", op );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asDouble ( 0.0 ) );
            }
            else if ( v.isBoolean () )
            {
                condition.condition = String.format ( "  openscada_variant_to_boolean(openscada_ae_extract_field(DATA, ?)) %s ?", op );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asBoolean ( false ) );
            }
            else if ( v.isNull () )
            {
                condition.condition = String.format ( " openscada_ae_extract_field(DATA, ?) IS NULL" );
                condition.parameters.add ( attribute );
            }
            else
            {
                condition.condition = String.format ( "  lower(openscada_variant_to_string(openscada_ae_extract_field(DATA, ?))) %s ?", op );
                condition.parameters.add ( attribute );
                condition.parameters.add ( v.asString ( "" ) );
            }
        }
        j.getAndIncrement ();
        return condition;
    }

    @SuppressWarnings ( "unchecked" )
    private static Variant toVariant ( Object value )
    {
        if ( value instanceof List<?> )
        {
            value = StringHelper.join ( (List<String>)value, "%" ).replaceAll ( "\\?", "_" );
        }
        if ( ( value instanceof String ) && ( (String)value ).contains ( "#" ) )
        {
            try
            {
                return VariantEditor.toVariant ( (String)value );
            }
            catch ( final IllegalArgumentException e )
            {
                // pass
            }
        }
        return Variant.valueOf ( value );
    }

    static SqlCondition toSql ( final String schema, final FilterExpression expression, final AtomicInteger j ) throws NotSupportedException
    {
        final SqlCondition result = new SqlCondition ();
        result.condition = "(";
        int i = 0;
        for ( final Filter term : expression.getFilterSet () )
        {
            if ( i > 0 )
            {
                if ( expression.getOperator () == Operator.AND )
                {
                    result.condition += " AND ";
                }
                else if ( expression.getOperator () == Operator.OR )
                {
                    result.condition += " OR ";
                }
            }
            if ( term.isExpression () )
            {
                final SqlCondition r = toSql ( schema, (FilterExpression)term, j );
                result.condition += r.condition;
                result.parameters.addAll ( r.parameters );
            }
            else if ( term.isAssertion () )
            {
                final SqlCondition r = toSql ( schema, (FilterAssertion)term, j );
                result.condition += r.condition;
                result.parameters.addAll ( r.parameters );
            }
            i++;
        }
        if ( expression.getOperator () == Operator.NOT )
        {
            result.condition = "NOT " + result.condition;
        }
        result.condition += ")";
        return result;
    }
}
