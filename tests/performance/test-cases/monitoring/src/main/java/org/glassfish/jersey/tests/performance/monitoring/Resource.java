/*
 * Copyright (c) 2014, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.tests.performance.monitoring;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * @author Michal Gajdos
 */
@Path("/")
@Produces("text/plain")
public class Resource {

    @GET
    public String get() {
        return "get";
    }

    @GET
    @Path("sub")
    public String subget() {
        return "sub-get";
    }

    @POST
    public String post(final String post) {
        return post;
    }

    @POST
    @Path("sub")
    public String subpost(final String post) {
        return post;
    }

    @PUT
    public String put(final String put) {
        return put;
    }

    @PUT
    @Path("sub")
    public String subput(final String put) {
        return put;
    }

    @DELETE
    @Path("{id}")
    public String delete(final String delete) {
        return delete;
    }

    @DELETE
    @Path("sub/{id}")
    public String subdelete(final String delete) {
        return delete;
    }

    @Path("locator")
    public Class<SubResourceLocator> locator() {
        return SubResourceLocator.class;
    }
}
