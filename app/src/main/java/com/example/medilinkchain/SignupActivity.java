package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SignupActivity extends AppCompatActivity {

    EditText signupEmail, signupPassword;
    Button signupBtn;

    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        signupEmail = findViewById(R.id.signupEmail);
        signupPassword = findViewById(R.id.signupPassword);
        signupBtn = findViewById(R.id.signupBtn);

        auth = FirebaseAuth.getInstance();

        signupBtn.setOnClickListener(v -> {

            String email = signupEmail.getText().toString();
            String password = signupPassword.getText().toString();

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if(task.isSuccessful()) {

                            Toast.makeText(
                                    SignupActivity.this,
                                    "Signup Successful",
                                    Toast.LENGTH_SHORT
                            ).show();

                            startActivity(
                                    new Intent(
                                            SignupActivity.this,
                                            LoginActivity.class
                                    )
                            );

                        } else {

                            Toast.makeText(
                                    SignupActivity.this,
                                    "Signup Failed",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                    });

        });

    }
}