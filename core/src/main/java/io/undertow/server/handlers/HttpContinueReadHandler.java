package io.undertow.server.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Handler for requests that require 100-continue responses. If an attempt is made to read from the source
 * channel then
 *
 * @author Stuart Douglas
 */
public class HttpContinueReadHandler implements HttpHandler {

    private static final ConduitWrapper<StreamSourceConduit> WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
        @Override
        public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
            return new ContinueConduit(factory.create(), exchange);
        }
    };

    private final HttpHandler handler;

    public HttpContinueReadHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (HttpContinue.requiresContinueResponse(exchange)) {
            exchange.addRequestWrapper(WRAPPER);
        }
        handler.handleRequest(exchange);
    }

    private static final class ContinueConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {

        private boolean sent = false;
        private HttpContinue.ContinueResponseSender response = null;
        private final HttpServerExchange exchange;


        protected ContinueConduit(final StreamSourceConduit next, final HttpServerExchange exchange) {
            super(next);
            this.exchange = exchange;
        }

        @Override
        public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.transferTo(position, count, target);
        }

        @Override
        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.transferTo(count, throughBuffer, target);
        }

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.read(dst);
        }

        @Override
        public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.read(dsts, offs, len);
        }

        @Override
        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            long exitTime = System.currentTimeMillis() + timeUnit.toMillis(time);
            if (response != null) {
                while (!response.send()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > exitTime) {
                        return;
                    }
                    response.awaitWritable(exitTime - currentTime, TimeUnit.MILLISECONDS);
                }
                response = null;
            }

            long currentTime = System.currentTimeMillis();
            super.awaitReadable(exitTime - currentTime, TimeUnit.MILLISECONDS);
        }

        @Override
        public void awaitReadable() throws IOException {
            if (exchange.getResponseCode() == 417) {
                //rejected
                return;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                while (!response.send()) {
                    response.awaitWritable();
                }
                response = null;
            }
            super.awaitReadable();
        }
    }
}
