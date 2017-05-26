package org.fruct.oss.gets;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import org.fruct.oss.gets.annotations.Blocking;
import org.fruct.oss.gets.api.GetsProvider;
import org.fruct.oss.gets.utils.Function;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class PointsService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

	public static final int POINT_UPDATE_INTERVAL = 60 * 3600;
	public static final int POINT_UPDATE_DISTANCE = 1000;

	public static final String PREF_GETS_TOKEN = "pref-gets-token";
    public static final String PREF_LAST_POINTS_UPDATE_TIMESTAMP = "pref-last-points-update-timestamp";
	public static final String PREF_LAST_UPDATE = "pref_last_update";

	private final Binder binder = new Binder();

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	private Handler handler;
	private SharedPreferences pref;

	private PointsDatabase database;

	// Tasks
	private Future<?> refreshProvidersTask;
	private Future<?> synchronizationTask;

	private Location lastLocation;

    private String serverUrl;

	public PointsService() { }

    @Override
    public IBinder onBind(Intent intent) {
		return binder;
    }

	@Override
	public void onCreate() {
		super.onCreate();

		database = new PointsDatabase(this);
		handler = new Handler(Looper.getMainLooper());

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		pref.registerOnSharedPreferenceChangeListener(this);

		synchronize();

		Log.i(getClass().getSimpleName(), "created");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handler.removeCallbacks(stopRunnable);
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		if (refreshProvidersTask != null && !refreshProvidersTask.isDone())
			refreshProvidersTask.cancel(true);

		if (synchronizationTask != null && !synchronizationTask.isDone())
			synchronizationTask.cancel(true);

		pref.unregisterOnSharedPreferenceChangeListener(this);

		executor.execute(new Runnable() {
			@Override
			public void run() {
				database.close();
			}
		});
		executor.shutdownNow();

		Log.i(getClass().getSimpleName(), "destroyed");
		super.onDestroy();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		handler.postDelayed(stopRunnable, 10000);
		return true;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
		handler.removeCallbacks(stopRunnable);
	}

	private Runnable stopRunnable = new Runnable() {
		@Override
		public void run() {
			stopSelf();
		}
	};

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(PREF_GETS_TOKEN)) {
			String token = sharedPreferences.getString(key, null);
			if (token != null) {
				synchronize();
				refresh();
			}
		}
	}

	public void commitRefreshTimeAndLocation(long timestamp, @Nullable GeoPoint location) {
		SharedPreferences appPref = PreferenceManager.getDefaultSharedPreferences(this);

		appPref.edit().putLong(PREF_LAST_POINTS_UPDATE_TIMESTAMP, timestamp).apply();
		if (location != null) {
			appPref.edit().putFloat(PREF_LAST_UPDATE + "_lat", (float) location.getLatitude())
                    .putFloat(PREF_LAST_UPDATE + "_lon", (float) location.getLongitude()).apply();
		}
	}

	public void refreshIfNeed() {
		if (lastLocation == null) {
			return;
		}

		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();

		if (networkInfo == null || !networkInfo.isConnected()) {
			return;
		}

		SharedPreferences appPref = PreferenceManager.getDefaultSharedPreferences(this);

		long lastUpdateTime = appPref.getLong(PREF_LAST_POINTS_UPDATE_TIMESTAMP, -1);
		long currentTime = System.currentTimeMillis();

		GeoPoint currentLocation = new GeoPoint(lastLocation);
        float lat = pref.getFloat(PREF_LAST_UPDATE + "_lat", 0.0f);
        float lon = pref.getFloat(PREF_LAST_UPDATE + "_lon", 0.0f);

        GeoPoint lastLocation = new GeoPoint(lat, lon);

		if (lastUpdateTime < 0 || currentTime - lastUpdateTime > POINT_UPDATE_INTERVAL
				|| lastLocation == null || currentLocation.distanceTo(lastLocation) > POINT_UPDATE_DISTANCE) {
			refresh();
		}
	}

	public void refresh() {
		if (refreshProvidersTask != null && !refreshProvidersTask.isDone())
			refreshProvidersTask.cancel(true);

		if (lastLocation == null) {
			return;
		}
		final GeoPoint geoPoint = new GeoPoint(lastLocation);

		refreshProvidersTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					Log.i(getClass().getSimpleName(), "Starting points refresh for geoPoint " + geoPoint);
					long refreshStartTime = System.nanoTime();
					commitRefreshTimeAndLocation(System.currentTimeMillis(), geoPoint);
					refreshRemote(geoPoint);
					long refreshEndTime = System.nanoTime();
					Log.i(getClass().getSimpleName(), "Points refresh time: " + (refreshEndTime - refreshStartTime) * 1e-9f);

					notifyDataUpdated(true);

				} catch (Exception ex) {
					// TODO: refreshRemote should throw specific checked exception
					Log.e(getClass().getSimpleName(), "Cannot refresh provider: " + ex);
					notifyDataUpdateFailed(ex);
				}
			}
		});
	}

	public void synchronize() {
		if (synchronizationTask != null && !synchronizationTask.isDone())
			return;

		synchronizationTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					doSynchronize();
					Log.i(getClass().getSimpleName(), "Points successfully synchronized");
				} catch (PointsException ex) {
					Log.e(getClass().getSimpleName(), "Points synchronization failed: " + ex);
					// TODO: report user
				}
			}
		});
	}

	public void setLocation(Location location) {
		this.lastLocation = location;

		refreshIfNeed();
	}

	@Blocking
	private void doSynchronize() throws PointsException {
		PointsProvider provider = setupProvider();
		Cursor pointsToUpload = database.loadNotSynchronizedPoints();

		while (pointsToUpload.moveToNext()) {
			Point point = new Point(pointsToUpload, 1);
			String newUuid = provider.uploadPoint(point);
			database.markAsUploaded(point, newUuid);
		}

		pointsToUpload.close();
	}

	public void notifyDataUpdated(final boolean isRemoteUpdate) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.onDataUpdated(isRemoteUpdate);
				}
			}
		});
	}

	private void notifyDataUpdateFailed(final Throwable throwable) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.onDataUpdateFailed(throwable);
				}
			}
		});
	}

	public void queryCursor(final Request<?> request, final Function<Cursor> callback) {
		new AsyncTask<Void, Void, Cursor>() {
			@Override
			protected Cursor doInBackground(Void... params) {
				return request.doQuery();
			}

			@Override
			protected void onPostExecute(Cursor cursor) {
				callback.call(cursor);
			}
		}.execute();
	}

	@Blocking
	public Cursor queryCursor(Request<?> request) {
		return request.doQuery();
	}

	@Blocking
	public <T> List<T> queryList(Request<T> request) {
		Cursor cursor = queryCursor(request);

		List<T> ret = new ArrayList<>();
		while (cursor.moveToNext()) {
			ret.add(request.cursorToObject(cursor));
		}
		return ret;
	}

	public Request<Category> requestCategories() {
		return new Request<Category>() {
			@Override
			public Cursor doQuery() {
				return database.loadCategories();
			}

			@Override
			public Category cursorToObject(Cursor cursor) {
				return new Category(cursor, 0);
			}

			@Override
			public int getId(Category category) {
				return category.getId();
			}
		};
	}

	public Request<Point> requestPoints() {
		return new Request<Point>() {
			@Override
			public Cursor doQuery() {
				return database.loadPoints();
			}

			@Override
			public Point cursorToObject(Cursor cursor) {
				return new Point(cursor, 1);
			}

			@Override
			public int getId(Point point) {
				throw new UnsupportedOperationException("Point doesn't has public id");
			}
		};
	}

	public Request<Point> requestPrivatePoints() {
		return new Request<Point>() {
			@Override
			public Cursor doQuery() {
				return database.loadPrivatePoints();
			}

			@Override
			public Point cursorToObject(Cursor cursor) {
				return new Point(cursor, 1);
			}

			@Override
			public int getId(Point point) {
				throw new UnsupportedOperationException("Point doesn't has public id");
			}
		};
	}

	public Request<Disability> requestDisabilities() {
		return new Request<Disability>() {
			@Override
			public Cursor doQuery() {
				return database.loadDisabilities();
			}

			@Override
			public Disability cursorToObject(Cursor cursor) {
				return new Disability(cursor);
			}

			@Override
			public int getId(Disability disability) {
				throw new UnsupportedOperationException("Point doesn't has public id");
			}
		};
	}

	@Blocking
	private void refreshRemote(GeoPoint geoPoint) throws PointsException {
		PointsProvider pointsProvider = setupProvider();
		
		List<Disability> disabilities = pointsProvider.loadDisabilities();
		if (disabilities != null) {
			database.setDisabilities(disabilities);
		}

		List<Category> categories;
		categories = pointsProvider.loadCategories();

		for (Category category : categories) {
			database.insertCategory(category);

			if (Thread.currentThread().isInterrupted()) {
				return;
			}

			List<Point> points;
			try {
				Log.v(getClass().getSimpleName(), "Loading points for category " + category.getName());
				points = pointsProvider.loadPoints(category, geoPoint);
				Log.v(getClass().getSimpleName(), "Points loaded, size=" + points.size());
			} catch (PointsException ex) {
				continue;
			}

			//log.debug("Inserting points to database");
			database.insertPoints(points);
			//log.debug("Points inserted to database");
		}
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void setServerUrl(String uri) {
        serverUrl = uri;
    }

	private PointsProvider setupProvider() {
		SharedPreferences appPref = PreferenceManager.getDefaultSharedPreferences(this);

		GetsProvider getsProvider;
        if (serverUrl == null) throw new NullPointerException("GeTS server url not defined");
		getsProvider = new GetsProvider(appPref.getString(PREF_GETS_TOKEN, null), serverUrl);
		return getsProvider;
	}

	public void addPoint(Point point) {
		database.insertPoint(point);
		notifyDataUpdated(false);
		synchronize();
	}

	public void addCategory(Category category) {
		database.insertCategory(category);
	}

	public void setDisabilityState(Disability disability, boolean isActive) {
		database.setDisabilityState(disability, isActive);
	}

	public void setCategoryState(Category category, boolean isActive) {
		database.setCategoryState(category, isActive);
	}

	public void commitDisabilityStates() {
		notifyDataUpdated(false);
	}

	public class Binder extends android.os.Binder {
		public PointsService getService() {
			return PointsService.this;
		}
	}

	public interface Listener {
		void onDataUpdated(boolean isRemoteUpdate);
		void onDataUpdateFailed(Throwable throwable);
	}
}
