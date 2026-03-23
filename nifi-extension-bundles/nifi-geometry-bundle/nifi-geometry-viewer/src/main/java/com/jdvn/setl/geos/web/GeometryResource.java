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
            logger.info("Start to gen tiles");

            // 1. Initialize NiFi Content Access
            final ContentRequestContext requestContext = new HttpServletContentRequestContext(request);
            final ServletContext servletContext = request.getServletContext();
            final ContentAccess contentAccess = (ContentAccess) servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);

            // 2. Retrieve Content
            final DownloadableContent downloadableContent = contentAccess.getContent(requestContext);
            
            // 3. Process Avro into JSON String (Your original logic)
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
            
            logger.info("JSON Generated from Tiles: {}", prettyJson);

            //return Response.ok(prettyJson).build();

        } catch (Exception e) {
            logger.error("Error processing Avro content in /hello", e);
        }
        logger.info("End to gen tiles");
        
        
        // to feed your VectorTileEncoder.
        byte[] tileData = new byte[0]; 

        if (tileData.length == 0) {
            return Response.noContent().build();
        }

        return Response.ok(tileData).build();
    }
}