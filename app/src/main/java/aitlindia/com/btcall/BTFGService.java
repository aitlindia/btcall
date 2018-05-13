package aitlindia.com.btcall;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.UUID;

import static aitlindia.com.btcall.MainActivity.MESSAGE_READ;

public class BTFGService extends Service{

    private String TAG = "BTFGService";

    private static final String MANUFACTURER_HTC = "HTC";
    public static final String INCOMING_CALL_NAME="incoming_call_name";
    private static final int NOTIFICATION_ID = 1;

    public Handler mHandler; // Our main handler that will receive callback notifications
    public ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    public BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier
    public  BluetoothAdapter mBTAdapter;

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PLAY = "ACTION_PLAY";


    private AudioManager audioManager;
    TelephonyManager telephonyManager;
    UserPrefSettings btcPref;

    public BTFGService() {


    }

    private void showForegroundNotification(String contentText) {
        // Create intent that will bring our app to the front, as if it was tapped in the app
        // launcher
        Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
        showTaskIntent.setAction(Intent.ACTION_MAIN);
        showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                showTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }




    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //return super.onStartCommand(intent, flags, startId);

        Toast.makeText(this,"Start Service",Toast.LENGTH_SHORT).show();

        showForegroundNotification("BT Call Receiver");


        UserPrefSettings.ADDRESS = btcPref.getFromPrefs(getApplicationContext(),UserPrefSettings.PREFS_PAIREDDEVICE_MAC_KEY,"");
        UserPrefSettings.NAME = btcPref.getFromPrefs(getApplicationContext(),UserPrefSettings.PREFS_PAIREDDEVICE_NAME_KEY,"");
        ConnectToDevice( UserPrefSettings.ADDRESS, UserPrefSettings.NAME);


        return START_STICKY;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopForeground(true);
        mConnectedThread.cancel();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MainActivity.MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    //mReadBuffer.setText(readMessage);
                    String command = readMessage.toString().trim();
                    if(command.equals("8")){
                        acceptCall();
                        //acceptCallNew();
                        //acceptCallNewRefection();
                        //acceptCall_4_1();
                        /*
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //sleep();
                                //acceptCall();
                                acceptCallNew();

                            }
                        }).start();
                        */
                        Log.d(TAG,command+" - Accept Incomming Call...");
                    }else if( command.equals("9")){
                        rejectCall();
                        Log.d(TAG,command+" - Reject Incomming Call...");
                    }
                }

                if(msg.what == MainActivity.CONNECTING_STATUS){
                    if(msg.arg1 == 1) {
                        //mBluetoothStatus.setText("Connected to Device: " + (String) (msg.obj));
                    }
                    else {
                        //mBluetoothStatus.setText("Connection Failed");
                    }
                }
            }
        };

    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }

    void  ConnectToDevice(final String address, final String name){

        //final String address = address;
        //final String name = info.substring(0,info.length() - 17);

        Log.d(TAG,"Device Address: "+address+" / Name: "+name);

        // Spawn a new thread to avoid blocking the GUI one
        new Thread()
        {
            public void run() {
                boolean fail = false;

                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                try {
                    mBTSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    fail = true;
                    Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    try {
                        fail = true;
                        mBTSocket.close();
                        mHandler.obtainMessage(MainActivity.CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e2) {
                        //insert code to deal with this
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                }
                if(fail == false) {

                    mConnectedThread = new ConnectedThread(mBTSocket,mHandler);
                    mConnectedThread.start();

                    mHandler.obtainMessage(MainActivity.CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget();
                }
            }
        }.start();
    }


    public void acceptCall() {
        try {
            Method method = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder binder = (IBinder) method.invoke(null, new Object[]{Context.TELEPHONY_SERVICE});
            ITelephony telephony = ITelephony.Stub.asInterface(binder);
            telephony.answerRingingCall();
        } catch (Exception e) {
            Log.e(TAG, "for version 4.1 or larger");
            acceptCall_4_1();
            try{

            }catch ( Exception eex){

                Log.e(TAG,"",eex);
            }

        }
    }

    public void acceptCall_4_1() {

        // Simulate the wireless headset button to answer the call
        // for HTC devices we need to broadcast a connected headset
        boolean broadcastConnected = MANUFACTURER_HTC.equalsIgnoreCase(Build.MANUFACTURER)
                && !audioManager.isWiredHeadsetOn();
        if (broadcastConnected) {
            //broadcastHeadsetConnected(false);
        }
        try {
            try {
                Runtime.getRuntime().exec("input keyevent " +
                        Integer.toString(KeyEvent.KEYCODE_HEADSETHOOK));
            } catch (IOException e) {
                // Runtime.exec(String) had an I/O problem, try to fall back
                String enforcedPerm = "android.permission.CALL_PRIVILEGED";
                Intent btnDown = new Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                        Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_HEADSETHOOK));
                Intent btnUp = new Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                        Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_HEADSETHOOK));
                //view.getActivity().sendOrderedBroadcast(btnDown, enforcedPerm);
                //view.getActivity().sendOrderedBroadcast(btnUp, enforcedPerm);
            }
        } finally {
            if (broadcastConnected) {
                // broadcastHeadsetConnected(false);
            }
        }
    }

    public void acceptCallNew(){

        TelecomManager tm = (TelecomManager) getApplicationContext().getSystemService(Context.TELECOM_SERVICE);

        if (tm == null) {
            // whether you want to handle this is up to you really
            throw new NullPointerException("tm == null");
        }

        tm.acceptRingingCall();
    }

    public void acceptCallNewRefection(){

        // set the logging tag constant; you probably want to change this
        final String LOG_TAG = "TelephonyAnswer";

        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

        try {
            if (tm == null) {
                // this will be easier for debugging later on
                throw new NullPointerException("tm == null");
            }

            // do reflection magic
            tm.getClass().getMethod("answerRingingCall").invoke(tm);
        } catch (Exception e) {
            // we catch it all as the following things could happen:
            // NoSuchMethodException, if the answerRingingCall() is missing
            // SecurityException, if the security manager is not happy
            // IllegalAccessException, if the method is not accessible
            // IllegalArgumentException, if the method expected other arguments
            // InvocationTargetException, if the method threw itself
            // NullPointerException, if something was a null value along the way
            // ExceptionInInitializerError, if initialization failed
            // something more crazy, if anything else breaks

            // TODO decide how to handle this state
            // you probably want to set some failure state/go to fallback
            Log.e(LOG_TAG, "Unable to use the Telephony Manager directly.", e);
        }
    }



    //@Override
    public void rejectCall() {
        try {
            Method method = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class);
            IBinder binder = (IBinder) method.invoke(null, new Object[]{Context.TELEPHONY_SERVICE});
            ITelephony telephony = ITelephony.Stub.asInterface(binder);
            telephony.endCall();
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "", e);
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "", e);
        } catch (Exception e) {
        }
    }

}
