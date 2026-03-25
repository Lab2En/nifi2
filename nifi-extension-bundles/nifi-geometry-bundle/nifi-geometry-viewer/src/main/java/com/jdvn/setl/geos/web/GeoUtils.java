package com.jdvn.setl.geos.web;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.avro.Conversions;
import org.apache.avro.Schema.Field;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;

import org.apache.nifi.web.DownloadableContent;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.label.LabelCacheImpl;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.AnchorPoint;
import org.geotools.styling.Displacement;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Fill;
import org.geotools.styling.Graphic;
import org.geotools.styling.LineSymbolizer;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.Stroke;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.style.GraphicalSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import com.wdtinc.mapbox_vector_tile.VectorTile;
import com.wdtinc.mapbox_vector_tile.adapt.jts.IGeometryFilter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.JtsAdapter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TileGeomResult;
import com.wdtinc.mapbox_vector_tile.adapt.jts.UserDataKeyValueMapConverter;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerBuild;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerParams;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerProps;

public class GeoUtils {

    public static final String GEO_CRS = "crs";
    public static final String GEO_ENVELOP = "geo.envelope";
    
	private static final Logger logger = LoggerFactory.getLogger(GeoUtils.class);
    private static final IGeometryFilter ACCEPT_ALL_FILTER = geometry -> true;
    private static final MvtLayerParams DEFAULT_MVT_PARAMS = new MvtLayerParams();
    private static String TEST_LAYER_NAME = "myPolygons";
    
