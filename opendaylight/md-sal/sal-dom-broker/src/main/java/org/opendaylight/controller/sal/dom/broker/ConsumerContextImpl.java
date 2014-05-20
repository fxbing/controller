/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.dom.broker;

import java.util.Collection;
import java.util.concurrent.Future;

import javax.annotation.concurrent.GuardedBy;

import org.opendaylight.controller.sal.core.api.Broker.ConsumerSession;
import org.opendaylight.controller.sal.core.api.BrokerService;
import org.opendaylight.controller.sal.core.api.Consumer;
import org.opendaylight.controller.sal.dom.broker.osgi.AbstractBrokerServiceProxy;
import org.opendaylight.controller.sal.dom.broker.osgi.ProxyFactory;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.CompositeNode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

class ConsumerContextImpl implements ConsumerSession {

    private final ClassToInstanceMap<BrokerService> instantiatedServices = MutableClassToInstanceMap
            .create();
    private final BundleContext context;
    private final Consumer consumer;

    private BrokerImpl broker = null;
    @GuardedBy("this")
    private boolean closed = false;

    public ConsumerContextImpl(final Consumer consumer, final BundleContext ctx) {
        this.consumer = consumer;
        this.context = ctx;
    }

    @Override
    public Future<RpcResult<CompositeNode>> rpc(final QName rpc,
            final CompositeNode input) {
        return broker.invokeRpcAsync(rpc, input);
    }

    @Override
    public <T extends BrokerService> T getService(final Class<T> service) {
        final T localProxy = instantiatedServices.getInstance(service);
        if (localProxy != null) {
            return localProxy;
        }
        final ServiceReference<T> serviceRef = context
                .getServiceReference(service);
        if (serviceRef == null) {
            return null;
        }
        final T serviceImpl = context.getService(serviceRef);
        final T ret = ProxyFactory.createProxy(serviceRef, serviceImpl);
        if (ret != null) {
            instantiatedServices.putInstance(service, ret);
        }
        return ret;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            this.closed = true;
        }

        Collection<BrokerService> toStop = instantiatedServices.values();
        for (BrokerService brokerService : toStop) {
            if (brokerService instanceof AbstractBrokerServiceProxy<?>) {
                ((AbstractBrokerServiceProxy<?>) brokerService).close();
            }
        }
        broker.consumerSessionClosed(this);
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * @return the broker
     */
    public BrokerImpl getBroker() {
        return broker;
    }

    /**
     * @param broker
     *            the broker to set
     */
    public void setBroker(final BrokerImpl broker) {
        this.broker = broker;
    }

    /**
     * @return the _consumer
     */
    public Consumer getConsumer() {
        return consumer;
    }
}
