package com.jdvn.setl.geos.web;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.web.ContentAccess;
import org.apache.nifi.web.ContentRequestContext;
import org.apache.nifi.web.DownloadableContent;
import org.apache.nifi.web.HttpServletContentRequestContext;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jdvn.setl.geos.web.util.GeoUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/geometry")
public class GeometryResource {
	
    private static final Logger logger = LoggerFactory.getLogger(GeometryResource.class);    
    private static final String CONTENT_ACCESS_ATTRIBUTE = "nifi-content-access";
    // For raster tiles caching
	@SuppressWarnings("rawtypes")
	private static final Cache<MapCacheKey, FeatureCollection> mapViewCache = Caffeine.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build();
    // For vector tiles caching
    private static final Cache<MapCacheKey, byte[]> mapVectorCache = Caffeine.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build();
    
    public record MapCacheKey(String ref, int x, int y, int z) {}

	@GET
	@Path("/hello")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getHello(@QueryParam("ref") String ref) {
	    try {
	        logger.info("Successfully reached GeometryResource with ref: {}", ref);
	        
	        // This is a valid GeoJSON FeatureCollection
	        // It places a single point in the center of Gangnam
	        String geoJson = "{" +
	            "  \"type\": \"FeatureCollection\"," +
	            "  \"features\": [" +
	            "    {" +
	            "      \"type\": \"Feature\"," +
	            "      \"geometry\": {" +
	            "        \"type\": \"Point\"," +
	            "        \"coordinates\": [127.0276, 37.4979]" +
	            "      }," +
	            "      \"properties\": {" +
	            "        \"name\": \"Gangnam Center Point\"," +
	            "        \"ref\": \"" + ref + "\"," +
	            "        \"status\": \"Success\"" +
	            "      }" +
	            "    }" +
	            "  ]" +
	            "}";
	        
	        return Response.ok(geoJson).build();
	    } catch (Exception e) {
	        logger.error("Error in getHello", e);
	        return Response.serverError().entity(e.getMessage()).build();
	    }
	}
	
