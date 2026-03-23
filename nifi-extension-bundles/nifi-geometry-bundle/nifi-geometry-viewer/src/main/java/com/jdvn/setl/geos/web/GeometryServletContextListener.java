package com.jdvn.setl.geos.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;


@WebListener
public class GeometryServletContextListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(GeometryServletContextListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent sce) {    	
    	final ServletContext context = sce.getServletContext();
        // Use Jersey ServletContainer to manage JAX-RS resources
        ServletRegistration.Dynamic servlet = context.addServlet("JerseyServlet", "org.glassfish.jersey.servlet.ServletContainer");        
        servlet.addMapping("/api/*");        
        // Use Package Scanning so you don't need a separate Application class
        servlet.setInitParameter("jersey.config.server.provider.packages", "com.jdvn.setl.geos.web");
        servlet.setLoadOnStartup(1);    	
    	logger.info("Servlet Context for Geometry Viewer Is Initialized");
    }
}
