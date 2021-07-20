package com.example.webrtc.signal;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public interface SignalingClientListerner {
    public void onConnectionEstablished();
    public void onOfferReceived(SessionDescription description);
    public void onAnswerReceived(SessionDescription description);
    public void onIceCandidateReceived(IceCandidate iceCandidate);
    public void onCallEnded();
}
