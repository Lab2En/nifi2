package com.jdvn.setl.geos.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import org.apache.avro.Conversions;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.nifi.authorization.AccessDeniedException;
import org.apache.nifi.web.ContentAccess;
import org.apache.nifi.web.ContentRequestContext;
import org.apache.nifi.web.DownloadableContent;
import org.apache.nifi.web.HttpServletContentRequestContext;
import org.apache.nifi.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GeometryViewerController extends HttpServlet {

	private static final long serialVersionUID = 1L;

	static final String CONTENT_ACCESS_ATTRIBUTE = "nifi-content-access";

    private static final Logger logger = LoggerFactory.getLogger(GeometryViewerController.class);

    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final ContentRequestContext requestContext = new HttpServletContentRequestContext(request);

        // get the content
        final ServletContext servletContext = request.getServletContext();
        final ContentAccess contentAccess = (ContentAccess) servletContext.getAttribute(CONTENT_ACCESS_ATTRIBUTE);

        // get the content
        final DownloadableContent downloadableContent;
        try {
            downloadableContent = contentAccess.getContent(requestContext);
        } catch (final ResourceNotFoundException e) {
            logger.warn("Content not found", e);
            response.sendError(HttpURLConnection.HTTP_NOT_FOUND, "Content not found");
            return;
        } catch (final AccessDeniedException e) {
            logger.warn("Content access denied", e);
            response.sendError(HttpURLConnection.HTTP_FORBIDDEN, "Content access denied");
            return;
        } catch (final Exception e) {
            logger.warn("Content retrieval failed", e);
            response.sendError(HttpURLConnection.HTTP_INTERNAL_ERROR, "Content retrieval failed");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);

        final boolean formatted = Boolean.parseBoolean(request.getParameter("formatted"));
        if (!formatted) {
            final InputStream contentStream = downloadableContent.getContent();
            contentStream.transferTo(response.getOutputStream());
            return;
        }

        // allow the user to drive the data type but fall back to the content type if necessary
        String displayName = request.getParameter("mimeTypeDisplayName");
        if (displayName == null) {
            final String contentType = downloadableContent.getType();
            displayName = getDisplayName(contentType);
        }

        if (displayName == null) {
            response.sendError(HttpURLConnection.HTTP_BAD_REQUEST, "Unknown content type");
            return;
        }

        try {
            switch (displayName) {
                case "geometry": {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    // Use Avro conversions to display logical type values in human readable way.
                    final GenericData genericData = new GenericData();
                    genericData.addLogicalTypeConversion(new Conversions.DecimalConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.DateConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.TimeMillisConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.LocalTimestampMicrosConversion());
                    genericData.addLogicalTypeConversion(new TimeConversions.LocalTimestampMillisConversion());
                    final DatumReader<GenericData.Record> datumReader = new GenericDatumReader<>(null, null, genericData);
                    try (final DataFileStream<GenericData.Record> dataFileReader = new DataFileStream<>(downloadableContent.getContent(), datumReader)) {
                        while (dataFileReader.hasNext()) {
                            final GenericData.Record record = dataFileReader.next();
                            final String formattedRecord = genericData.toString(record);
                            sb.append(formattedRecord);
                            sb.append(",");
                            // Do not format more than 10 MB of content.
                            if (sb.length() > 1024 * 1024 * 2) {
                                break;
                            }
                        }
                    }

                    if (sb.length() > 1) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    sb.append("]");
                    final String json = sb.toString();

                    final ObjectMapper mapper = new ObjectMapper();
                    final Object objectJson = mapper.readValue(json, Object.class);

                    mapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), objectJson);
                    break;
                }
                default: {
                    response.sendError(HttpURLConnection.HTTP_BAD_REQUEST, "Unsupported content type: " + displayName);
                }
            }
        } catch (final Throwable t) {
            logger.warn("Unable to format FlowFile content", t);
            response.sendError(HttpURLConnection.HTTP_INTERNAL_ERROR, "Unable to format FlowFile content");
        }
    }

    private String getDisplayName(final String contentType) {
        return switch (contentType) {
            case "application/vnd.jdvn.geometry" -> "geometry";
            case null, default -> null;
        };
    }
}
