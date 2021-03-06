// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.server.handler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

/* ------------------------------------------------------------ */
/** A <code>HandlerWrapper</code> acts as a {@link Handler} but delegates the {@link Handler#handle handle} method and
 * {@link LifeCycle life cycle} events to a delegate. This is primarily used to implement the <i>Decorator</i> pattern.
 * 
 */
public class HandlerWrapper extends AbstractHandlerContainer
{
    protected Handler _handler;

    /* ------------------------------------------------------------ */
    /**
     * 
     */
    public HandlerWrapper()
    {
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the handlers.
     */
    public Handler getHandler()
    {
        return _handler;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the handlers.
     */
    public Handler[] getHandlers()
    {
        return new Handler[] {_handler};
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param handler Set the {@link Handler} which should be wrapped.
     */
    public void setHandler(Handler handler)
    {
        if (isStarted())
            throw new IllegalStateException(STARTED);
        
        Handler old_handler = _handler;

        if (getServer()!=null)
            getServer().getContainer().update(this, old_handler, handler, "handler");

        if (handler!=null)
        {
            handler.setServer(getServer());
        }

        _handler = handler;
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.eclipse.thread.AbstractLifeCycle#doStart()
     */
    protected void doStart() throws Exception
    {
        if (_handler!=null)
            _handler.start();
        super.doStart();
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.eclipse.thread.AbstractLifeCycle#doStop()
     */
    protected void doStop() throws Exception
    {
        super.doStop();
        if (_handler!=null)
            _handler.stop();
    }
    
    /* ------------------------------------------------------------ */
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        if (_handler!=null && isStarted())
        {
            _handler.handle(target,baseRequest, request, response);
        }
    }
    

    /* ------------------------------------------------------------ */
    @Override
    public void setServer(Server server)
    {
        Server old_server=getServer();
        if (server==old_server)
            return;
        
        if (isStarted())
            throw new IllegalStateException(STARTED);
        
        super.setServer(server);
        
        Handler h=getHandler();
        if (h!=null)
            h.setServer(server);
        
        if (server!=null && server!=old_server)
            server.getContainer().update(this, null,_handler, "handler");
    }
    

    /* ------------------------------------------------------------ */
    @Override
    protected Object expandChildren(Object list, Class byClass)
    {
        return expandHandler(_handler,list,byClass);
    }

   
}
