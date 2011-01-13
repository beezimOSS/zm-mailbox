/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009, 2010, 2011 Zimbra, Inc.
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.imap;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.server.NioHandler;
import com.zimbra.cs.server.NioOutputStream;
import com.zimbra.cs.server.NioConnection;
import com.zimbra.cs.stats.ZimbraPerf;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.codec.RecoverableProtocolDecoderException;

final class NioImapHandler extends ImapHandler implements NioHandler {
    private final ImapConfig config;
    private final NioConnection connection;
    private NioImapRequest request;

    NioImapHandler(NioImapServer server, NioConnection conn) {
        super(server.getConfig());
        connection = conn;
        config = server.getConfig();
        output = conn.getOutputStream();
    }

    @Override
    public void connectionOpened() throws IOException {
        sendGreeting();
    }

    @Override
    public void messageReceived(Object msg) throws IOException {
        if (request == null) {
            request = new NioImapRequest(this);
        }

        if (request.parse(msg)) {
            // Request is complete
            try {
                if (!processRequest(request)) {
                    dropConnection();
                }
            } finally {
                ZimbraLog.clearContext();
                if (request != null) {
                    request.cleanup();
                    request = null;
                }
            }
            if (consecutiveBAD >= ImapHandler.MAXIMUM_CONSECUTIVE_BAD) {
                dropConnection();
            }
        }
    }

    @Override
    public void exceptionCaught(Throwable e) throws IOException {
        if (e instanceof RecoverableProtocolDecoderException) {
            sendBAD("*", e.getMessage());
        } else if (e instanceof ProtocolDecoderException) {
            sendBAD("*", e.getMessage());
            dropConnection(true);
        }
    }

    private boolean processRequest(NioImapRequest req) throws IOException {
        ImapSession i4selected = selectedFolder;
        if (i4selected != null)
            i4selected.updateAccessTime();

        long start = ZimbraPerf.STOPWATCH_IMAP.start();

        try {
            if (!checkAccountStatus()) {
                return false;
            }
            if (authenticator != null && !authenticator.isComplete()) {
                return continueAuthentication(req);
            }
            try {
                boolean keepGoing = executeRequest(req);
                consecutiveBAD = 0;
                return keepGoing;
            } catch (ImapProxyException e) {
                ZimbraLog.imap.debug("proxy failed", e);
                sendNO(req.getTag(), "Shared folder temporally unavailable");
                return false; // disconnect
            } catch (ImapParseException ipe) {
                handleParseException(ipe);
                return true;
            }
        } finally {
            ZimbraPerf.STOPWATCH_IMAP.stop(start);
            if (lastCommand != null) {
                ZimbraPerf.IMAP_TRACKER.addStat(lastCommand.toUpperCase(), start);
            }
        }
    }

    @Override
    public void dropConnection() {
        dropConnection(true);
    }

    @Override
    public void connectionClosed() {
        cleanup();
        connection.close();
    }

    private void cleanup() {
        if (request != null) {
            request.cleanup();
            request = null;
        }
        try {
            unsetSelectedFolder(false);
        } catch (Exception e) {}
    }

    @Override
    public void connectionIdle() {
        ZimbraLog.imap.debug("dropping connection for inactivity");
        dropConnection();
    }

    @Override
    public void setLoggingContext() {
        setLoggingContext(connection.getRemoteAddress().toString());
    }

    @Override
    void sendLine(String line, boolean flush) throws IOException {
        NioOutputStream out = (NioOutputStream) output;
        if (out != null) {
            out.write(line);
            out.write("\r\n");
            if (flush) {
                out.flush();
            }
        }
    }

    /**
     * Called when connection is closed. No need to worry about concurrent execution since requests are processed in
     * sequence for any given connection.
     */
    @Override
    void dropConnection(boolean sendBanner) {
        try {
            unsetSelectedFolder(false);
        } catch (Exception e) {
        }

        if (credentials != null && !goodbyeSent) {
            ZimbraLog.imap.info("dropping connection for user " + credentials.getUsername() + " (server-initiated)");
        }

        if (!connection.isOpen()) {
            return; // No longer connected
        }
        ZimbraLog.imap.debug("dropConnection: sendBanner = %s\n", sendBanner);
        cleanup();

        if (sendBanner && !goodbyeSent) {
            sendBYE();
        }
        connection.close();
    }

    @Override
    void enableInactivityTimer() {
        connection.setMaxIdleSeconds(config.getAuthenticatedMaxIdleTime());
    }

    @Override
    void completeAuthentication() throws IOException {
        if (authenticator.isEncryptionEnabled()) {
            connection.startSasl(authenticator.getSaslServer());
        }
        authenticator.sendSuccess();
    }

    @Override
    boolean doSTARTTLS(String tag) throws IOException {
        if (!checkState(tag, State.NOT_AUTHENTICATED)) {
            return true;
        } else if (startedTLS) {
            sendNO(tag, "TLS already started");
            return true;
        }

        connection.startTls();
        sendOK(tag, "begin TLS negotiation now");
        startedTLS = true;
        return true;
    }

    @Override
    InetSocketAddress getLocalAddress() {
        return connection.getLocalAddress();
    }
}
