/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.rest;

import org.gridgain.client.marshaller.*;
import org.gridgain.client.marshaller.protobuf.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.rest.client.message.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.logger.java.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.kernal.processors.rest.client.message.GridClientCacheRequest.GridCacheOperation.*;

/**
 * Test client.
 */
final class GridTestBinaryClient {
    /** Logger. */
    private final GridLogger log = new GridJavaLogger();

    /** Hessian marshaller. */
    private final GridClientMarshaller protobufMarshaller = new GridClientProtobufMarshaller();

    /** Socket. */
    private final Socket sock;

    /** Socket input stream. */
    private final InputStream input;

    /** Opaque counter. */
    private final AtomicInteger idCntr = new AtomicInteger(0);

    /** Response queue. */
    private final BlockingQueue<Response> queue = new LinkedBlockingQueue<>();

    /** Socket reader. */
    private final Thread rdr;

    /** Quit response. */
    private static final Response QUIT_RESP = new Response(0, GridRestResponse.STATUS_FAILED, null, null);

    /** Random client id. */
    private UUID id = UUID.randomUUID();

    /**
     * Creates client.
     *
     * @param host Hostname.
     * @param port Port number.
     * @throws GridException In case of error.
     */
    GridTestBinaryClient(String host, int port) throws GridException {
        assert host != null;
        assert port > 0;

        try {
            sock = new Socket(host, port);

            input = sock.getInputStream();

            // Write handshake.
            sock.getOutputStream().write(new GridClientHandshakeRequest(protobufMarshaller.getProtocolId()).rawBytes());

            byte[] buf = new byte[1];

            // Wait for handshake response.
            assert input.read(buf) == 1;
            assert buf[0] == GridClientHandshakeResponse.OK.resultCode() :
                "Client handshake failed [code=" + buf[0] + ']';
        }
        catch (IOException e) {
            throw new GridException("Failed to establish connection.", e);
        }

        // Start socket reader thread.
        rdr = new Thread(new Runnable() {
            @SuppressWarnings("InfiniteLoopStatement")
            @Override public void run() {
                try {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();

                    int len = 0;

                    boolean running = true;

                    while (running) {
                        // Header.
                        int symbol = input.read();

                        if (symbol == -1)
                            break;

                        if ((byte)symbol != (byte)0x90) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to parse incoming packet (invalid packet start): " +
                                    Integer.toHexString(symbol & 0xFF));

                            break;
                        }

                        // Packet.
                        while (true) {
                            symbol = input.read();

                            if (symbol == -1) {
                                running = false;

                                break;
                            }

                            byte b = (byte)symbol;

                            buf.write(b);

                            if (len == 0) {
                                if (buf.size() == 4) {
                                    len = U.bytesToInt(buf.toByteArray(), 0);

                                    if (log.isInfoEnabled())
                                        log.info("Read length: " + len);

                                    buf.reset();
                                }
                            }
                            else {
                                if (buf.size() == len) {
                                    byte[] bytes = buf.toByteArray();
                                    byte[] hdrBytes = Arrays.copyOfRange(bytes, 0, 40);
                                    byte[] msgBytes = Arrays.copyOfRange(bytes, 40, bytes.length);

                                    GridClientResponse msg = protobufMarshaller.unmarshal(msgBytes);

                                    long reqId = GridClientByteUtils.bytesToLong(hdrBytes, 0);
                                    UUID clientId = GridClientByteUtils.bytesToUuid(hdrBytes, 8);
                                    UUID destId = GridClientByteUtils.bytesToUuid(hdrBytes, 24);

                                    msg.requestId(reqId);
                                    msg.clientId(clientId);
                                    msg.destinationId(destId);

                                    buf.reset();

                                    len = 0;

                                    queue.offer(new Response(msg.requestId(), msg.successStatus(), msg.result(),
                                        msg.errorMessage()));

                                    break;
                                }
                            }
                        }
                    }
                }
                catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted())
                        U.error(log, e);
                }
                finally {
                    U.closeQuiet(sock);

                    queue.add(QUIT_RESP);
                }
            }
        });

        rdr.start();
    }

    /** {@inheritDoc} */
    public void shutdown() throws GridException {
        try {
            if (rdr != null) {
                rdr.interrupt();

                U.closeQuiet(sock);

                rdr.join();
            }
        }
        catch (InterruptedException e) {
            throw new GridException(e);
        }
    }

    /**
     * Makes request to server and waits for response.
     *
     * @param msg Message to request,
     * @return Response object.
     * @throws GridException If request failed.
     */
    private Response makeRequest(GridClientMessage msg) throws GridException {
        assert msg != null;

        // Send request
        try {
            sock.getOutputStream().write(createPacket(msg));
        }
        catch (IOException e) {
            throw new GridException("Failed to send packet.", e);
        }

        // Wait for response.
        while (true) {
            try {
                // Take response from queue.
                Response res = queue.take();

                if (res == QUIT_RESP)
                    return res;

                // Check opaque value.
                if (res.opaque() == msg.requestId()) {
                    if (!res.isSuccess() && res.error() != null)
                        throw new GridException(res.error());
                    else
                        return res;
                }
                else
                    // Return response to queue if opaque is incorrect.
                    queue.add(res);
            }
            catch (InterruptedException e) {
                throw new GridException("Interrupted while waiting for response.", e);
            }
        }

    }

    /**
     * Creates hessian packet from client message.
     *
     * @param msg Message to be sent.
     * @return Raw packet.
     * @throws IOException If serialization failed.
     */
    private byte[] createPacket(GridClientMessage msg) throws IOException {
        msg.clientId(id);

        byte[] data = protobufMarshaller.marshal(msg);

        byte[] res = new byte[45 + data.length];

        res[0] = (byte)0x90;

        U.intToBytes(data.length + 40, res, 1);
        U.longToBytes(msg.requestId(), res, 5);
        U.uuidToBytes(msg.clientId(), res, 13);
        U.uuidToBytes(msg.destinationId(), res, 29);

        U.arrayCopy(data, 0, res, 45, data.length);

        return res;
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param val Value.
     * @return If value was actually put.
     * @throws GridException In case of error.
     */
    public <K, V> boolean cachePut(@Nullable String cacheName, K key, V val)
        throws GridException {
        return cachePutAll(cacheName, Collections.singletonMap(key, val));
    }

    /**
     * @param cacheName Cache name.
     * @param entries Entries.
     * @return {@code True} if map contained more then one entry or if put succeeded in case of one entry,
     *      {@code false} otherwise
     * @throws GridException In case of error.
     */
    public <K, V> boolean cachePutAll(@Nullable String cacheName, Map<K, V> entries)
        throws GridException {
        assert entries != null;

        GridClientCacheRequest<K, V> req = new GridClientCacheRequest<>(PUT_ALL);

        req.requestId(idCntr.incrementAndGet());
        req.cacheName(cacheName);
        req.values(entries);

        return makeRequest(req).<Boolean>getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @return Value.
     * @throws GridException In case of error.
     */
    public <K, V> V cacheGet(@Nullable String cacheName, K key)
        throws GridException {
        assert key != null;

        GridClientCacheRequest<K, V> req = new GridClientCacheRequest<>(GET);

        req.requestId(idCntr.getAndIncrement());
        req.cacheName(cacheName);
        req.key(key);

        return makeRequest(req).getObject();

    }

    /**
     * @param cacheName Cache name.
     * @param keys Keys.
     * @return Entries.
     * @throws GridException In case of error.
     */
    public <K, V> Map<K, V> cacheGetAll(@Nullable String cacheName, K... keys)
        throws GridException {
        assert keys != null;

        GridClientCacheRequest<K, V> req = new GridClientCacheRequest<>(GET_ALL);

        req.requestId(idCntr.getAndIncrement());
        req.cacheName(cacheName);
        req.keys(Arrays.asList(keys));

        return makeRequest(req).getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @return Whether entry was actually removed.
     * @throws GridException In case of error.
     */
    @SuppressWarnings("unchecked")
    public <K> boolean cacheRemove(@Nullable String cacheName, K key) throws GridException {
        assert key != null;

        GridClientCacheRequest<K, Object> req = new GridClientCacheRequest<>(RMV);

        req.requestId(idCntr.getAndIncrement());
        req.cacheName(cacheName);
        req.key(key);

        return makeRequest(req).<Boolean>getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param keys Keys.
     * @return Whether entries were actually removed
     * @throws GridException In case of error.
     */
    public <K> boolean cacheRemoveAll(@Nullable String cacheName, K... keys)
        throws GridException {
        assert keys != null;

        GridClientCacheRequest<K, Object> req = new GridClientCacheRequest<>(RMV_ALL);

        req.requestId(idCntr.getAndIncrement());
        req.cacheName(cacheName);
        req.keys(Arrays.asList(keys));

        return makeRequest(req).isSuccess();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param val Value.
     * @return Whether value was actually replaced.
     * @throws GridException In case of error.
     */
    public <K, V> boolean cacheReplace(@Nullable String cacheName, K key, V val)
        throws GridException {
        assert key != null;
        assert val != null;

        GridClientCacheRequest<K, V> replace = new GridClientCacheRequest<>(REPLACE);

        replace.requestId(idCntr.getAndIncrement());
        replace.cacheName(cacheName);
        replace.key(key);
        replace.value(val);

        return makeRequest(replace).<Boolean>getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param val1 Value 1.
     * @param val2 Value 2.
     * @return Whether new value was actually set.
     * @throws GridException In case of error.
     */
    public <K, V> boolean cacheCompareAndSet(@Nullable String cacheName, K key, @Nullable V val1, @Nullable V val2)
        throws GridException {
        assert key != null;

        GridClientCacheRequest<K, V> msg = new GridClientCacheRequest<>(CAS);

        msg.requestId(idCntr.getAndIncrement());
        msg.cacheName(cacheName);
        msg.key(key);
        msg.value(val1);
        msg.value2(val2);

        return makeRequest(msg).<Boolean>getObject();
    }

    /**
     * @param cacheName Cache name.
     * @return Metrics.
     * @throws GridException In case of error.
     */
    public <K> Map<String, Long> cacheMetrics(@Nullable String cacheName) throws GridException {
        GridClientCacheRequest<K, Object> metrics = new GridClientCacheRequest<>(METRICS);

        metrics.requestId(idCntr.getAndIncrement());
        metrics.cacheName(cacheName);

        return makeRequest(metrics).getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param val Value.
     * @return Whether entry was appended.
     * @throws GridException In case of error.
     */
    public <K, V> boolean cacheAppend(@Nullable String cacheName, K key, V val)
        throws GridException {
        assert key != null;
        assert val != null;

        GridClientCacheRequest<K, V> add = new GridClientCacheRequest<>(APPEND);

        add.requestId(idCntr.getAndIncrement());
        add.cacheName(cacheName);
        add.key(key);
        add.value(val);

        return makeRequest(add).<Boolean>getObject();
    }

    /**
     * @param cacheName Cache name.
     * @param key Key.
     * @param val Value.
     * @return Whether entry was prepended.
     * @throws GridException In case of error.
     */
    public <K, V> boolean cachePrepend(@Nullable String cacheName, K key, V val)
        throws GridException {
        assert key != null;
        assert val != null;

        GridClientCacheRequest<K, V> add = new GridClientCacheRequest<>(PREPEND);

        add.requestId(idCntr.getAndIncrement());
        add.cacheName(cacheName);
        add.key(key);
        add.value(val);

        return makeRequest(add).<Boolean>getObject();
    }

    /**
     * @param taskName Task name.
     * @param arg Task arguments.
     * @return Task execution result.
     * @throws GridException In case of error.
     */
    public GridClientTaskResultBean execute(String taskName, @Nullable Object arg) throws GridException {
        assert !F.isEmpty(taskName);

        GridClientTaskRequest msg = new GridClientTaskRequest();

        msg.taskName(taskName);
        msg.argument(arg);

        return makeRequest(msg).getObject();
    }

    /**
     * @param id Node ID.
     * @param includeAttrs Whether to include attributes.
     * @param includeMetrics Whether to include metrics.
     * @return Node.
     * @throws GridException In case of error.
     */
    public GridClientNodeBean node(UUID id, boolean includeAttrs, boolean includeMetrics)
        throws GridException {
        assert id != null;

        GridClientTopologyRequest msg = new GridClientTopologyRequest();

        msg.nodeId(id);
        msg.includeAttributes(includeAttrs);
        msg.includeMetrics(includeMetrics);

        return makeRequest(msg).getObject();
    }

    /**
     * @param ipAddr IP address.
     * @param includeAttrs Whether to include attributes.
     * @param includeMetrics Whether to include metrics.
     * @return Node.
     * @throws GridException In case of error.
     */
    public GridClientNodeBean node(String ipAddr, boolean includeAttrs, boolean includeMetrics)
        throws GridException {
        assert !F.isEmpty(ipAddr);

        GridClientTopologyRequest msg = new GridClientTopologyRequest();

        msg.nodeIp(ipAddr);
        msg.includeAttributes(includeAttrs);
        msg.includeMetrics(includeMetrics);

        return makeRequest(msg).getObject();
    }

    /**
     * @param includeAttrs Whether to include attributes.
     * @param includeMetrics Whether to include metrics.
     * @return Nodes.
     * @throws GridException In case of error.
     */
    public List<GridClientNodeBean> topology(boolean includeAttrs, boolean includeMetrics)
        throws GridException {
        GridClientTopologyRequest msg = new GridClientTopologyRequest();

        msg.includeAttributes(includeAttrs);
        msg.includeMetrics(includeMetrics);

        return makeRequest(msg).getObject();
    }

    /**
     * @param path Log file path.
     * @return Log file contents.
     * @throws GridException In case of error.
     */
    public List<String> log(@Nullable String path, int from, int to) throws GridException {
        GridClientLogRequest msg = new GridClientLogRequest();

        msg.requestId(idCntr.getAndIncrement());
        msg.path(path);
        msg.from(from);
        msg.to(to);

        return makeRequest(msg).getObject();
    }

    /**
     * Response data.
     */
    private static class Response {
        /** Opaque. */
        private final long opaque;

        /** Success flag. */
        private final int success;

        /** Response object. */
        private final Object obj;

        /** Error message, if any */
        private final String error;

        /**
         * @param opaque Opaque.
         * @param success Success flag.
         * @param obj Response object.
         * @param error Error message, if any.
         */
        Response(long opaque, int success, @Nullable Object obj, @Nullable String error) {
            assert opaque >= 0;

            this.opaque = opaque;
            this.success = success;
            this.obj = obj;
            this.error = error;
        }

        /**
         * @return Opaque.
         */
        long opaque() {
            return opaque;
        }

        /**
         * @return Success flag.
         */
        boolean isSuccess() {
            return success == GridRestResponse.STATUS_SUCCESS;
        }

        /**
         * @return Response object.
         */
        @SuppressWarnings("unchecked")
        <T> T getObject() {
            return (T)obj;
        }

        /**
         * @return Error message in case if error occurred.
         */
        String error() {
            return error;
        }
    }
}
