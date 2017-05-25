package org.fruct.oss.gets.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GetsProperties {
	private Map<String, String> fields = new HashMap<>();

	public GetsProperties() {
	}

	public void addJson(String fieldName, String fieldValue) {
		JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(fieldValue);
		} catch (JSONException e) {
			fields.put(fieldName, fieldValue);
			return;
		}

		Iterator<String> keysIterator = jsonObject.keys();
		while (keysIterator.hasNext()) {
			String key = keysIterator.next();
			try {
				fields.put(key, jsonObject.getString(key));
			} catch (JSONException e) {
				Log.i(getClass().getSimpleName(), "Received non-string gets parameter");
			}
		}
	}

	public String getProperty(String key) {
		return fields.get(key);
	}

	public String getProperty(String key, String defValue) {
		String value = fields.get(key);
		if (value == null) {
			return defValue;
		} else {
			return value;
		}
	}
}
