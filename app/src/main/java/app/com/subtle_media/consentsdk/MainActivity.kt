package app.com.subtle_media.consentsdk

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.ads.consent.*
import kotlinx.android.synthetic.main.activity_main.*
import java.net.MalformedURLException
import java.net.URL

class MainActivity : AppCompatActivity() {

    /* AdMob Publisher ID */
    private val PUBLISHER_ID = "pub-4477832346960282"

    /* Privacy Policy URL which will be display in the consent form */
    private val PRIVACY_POLICY_URL = "http://google.com"

    /* Consent for for showing the the user */
    private lateinit var consentForm: ConsentForm

    /* SharedPreferences for saving the status locally */
    private val sharedPrefs by lazy {
        this.getPreferences(Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestConsentInfoUpdate() // Called at every app launch to request consent status
        setupClickListeners() // Buttons for testing only
    }

    /*
    * Called at every app launch.
    * We will request the consent status of the user and process accordingly.
    */
    private fun requestConsentInfoUpdate() {
        val consentInformation = ConsentInformation.getInstance(this)
        Log.d("Consent Status", consentInformation.consentStatus.toString())
        Log.d("Location", consentInformation.debugGeography.toString())
        val publishers = arrayOf(PUBLISHER_ID) // Update with your id

        consentInformation.requestConsentInfoUpdate(publishers, object : ConsentInfoUpdateListener {

            /*
            * The [@consentStatus] was successfully fetched from the SDK. We now need to determine
            * where the request came from by calling
            * [consentInformation.isRequestLocationInEeaOrUnknown]
            *
            * If [@isRequestLocationInEeaOrUnknown] returns true, the user is in the European
            * Economic Area, and we need to process their status with [processConsentStatus].
            *
            * if [@isRequestLocationInEeaOrUnknown] returns false, the user is outside of the
            * European Economic Area, so we can send requests to the ad servers as normal.
            */
            override fun onConsentInfoUpdated(consentStatus: ConsentStatus?) {
                if (consentInformation.isRequestLocationInEeaOrUnknown) {
                    processConsentStatus(consentStatus)
                } else {
                    setPersonalisedAdsAllowed(true)
                    consentInformation.consentStatus = ConsentStatus.PERSONALIZED
                }
            }

            /*
           * The [@consentStatus] failed to update
           */
            override fun onFailedToUpdateConsentInfo(reason: String?) {
                Log.e("onFailedToUpdateConsentInfo()", reason)
            }
        })
    }

    private fun processConsentStatus(consentStatus: ConsentStatus?) {
        when (consentStatus) {
            ConsentStatus.UNKNOWN -> {
                /*
                * The user has neither granted nor declined consent for
                * personalized or non-personalized ads.
                *
                * We will load a consent form, ready to show to this user.
                */
                loadConsentForm()
            }
            ConsentStatus.NON_PERSONALIZED -> {
                /*
                * The user has granted consent for non-personalized ads.
                *
                * Lets save this in sharedPreferences for easy access.
                */
                setPersonalisedAdsAllowed(false)
            }
            ConsentStatus.PERSONALIZED -> {
                /*
                * The user has granted consent for personalized ads.
                *
                * Lets save this in sharedPreferences for easy access.
                */
                setPersonalisedAdsAllowed(true)
            }
        }
    }

    /*
    * Load a consent form instance and handle the users choice in callbacks.
    *
    * [onConsentFormLoaded()] - The consent form was successfully loaded. Let's show it
    * with a call to [showConsentForm()]
    *
    * [onConsentFormOpened()] - The consent form was opened. We don't need tro do anything here.
    *
    * [onConsentFormClosed()] - The consent form was closed after the user made a choice.
    *
    * [onConsentFormError()] - The consent form failed to load. The [@errorDescription] parameter
    * provides a description of the error. This usually happens if the user is not in the EU,
    * therefore we will set the consent status to PERSONALISED. This is a matter of opinion,
    * of course, and you can set to NON_PERSONALISED if you wish.
    */
    private fun loadConsentForm() {
        var privacyUrl: URL? = null
        try {
            privacyUrl = URL(PRIVACY_POLICY_URL)
        } catch (e: MalformedURLException) {
            Log.e("displayConsentForm()", e.toString())
        }
        consentForm = ConsentForm.Builder(this, privacyUrl).withListener(
            object : ConsentFormListener() {

                override fun onConsentFormLoaded() {
                    // The form is loaded, let's show it
                    showConsentForm()
                }

                override fun onConsentFormOpened() {
                    // Not implemented in this example
                }

                override fun onConsentFormClosed(
                    consentStatus: ConsentStatus,
                    userPrefersAdFree: Boolean
                ) {
                    // Form closed, let's process the users choice
                    handleConsentFormClosed(consentStatus, userPrefersAdFree)
                }

                override fun onConsentFormError(errorDescription: String) {
                    // Error, default to NON_PERSONALISED
                    setPersonalisedAdsAllowed(false)
                }

            }
        )
            .withPersonalizedAdsOption() // Offer users a PERSONALISED option
            .withNonPersonalizedAdsOption() // Offer users a NON_PERSONALISED option
            .withAdFreeOption() // Offer users an ad-free option
            .build()
        consentForm.load()
    }

    /*
    * The [@consentForm] is now loaded, let's show it to the user.
    */
    private fun showConsentForm() {
        if (!this@MainActivity.isFinishing) {
            try {
                consentForm.show()
            } catch (e: IllegalStateException) {
                // Happens when a user navigates away before calling consentForm.show()
                Log.e("onConsentFormLoaded()", e.toString())
            }
        }
    }


    /*
    * Called when the consent form is closed so we can handle the result.
    *
    * @[consentStatus] is a ConsentStatus value that describes the updated
    * consent status of the user.
    *
    * @[userPrefersAdFree] has a value of true when the user chose to use
    * a paid version of the app instead of viewing ads.
    */
    private fun handleConsentFormClosed(
        consentStatus: ConsentStatus, userPrefersAdFree: Boolean
    ) {
        if (userPrefersAdFree) {
            // Prompt user to buy ad-free version
        } else {
            processConsentStatus(consentStatus)
        }
    }

    /*
    * Save a value to SharePreferences on whether the user has consented to PERSONALISED
    * ads or not. We don't actually need this, as we can just call
    * ConsentInformation.getInstance(this).getConsentStatus() to get the status,
    * but I prefer this method as it allows for easier integration with
    * the billing library if adding a paid (ad-free) version in the future.
    */
    private fun setPersonalisedAdsAllowed(allowed: Boolean) {
        with(sharedPrefs.edit()) {
            putBoolean("allow_personalised_ads", allowed)
            apply()
        }
    }

    /*
    * Allows the updating of status via buttons for illustration/testing purposes.
    */
    private fun setupClickListeners() {

        /*
        * Reset the consent status.
        * We can then either show the form again or wait until
        * the app restarts, where it will show automatically with
        * [requestConsentInfoUpdate()] in [onCreate()]
        */
        btnReset.setOnClickListener {
            ConsentInformation.getInstance(this).reset()
            loadConsentForm()
        }

        /* Set the status to PERSONALISED */
        btnSetPersonalised.setOnClickListener {
            ConsentInformation.getInstance(this).consentStatus =
                ConsentStatus.PERSONALIZED
        }

        /* Set the status to NON_PERSONALISED */
        btnSetNonPersonalised.setOnClickListener {
            ConsentInformation.getInstance(this).consentStatus =
                ConsentStatus.NON_PERSONALIZED
        }

        /* Set the status to UNKNOWN */
        btnSetUnknownStatus.setOnClickListener {
            ConsentInformation.getInstance(this).consentStatus =
                ConsentStatus.UNKNOWN
        }

        btnSetLocationInEea.setOnClickListener {
            ConsentInformation.getInstance(this).debugGeography =
                DebugGeography.DEBUG_GEOGRAPHY_EEA
        }

        btnSetLocationNotInEea.setOnClickListener {
            ConsentInformation.getInstance(this).debugGeography =
                DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
        }

        btnSetLocationUnknown.setOnClickListener {
            ConsentInformation.getInstance(this).debugGeography =
                DebugGeography.DEBUG_GEOGRAPHY_DISABLED
        }
    }
}
