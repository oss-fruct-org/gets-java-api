package org.fruct.oss.gets;

public interface PointsServiceConnectionListener {
	void onPointsServiceReady(PointsService pointsService);
	void onPointsServiceDisconnected();
}
