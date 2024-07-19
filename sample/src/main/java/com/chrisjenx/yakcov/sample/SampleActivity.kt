package com.chrisjenx.yakcov.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.Email
import com.chrisjenx.yakcov.Required
import com.chrisjenx.yakcov.TextFieldValueValidator
import com.chrisjenx.yakcov.rememberTextFieldValueValidator
import com.chrisjenx.yakcov.sample.ui.theme.YakcovTheme

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YakcovTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {

                        val emailValidator = rememberTextFieldValueValidator(
                            rules = listOf(Required, Email), alwaysShowRule = true
                        )
                        with(emailValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Email") },
                                modifier = Modifier.validateFocusChanged(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                supportingText = supportingText()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YakcovTheme {
        Greeting("Android")
    }
}