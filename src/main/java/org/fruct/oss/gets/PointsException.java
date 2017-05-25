package org.fruct.oss.gets;

public class PointsException extends Exception {
	public PointsException(String s, Throwable ex) {
		super(s, ex);
	}

	public PointsException(String s) {
		super(s);
	}
}