	public static byte[] getVectorTileFromDownloadableContent(final DownloadableContent content, String crs, String envelope, int z, int x, int y) {
		byte[] result = null;
    	// Create BoundingBox from XYZ in EPSG:4326, it is Leaflet projection
		int x0 = x;
		int y0 = y;
		int z0 = z;					
		BoundingBox bb = tile2boundingBox(x0,y0,z0);
		MathTransform transform;
		logger.info("Generating Tile: " + String.valueOf(x0) + "/" + String.valueOf(y0) + "/" + String.valueOf(z0));
		
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
        
		try (final DataFileStream<GenericData.Record> dataFileReader = new DataFileStream<>(content.getContent(), datumReader)) {
			if (crs != null) {
    			CoordinateReferenceSystem crs_source = CRS.parseWKT(crs);
    			
    			transform = CRS.findMathTransform(CRS.decode("EPSG:4326"),crs_source, true); // true for Lenient? to void Bursa-Wolf Parameters Required 
    	        GeometryFactory gf = new GeometryFactory();
    	        Point nw1 = gf.createPoint(new Coordinate(bb.north, bb.west));
    	        Point se1 = gf.createPoint(new Coordinate(bb.south, bb.east));
    	        Point nw = (Point) JTS.transform(nw1, transform);
    	        Point se = (Point) JTS.transform(se1, transform);
    						        
    			double x_i1 = nw.getX();
    			double x_i2 = se.getX();
    			double y_i1 = nw.getY();
    			double y_i2 = se.getY();
    			
    			if (envelope != null) {
    				envelope = envelope.substring(1, envelope.length() - 1);
    				List<String> xy = Arrays.asList(envelope.split(","));					
    				double x_o1 = Double.valueOf(xy.get(0).trim().replace("[", ""));
    				double x_o2 = Double.valueOf(xy.get(1).trim().replace("]", ""));
    				double y_o1 = Double.valueOf(xy.get(2).trim().replace("[", ""));
    				double y_o2 = Double.valueOf(xy.get(3).trim().replace("]", ""));
    				
    				logger.info("GET INTO ENVELOP with " + envelope);
    				logger.info("GET INTO CRS with " + crs_source);
        			
    				// Envelope of original all data
        			ReferencedEnvelope env_o  = new ReferencedEnvelope(x_o1, x_o2, y_o1, y_o2, crs_source); 
        			// Envelope of Tile, make sure its coordinates is same CRS with features
        			ReferencedEnvelope env_t  = new ReferencedEnvelope(x_i1, x_i2, y_i1, y_i2, crs_source);

        			if (env_o.intersects(new Coordinate(x_i1,y_i1), new Coordinate(x_i2,y_i2))) {
        				result = generateMapBoxVectorTiles(dataFileReader, env_t);
        			}    				
    			}	            							
			}
		} catch (IOException | FactoryException | MismatchedDimensionException | TransformException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return result;
	}		
	private static byte[] generateMapBoxVectorTiles(final DataFileStream<GenericData.Record> dataFileReader, ReferencedEnvelope envelopeTile) {
		String geokey = null;		
		boolean bGetType = false;
		boolean bGeometryCollection = false;
		String layerName = TEST_LAYER_NAME;
		WKTReader wktRdr = new WKTReader();		 
		List<Geometry> g_list = new ArrayList<Geometry>();
		Envelope env_t = new Envelope(envelopeTile.getMinX(),envelopeTile.getMaxX(),envelopeTile.getMinY(),envelopeTile.getMaxY());
		while (dataFileReader.hasNext()) {
			final GenericData.Record record = dataFileReader.next();
			geokey = getGeometryFieldName(record);
			if (bGetType == false) {				
				@SuppressWarnings("rawtypes")
				Class geometryClass = getTypeGeometry(record);
				if (geometryClass == MultiLineString.class || geometryClass == LineString.class) {
					layerName = "myLines";
				} 
				else if (geometryClass == MultiPoint.class || geometryClass == Point.class) {
					layerName = "myPoints";
				}
				else if (geometryClass == MultiPolygon.class || geometryClass == Polygon.class) {
					layerName = "myPolygons";
				}
				else if (geometryClass == GeometryCollection.class) {
					layerName = "myPolygons"; // just for client style
					bGeometryCollection = true;
				}
				bGetType = true;
			}
			String wktGeo = record.get(geokey) == null ? null : record.get(geokey) .toString();
			if (wktGeo != null)
				if (!wktGeo.contains("EMPTY")) {
					try {
						Geometry g = wktRdr.read(wktGeo);						
						if (env_t.intersects(g.getEnvelopeInternal())) {
							if (bGeometryCollection == true) {
						        Map<String, Object> attributes = new LinkedHashMap<>();					 
								for (int j = 0; j < record.getSchema().getFields().size(); j++) {
									Field f = record.getSchema().getFields().get(j);
									String value = record.get(f.name()) == null ? null: record.get(f.name()).toString();
									if ((f.name() != geokey && value != null)) {
										attributes.put(f.name(), value);
										
									}
								}
								attributes.put("feature_id", String.valueOf(attributes.hashCode()));
								
								List<Geometry> gc = JtsAdapter.flatFeatureList(g);
								for (int i = 0; i < gc.size(); i++) {
									g_list.add(gc.get(i));
									gc.get(i).setUserData(attributes);
								}																
							}
							else {
								g_list.add(g);
						        Map<String, Object> attributes = new LinkedHashMap<>();					 
								for (int i = 0; i < record.getSchema().getFields().size(); i++) {
									Field f = record.getSchema().getFields().get(i);
									String value = record.get(f.name()) == null ? null: record.get(f.name()).toString();
									if ((f.name() != geokey && value != null)) {
										attributes.put(f.name(), value);
										
									}
								}
								attributes.put("feature_id", String.valueOf(attributes.hashCode()));
								g.setUserData(attributes);
							}
						}							
					} catch (com.vividsolutions.jts.io.ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}			
		}
		com.vividsolutions.jts.geom.GeometryFactory geomFactory = new com.vividsolutions.jts.geom.GeometryFactory();		
		// Use buffer with clip envelope - (10 * 2)% buffered area of the tile envelope
		// to display well geometries belongs many tiles
		double tileWidth  = env_t.getWidth();
		double tileHeight = env_t.getHeight();
		Envelope clipEnvelope = new Envelope(env_t);
        double bufferWidth = tileWidth * .1f;
        double bufferHeight = tileHeight * .1f;
        
        clipEnvelope.expandBy(bufferWidth, bufferHeight);	
        
        TileGeomResult bufferedTileGeom = JtsAdapter.createTileGeom(g_list, env_t, clipEnvelope, geomFactory, DEFAULT_MVT_PARAMS, ACCEPT_ALL_FILTER);
        VectorTile.Tile mvt = encodeMvt(DEFAULT_MVT_PARAMS, bufferedTileGeom, layerName);
        
		return mvt.toByteArray();		
	}	
    private static VectorTile.Tile encodeMvt(MvtLayerParams mvtParams, TileGeomResult tileGeom, String layerName) {
        // Build MVT
        final VectorTile.Tile.Builder tileBuilder = VectorTile.Tile.newBuilder();
        // Create MVT layer
        final VectorTile.Tile.Layer.Builder layerBuilder = MvtLayerBuild.newLayerBuilder(layerName, mvtParams);
        final MvtLayerProps layerProps = new MvtLayerProps();
        //final UserDataIgnoreConverter ignoreUserData = new UserDataIgnoreConverter();
        //final List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, ignoreUserData);
        
        final UserDataKeyValueMapConverter getUserData = new UserDataKeyValueMapConverter();
        // MVT tile geometry to MVT features
        final List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, getUserData);
        layerBuilder.addAllFeatures(features);
        MvtLayerBuild.writeProps(layerBuilder, layerProps);
        // Build MVT layer
        final VectorTile.Tile.Layer layer = layerBuilder.build();
        // Add built layer to MVT
        tileBuilder.addLayers(layer);
        /// Build MVT
        return tileBuilder.build();
    }	
	@SuppressWarnings("rawtypes")
	public static Class getTypeGeometry(GenericData.Record record) {
		Class geometryClass = null; // default
		String geokey = getGeometryFieldName(record);
		String value = record.get(geokey) == null ? null : record.get(geokey).toString().trim();
		if (value != null) {
			if (value.indexOf("(") != -1) {
				value = value.substring(0, value.indexOf("(")).trim();
			} else if (value.contains("EMPTY")) {  // found case EMPTY
				value = value.substring(0, value.indexOf("EMPTY")).trim();
			}
			switch (value) {
				case "MULTILINESTRING":
					geometryClass = MultiLineString.class;
					break;
				case "LINESTRING":
					geometryClass = LineString.class;
					break;
				case "MULTIPOLYGON":
					geometryClass = MultiPolygon.class;
					break;
				case "POLYGON":
					geometryClass = Polygon.class;
					break;
				case "MULTIPOINT":
					geometryClass = MultiPoint.class;
					break;
				case "POINT":
					geometryClass = Point.class;
					break;
				case "GEOMETRYCOLLECTION":
					geometryClass = GeometryCollection.class;
					break;
				default:
					geometryClass = Point.class;
			}
		}
		return geometryClass;
	}		
	public static String getGeometryFieldName(GenericData.Record record) {
		String geoKey = null;
		for (int i = 0; i < record.getSchema().getFields().size(); i++) {
			Field f = record.getSchema().getFields().get(i);
			String value = record.get(f.name()) == null ? null: record.get(f.name()).toString();
			
			if ((value != null) && (value.contains("MULTILINESTRING") || value.contains("LINESTRING") || value.contains("MULTIPOLYGON")
					|| value.contains("POLYGON") || value.contains("POINT") || value.contains("MULTIPOINT")
					|| value.contains("GEOMETRYCOLLECTION"))) {

				geoKey = f.name();
				break;
			}
		}
		return geoKey;

	}	
	public static String getTileZXYFromLatLon(final double lat, final double lon, final int zoom) {
		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
		if (xtile < 0)
			xtile = 0;
		if (xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if (ytile < 0)
			ytile = 0;
		if (ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);
		return ("" + zoom + "/" + xtile + "/" + ytile);
	}
		 
	public static BoundingBox tile2boundingBox(final int x, final int y, final int zoom) {
		BoundingBox bb = new BoundingBox();
		bb.north = tile2lat(y, zoom);
		bb.south = tile2lat(y + 1, zoom);
		bb.west = tile2lon(x, zoom);
		bb.east = tile2lon(x + 1, zoom);
		return bb;
	}

	private static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	private static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}
	static class BoundingBox {
		double north;
		double south;
		double east;
		double west;
	}	
}
