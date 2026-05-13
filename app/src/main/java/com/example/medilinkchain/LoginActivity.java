package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    EditText emailEditText, passwordEditText;
    Button loginBtn;

    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginBtn = findViewById(R.id.loginBtn);

        auth = FirebaseAuth.getInstance();

        loginBtn.setOnClickListener(v -> {

            String email = emailEditText.getText().toString();
            String password = passwordEditText.getText().toString();

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if(task.isSuccessful()) {

                            Toast.makeText(LoginActivity.this,
                                    "Login Successful",
                                    Toast.LENGTH_SHORT).show();

                            startActivity(new Intent(
                                    LoginActivity.this,
                                    DashboardActivity.class));

                        } else {

                            Toast.makeText(LoginActivity.this,
                                    "Login Failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}