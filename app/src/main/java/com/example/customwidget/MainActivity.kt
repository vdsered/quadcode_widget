package com.example.customwidget

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val polygon = findViewById<RegularPolygonView>(R.id.polygon)

        findViewById<Button>(R.id.collapse).setOnClickListener {
            polygon.collapse()
        }
        findViewById<Button>(R.id.expand).setOnClickListener {
            polygon.expand()
        }

        // Hint: Если выставить значение N=960, то будет что-то похожее на ленту Мебиуса
        findViewById<TextInputEditText>(R.id.count).addTextChangedListener {
            val text = it?.toString()

            val count = text?.toIntOrNull() ?: return@addTextChangedListener
            polygon.iconCount = count
        }

        findViewById<TextInputEditText>(R.id.index).addTextChangedListener {
            val index = it?.toString()?.toIntOrNull() ?: return@addTextChangedListener
            polygon.selectItem = index
        }

        findViewById<Button>(R.id.rotate).setOnClickListener {
            val angle = findViewById<TextInputEditText>(R.id.angle).text?.toString()?.toIntOrNull() ?: return@setOnClickListener
            polygon.rotate(angle.toFloat())
        }
    }
}