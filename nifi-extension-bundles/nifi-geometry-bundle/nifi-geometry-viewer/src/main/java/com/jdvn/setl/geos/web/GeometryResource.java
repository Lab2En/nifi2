package com.jdvn.setl.geos.web;

import java.io.InputStream;

import org.apache.avro.Conversions;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.nifi.web.ContentAccess;
import org.apache.nifi.web.ContentRequestContext;
import org.apache.nifi.web.DownloadableContent;
import org.apache.nifi.web.HttpServletContentRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
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

	
    @SuppressWarnings("unchecked")
	@GET
    @Path("/tiles/{z}/{x}/{y}")
    @Produces("application/x-protobuf")
    public Response getVectorTile(
            @Context HttpServletRequest request,
            @PathParam("z") int z,
            @PathParam("x") int x,
            @PathParam("y") int y,
            @QueryParam("ref") String ref) {
        
        logger.info("Fetching tile at Z:{}, X:{}, Y:{} for ref: {}", z, x, y, ref);

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

							java.util.Map<String, String> finalAttributes = new java.util.HashMap<>();

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

							if (!finalAttributes.isEmpty()) {
								logger.info("Successfully retrieved {} attributes", finalAttributes.size());
								finalAttributes.forEach((k, v) -> logger.info("Attribute: {} = {}", k, v));
							}
						} else {
							logger.error("Metadata DTO not found for ref: {}", ref);
						}
					}
				} catch (Exception e) {
					logger.error("Could not extract Metadata from ServiceFacade: " + e.getMessage(), e);
				}
			}            
            
			// 3. Initialize NiFi Content Access
            final ContentRequestContext requestContext = new HttpServletContentRequestContext(request);
            final ContentAccess contentAccess = (ContentAccess) servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);
            // 4. Retrieve Content
            final DownloadableContent downloadableContent = contentAccess.getContent(requestContext);
        	                
            // 5. Process Avro into JSON String (Your original logic)
            final StringBuilder sb = new StringBuilder();
            sb.append("[");
            
            final GenericData genericData = new GenericData();
            // Standard NiFi Avro Conversions
            genericData.addLogicalTypeConversion(new Conversions.DecimalConversion());
            genericData.addLogicalTypeConversion(new TimeConversions.DateConversion());
            genericData.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
            genericData.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());

            final DatumReader<GenericData.Record> datumReader = new GenericDatumReader<>(null, null, genericData);
            
            try (final InputStream contentStream = downloadableContent.getContent();
                 final DataFileStream<GenericData.Record> dataFileReader = new DataFileStream<>(contentStream, datumReader)) {
                
                while (dataFileReader.hasNext()) {
                    final GenericData.Record record = dataFileReader.next();
                    sb.append(genericData.toString(record)).append(",");
                    
                    // Limit for browser safety (2MB)
                    if (sb.length() > 1024 * 1024 * 2) break;
                }
            }

            if (sb.length() > 1) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]");
            
            final String json = sb.toString();
            
            // 4. Return formatted JSON
            final ObjectMapper mapper = new ObjectMapper();
            final Object objectJson = mapper.readValue(json, Object.class);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectJson);
            
            //logger.info("JSON Generated from Tiles: {}", prettyJson);

            //return Response.ok(prettyJson).build();

        } catch (Exception e) {
            logger.error("Error processing Avro content in /hello", e);
        }
                
        // to feed your VectorTileEncoder.
        byte[] tileData = new byte[0]; 

        if (tileData.length == 0) {
            return Response.noContent().build();
        }

        return Response.ok(tileData).build();
    }
}