package com.jdvn.setl.geos.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/geometry")
public class GeometryResource {
	private static final Logger logger = LoggerFactory.getLogger(GeometryResource.class);
    @GET
    @Path("/hello")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHello(@QueryParam("ref") String ref) {
        try {
            logger.info("Successfully reached GeometryResource with ref: {}", ref);
            
            // Return a raw String to test if Jersey can send basic data
            String json = "{\"message\": \"Hello World Map\", \"status\": \"Success\"}";
            
            return Response.ok(json).build();
        } catch (Exception e) {
            logger.error("Error in getHello", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}