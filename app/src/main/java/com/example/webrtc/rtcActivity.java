package com.example.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.webrtc.databinding.ActivityRtcBinding;
import com.example.webrtc.observer.AddSdpObserver;
import com.example.webrtc.observer.PeerConnectionObserver;
import com.example.webrtc.rtc.rtcAudioManager;
import com.example.webrtc.rtc.rtcClient;
import com.example.webrtc.signal.SignalingClientListerner;
import com.example.webrtc.signal.signalClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

public class rtcActivity extends AppCompatActivity {

    private int CAMERA_AUDIO_PERMISSION_REQUEST_CODE = 1;
    private String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private String AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO;

    private final String TAG = rtcActivity.class.getSimpleName();

    private SessionDescription description;

    private rtcClient rtcclient;

    private String meetingID = "test-call";

    private Boolean isJoin = false;

    private Boolean isMute = false;

    private Boolean isVideoPaused = false;

    private Boolean inSpeakerMode = true;

    private signalClient signallingClient;

    private ActivityRtcBinding binding;

    private rtcAudioManager audioManager;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private AddSdpObserver sdpObserver = new AddSdpObserver() {


        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            super.onCreateSuccess(sessionDescription);
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRtcBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        if (getIntent().hasExtra("meetingID"))
            meetingID = getIntent().getStringExtra("meetingID");
        if (getIntent().hasExtra("isJoin"))
            isJoin = getIntent().getBooleanExtra("isJoin", false);


        audioManager = new rtcAudioManager(this);

        checkCameraAndAudioPermission();



        audioManager.selectAudioDevice(rtcAudioManager.AudioDevice.SPEAKER_PHONE);
        binding.switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rtcclient.switchCamera();
            }
        });




        binding.audioOutputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inSpeakerMode) {
                    inSpeakerMode = false;
                    binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24);
                    audioManager.setDefaultAudioDevice(rtcAudioManager.AudioDevice.EARPIECE);
                } else {
                    inSpeakerMode = true;
                    binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24);
                    audioManager.setDefaultAudioDevice(rtcAudioManager.AudioDevice.SPEAKER_PHONE);
                }
            }
        });
        binding.videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isVideoPaused) {
                    isVideoPaused = false;
                    binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24);
                } else {
                    isVideoPaused = true;
                    binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_24);
                }
                rtcclient.enableVideo(isVideoPaused);
            }
        });
        binding.micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMute) {
                    isMute = false;
                    binding.micButton.setImageResource(R.drawable.ic_baseline_mic_off_24);
                } else {
                    isMute = true;
                    binding.micButton.setImageResource(R.drawable.ic_baseline_mic_24);
                }
                rtcclient.enableAudio(isMute);
            }
        });
        binding.endCallButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                rtcclient.endCall(meetingID);
                binding.remoteView.setVisibility(View.GONE);
                Constants.isCallEnded = true;
                finish();
                startActivity(new Intent(rtcActivity.this, MainActivity.class));
            }
        });


    }



    private void checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION)
                        != PackageManager.PERMISSION_GRANTED)) {

        } else {
            onCameraAndAudioPermissionGranted();
        }
    }

    private SignalingClientListerner createSignallingClientListener() {

        return new SignalingClientListerner() {
            @Override
            public void onConnectionEstablished() {
                binding.endCallButton.setClickable(true);
            }

            @Override
            public void onOfferReceived(SessionDescription description) {
                rtcclient.onRemoteSessionReceived(description);
                Constants.isIntiatedNow = false;
                rtcclient.onAnswer(sdpObserver, meetingID);
                binding.remoteViewLoading.setVisibility(View.GONE);
            }

            @Override
            public void onAnswerReceived(SessionDescription description) {
                rtcclient.onRemoteSessionReceived(description);
                Constants.isIntiatedNow = false;
                binding.remoteViewLoading.setVisibility(View.GONE);
            }

            @Override
            public void onIceCandidateReceived(IceCandidate iceCandidate) {
                rtcclient.addIceCandiate(iceCandidate);
            }

            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onCallEnded() {
                if (!Constants.isCallEnded) {
                    Constants.isCallEnded = true;
                    rtcclient.endCall(meetingID);
                    finish();
                    startActivity(new Intent(rtcActivity.this, MainActivity.class));
                }
            }
        };

    }


    private void onCameraAndAudioPermissionGranted() {


        signallingClient = new signalClient(meetingID, createSignallingClientListener());

        rtcclient = new rtcClient(rtcActivity.this, new PeerConnectionObserver() {

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.e(TAG, "onIceCandidate: check");
                signallingClient.sendIceCandidate(iceCandidate, isJoin);
                rtcclient.addIceCandiate(iceCandidate);
            }



            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                Log.e(TAG, "onAddStream: "+mediaStream.toString());
                mediaStream.videoTracks.get(0).addSink(binding.remoteView);
            }

            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                super.onStandardizedIceConnectionChange(newState);
                Log.e(TAG, "onStandardizedIceConnectionChange: "+newState);
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                super.onConnectionChange(newState);
                Log.e(TAG, "onConnectionChange: "+newState);
            }
        });

        rtcclient.initSurfaceView(binding.remoteView);
        rtcclient.initSurfaceView(binding.localView);
        rtcclient.startLocalVidoeCapture(binding.localView);
        if (!isJoin)
            rtcclient.onCall(sdpObserver,meetingID);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        signallingClient.destroy();
    }
}