	@GET
    @Path("/tiles/{z}/{x}/{y}.mvt")
    //@Path("/tiles/{z}/{x}/{y}")
    //@Produces("application/x-protobuf")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getVectorTile(
            @Context HttpServletRequest request,
            @PathParam("z") int z,
            @PathParam("x") int x,
            @PathParam("y") int y,
            @QueryParam("ref") String ref) {
        
        logger.info("Fetching tile at Z:{}, X:{}, Y:{} for ref: {}", z, x, y, ref);
        String crs = null; 
        String envelope = null;
        byte[] bais = null;
                       
    	final ServletContext servletContext = request.getServletContext();               
		java.util.Map<String, String> finalAttributes =getMapAttributes(request, ref);
		crs = finalAttributes.get(GeoUtils.GEO_CRS);
		envelope = finalAttributes.get(GeoUtils.GEO_ENVELOP);
		
		// 3. Initialize NiFi Content Access
        final ContentRequestContext requestContext = new HttpServletContentRequestContext(request);
        final ContentAccess contentAccess = (ContentAccess) servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);
        // 4. Retrieve Content
        final DownloadableContent downloadableContent = contentAccess.getContent(requestContext);
            		    		
		MapCacheKey key = new MapCacheKey(ref, x, y, z);    		
		if (mapVectorCache.getIfPresent(key) == null) {
			bais = GeoUtils.getVectorTileFromDownloadableContent(downloadableContent, crs, envelope, z, x, y);
			if (bais != null) 
				mapVectorCache.put(key, bais);				
		} else {
			bais = mapVectorCache.getIfPresent(key);
		}		    		                
        return Response.ok(bais).build();
    }
            
	@GET
    @Path("/tiles/{z}/{x}/{y}")
    @Consumes(MediaType.WILDCARD)
    @Produces("image/png")
    public Response getRasterTile(
            @Context HttpServletRequest request,
            @PathParam("z") int z,
            @PathParam("x") int x,
            @PathParam("y") int y,
            @QueryParam("ref") String ref) {
        
        logger.info("Fetching tile at Z:{}, X:{}, Y:{} for ref: {}", z, x, y, ref);
        String crs = null; 
        String geoType = null;
        ByteArrayInputStream bais = null;
       
    	final ServletContext servletContext = request.getServletContext();               
		java.util.Map<String, String> finalAttributes =getMapAttributes(request, ref);
		crs = finalAttributes.get(GeoUtils.GEO_CRS);
		geoType = finalAttributes.get(GeoUtils.GEO_TYPE);
            
        final ContentRequestContext requestContext = new HttpServletContentRequestContext(request);
        final ContentAccess contentAccess = (ContentAccess) servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);
        // 4. Retrieve Content
        final DownloadableContent downloadableContent = contentAccess.getContent(requestContext);
            		    		            		    		    		
		if (geoType.equals("Features")) {
			MapCacheKey key = new MapCacheKey(ref, x, y, z); 
			if (mapViewCache.getIfPresent(key) == null) {
				final SimpleFeatureCollection drawablefc = GeoUtils.drawableFeatureCollectionFromDownloadableContent(downloadableContent, crs, geoType);
				mapViewCache.put(key, drawablefc);
				bais = GeoUtils.getImageTileFromFeatureCollection(drawablefc, z, x, y);
			} else {
				bais = GeoUtils.getImageTileFromFeatureCollection((SimpleFeatureCollection) mapViewCache.getIfPresent(key), z, x, y);
			}
		} else if (geoType.equals("Tiles")){
			bais = GeoUtils.getImageTileFromDownloadableContent(downloadableContent, geoType, z, x, y);
		}         
        return Response.ok(bais).build();
    }
	/*
	 * Utility using Reflection to get Attribute information
	 * from DownloadableContent using ServletRequest as Plugin
	 * is isolated from Nifi2 frameworks  
	 */
    @SuppressWarnings("unchecked")
	public java.util.Map<String, String> getMapAttributes(HttpServletRequest request, String ref ){
    	java.util.Map<String, String> finalAttributes = new java.util.HashMap<>();
    	
        try {
            // 1. Initialize Context
            final ServletContext servletContext = request.getServletContext();
  
            // 2. Retrieve Attributes using Reflection as this bundle is isolated from Nifi2 
			Object contentAccessObj = servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);
			if (contentAccessObj != null) {
				try {
					java.lang.reflect.Field facadeField = contentAccessObj.getClass().getDeclaredField("serviceFacade");
					facadeField.setAccessible(true);
					Object serviceFacade = facadeField.get(contentAccessObj);

					if (serviceFacade != null) {
						Object dto = null;
						String[] parts = ref.split("/");

						if (ref.contains("/provenance-events/")) {
							// PROVENANCE EVENT LOGIC URL usually: .../provenance-events/{id}/content/...
							String eventId = "";
							for (int i = 0; i < parts.length; i++) {
								if ("provenance-events".equals(parts[i])) {
									eventId = parts[i + 1];
									break;
								}
							}

							// Remove query params if present (e.g. 1?clientId=...)
							if (eventId.contains("?")) {
								eventId = eventId.substring(0, eventId.indexOf("?"));
							}

							java.lang.reflect.Method getProvMethod = serviceFacade.getClass()
									.getMethod("getProvenanceEvent", Long.class);
							dto = getProvMethod.invoke(serviceFacade, Long.valueOf(eventId));

						} else if (ref.contains("/flowfile-queues/")) {
							// --- QUEUE FLOWFILE LOGIC ---
							java.lang.reflect.Method getFlowFileMethod = serviceFacade.getClass()
									.getMethod("getFlowFile", String.class, String.class);

							String connectionId = parts[parts.length - 4];
							String flowFileUuid = parts[parts.length - 2];

							dto = getFlowFileMethod.invoke(serviceFacade, connectionId, flowFileUuid);
						}

						// --- EXTRACT ATTRIBUTES FROM DTO ---
						if (dto != null) {
							java.lang.reflect.Method getAttributesMethod = dto.getClass().getMethod("getAttributes");
							Object attributesObj = getAttributesMethod.invoke(dto);

							if (attributesObj instanceof java.util.Map) {
								// This handles the FlowFileDTO case
								finalAttributes.putAll((java.util.Map<String, String>) attributesObj);

							} else if (attributesObj instanceof java.util.Collection) {
								// This handles the ProvenanceEventDTO case (TreeSet of AttributeDTO)
								java.util.Collection<?> attributeSet = (java.util.Collection<?>) attributesObj;
								for (Object attrDto : attributeSet) {
									try {
										// Use reflection to get 'name' and 'value' from AttributeDTO
										java.lang.reflect.Method getName = attrDto.getClass().getMethod("getName");
										java.lang.reflect.Method getValue = attrDto.getClass().getMethod("getValue");

										String name = (String) getName.invoke(attrDto);
										String value = (String) getValue.invoke(attrDto);

										if (name != null) {
											finalAttributes.put(name, value);
										}
									} catch (Exception e) {
										logger.error("Failed to extract individual attribute from Provenance set: "
												+ e.getMessage());
									}
								}
							}

						} else {
							logger.error("Metadata DTO not found for ref: {}", ref);
						}
					}
				} catch (Exception e) {
					logger.error("Could not extract Metadata from ServiceFacade: " + e.getMessage(), e);
				}
			}            
                		    		    		
        } catch (Exception e) {
            logger.error("Error processing MAP attributes", e);
        }      	
    	return finalAttributes;
    }	
    
}