package com.jdvn.setl.geos.web;


import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;
import org.apache.nifi.web.servlet.filter.QueryStringToFragmentFilter;
import org.eclipse.jetty.ee11.servlet.DefaultServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * Servlet Context Listener supporting registration of Filters
 */
@WebListener
public class GeometryServletContextListener implements ServletContextListener {
//    private static final String API_CONTENT_MAPPING = "/api/content";
//
//    private static final int LOAD_ON_STARTUP_ENABLED = 1;
//
//    private static final String DIR_ALLOWED_PARAMETER = "dirAllowed";
//
//    private static final String BASE_RESOURCE_PARAMETER = "baseResource";
//
//    private static final String BASE_RESOURCE_DIRECTORY = "WEB-INF/classes/static";
//
//    private static final String DEFAULT_MAPPING = "/";

    private static final Logger logger = LoggerFactory.getLogger(GeometryServletContextListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
//        final ServletContext servletContext = sce.getServletContext();
//        final FilterRegistration.Dynamic filter = servletContext.addFilter(QueryStringToFragmentFilter.class.getSimpleName(), QueryStringToFragmentFilter.class);
//        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, DEFAULT_MAPPING);
//
//        final ServletRegistration.Dynamic servlet = servletContext.addServlet(GeometryViewerController.class.getSimpleName(), GeometryViewerController.class);
//        servlet.addMapping(API_CONTENT_MAPPING);
//        servlet.setLoadOnStartup(LOAD_ON_STARTUP_ENABLED);
//
//        final ServletRegistration.Dynamic defaultServlet = servletContext.addServlet(DefaultServlet.class.getSimpleName(), DefaultServlet.class);
//        defaultServlet.addMapping(DEFAULT_MAPPING);
//        defaultServlet.setInitParameter(DIR_ALLOWED_PARAMETER, Boolean.FALSE.toString());
//        defaultServlet.setInitParameter(BASE_RESOURCE_PARAMETER, BASE_RESOURCE_DIRECTORY);
//        defaultServlet.setLoadOnStartup(LOAD_ON_STARTUP_ENABLED);
//
//        logger.info("Standard Content Viewer Initialized");
    	

    	final ServletContext context = sce.getServletContext();

        // Use Jersey ServletContainer to manage JAX-RS resources
        ServletRegistration.Dynamic servlet = context.addServlet("JerseyServlet", "org.glassfish.jersey.servlet.ServletContainer");
        
        servlet.addMapping("/api/*");
        
        // Use Package Scanning so you don't need a separate Application class
        servlet.setInitParameter("jersey.config.server.provider.packages", "com.jdvn.setl.geos.web");
        servlet.setLoadOnStartup(1);
    	
    	logger.info("Geometry Servlet Context Initialized");
    }
}
