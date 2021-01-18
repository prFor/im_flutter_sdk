/**
 * Copyright (C) 2016 Hyphenate Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.easemob.im_flutter_sdk_example.call;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.easemob.im_flutter_sdk_example.utils.PhoneStateManager;
import com.easemob.im_flutter_sdk_example.R;
import com.hyphenate.chat.EMCallSession;
import com.hyphenate.chat.EMCallStateChangeListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.exceptions.HyphenateException;
import com.hyphenate.util.EMLog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 语音通话页面
 *
 */
public class VoiceCallActivity extends CallActivity implements OnClickListener {
	private LinearLayout comingBtnContainer;
	private Button hangupBtn;
	private Button refuseBtn;
	private Button answerBtn;
	private ImageView muteImage;
	private ImageView handsFreeImage;

	private boolean isMuteState;
	private boolean isHandsfreeState;

	private TextView callStateTextView;
	private boolean endCallTriggerByMe = false;
	private Chronometer chronometer;
	String st1;
	private LinearLayout voiceContronlLayout;
    private TextView netwrokStatusVeiw;
    private boolean monitor = false;

    private String CallErrorDesc = "未知异常";
    private int CallErrorCode = -1;
    private EMCallSession callSession;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(savedInstanceState != null){
        	finish();
        	return;
        }
		setContentView(R.layout.em_activity_voice_call);

		callType = 0;

        comingBtnContainer = (LinearLayout) findViewById(R.id.ll_coming_call);
		refuseBtn = (Button) findViewById(R.id.btn_refuse_call);
		answerBtn = (Button) findViewById(R.id.btn_answer_call);
		hangupBtn = (Button) findViewById(R.id.btn_hangup_call);
		muteImage = (ImageView) findViewById(R.id.iv_mute);
		handsFreeImage = (ImageView) findViewById(R.id.iv_handsfree);
		callStateTextView = (TextView) findViewById(R.id.tv_call_state);
        TextView nickTextView = (TextView) findViewById(R.id.tv_nick);
        TextView durationTextView = (TextView) findViewById(R.id.tv_calling_duration);
		chronometer = (Chronometer) findViewById(R.id.chronometer);
		voiceContronlLayout = (LinearLayout) findViewById(R.id.ll_voice_control);
		netwrokStatusVeiw = (TextView) findViewById(R.id.tv_network_status);

		refuseBtn.setOnClickListener(this);
		answerBtn.setOnClickListener(this);
		hangupBtn.setOnClickListener(this);
		muteImage.setOnClickListener(this);
		handsFreeImage.setOnClickListener(this);

		addCallStateListener();
		msgid = UUID.randomUUID().toString();

		username = getIntent().getStringExtra("username");
		isInComingCall = getIntent().getBooleanExtra("isComingCall", false);
		nickTextView.setText(username);
		if (!isInComingCall) {// outgoing call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SoundPool.Builder sb = new SoundPool.Builder();
                sb.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                soundPool = sb.build();
            }else {
                soundPool = new SoundPool(1, AudioManager.STREAM_RING, 0);
            }
			outgoing = soundPool.load(this, R.raw.em_outgoing, 1);

