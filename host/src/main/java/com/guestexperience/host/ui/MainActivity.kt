package com.guestexperience.host.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.guestexperience.host.hostContainer

class MainActivity : ComponentActivity() {
    private val vm: HostViewModel by viewModels {
        viewModelFactory { initializer { HostViewModel(hostContainer) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HostTheme { HostRoot(vm) }
        }
    }

    // DNS-SD discovery runs only while the app is visible.
    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}
