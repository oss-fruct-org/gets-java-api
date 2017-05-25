package org.fruct.oss.gets;

import android.database.Cursor;

public interface Request<T> {
	Cursor doQuery();
	T cursorToObject(Cursor cursor);
	int getId(T t);
}
