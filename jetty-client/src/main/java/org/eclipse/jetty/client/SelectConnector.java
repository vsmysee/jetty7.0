// ========================================================================
// Copyright (c) 2006-2009 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.HttpVersions;
import org.eclipse.jetty.http.ssl.SslSelectChannelEndPoint;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Buffers;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.ThreadLocalBuffers;
import org.eclipse.jetty.io.nio.DirectNIOBuffer;
import org.eclipse.jetty.io.nio.IndirectNIOBuffer;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.io.nio.SelectorManager;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;

class SelectConnector extends AbstractLifeCycle implements HttpClient.Connector, Runnable
{
    private final HttpClient _httpClient;
    private SSLContext _sslContext;
    private Buffers _sslBuffers;
    private boolean _blockingConnect;

    Manager _selectorManager=new Manager();

    /**
     * @param httpClient
     */
    SelectConnector(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    /* ------------------------------------------------------------ */
    /** Get the blockingConnect.
     * @return the blockingConnect
     */
    public boolean isBlockingConnect()
    {
        return _blockingConnect;
    }

    /* ------------------------------------------------------------ */
    /** Set the blockingConnect.
     * @param blockingConnect If true, connections are made in blocking mode.
     */
    public void setBlockingConnect(boolean blockingConnect)
    {
        _blockingConnect = blockingConnect;
    }

    /* ------------------------------------------------------------ */
    protected void doStart() throws Exception
    {
        super.doStart();

        _selectorManager.start();

        final boolean direct=_httpClient.getUseDirectBuffers();

        SSLEngine sslEngine=_selectorManager.newSslEngine();
        final SSLSession ssl_session=sslEngine.getSession();
        ThreadLocalBuffers ssl_buffers = new ThreadLocalBuffers()
        {
            {
                super.setBufferSize(ssl_session.getApplicationBufferSize());
                super.setHeaderSize(ssl_session.getApplicationBufferSize());
            }

            @Override
            protected Buffer newBuffer(int size)
            {
                return direct?new DirectNIOBuffer(size):new IndirectNIOBuffer(size);
            }
            @Override
            protected Buffer newHeader(int size)
            {
                return direct?new DirectNIOBuffer(size):new IndirectNIOBuffer(size);
            }
            @Override
            protected boolean isHeader(Buffer buffer)
            {
                return true;
            }

            @Override
            public void setBufferSize(int size)
            {
            }

            @Override
            public void setHeaderSize(int size)
            {
            }
        };
        _sslBuffers=ssl_buffers;

        _httpClient._threadPool.dispatch(this);
    }

    /* ------------------------------------------------------------ */
    protected void doStop() throws Exception
    {
        _selectorManager.stop();
    }

    /* ------------------------------------------------------------ */
    public void startConnection( HttpDestination destination )
        throws IOException
    {
        SocketChannel channel = SocketChannel.open();
        Address address = destination.isProxied() ? destination.getProxy() : destination.getAddress();
        channel.configureBlocking( false );
        channel.connect(address.toSocketAddress());
        _selectorManager.register( channel, destination );
    }

    /* ------------------------------------------------------------ */
    public void run()
    {
        while (_httpClient.isRunning())
        {
            try
            {
                _selectorManager.doSelect(0);
            }
            catch (Exception e)
            {
                Log.warn(e.toString());
                Log.debug(e);
                Thread.yield();
            }
        }
    }

    /* ------------------------------------------------------------ */
    class Manager extends SelectorManager
    {
        @Override
        protected SocketChannel acceptChannel(SelectionKey key) throws IOException
        {
            throw new IllegalStateException();
        }

        @Override
        public boolean dispatch(Runnable task)
        {
            return SelectConnector.this._httpClient._threadPool.dispatch(task);
        }

        @Override
        protected void endPointOpened(SelectChannelEndPoint endpoint)
        {
        }

        @Override
        protected void endPointClosed(SelectChannelEndPoint endpoint)
        {
        }

        @Override
        protected Connection newConnection(SocketChannel channel, SelectChannelEndPoint endpoint)
        {
            if (endpoint instanceof SslSelectChannelEndPoint)
                return new HttpConnection(_sslBuffers,_sslBuffers,endpoint);

            return new HttpConnection(_httpClient.getRequestBuffers(),_httpClient.getResponseBuffers(),endpoint);
        }

        @Override
        protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey key) throws IOException
        {
            // key should have destination at this point (will be replaced by endpoint after this call)
            HttpDestination dest=(HttpDestination)key.attachment();

            SelectChannelEndPoint ep=null;

            if (dest.isSecure())
            {
                if (dest.isProxied())
                {
                    String connect = HttpMethods.CONNECT+" "+dest.getAddress()+HttpVersions.HTTP_1_0+"\r\n\r\n";
                    // TODO need to send this over channel unencrypted and setup endpoint to ignore the 200 OK response.

                    throw new IllegalStateException("Not Implemented");
                }

                SSLEngine engine=newSslEngine();
                ep = new SslSelectChannelEndPoint(_sslBuffers,channel,selectSet,key,engine);
            }
            else
            {
                ep=new SelectChannelEndPoint(channel,selectSet,key);
            }

            HttpConnection connection=(HttpConnection)ep.getConnection();
            connection.setDestination(dest);
            dest.onNewConnection(connection);
            return ep;
        }

        private synchronized SSLEngine newSslEngine() throws IOException
        {
            if (_sslContext==null)
            {
                _sslContext = SelectConnector.this._httpClient.getSSLContext();
            }

            SSLEngine sslEngine = _sslContext.createSSLEngine();
            sslEngine.setUseClientMode(true);
            sslEngine.beginHandshake();

            return sslEngine;
        }

        /* ------------------------------------------------------------ */
        /* (non-Javadoc)
         * @see org.eclipse.io.nio.SelectorManager#connectionFailed(java.nio.channels.SocketChannel, java.lang.Throwable, java.lang.Object)
         */
        protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment)
        {
            if (attachment instanceof HttpDestination)
                ((HttpDestination)attachment).onConnectionFailed(ex);
            else
                super.connectionFailed(channel,ex,attachment);
        }
    }
}
