package org.fruct.oss.gets;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.NonNull;

public class PointsServiceConnection implements ServiceConnection {
	private final PointsServiceConnectionListener listener;

	private PointsService pointsService;

	public PointsServiceConnection(@NonNull PointsServiceConnectionListener listener) {
		this.listener = listener;
	}

	public void bindService(@NonNull Context context) {
		Intent intent = new Intent(context, PointsService.class);
		context.bindService(intent, this, Context.BIND_AUTO_CREATE);
	}

	public void unbindService(@NonNull Context context) {
		context.unbindService(this);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		pointsService = ((PointsService.Binder) service).getService();
		listener.onPointsServiceReady(pointsService);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		listener.onPointsServiceDisconnected();
	}
}