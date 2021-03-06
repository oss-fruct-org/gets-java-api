package org.fruct.oss.gets;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class Category implements Parcelable {

    private static final Map<String, Bitmap> icons = Collections.synchronizedMap(new HashMap<String, Bitmap>());
	// препятствия
	public static final String CURB = "curb"; // бордюр
	public static final String CROSSWALK = "crosswalk"; // пешеходный переход
	public static final String ROUGH_ROAD = "rough_road"; // неровная дорога
	public static final String RAMP = "ramp"; // Пандус
	public static final String SLOPE = "slope"; // уклон дороги
	public static final String STAIRS = "stairs"; // лестница
	public static final String OBJECT_ON_THE_ROAD = "object_on_the_road"; // объект на дороге
	public static final String BUS_STOP = "bus_stop"; // остановка транспорта
	public static final String GATE = "gate"; // ворота
	public static final String NARROW_ROAD = "narrow_road"; // узкая дорога
	public static final String TRAFFIC_LIGHT = "traffic_light"; // светофор


	public static final String UNKNOWN_OBJECT = "unknown_obj";


	private final String name;
	private Map<String, String> localNames;
	private final String description;
	private final String url;
	private final String iconUrl;
	private final int id;
    private boolean isActive;
	private boolean published;

	public Category(String name, String description, String url, String iconUrl, int id, boolean published) {
		this.name = name;
		this.description = description;
		this.url = url;
		this.id = id;
		this.iconUrl = iconUrl;
        if (!icons.containsKey(this.iconUrl)) {
			Log.v(getClass().getSimpleName(), "Search in icons=" + icons.toString());
            icons.put(iconUrl, null);
            new RetrieveIconTask().execute(this.iconUrl);
        }
		this.published = published;
        this.isActive = true;
	}

    public Category(Cursor cursor, int offset) {
		this.id = cursor.getInt(offset);
		this.name = cursor.getString(offset + 1);
		this.description = cursor.getString(offset + 2);
		this.url = cursor.getString(offset + 3);
		this.iconUrl = cursor.getString(offset + 4);
		this.published = cursor.getInt(offset + 5) != 0;
        this.isActive = cursor.getInt(offset + 6) != 0;
        if (!icons.containsKey(this.iconUrl)) {
            Log.v(getClass().getSimpleName(), "Search in icons=" + icons.toString());
            icons.put(iconUrl, null);
            new RetrieveIconTask().execute(this.iconUrl);
        }
    }

	public Category(Parcel source) {
		this.name = source.readString();
		this.description = source.readString();
		this.url = source.readString();
		this.iconUrl = source.readString();
		this.id = source.readInt();
		this.published = source.readInt() != 0;
        this.published = source.readInt() != 0;
        if (!icons.containsKey(this.iconUrl)) {
            Log.v(getClass().getSimpleName(), "Search in icons=" + icons.toString());
            icons.put(iconUrl, null);
            new RetrieveIconTask().execute(this.iconUrl);
        }
	}

	public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

	public String getName() {

		if (localNames != null) {
			//log.debug("Point key = " + "name_" + Locale.getDefault().getLanguage());
			if (localNames.containsKey("name_" + Locale.getDefault().getLanguage()))
				return localNames.get("name_" + Locale.getDefault().getLanguage());
			else
				return localNames.get("name");
		}

		// если не распарсено, то парсим и в хеш
		localNames = new HashMap();
		try {
			JSONObject json = new JSONObject(name);
			Iterator<String> temp = json.keys();
			while (temp.hasNext()) {
				String key = temp.next();
				localNames.put(key, json.get(key).toString());
			}
		} catch (Exception e) {
			//log.debug("Catch exception: " + e.getMessage());
			localNames.put("name", name);
		}
		return name;
	}

	public String getIdentifiedName() {
		if (this.localNames == null) {
			getName();
		}
		switch (this.localNames.get("name")) {
			case "Stairs": return STAIRS;
			case "Object on the road": return OBJECT_ON_THE_ROAD;
			case "Bus stop": return BUS_STOP;
			case "Gate": return GATE;
			case "Traffic light": return TRAFFIC_LIGHT;
			case "Kerb": return CURB;
		}
		return UNKNOWN_OBJECT;
	}

	public String getDescription() {
		return description;
	}

	public String getUrl() {
		return url;
	}
	
	public String getIconUrl() {
		return iconUrl;
	}

	public Bitmap getIcon() {return icons.get(iconUrl); }

	public int getId() {
		return id;
	}

	public boolean isPublished() {
		return published;
	}

	public void setPublished(boolean published) {
		this.published = published;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Category category = (Category) o;

		if (id != category.id) return false;

		return true;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(name);
		dest.writeString(description);
		dest.writeString(url);
		dest.writeString(iconUrl);
		dest.writeInt(id);
		dest.writeInt(published ? 1 : 0);
        dest.writeInt(isActive ? 1 : 0);
	}

	public static final Creator<Category> CREATOR = new Creator<Category>() {
		@Override
		public Category createFromParcel(Parcel source) {
			return new Category(source);
		}

		@Override
		public Category[] newArray(int size) {
			return new Category[size];
		}
	};

	class RetrieveIconTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... src) {
            try {
                URL url = new URL(src[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(input);
                return myBitmap;
            } catch (IOException e) {
                // Log exception
                Log.w(getClass().getSimpleName(), "Error download icon: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap s) {
            super.onPostExecute(s);
            icons.put(iconUrl, s);
            Log.v(getClass().getSimpleName(), "Icon downloaded: " + icons);
        }
    }
}
