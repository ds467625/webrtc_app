package com.example.webrtc.rtc;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import com.example.webrtc.observer.PeerConnectionObserver;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class rtcClient {

    private final String TAG = rtcClient.class.getSimpleName();

    private final String LOCAL_TRACK_ID = "local_track";
    private final String LOCAL_STREAM_ID = "local_track";

    private Context context;
    private PeerConnectionObserver peerConnectionObserver;
    private AudioTrack localaudioTrack = null;
    private VideoTrack localvideoTrack = null;
    private SessionDescription sessionDescription = null;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private PeerConnectionFactory peerConnectionFactory;
    private CameraVideoCapturer videoCapturer;
    private VideoSource localVideoSource;
    private AudioSource audioSource;

    private PeerConnection peerConnection;

    private EglBase rootEglBase = EglBase.create();

    private List<PeerConnection.IceServer> iceServer = List.of(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302")
                    .createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302")
                    .createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
                    .createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302")
                    .createIceServer()
    );

    public rtcClient(Context context, PeerConnectionObserver peerConnectionObserver) {
        this.context = context;
        this.peerConnectionObserver = peerConnectionObserver;
        intiPeerConnectionFactory();
    }


    private void intiPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = buildPeerConnectionFactory();
        videoCapturer = getVideoCapturer();
        localVideoSource = peerConnectionFactory.createVideoSource(false);
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        peerConnection = buildPeerConnection(peerConnectionObserver);
    }

    private PeerConnectionFactory buildPeerConnectionFactory() {

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = true;
        options.disableNetworkMonitor = true;
        return PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
                .setOptions(options)
                .createPeerConnectionFactory();
    }

    private PeerConnection buildPeerConnection(PeerConnection.Observer observer) {
        return peerConnectionFactory.createPeerConnection(iceServer, observer);

    }

    public void initSurfaceView(SurfaceViewRenderer viewRenderer) {
        viewRenderer.setMirror(true);
        viewRenderer.setEnableHardwareScaler(true);
        viewRenderer.init(rootEglBase.getEglBaseContext(), null);
    }

    private CameraVideoCapturer getVideoCapturer() {
        Log.e(TAG, "getVideoCapturer: " + context.getPackageName());
//        Log.w(TAG, "Camera2 enumerator supported: " + Camera2Enumerator.isSupported(context));
        Camera2Enumerator enumerator;
        enumerator = new Camera2Enumerator(context);
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.w(TAG, "Creating front facing camera capturer."+deviceName);
                final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    Log.w(TAG, "Found front facing capturer: " + deviceName);
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    public void startLocalVidoeCapture(SurfaceViewRenderer localVideoOutput) {

        Log.e(TAG, "startLocalVidoeCapture: "+videoCapturer);
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
        videoCapturer.initialize(surfaceTextureHelper, localVideoOutput.getContext(), localVideoSource.getCapturerObserver());
        videoCapturer.startCapture(320, 240, 60);
        localaudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource);
        localvideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource);
        localvideoTrack.addSink(localVideoOutput);
        MediaStream localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        localStream.addTrack(localvideoTrack);
        localStream.addTrack(localaudioTrack);
        peerConnection.addStream(localStream);
    }

    public void onCall(SdpObserver observer, String meetingId) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        SdpObserver observer1 = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Map<String,Object> answer = new HashMap<>();
                answer.put("sdp",sessionDescription.description);
                answer.put("type",sessionDescription.type);
                Log.e(TAG, "onCreateSuccess: "+meetingId);

                db.collection("calls").document(meetingId)
                        .set(answer)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Log.d(TAG, "onSuccess: document created");
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: error");

                    }
                });
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {

                    }

                    @Override
                    public void onCreateFailure(String s) {

                    }

                    @Override
                    public void onSetFailure(String s) {

                    }
                }, sessionDescription);
                observer.onCreateSuccess(sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }

            @Override
            public void onCreateFailure(String s) {

            }

            @Override
            public void onSetFailure(String s) {

            }

        };

        peerConnection.createOffer(observer1, constraints);
    }

    public void onAnswer(SdpObserver observer, String meetingId) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        SdpObserver sdpObserver = new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Map<String,Object> answer = new HashMap<>();
                answer.put("sdp",sessionDescription.description);
                answer.put("type",sessionDescription.type);
                Log.e(TAG, "onCreateSuccess: "+meetingId);

                db.collection("calls").document(meetingId)
                        .set(answer)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Log.d(TAG, "onSuccess: document created");
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: error");

                    }
                });
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {

                    }

                    @Override
                    public void onCreateFailure(String s) {

                    }

                    @Override
                    public void onSetFailure(String s) {

                    }
                }, sessionDescription);
                observer.onCreateSuccess(sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }

            @Override
            public void onCreateFailure(String s) {

            }

            @Override
            public void onSetFailure(String s) {

            }
        };

        peerConnection.createAnswer(sdpObserver , constraints);
    }

    public void onRemoteSessionReceived(SessionDescription sessionDescription) {
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.e(TAG, "onSetFailure: $sessionDescription");
            }

            @Override
            public void onSetSuccess() {
                Log.e(TAG, "onSetSuccessRemoteSession");
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "onCreateFailure");
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "onSetFailure: $s");
            }
        }, sessionDescription);
    }

    public void addIceCandiate(IceCandidate iceCandidate) {
        peerConnection.addIceCandidate(iceCandidate);
    }


    public void enableVideo(Boolean videoEnabled) {
        if (localvideoTrack != null)
            localvideoTrack.setEnabled(videoEnabled);
    }

    public void enableAudio(Boolean audioEnabled) {
        if (localaudioTrack != null)
            localaudioTrack.setEnabled(audioEnabled);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void endCall(String meetingId) {
        db.collection("calls").document(meetingId)
                .collection("candidates")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        List<IceCandidate> iceCandidateArray = new ArrayList<>();
                        for (DocumentSnapshot dataSnapshot : queryDocumentSnapshots.getDocuments()) {
                            if (dataSnapshot.contains("type") && dataSnapshot.getString("type") == "offerCandidate") {
                                DocumentSnapshot offerCandidate = dataSnapshot;
                                iceCandidateArray.add(new IceCandidate(offerCandidate.get("sdpMid").toString(), Math.toIntExact(offerCandidate.getLong("sdpMLineIndex")), offerCandidate.get("sdp").toString()));
                            } else if (dataSnapshot.contains("type") && dataSnapshot.getString("type") == "answerCandidate") {
                                DocumentSnapshot answerCandidate = dataSnapshot;
                                iceCandidateArray.add(new IceCandidate(answerCandidate.get("sdpMid").toString(), Math.toIntExact(answerCandidate.getLong("sdpMLineIndex")), answerCandidate.get("sdp").toString()));
                            }
                        }

                        IceCandidate[] candidates = (IceCandidate[]) iceCandidateArray.toArray();
                        peerConnection.removeIceCandidates(candidates);

                    }
                });
        Map<String, Object> endCall = new HashMap<>();
        endCall.put("type", "END_CALL");
        db.collection("calls").document(meetingId)
                .set(endCall)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.e(TAG, "DocumentSnapshot added");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error adding document", e);
                    }
                });
        peerConnection.close();
    }

    public void switchCamera() {
        videoCapturer.switchCamera(null);
    }

}
