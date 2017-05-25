package org.fruct.oss.gets;

import org.osmdroid.util.GeoPoint;

import java.util.List;

public interface PointsProvider {
	String getProviderName();

	List<Disability> loadDisabilities() throws PointsException;
	List<Category> loadCategories() throws PointsException;
	List<Point> loadPoints(Category category, GeoPoint geoPoint) throws PointsException;

	/**
	 * @return uuid of new point
	 */
	String uploadPoint(Point point) throws PointsException;
}
