/*
 * This file is part of the OpenSCADA project
 * 
 * Copyright (C) 2013 Jens Reimann (ctron@dentrassid.de)
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

package org.openscada.sec.authz.signature;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;
import javax.xml.crypto.KeySelectorResult;

import org.openscada.sec.AuthenticationImplementation;
import org.openscada.sec.AuthorizationResult;
import org.openscada.sec.audit.AuditLogService;
import org.openscada.sec.authz.AuthorizationContext;
import org.openscada.sec.authz.AuthorizationRule;
import org.openscada.sec.authz.signature.RequestValidator.Result;
import org.openscada.sec.callback.Callback;
import org.openscada.sec.callback.Callbacks;
import org.openscada.sec.callback.XMLSignatureCallback;
import org.openscada.utils.concurrent.InstantErrorFuture;
import org.openscada.utils.concurrent.NotifyFuture;
import org.openscada.utils.concurrent.TransformResultFuture;
import org.openscada.utils.script.ScriptExecutor;
import org.openscada.utils.statuscodes.SeverityLevel;
import org.openscada.utils.statuscodes.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * @since 1.1
 */
public class RequestSignatureRuleImpl implements AuthorizationRule
{

    private final static Logger logger = LoggerFactory.getLogger ( RequestSignatureRuleImpl.class );

    private static StatusCode VERIFY_NO_SIGNATURE = new StatusCode ( "OSSEC", "XMLSIG", 1, SeverityLevel.ERROR );

    private static StatusCode VERIFY_SIGNATURE_INVALID = new StatusCode ( "OSSEC", "XMLSIG", 2, SeverityLevel.ERROR );

    private final SignatureRequestBuilder builder;

    private final RequestValidator validator;

    private final AuditLogService auditLogService;

    private final boolean indent;

    private final ScriptExecutor postProcessor;

    private final AuthenticationImplementation authenticator;

    public RequestSignatureRuleImpl ( final SignatureRequestBuilder builder, final RequestValidator validator, final AuditLogService auditLogService, final boolean indent, final ScriptExecutor postProcessor, final AuthenticationImplementation authenticator )
    {
        this.builder = builder;
        this.validator = validator;
        this.auditLogService = auditLogService;
        this.indent = indent;
        this.postProcessor = postProcessor;
        this.authenticator = authenticator;
    }

    @Override
    public void dispose ()
    {
    }

    @Override
    public NotifyFuture<AuthorizationResult> authorize ( final AuthorizationContext context )
    {
        final Document doc = this.builder.buildFromRequest ( context.getRequest () );

        NotifyFuture<Callback[]> future;
        try
        {
            future = Callbacks.callback ( context.getCallbackHandler (), new XMLSignatureCallback ( this.builder.toString ( doc, this.indent ) ) );
        }
        catch ( final Exception e )
        {
            return new InstantErrorFuture<AuthorizationResult> ( e );
        }

        return new TransformResultFuture<Callback[], AuthorizationResult> ( future ) {

            @Override
            protected AuthorizationResult transform ( final Callback[] from ) throws Exception
            {
                return validateCallback ( context, doc, (XMLSignatureCallback)from[0] );
            }
        };
    }

    protected AuthorizationResult validateCallback ( final AuthorizationContext context, final Document doc, final XMLSignatureCallback callback )
    {
        if ( callback.isCanceled () || callback.getSignedDocument () == null )
        {
            return AuthorizationResult.createReject ( VERIFY_NO_SIGNATURE, "No signature data found" );
        }

        try
        {
            final Document signedDoc = this.builder.fromString ( callback.getSignedDocument () );

            final Result result = this.validator.validate ( signedDoc );

            if ( !result.isValid () )
            {
                this.auditLogService.info ( "Validation failed:\n{}", this.builder.toString ( signedDoc, true ) );
                return AuthorizationResult.createReject ( VERIFY_SIGNATURE_INVALID, "Signature is not valid" );
            }

            // next we need to check if the request was the request we actually wanted, somebody might just have sent some signed XML content
            try
            {
                this.builder.compare ( doc, signedDoc );
            }
            catch ( final Exception e )
            {
                this.auditLogService.info ( "Requests don't match" );
                this.auditLogService.info ( "Original: {}", this.builder.toString ( doc, true ) );
                this.auditLogService.info ( "Signed: {}", this.builder.toString ( signedDoc, true ) );
                return AuthorizationResult.createReject ( e );
            }

            postProcess ( context, result );

            // now we can create an abstain .. since the may be other rules to check
            return null;
        }
        catch ( final Exception e )
        {
            this.auditLogService.info ( "Failed to validate", e );
            return AuthorizationResult.createReject ( e );
        }
    }

    private void postProcess ( final AuthorizationContext context, final Result result ) throws Exception
    {
        if ( this.postProcessor == null )
        {
            return;
        }

        logger.debug ( "Running post processor" );

        final ScriptContext scriptContext = new SimpleScriptContext ();
        final Map<String, Object> scriptObjects = new HashMap<String, Object> ();

        final KeySelectorResult keySelectorResult = result.getKeySelectorResult ();
        if ( keySelectorResult instanceof X509KeySelectorResult )
        {
            final X509Certificate cert = ( (X509KeySelectorResult)keySelectorResult ).getCertificate ();
            if ( cert != null )
            {
                logger.debug ( "User certifcate from result: {}", cert );
                scriptObjects.put ( "certificate", cert );
            }
        }

        scriptObjects.put ( "authorizationContext", context );
        scriptObjects.put ( "authenticator", this.authenticator );

        this.postProcessor.execute ( scriptContext, scriptObjects );
    }
}
