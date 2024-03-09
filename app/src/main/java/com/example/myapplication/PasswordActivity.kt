package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PasswordActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)
        ButtonFunc()
    }

    private
    fun ButtonFunc()
    {
        val nextButton: Button = findViewById(R.id.next_button)
        nextButton.setOnClickListener{
            val intent = Intent(this, BirthdayActivity::class.java)
            startActivity(intent)
        }
    }
}