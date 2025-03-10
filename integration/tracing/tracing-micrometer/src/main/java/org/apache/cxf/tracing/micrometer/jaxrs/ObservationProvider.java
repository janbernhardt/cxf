/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.tracing.micrometer.jaxrs;

import static org.apache.cxf.tracing.micrometer.jaxrs.DefaultContainerRequestReceiverObservationConvention.INSTANCE;
import static org.apache.cxf.tracing.micrometer.jaxrs.JaxrsObservationDocumentation.IN_OBSERVATION;

import java.io.IOException;
import java.lang.annotation.Annotation;

import org.apache.cxf.tracing.micrometer.AbstractObservationProvider;
import org.apache.cxf.tracing.micrometer.ObservationScope;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ObservationProvider extends AbstractObservationProvider
        implements ContainerRequestFilter, ContainerResponseFilter {
    @Context
    private ResourceInfo resourceInfo;

    private final ContainerRequestReceiverObservationConvention convention;

    public ObservationProvider(final ObservationRegistry observationRegistry) {
        this(observationRegistry, null);
    }

    public ObservationProvider(final ObservationRegistry observationRegistry,
                               ContainerRequestReceiverObservationConvention convention) {
        super(observationRegistry);
        this.convention = convention;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final ContainerRequestReceiverContext receiverContext = new ContainerRequestReceiverContext(requestContext);

        Observation observation = IN_OBSERVATION.start(convention,
                                                       INSTANCE,
                                                       () -> receiverContext,
                                                       this.observationRegistry);

        final TraceScopeHolder<ObservationScope> holder = super.startScopedObservation(observation);
        if (holder != null) {
            requestContext.setProperty(OBSERVATION_SCOPE, holder);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) throws IOException {
        super.stopTraceSpan((TraceScopeHolder<ObservationScope>) requestContext.getProperty(OBSERVATION_SCOPE),
                            observation -> {
                                ContainerRequestReceiverContext context =
                                        (ContainerRequestReceiverContext) observation.getContext();
                                context.setResponse(responseContext);
                            });
    }

    @Override
    protected boolean isAsyncResponse() {
        for (final Annotation[] annotations : resourceInfo.getResourceMethod().getParameterAnnotations()) {
            for (final Annotation annotation : annotations) {
                if (annotation.annotationType().equals(Suspended.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
