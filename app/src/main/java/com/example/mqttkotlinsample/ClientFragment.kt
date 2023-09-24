package com.example.mqttkotlinsample

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.internal.telephony.ITelephony
import org.eclipse.paho.client.mqttv3.*
import java.util.*
import kotlin.concurrent.schedule


class ClientFragment : Fragment() {
    private lateinit var mqttClient : MQTTClient

    // Define your permission request code
    private val MY_PERMISSIONS_REQUEST_CALL_PHONE = 123 // Use any unique integer value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mqttClient.isConnected()) {
                    // Disconnect from MQTT Broker
                    mqttClient.disconnect(object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(this.javaClass.name, "Disconnected")

                            Toast.makeText(context, "MQTT Disconnection success", Toast.LENGTH_SHORT).show()

                            // Disconnection success, come back to Connect Fragment
                            findNavController().navigate(R.id.action_ClientFragment_to_ConnectFragment)
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.d(this.javaClass.name, "Failed to disconnect")
                        }
                    })
                } else {
                    Log.d(this.javaClass.name, "Impossible to disconnect, no server connected")
                }
            }
        })
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_client, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get arguments passed by ConnectFragment
        val serverURI   = arguments?.getString(MQTT_SERVER_URI_KEY)
        val clientId    = arguments?.getString(MQTT_CLIENT_ID_KEY)
        val username    = arguments?.getString(MQTT_USERNAME_KEY)
        val pwd         = arguments?.getString(MQTT_PWD_KEY)

        // Check if passed arguments are valid
        if (    serverURI   != null    &&
                clientId    != null    &&
                username    != null    &&
                pwd         != null        ) {
            // Open MQTT Broker communication
            mqttClient = MQTTClient(context, serverURI, clientId)

            // Connect and login to MQTT Broker
            mqttClient.connect( username,
                    pwd,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(this.javaClass.name, "Connection success")

                            Toast.makeText(context, "MQTT Connection success", Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.d(this.javaClass.name, "Connection failure: ${exception.toString()}")

                            Toast.makeText(context, "MQTT Connection fails: ${exception.toString()}", Toast.LENGTH_SHORT).show()

                            // Come back to Connect Fragment
                            findNavController().navigate(R.id.action_ClientFragment_to_ConnectFragment)
                        }
                    },
                    object : MqttCallback {
                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            val msg = "Receive message: ${message.toString()} from topic: $topic"
                            Log.d(this.javaClass.name, msg)

                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                            if (message.toString() == "endCall") {
                                //end call here
                                if (ContextCompat.checkSelfPermission(
                                        requireContext(),
                                        Manifest.permission.READ_CONTACTS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    // Permission is not granted, request it from the fragment.
                                    requestPermissions(
                                        arrayOf(
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.ANSWER_PHONE_CALLS,
                                            Manifest.permission.READ_PHONE_STATE,
                                            Manifest.permission.READ_CALL_LOG
                                        ),
                                        MY_PERMISSIONS_REQUEST_CALL_PHONE
                                    )
                                } else {
                                    // Permission already granted, proceed to end the call.
                                    var telephonyService: ITelephony
                                    val telephonyManager =
                                        requireContext().getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                                    if (telephonyManager != null) {
                                        // Use telephonyManager to access telephony-related functionality.
                                        try {
                                            val telephonyClass =
                                                Class.forName(telephonyManager.javaClass.name)
//                                            val methodEndCall =
//                                                telephonyClass.getDeclaredMethod("endCall")
                                            val methodEndCall =
                                                telephonyClass.getDeclaredMethod("getITelephony")
                                            methodEndCall.isAccessible = true
                                            telephonyService = methodEndCall.invoke(telephonyManager) as ITelephony
                                            if (telephonyService != null) {
                                                telephonyService.silenceRinger()
                                                telephonyService.endCall()
                                            }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                val telecomManager =
                                                    context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                                telecomManager?.endCall()
                                            }

                                        }catch (e: Exception){
                                            e.printStackTrace();
                                        }

                                        Toast.makeText(context, "End Call", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }else if(message.toString() == "kill"){
                                //kill process
                                val am = requireContext().getSystemService(Activity.ACTIVITY_SERVICE) as? ActivityManager
                                am!!.killBackgroundProcesses("com.sec.phone")

                            }else{
                                //start call here
                                if (ContextCompat.checkSelfPermission(
                                        requireContext(),
                                        Manifest.permission.CALL_PHONE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    // Permission is not granted, request it from the fragment.
                                    requestPermissions(
                                        arrayOf(Manifest.permission.CALL_PHONE),
                                        MY_PERMISSIONS_REQUEST_CALL_PHONE
                                    )
                                } else {
                                    // Permission already granted
                                    //Start Call here
                                    val callIntent: Intent = Uri.parse("tel:"+ message.toString()).let { number ->
                                        Intent(Intent.ACTION_CALL, number)
                                    }
                                    startActivity(callIntent)
                                }
                            }
                        }

                        override fun connectionLost(cause: Throwable?) {
                            Log.d(this.javaClass.name, "Connection lost ${cause.toString()}")
                        }

                        override fun deliveryComplete(token: IMqttDeliveryToken?) {
                            Log.d(this.javaClass.name, "Delivery complete")
                        }
                    })
        } else {
            // Arguments are not valid, come back to Connect Fragment
            findNavController().navigate(R.id.action_ClientFragment_to_ConnectFragment)
        }

        view.findViewById<Button>(R.id.button_prefill_client).setOnClickListener {
            // Set default values in edit texts
            view.findViewById<EditText>(R.id.edittext_pubtopic).setText(MQTT_TEST_TOPIC)
            view.findViewById<EditText>(R.id.edittext_pubmsg).setText(MQTT_TEST_MSG)
            view.findViewById<EditText>(R.id.edittext_subtopic).setText(MQTT_TEST_TOPIC)
        }

        view.findViewById<Button>(R.id.button_clean_client).setOnClickListener {
            // Clean values in edit texts
            view.findViewById<EditText>(R.id.edittext_pubtopic).setText("")
            view.findViewById<EditText>(R.id.edittext_pubmsg).setText("")
            view.findViewById<EditText>(R.id.edittext_subtopic).setText("")
        }

        view.findViewById<Button>(R.id.button_disconnect).setOnClickListener {
            if (mqttClient.isConnected()) {
                // Disconnect from MQTT Broker
                mqttClient.disconnect(object : IMqttActionListener {
                                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                                Log.d(this.javaClass.name, "Disconnected")

                                                Toast.makeText(context, "MQTT Disconnection success", Toast.LENGTH_SHORT).show()

                                                // Disconnection success, come back to Connect Fragment
                                                findNavController().navigate(R.id.action_ClientFragment_to_ConnectFragment)
                                            }

                                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                                Log.d(this.javaClass.name, "Failed to disconnect")
                                            }
                                        })
            } else {
                Log.d(this.javaClass.name, "Impossible to disconnect, no server connected")
            }
        }

        view.findViewById<Button>(R.id.button_publish).setOnClickListener {
            val topic   = view.findViewById<EditText>(R.id.edittext_pubtopic).text.toString()
            val message = view.findViewById<EditText>(R.id.edittext_pubmsg).text.toString()

            if (mqttClient.isConnected()) {
                mqttClient.publish(topic,
                                    message,
                                    1,
                                    false,
                                    object : IMqttActionListener {
                                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                                            val msg ="Publish message: $message to topic: $topic"
                                            Log.d(this.javaClass.name, msg)

                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                            Log.d(this.javaClass.name, "Failed to publish message to topic")
                                        }
                                    })
            } else {
                Log.d(this.javaClass.name, "Impossible to publish, no server connected")
            }
        }

        view.findViewById<Button>(R.id.button_subscribe).setOnClickListener {
            val topic   = view.findViewById<EditText>(R.id.edittext_subtopic).text.toString()

            if (mqttClient.isConnected()) {
                mqttClient.subscribe(topic,
                        1,
                        object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                val msg = "Subscribed to: $topic"
                                Log.d(this.javaClass.name, msg)

                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }

                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.d(this.javaClass.name, "Failed to subscribe: $topic")
                            }
                        })
            } else {
                Log.d(this.javaClass.name, "Impossible to subscribe, no server connected")
            }
        }

        view.findViewById<Button>(R.id.button_unsubscribe).setOnClickListener {
            val topic   = view.findViewById<EditText>(R.id.edittext_subtopic).text.toString()

            if (mqttClient.isConnected()) {
                mqttClient.unsubscribe( topic,
                        object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                val msg = "Unsubscribed to: $topic"
                                Log.d(this.javaClass.name, msg)

                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }

                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.d(this.javaClass.name, "Failed to unsubscribe: $topic")
                            }
                        })
            } else {
                Log.d(this.javaClass.name, "Impossible to unsubscribe, no server connected")
            }
        }
        view.findViewById<Button>(R.id.button_clean_client).isEnabled = false
        view.findViewById<Button>(R.id.button_disconnect).isEnabled = false
        view.findViewById<Button>(R.id.button_publish).isEnabled = false
        view.findViewById<Button>(R.id.button_unsubscribe).isEnabled = false


        view.findViewById<Button>(R.id.button_prefill_client).performClick()
        Timer().schedule(3000) {
            activity?.runOnUiThread {
                view.findViewById<Button>(R.id.button_subscribe).performClick()
            }
        }
    }
}