			comingBtnContainer.setVisibility(View.INVISIBLE);
			hangupBtn.setVisibility(View.VISIBLE);
			st1 = getResources().getString(R.string.Are_connected_to_each_other);
			callStateTextView.setText(st1);
			handler.sendEmptyMessage(MSG_CALL_MAKE_VOICE);
            handler.postDelayed(new Runnable() {
                public void run() {
                    streamID = playMakeCallSounds();
                }
            }, 300);
        } else { // incoming call
			voiceContronlLayout.setVisibility(View.INVISIBLE);
			Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
			audioManager.setMode(AudioManager.MODE_RINGTONE);
			audioManager.setSpeakerphoneOn(true);
			ringtone = RingtoneManager.getRingtone(this, ringUri);
			ringtone.play();
		}
        final int MAKE_CALL_TIMEOUT = 50 * 1000;
        handler.removeCallbacks(timeoutHangup);
        handler.postDelayed(timeoutHangup, MAKE_CALL_TIMEOUT);
	}

	/**
	 * set call state listener
	 */
	void addCallStateListener() {
	    callStateListener = new EMCallStateChangeListener() {

            @Override
            public void onCallStateChanged(CallState callState, final CallError error) {
                // Message msg = handler.obtainMessage();
                EMLog.d("EMCallManager", "onCallStateChanged:" + callState);
                switch (callState) {

                case CONNECTING:
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            callStateTextView.setText(st1);
                        }
                    });
                    break;
                case CONNECTED:
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            String st3 ="已经和对方建立连接";
                            callStateTextView.setText(st3);
                        }
                    });
                    break;

                case ACCEPTED:
                    handler.removeCallbacks(timeoutHangup);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                if (soundPool != null)
                                    soundPool.stop(streamID);
                            } catch (Exception e) {
                            }
                            if(!isHandsfreeState)
                                closeSpeakerOn();
                            //show relay or direct call, for testing purpose
                            ((TextView)findViewById(R.id.tv_is_p2p)).setText(EMClient.getInstance().callManager().isDirectCall()
                                    ? R.string.direct_call : R.string.relay_call);
                            chronometer.setVisibility(View.VISIBLE);
                            chronometer.setBase(SystemClock.elapsedRealtime());
                            // duration start
                            chronometer.start();
                            String str4 = "通话中……";
                            callStateTextView.setText(str4);
                            callingState = CallingState.NORMAL;
                            startMonitor();
                            // Start to watch the phone call state.
                            PhoneStateManager.get(VoiceCallActivity.this).addStateCallback(phoneStateCallback);

                            Map<String, Object> data = new HashMap<String, Object>();
                            data.put("callid",callSession.getCallId());
                            data.put("ext",callSession.getExt());
                            data.put("serverRecordId",callSession.getServerRecordId());
                            data.put("isRecordOnServer",callSession.isRecordOnServer());
                            data.put("getLocalName",callSession.getLocalName());
                            data.put("getRemoteName",callSession.getRemoteName());
                            data.put("getCallType",callSession.getType() == EMCallSession.Type.VOICE ? 0 : 1);

                            if (callSession.getConnectType() == EMCallSession.ConnectType.NONE){
                                data.put("connectType", 0);
                            }else if (callSession.getConnectType() == EMCallSession.ConnectType.DIRECT){
                                data.put("connectType", 1);
                            }else if (callSession.getConnectType() == EMCallSession.ConnectType.RELAY){
                                data.put("connectType", 2);
                            }
                            EMCallPlugin.onResult(0, data );
                        }
                    });
                    break;
                case NETWORK_UNSTABLE:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            netwrokStatusVeiw.setVisibility(View.VISIBLE);
                            if(error == CallError.ERROR_NO_DATA){
                                netwrokStatusVeiw.setText("没有通话数据");
                            }else{
                                netwrokStatusVeiw.setText("网络不稳定");
                            }
                        }
                    });
                    break;
                case NETWORK_NORMAL:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            netwrokStatusVeiw.setVisibility(View.INVISIBLE);
                        }
                    });
                    break;
                case VOICE_PAUSE:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), "VOICE_PAUSE", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                case VOICE_RESUME:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), "VOICE_RESUME", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                case DISCONNECTED:
                    handler.removeCallbacks(timeoutHangup);
                    @SuppressWarnings("UnnecessaryLocalVariable") final CallError fError = error;
                    runOnUiThread(new Runnable() {
                        private void postDelayedCloseMsg() {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d("AAA", "CALL DISCONNETED");
                                            removeCallStateListener();

                                            // Stop to watch the phone call state.
                                            PhoneStateManager.get(VoiceCallActivity.this).removeStateCallback(phoneStateCallback);

                                            saveCallRecord();
                                            Animation animation = new AlphaAnimation(1.0f, 0.0f);
                                            animation.setDuration(800);
                                            findViewById(R.id.root_layout).startAnimation(animation);
                                            finish();
                                        }
                                    });
                                }
                            }, 200);
                        }

                        @Override
                        public void run() {
                            chronometer.stop();
                            callDruationText = chronometer.getText().toString();

                            if (fError == CallError.REJECTED) {
                                callingState = CallingState.BEREFUSED;
                                CallErrorDesc = "对方拒绝接受！";
                            } else if (fError == CallError.ERROR_TRANSPORT) {
                                CallErrorDesc  = "连接建立失败！";
                            } else if (fError == CallError.ERROR_UNAVAILABLE) {
                                callingState = CallingState.OFFLINE;
                                CallErrorDesc = "对方不在线，请稍后再拨……";
                            } else if (fError == CallError.ERROR_BUSY) {
                                callingState = CallingState.BUSY;
                                CallErrorDesc = "对方正在通话中，请稍后再拨";
                            } else if (fError == CallError.ERROR_NORESPONSE) {
                                callingState = CallingState.NO_RESPONSE;
                                CallErrorDesc = "对方未接听";
                            } else if (fError == CallError.ERROR_LOCAL_SDK_VERSION_OUTDATED ){
                                CallErrorDesc = "通话协议版本不一致";
                                callingState = CallingState.VERSION_NOT_SAME;
                            } else if (fError == CallError.ERROR_REMOTE_SDK_VERSION_OUTDATED){
                                CallErrorDesc = "通话协议版本不一致";
                                callingState = CallingState.VERSION_NOT_SAME;
                            } else {
                                if (isRefused) {
                                    callingState = CallingState.REFUSED;
                                    CallErrorDesc = getResources().getString(R.string.Refused);
                                }
                                else if (isAnswered) {
                                    callingState = CallingState.NORMAL;
                                    if (!endCallTriggerByMe) {
                                        CallErrorDesc = "对方已经挂断";
                                    }
                                } else {
                                    if (isInComingCall) {
                                        callingState = CallingState.UNANSWERED;
                                        CallErrorDesc = getResources().getString(R.string.did_not_answer);
                                    } else {
                                        if (callingState != CallingState.NORMAL) {
                                            callingState = CallingState.CANCELLED;
                                            CallErrorDesc = getResources().getString(R.string.Has_been_cancelled);
                                        }else {
                                            CallErrorDesc = "挂断";
                                        }
                                    }
                                }
                            }
                            callStateTextView.setText(CallErrorDesc);
                            postDelayedCloseMsg();
                        }

                    });
                    break;

                default:
                    break;
                }

            }
        };
		EMClient.getInstance().callManager().addCallStateChangeListener(callStateListener);
	}

    void removeCallStateListener() {
        EMClient.getInstance().callManager().removeCallStateChangeListener(callStateListener);
    }

    PhoneStateManager.PhoneStateCallback phoneStateCallback = new PhoneStateManager.PhoneStateCallback() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:   // 电话响铃
                    break;
                case TelephonyManager.CALL_STATE_IDLE:      // 电话挂断
                    // resume current voice conference.
                    if (isMuteState) {
                        try {
                            EMClient.getInstance().callManager().resumeVoiceTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:   // 来电接通 或者 去电，去电接通  但是没法区分
                    // pause current voice conference.
                    if (!isMuteState) {
                        try {
                            EMClient.getInstance().callManager().pauseVoiceTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_refuse_call:
		    isRefused = true;
		    refuseBtn.setEnabled(false);
		    handler.sendEmptyMessage(MSG_CALL_REJECT);
			break;

		case R.id.btn_answer_call:
		    answerBtn.setEnabled(false);
		    closeSpeakerOn();
            callStateTextView.setText("正在接听...");
			comingBtnContainer.setVisibility(View.INVISIBLE);
            hangupBtn.setVisibility(View.VISIBLE);
            voiceContronlLayout.setVisibility(View.VISIBLE);
            handler.sendEmptyMessage(MSG_CALL_ANSWER);
			break;

		case R.id.btn_hangup_call:
		    hangupBtn.setEnabled(false);
			chronometer.stop();
			endCallTriggerByMe = true;
			callStateTextView.setText("挂断");
            handler.sendEmptyMessage(MSG_CALL_END);
			break;

		case R.id.iv_mute:
			if (isMuteState) {
				muteImage.setImageResource(R.mipmap.em_icon_mute_normal);
                try {
                    EMClient.getInstance().callManager().resumeVoiceTransfer();
                } catch (HyphenateException e) {
                    e.printStackTrace();
                }
				isMuteState = false;
			} else {
				muteImage.setImageResource(R.mipmap.em_icon_mute_on);
                try {
                    EMClient.getInstance().callManager().pauseVoiceTransfer();
                } catch (HyphenateException e) {
                    e.printStackTrace();
                }
				isMuteState = true;
			}
			break;
		case R.id.iv_handsfree:
			if (isHandsfreeState) {
				handsFreeImage.setImageResource(R.mipmap.em_icon_speaker_normal);
				closeSpeakerOn();
				isHandsfreeState = false;
			} else {
				handsFreeImage.setImageResource(R.mipmap.em_icon_speaker_on);
				openSpeakerOn();
				isHandsfreeState = true;
			}
			break;
		default:
			break;
		}
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

	@Override
	public void onBackPressed() {
		callDruationText = chronometer.getText().toString();
	}

    /**
     * for debug & testing, you can remove this when release
     */
    void startMonitor(){
        monitor = true;
        callSession = EMClient.getInstance().callManager().getCurrentCallSession();
        final boolean isRecord = callSession.isRecordOnServer();
        final String serverRecordId = callSession.getServerRecordId();

        EMLog.e(TAG, "server record: " + isRecord );
        if (isRecord) {
            EMLog.e(TAG, "server record id: " + serverRecordId);
        }

        new Thread(new Runnable() {
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String status = getApplicationContext().getString(EMClient.getInstance().callManager().isDirectCall()
                                ? R.string.direct_call : R.string.relay_call);
                        status += " record? " + isRecord;
                        status += " id: " + serverRecordId;

                        ((TextView)findViewById(R.id.tv_is_p2p)).setText(status);
                    }
                });
                while(monitor){
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }, "CallMonitor").start();
    }

}
