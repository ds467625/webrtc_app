package com.example.webrtc.signal;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.webrtc.Constants;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

public class signalClient {

    private String meetingID;
    private SignalingClientListerner listener;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final String TAG = signalClient.class.getSimpleName();


    String SDPtype = null;

    public signalClient(String meetingID, SignalingClientListerner listener) {
        this.meetingID = meetingID;
        this.listener = listener;
        connect();
    }

    private void connect() {
        db.enableNetwork().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                listener.onConnectionEstablished();
            }
        });


            Log.e(TAG, "connect: starting...");
            db.collection("calls")
                    .document(meetingID)
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {

                            if (error != null) {
                                Log.w(TAG, "listen:error", error);
                                return;
                            }

                            Log.e(TAG, "onEvent: running");

                            if (value != null && value.exists()) {
                                Map<String, Object> data = value.getData();

                                if (data.containsKey("type") && data.get("type").toString().equals("OFFER")) {
                                    Log.e(TAG, "onEvent: "+data.get("type").toString());
                                    listener.onOfferReceived(new SessionDescription(
                                            SessionDescription.Type.OFFER, data.get("sdp").toString()));
                                    SDPtype = "Offer";
                                } else if (data.containsKey("type") &&
                                        data.get("type").toString().equals("ANSWER")) {
                                    listener.onAnswerReceived(new SessionDescription(
                                            SessionDescription.Type.ANSWER, data.get("sdp").toString()));
                                    SDPtype = "Answer";
                                } else if (!Constants.isIntiatedNow && data.containsKey("type") &&
                                        data.get("type").toString().equals("END_CALL")) {
                                    listener.onCallEnded();
                                    SDPtype = "End Call";

                                }

                            } else {
                                Log.d(TAG, "Current data: null");
                            }
                        }
                    });


            db.collection("calls").document(meetingID)
                    .collection("candidates")
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                            if (error != null) {
                                Log.w(TAG, "listen:error", error);
                                return;
                            }

//                            Log.e(TAG, "onEvent: 2nd running"+SDPtype);


                                for (DocumentSnapshot dataSnapShot : value.getDocuments()) {

                                    Log.e(TAG, "onEvent: "+dataSnapShot);

                                    Map<String, Object> data = dataSnapShot.getData();
                                    if (SDPtype.equals("Offer") && data.containsKey("type") && data.get("type").toString().equals("offerCandidate")) {
                                        listener.onIceCandidateReceived(
                                                new IceCandidate(data.get("sdpMid").toString(),
                                                        Math.toIntExact((Long) data.get("sdpMLineIndex")),
                                                        data.get("sdpCandidate").toString()));
                                    } else if (SDPtype.equals("Answer") && data.containsKey("type") && data.get("type").toString().equals("answerCandidate")) {
                                        listener.onIceCandidateReceived(
                                                new IceCandidate(data.get("sdpMid").toString(),
                                                        Math.toIntExact((Long) data.get("sdpMLineIndex")),
                                                        data.get("sdpCandidate").toString()));
                                    }
//                                    Log.e(TAG, "candidateQuery: "+dataSnapShot);
                                }

                        }
                    });



    }

    public void sendIceCandidate(IceCandidate candidate, Boolean isJoin) {
        String type;
        if (isJoin) {
            type = "answerCandidate";
        } else {
            type = "offerCandidate";
        }
        Map<String, Object> candidateConstant = new HashMap<>();
        candidateConstant.put("serverUrl", candidate.serverUrl);
        candidateConstant.put("sdpMid", candidate.sdpMid);
        candidateConstant.put("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateConstant.put("sdpCandidate", candidate.sdp);
        candidateConstant.put("type", type);

        db.collection("calls")
                .document(meetingID).collection("candidates").document(type)
                .set(candidateConstant)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.e(TAG, "sendIceCandidate: Success");
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "sendIceCandidate: Error $e");
            }
        });
    }

    public void destroy() {

    }

}
