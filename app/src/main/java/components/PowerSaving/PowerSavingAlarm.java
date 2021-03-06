package components.PowerSaving;

import java.util.LinkedList;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;

import components.Location.AccelerometerValues;
import components.Location.EmbeddedLocationListener;
import components.Service.CollectingService;

import config.Variables.Variables;

public class PowerSavingAlarm extends BroadcastReceiver {
	int count = 0;
	double last_acceleration, max_acceleration;
	float[] gravity;
	long number = 0;
	private static final float ALPHA = 0.8f;
	public static PowerManager pm;
	public static PowerManager.WakeLock wl;

	private SensorEventListener mSensorListener;
	private SensorManager mSensorManager;
	
	LinkedList<float[]> accelerometerValues;

	public PowerSavingAlarm() {

	}

	PowerSavingAlarm(long number) {
		this.number = number;
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		final Context thisContext =context;
		
		accelerometerValues = new LinkedList<float[]>();
		
	/*	if (!CollectingService.secListening)
		EmbeddedLocationListener.getMove();*/

			pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"Maybe it is working");
			wl.acquire();

			CollectingService.stopListening(thisContext);
			
			gravity = new float[3];

			mSensorManager = (SensorManager) context
					.getSystemService(Context.SENSOR_SERVICE);
			mSensorListener = new SensorEventListener() {

				@Override
				public void onAccuracyChanged(Sensor arg0, int arg1) {

				}

				@Override
				public void onSensorChanged(SensorEvent event) {
					count++;
					float[] values = event.values.clone();
					values = lowPassFilter(values[0], values[1], values[2]);
					if (count > 10) {
						accelerometerValues.add(values);
					}

					if (count == 150) {
						AccelerometerValues aV = new AccelerometerValues(
								accelerometerValues);

						if (aV.isTotalIsMoving()) {
							mSensorManager.unregisterListener(mSensorListener);
							if (wl!=null) 
								if (wl.isHeld()) wl.release();
							try {
								CancelAlarm(thisContext);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						
							/// REPLACE THIS WITH SERVICE RESTART LISTENING 
							
							// WORK ON THE LISTENER RATHER THAN ON THE SERVICE z
							try {
								
								//CollectingService.stopSecListening(thisContext);
								
								CollectingService.startListening(thisContext);

							} catch (Exception e) {
								try {
									e.printStackTrace();
									CollectingService.stopListening(thisContext);
								} catch (Exception e2) {
									e2.printStackTrace();
								}
								Intent collectionServiceIntent = new Intent(
										CollectingService.class.getName());
								thisContext.startService(collectionServiceIntent);
								e.printStackTrace();
							}
						} else {
							try {
								CollectingService.stopListening(thisContext);
							} catch (Exception e) {
								e.printStackTrace();
							}
							PowerSavingAlarm alarm = new PowerSavingAlarm();
							alarm.SetAlarm(thisContext);
						}

						mSensorManager.unregisterListener(mSensorListener);
						try {
							if (wl!=null)
								if (wl.isHeld())
									wl.release();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}
				}
			};
			
			EmbeddedLocationListener.getMove();
			boolean isMoving = EmbeddedLocationListener.isMoving;
				if (isMoving)
					CollectingService.startSecondaryListening(thisContext);
			
		/*	if (EmbeddedLocationListener.isMoving)
			{
				try {
					CollectingService.stopListening(thisContext);
					CollectingService.startSecondaryListening(thisContext);
					CancelAlarm(thisContext);
				} catch (Exception e) {
					CollectingService.stopSecListening(thisContext);
					mSensorManager.registerListener(mSensorListener,
							mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
							SensorManager.SENSOR_DELAY_NORMAL);

				}
			}
			else*/
			mSensorManager.registerListener(mSensorListener,
					mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
					SensorManager.SENSOR_DELAY_NORMAL);

	}

	private float[] lowPassFilter(float x, float y, float z) {

		float[] filteredValues = new float[3];

		gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x;
		gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y;
		gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z;

		filteredValues[0] = x - gravity[0];
		filteredValues[1] = y - gravity[1];
		filteredValues[2] = z - gravity[2];
		
		return filteredValues;
	}

	/**
	 * The alarm gets set from within in order to be able to periodically check
	 * for motion
	 * 
	 * @param context
	 */
	public void SetAlarm(Context context) {
		AlarmManager am = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(context, PowerSavingAlarm.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
				+ Variables.acceleromterFrequency, pi);
	}

	/**
	 * The alarm is set from outside and handled by the location listener
	 * 
	 * @param context
	 * @param time
	 */
	public void SetAlarm(Context context, boolean outside) {

		AlarmManager am = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(context, PowerSavingAlarm.class);
		
		/*//EmbeddedLocationListener.getMove();
		i.putExtra("move", EmbeddedLocationListener.isMoving);
		Log.d("MY TAG PUT EXTRA ", EmbeddedLocationListener.isMoving+" ");*/
		
		PendingIntent pi = PendingIntent.getBroadcast(context, 2, i, 0);
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
				+ Variables.accelerometerSleepTime, pi);
		
	}

	/**
	 * Cancels the alarm from within
	 * 
	 * @param context
	 */
	public void CancelAlarm(Context context) {
		Intent intent = new Intent(context, PowerSavingAlarm.class);
		PendingIntent sender = PendingIntent
				.getBroadcast(context, 0, intent, 0);
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(sender);

		/*try {
			wl.release();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}

	/**
	 * Cancels the alarm by the listener
	 * 
	 * @param context
	 * @param time
	 */
	public void CancelAlarm(Context context, boolean time) {
		Intent intent = new Intent(context, PowerSavingAlarm.class);
		PendingIntent sender = PendingIntent
				.getBroadcast(context, 2, intent, 0);
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(sender);
		/*try {
			wl.release();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}
}
