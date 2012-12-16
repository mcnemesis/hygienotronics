package sanhack.hygienotronics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.client.ClientProtocolException;

import sanhack.hygienotronics.data.GlobalData;
import sanhack.hygienotronics.data.Preferences;
import sanhack.hygienotronics.detection.AggregateLumaMotionDetection;
import sanhack.hygienotronics.detection.IMotionDetection;
import sanhack.hygienotronics.detection.LumaMotionDetection;
import sanhack.hygienotronics.detection.RgbMotionDetection;
import sanhack.hygienotronics.image.ImageProcessing;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.MediaPlayer;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * This class extends Activity to handle a picture preview, process the frame
 * for motion, and then save the file to the SD card.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class MotionDetectionActivity extends SensorsActivity {

	private static final String TAG = "MotionDetectionActivity";

	private static final int NO_PENDING_PAIR = 1;

	private static final int PENDING_PAIR = 2;

	private static final int PAIR_ACTIVE = 3;

	// The agent that raises dust if the reported party
	// is noticed to not have washed their hands!
	private static final String LISTENING_AGENT = "TAP_ROBOT";

	// The agent that alerts the tap monitor for when someone
	// exits the loos
	private static final String REPORTING_AGENT = "TOILET_ROBOT";

	public static final String LISTENER_TRIGGER_COMMAND = "ACTIVATE";



	private static final Uri HANDWASH_ALARM_URI = Uri
			.parse("android.resource://sanhack.hygienotronics/"
					+ R.raw.wash_hands);
	//RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	private static final Uri HANDWASH_REMINDER_URI = Uri
			.parse("android.resource://sanhack.hygienotronics/"
					+ R.raw.dont_forget);

	private static final int INTENT_SETTINGS = 0;

	private static final String STATE_APP_ON = "A";
	private static final String STATE_APP_OFF = "a";
	private static final String STATE_LISTENING = "B";
	private static final String STATE_APP_MOTION = "C";

	

	private static String DASHBOARD_HOST = "192.168.1.10";

	private static String DASHBOARD_PORT = "8080";

	private static SurfaceView preview = null;
	private static SurfaceHolder previewHolder = null;
	private static Camera camera = null;
	private static boolean inPreview = false;
	private static long mReferenceTime = 0;
	private static IMotionDetection detector = null;

	private static volatile AtomicBoolean processing = new AtomicBoolean(false);

	private static int currentMotionState = NO_PENDING_PAIR;

	// what role does this phone assume in the task?
	// private static int activeROLE = REPORTING_AGENT; //as toilet monitor
	private static String activeROLE = LISTENING_AGENT; // as water tap monitor

	private static long timeOfLastTrigger;

	private static boolean connectedToClient;

	// the water-tap monitor... (is the client from the perspective of toilet
	// monitor)
	public static String serverIpAddress = "192.168.1.18";

	public static int serverPort = 2012;

	// the toilet monitor... (is the server from perspective of water monitor)
	public static String clientIpAddress;

	public static int clientPort = serverPort; // let the app ports stay the
												// same

	private Handler handler = new Handler();

	private ServerSocket serverSocket;

	private Thread expectHandWashThread;

	// holds time from when the water monitor starts to wait for hand-washing
	// event to occur
	private static long startExpectingHashWashTime = -1;

	SharedPreferences preferences;

	private boolean pauseMonitoring;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		this.preferences = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());

		loadPreferences();

		dashboardSENDSTATE(STATE_APP_ON);

		preview = (SurfaceView) findViewById(R.id.preview);
		previewHolder = preview.getHolder();
		previewHolder.addCallback(surfaceCallback);
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		if (Preferences.USE_RGB) {
			detector = new RgbMotionDetection();
		} else if (Preferences.USE_LUMA) {
			detector = new LumaMotionDetection();
		} else {
			// Using State based (aggregate map)
			detector = new AggregateLumaMotionDetection();
		}

		clientIpAddress = getLocalIpAddress();

		if (activeROLE.equals(LISTENING_AGENT)) {
			Thread fst = new Thread(new ServerThread());
			fst.start();
		}
	}

	private void loadPreferences() {
		activeROLE = getPrefferedRole();

		serverIpAddress = preferences.getString("WATER_ROBOT_IP",
				serverIpAddress);

		DASHBOARD_HOST = preferences.getString("DASHBOARD_IP", DASHBOARD_HOST);

		DASHBOARD_PORT = preferences
				.getString("DASHBOARD_PORT", DASHBOARD_PORT);
	}

	private void dashboardSENDSTATE(String state) {
		if (activeROLE.equals(REPORTING_AGENT)) {
			forwardStateOverHTTP(state);
		} else {
			if (state.equals(STATE_APP_ON))
				forwardStateOverHTTP("P");
			else if (state.equals(STATE_APP_OFF))
				forwardStateOverHTTP("p");
			else if (state.equals(STATE_APP_MOTION))
				forwardStateOverHTTP("R");
			else if (state.equals(STATE_LISTENING))
				forwardStateOverHTTP("Q");
		}
	}

	private void forwardStateOverHTTP(String state) {
		try {
			Utilities.getHTTP(String.format("http://%s:%s/?c=%s",
					DASHBOARD_HOST, DASHBOARD_PORT, state));
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);

		pauseMonitoring = true;

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem v) {
		switch (v.getItemId()) {
		case R.id.menu_settings: {
			Intent settingsActivity = new Intent(this,
					MyPreferenceActivity.class);
			startActivityForResult(settingsActivity, INTENT_SETTINGS);
			return true;
		}
		case R.id.menu_about: {
			Utilities.showAlert(getString(R.string.app_name),
					getString(R.string.about), R.drawable.hands, this);
			pauseMonitoring = false;
			return true;
		}
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == INTENT_SETTINGS) {
			pauseMonitoring = false;
		}
	}

	private String getPrefferedRole() {
		String role = preferences.getString("ROBOT_MODE", activeROLE);
		if (role.equals("TOILET_ROBOT")) {
			return REPORTING_AGENT;
		} else
			return LISTENING_AGENT;
	}

	public class ServerThread implements Runnable {

		public void run() {
			try {
				if (clientIpAddress != null) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Log.d(TAG, "Listening on " + clientIpAddress + ":"
									+ clientPort);
						}
					});

					serverSocket = new ServerSocket(clientPort);
					while (true) {
						// listen for incoming clients
						Log.d(TAG, "Waiting for Incoming Commands...");
						Socket client = serverSocket.accept();

						Log.d(TAG, "Connected. Parsing Incoming Command...");

						try {
							BufferedReader in = new BufferedReader(
									new InputStreamReader(
											client.getInputStream()));
							String line = null;
							while ((line = in.readLine()) != null) {
								Log.d(TAG, line);
								if (line.equals(LISTENER_TRIGGER_COMMAND)) { // we
																						// have
																						// been
																						// asked
																						// to
																						// start
																				// anticipating
																						// hand-washing
									Log.d(TAG,"StartExpect: " + startExpectingHashWashTime);
									if (startExpectingHashWashTime == -1) { // we
																			// aren't
																			// already
																			// running
											Log.d(TAG,"ACtivating...");								// another
																			// trigger...
										handler.post(new Runnable() {
											@Override
											public void run() {
												triggerExpectHandWashing();
											}
										});
									}
								}
								break;
							}

						} catch (Exception e) {
							handler.post(new Runnable() {
								@Override
								public void run() {
									Log.d(TAG,
											"Oops. Connection interrupted. Please reconnect your phones.");
								}
							});
							e.printStackTrace();
						}

						client.close();
					}
				} else {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Log.d(TAG, "Couldn't detect internet connection.");
						}
					});
				}
			} catch (Exception e) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						Log.d(TAG, "Error");
					}
				});
				e.printStackTrace();
			}
		}
	}

	private void triggerExpectHandWashing() {
		Log.d(TAG, "Triggering HandWashing Expect");
		// first, raise an alert
		dashboardSENDSTATE(STATE_LISTENING);
		startExpectHandWashTimer();
	}

	private void startExpectHandWashTimer() {
		expectHandWashThread = new Thread(new ExpectHandWashThread());
		expectHandWashThread.start();
	}

	// gets the ip address of your phone's network
	private String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.d(TAG, ex.toString());
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onPause() {
		super.onPause();

		camera.setPreviewCallback(null);
		if (inPreview)
			camera.stopPreview();
		inPreview = false;
		camera.release();
		camera = null;

		dashboardSENDSTATE(STATE_APP_OFF);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onResume() {
		super.onResume();

		camera = Camera.open();

		dashboardSENDSTATE(STATE_APP_ON);
	}

	private PreviewCallback previewCallback = new PreviewCallback() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onPreviewFrame(byte[] data, Camera cam) {

			if (pauseMonitoring)// e.g when viewing menu/ preferences...
				return;

			if (data == null)
				return;
			Camera.Size size = cam.getParameters().getPreviewSize();
			if (size == null)
				return;

			if (!GlobalData.isPhoneInMotion()) {
				DetectionThread thread = new DetectionThread(data, size.width,
						size.height);
				thread.start();
			}
		}
	};

	private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				camera.setPreviewDisplay(previewHolder);
				camera.setPreviewCallback(previewCallback);
			} catch (Throwable t) {
				Log.d(TAG, "Exception in setPreviewDisplay()", t);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Camera.Parameters parameters = camera.getParameters();
			Camera.Size size = getBestPreviewSize(width, height, parameters);
			if (size != null) {
				parameters.setPreviewSize(size.width, size.height);
				Log.d(TAG, "Using width=" + size.width + " height="
						+ size.height);
			}
			camera.setParameters(parameters);
			camera.startPreview();
			inPreview = true;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// Ignore
		}
	};

	private boolean handWashingAlarmCancelled = false;

	private static Camera.Size getBestPreviewSize(int width, int height,
			Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if (newArea > resultArea)
						result = size;
				}
			}
		}

		return result;
	}

	private final class DetectionThread extends Thread {

		private byte[] data;
		private int width;
		private int height;

		public DetectionThread(byte[] data, int width, int height) {
			this.data = data;
			this.width = width;
			this.height = height;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			if (!processing.compareAndSet(false, true))
				return;

			// Log.d(TAG, "BEGIN PROCESSING...");
			try {
				// Previous frame
				int[] pre = null;
				if (Preferences.SAVE_PREVIOUS)
					pre = detector.getPrevious();

				// Current frame (with changes)
				// long bConversion = System.currentTimeMillis();
				int[] img = null;
				if (Preferences.USE_RGB) {
					img = ImageProcessing.decodeYUV420SPtoRGB(data, width,
							height);
				} else {
					img = ImageProcessing.decodeYUV420SPtoLuma(data, width,
							height);
				}
				// long aConversion = System.currentTimeMillis();
				// Log.d(TAG, "Converstion="+(aConversion-bConversion));

				// Current frame (without changes)
				int[] org = null;
				if (Preferences.SAVE_ORIGINAL && img != null)
					org = img.clone();

				if (img != null && detector.detect(img, width, height)) {
					// The delay is necessary to avoid taking a picture while in
					// the
					// middle of taking another. This problem can causes some
					// phones
					// to reboot.
					long now = System.currentTimeMillis();
					if (now > (mReferenceTime + Preferences.PICTURE_DELAY)) {
						mReferenceTime = now;

						Bitmap previous = null;
						if (Preferences.SAVE_PREVIOUS && pre != null) {
							if (Preferences.USE_RGB)
								previous = ImageProcessing.rgbToBitmap(pre,
										width, height);
							else
								previous = ImageProcessing.lumaToGreyscale(pre,
										width, height);
						}

						Bitmap original = null;
						if (Preferences.SAVE_ORIGINAL && org != null) {
							if (Preferences.USE_RGB)
								original = ImageProcessing.rgbToBitmap(org,
										width, height);
							else
								original = ImageProcessing.lumaToGreyscale(org,
										width, height);
						}

						Bitmap bitmap = null;
						if (Preferences.SAVE_CHANGES && img != null) {
							if (Preferences.USE_RGB)
								bitmap = ImageProcessing.rgbToBitmap(img,
										width, height);
							else
								bitmap = ImageProcessing.lumaToGreyscale(img,
										width, height);
						}

						Log.i(TAG, "Saving.. previous=" + previous
								+ " original=" + original + " bitmap=" + bitmap);
						Looper.prepare();

						// what should we do with the bitmaps? Just ignore and
						// hack on...
						// new SavePhotoTask().execute(previous, original,
						// bitmap);

						performActionOnMovement();

					} else {
						Log.i(TAG,
								"Not taking picture because not enough time has passed since the creation of the Surface");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				processing.set(false);
			}
			// Log.d(TAG, "END PROCESSING...");

			processing.set(false);
		}
	};

	private void raiseHandWashingAlarm() {
		dashboardSENDSTATE(STATE_APP_ON);
		MediaPlayer mMediaPlayer = MediaPlayer.create(getApplicationContext(), HANDWASH_ALARM_URI);//MediaPlayer.create(MotionDetectionActivity.this,R.raw.dont_forget);
				
		mMediaPlayer.start();

		Log.d(TAG, "HandWashing Alarm RAISED");

		long duration = mMediaPlayer.getDuration();

		// wait for media to play
		long mediaStart = System.currentTimeMillis();
		while (true) {
			long now = System.currentTimeMillis();
			if ((now - mediaStart) > duration)
				break;
		}
		mMediaPlayer.release();
	}

	private static final class SavePhotoTask extends
			AsyncTask<Bitmap, Integer, Integer> {

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected Integer doInBackground(Bitmap... data) {
			for (int i = 0; i < data.length; i++) {
				Bitmap bitmap = data[i];
				String name = String.valueOf(System.currentTimeMillis());
				if (bitmap != null)
					save(name, bitmap);
			}
			return 1;
		}

		private void save(String name, Bitmap bitmap) {
			File photo = new File(Environment.getExternalStorageDirectory(),
					name + ".jpg");
			if (photo.exists())
				photo.delete();

			try {
				FileOutputStream fos = new FileOutputStream(photo.getPath());
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
				fos.close();
			} catch (java.io.IOException e) {
				Log.d(TAG, "Exception in photoCallback", e);
			}
		}
	}

	public class ExpectHandWashThread implements Runnable {

		@Override
		public void run() {
			startExpectingHashWashTime = System.currentTimeMillis();
			while (true) {
				
				long now = System.currentTimeMillis();
				
				if ((now - startExpectingHashWashTime) > Preferences.EXPECT_HANDWASH_DELAY) {
					Log.d(TAG, "Raising Please Wash Hands Alarm!");

					handler.post(new Runnable() {
						@Override
						public void run() {
							if(!handWashingAlarmCancelled) {
								raiseHandWashingAlarm();
							}
							startExpectingHashWashTime = -1; // reset
						}
					});

					break;
				}
			}
		}

	}

	public class ClientThread implements Runnable {

		public void run() {
			try {
				InetAddress serverAddr = InetAddress.getByName(serverIpAddress);
				Log.d(TAG, "C: Connecting...");
				Socket socket = new Socket(serverAddr, serverPort);
				connectedToClient = true;
				try {
					Log.d(TAG, "C: Sending command.");
					PrintWriter out = new PrintWriter(new BufferedWriter(
							new OutputStreamWriter(socket.getOutputStream())),
							true);
					// where you issue the commands
					out.println(LISTENER_TRIGGER_COMMAND);
					Log.d(TAG, "C: Sent.");

				} catch (Exception e) {
					Log.d(TAG, "S: Error", e);
				}
				socket.close();
				Log.d(TAG, "C: Closed.");
				connectedToClient = false;
			} catch (Exception e) {
				Log.d(TAG, "C: Error", e);
				connectedToClient = false;
			}
		}
	}

	public void performActionOnMovement() {
		if (activeROLE.equals(LISTENING_AGENT)) {			
			if(startExpectingHashWashTime > 0) {
				// motion has been detected, thus we postpone raising alert
				dashboardSENDSTATE(STATE_APP_MOTION);
				cancelPendingAlert();
				startExpectingHashWashTime= -1;				
			}
		} else {
			// our role is to activate the listener when necessary...
			Log.d(TAG, "A....");
			if (currentMotionState == NO_PENDING_PAIR) {
				Log.d(TAG, "B....");
				// note this as initial motion event, wait for next to trigger
				// (dude is out!)
				currentMotionState = PENDING_PAIR;
				dashboardSENDSTATE(STATE_LISTENING);
				return;

			} else if (currentMotionState == PENDING_PAIR) {
				Log.d(TAG, "C....");
				// triger the pair event
				dashboardSENDSTATE(STATE_APP_MOTION);
				/**/
				currentMotionState = NO_PENDING_PAIR; //now pair is complete
				triggerMotionComplete();
				hintWashHandReminder();
				dashboardSENDSTATE(STATE_APP_ON);
				/*
				timeOfLastTrigger = System.currentTimeMillis();
				currentMotionState = PAIR_ACTIVE;
				hintWashHandReminder();
				while(true) {
					long now = System.currentTimeMillis();
					if((now - timeOfLastTrigger) > Preferences.SHOW_MOTION_DURATION)
						break;
				}
				dashboardSENDSTATE(STATE_APP_ON);
				currentMotionState = PENDING_PAIR;
				timeOfLastTrigger = 0;*/
				return;
			}
				
			 else {
				Log.d(TAG, "F....");
			}
		}

	}

	private void hintWashHandReminder() {
		MediaPlayer mMediaPlayer = MediaPlayer.create(getApplicationContext(), HANDWASH_REMINDER_URI);//MediaPlayer.create(MotionDetectionActivity.this,R.raw.dont_forget);
		
		mMediaPlayer.start();

		Log.d(TAG, "HandWashing Alarm RAISED");

		long duration = mMediaPlayer.getDuration();

		// wait for media to play
		long mediaStart = System.currentTimeMillis();
		while (true) {
			long now = System.currentTimeMillis();
			if ((now - mediaStart) > duration)
				break;
		}
		
		mMediaPlayer.release();		
	}

	@Override
	public void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		dashboardSENDSTATE(STATE_APP_OFF);
	}

	@Override
	public void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		dashboardSENDSTATE(STATE_APP_ON);
	}

	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
		dashboardSENDSTATE(STATE_APP_ON);
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		dashboardSENDSTATE(STATE_APP_OFF);
	}

	private void cancelPendingAlert() {
		// this will cancels any on-going alerts for expect hand-wash (e.g when
		// handwashing has occured)
		handWashingAlarmCancelled = true;
		expectHandWashThread.stop();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}// just to give a chance for state to toggle well
		dashboardSENDSTATE(STATE_APP_ON);
	}

	private void triggerMotionComplete() {
		// send an alert to the listening agent that it should start
		// anticipating offenders...
		// for simplicity's sake, all we need to do is send an ACTIVATE message
		// to it via configured
		// socket.
		//if (!connectedToClient) {
			if (!serverIpAddress.equals("")) {
				Thread cThread = new Thread(new ClientThread());
				cThread.start();
			}
		//}
	}

}