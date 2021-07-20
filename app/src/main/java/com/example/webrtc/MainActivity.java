package com.example.webrtc;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.webrtc.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {


    ActivityMainBinding binding;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Constants.isIntiatedNow = true;
        Constants.isCallEnded = true;
        binding.startMeeting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (binding.meetingId.getText().toString().trim().isEmpty())
                    binding.meetingId.setError("Please enter meeting id");
                else {
                    db.collection("calls")
                            .document(binding.meetingId.getText().toString())
                            .get()
                            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                @Override
                                public void onSuccess(DocumentSnapshot documentSnapshot) {
                                    if (documentSnapshot.getString("type") == "OFFER" || documentSnapshot.getString("type") == "ANSWER" || documentSnapshot.getString("type") == "END_CALL") {
                                        binding.meetingId.setError("Please enter new meeting ID");
                                    }
                                    else{
                                        Intent intent = new Intent(MainActivity.this, rtcActivity.class);
                                        intent.putExtra("meetingID", binding.meetingId.getText().toString());
                                        intent.putExtra("isJoin", false);
                                        startActivity(intent);
                                        }
                                    }
                                })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    binding.meetingId.setError("Please enter new meeting ID");
                                }
                            });
                    }
                }

        });

        binding.joinMeeting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (binding.meetingId.getText().toString().trim().isEmpty())
                    binding.meetingId.setError("Please enter meeting id");
                else {
                    Intent intent = new Intent(MainActivity.this, rtcActivity.class);
                    intent.putExtra("meetingID", binding.meetingId.getText().toString());
                    intent.putExtra("isJoin", true);
                    startActivity(intent);
                }
            }
        });


    }
}