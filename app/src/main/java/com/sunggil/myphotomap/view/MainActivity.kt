package com.sunggil.myphotomap.view

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.sunggil.myphotomap.R
import com.sunggil.myphotomap.base.BaseMainActivity
import com.sunggil.myphotomap.databinding.ActivityMainBinding
import com.sunggil.myphotomap.model.DriveServiceHelper
import com.sunggil.myphotomap.model.MapData
import com.sunggil.myphotomap.viewmodel.MapViewModel
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class MainActivity : BaseMainActivity<ActivityMainBinding>() {
    lateinit var vmMapViewModel : MapViewModel
    lateinit var mMapFragment : SupportMapFragment
    lateinit var mMap : GoogleMap

    lateinit var mGoogleAccount : GoogleAccountCredential
    lateinit var mDrive : Drive
    lateinit var mDriveServiceHelper: DriveServiceHelper

    override fun getLayout(): Int {
        return R.layout.activity_main
    }

    override fun initViewModel() {
        vmMapViewModel = ViewModelProvider(this).get(MapViewModel::class.java)

        mMapFragment = supportFragmentManager.findFragmentById(R.id.googleMap) as SupportMapFragment
        mMapFragment.getMapAsync({
            mMap = it

            //첫화면 서울 고정?
            mMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(37.56, 126.97)))
            mMap.animateCamera(CameraUpdateFactory.zoomTo(10.0f))
        })

        vmMapViewModel.mapDatas.observe(this, Observer { datas ->
            Log.e("SG2","mapObserver changed!")
            if (::mMap.isInitialized) {
                for (data in datas) {

                    if (data.latlon != null) {
                        val markerOpt = MarkerOptions()
                        markerOpt.position(data.latlon!!)
                        markerOpt.title(data.title)
                        markerOpt.snippet(data.snippet)

                        mMap.addMarker(markerOpt)
                    }
                }
            }
        })

        mDataBinding.btAdd.setOnClickListener {
            var mapDatas = arrayListOf<MapData>()

            var m = MapData()
            m.latlon = LatLng(37.56, 126.97)
            m.title = "서울"
            m.snippet = "설명"
            mapDatas.add(m)

            m = MapData()
            m.latlon = LatLng(38.56, 127.97)
            m.title = "모름1"
            m.snippet = "설명"
            mapDatas.add(m)

            m = MapData()
            m.latlon = LatLng(37.86, 127.97)
            m.title = "모름2"
            m.snippet = "설명"
            mapDatas.add(m)

            m = MapData()
            m.latlon = LatLng(37.89, 127.96)
            m.title = "모름2"
            m.snippet = "설명"
            mapDatas.add(m)

            vmMapViewModel.mapDatas.value = mapDatas
        }

        mDataBinding.btList.setOnClickListener {
            if (::mDriveServiceHelper.isInitialized) {
                mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener { fileList ->
                        val builder = StringBuilder();

                        for (file in fileList.getFiles()) {
                            builder.append(file.getName()).append("\n");
                        }

                        Log.e("SG2","File List : " + builder.toString())

    //                    mFileTitleEditText.setText("File List");
    //                    mDocContentEditText.setText(fileNames);
    //
    //                    setReadOnlyMode();
                    }
                    .addOnFailureListener { exception ->
                        Log.e("SG2", "Unable to query files.", exception)
                    }
            }
        }

        mDataBinding.btLogin.setSize(SignInButton.SIZE_STANDARD);
        mDataBinding.btLogin.setOnClickListener {
            requestLogin(true)
        }

        mDataBinding.btLogout.setOnClickListener {
            requestLogin(false)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode == 9999 -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(baseContext, "OK", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(baseContext, "FAIL", Toast.LENGTH_SHORT).show()
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    handleSignInResult(data);
                }
            }

            requestCode == 9000 -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(baseContext, "OK", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(baseContext, "FAIL", Toast.LENGTH_SHORT).show()
                }
                var task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    val account : GoogleSignInAccount? = task.getResult(ApiException::class.java)


                    Log.e("SG2", "signInResult:success account=" + account);
                    // Signed in successfully, show authenticated UI.
//                    updateUI(account);
                } catch (e : ApiException) {
                    // The ApiException status code indicates the detailed failure reason.
                    // Please refer to the GoogleSignInStatusCodes class reference for more information.
                    Log.e("SG2", "signInResult:failed code=" + e.getStatusCode());
//                    updateUI(null);
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private fun handleSignInResult(result : Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
            .addOnSuccessListener{ googleAccount ->
                // Use the authenticated account to sign in to the Drive service.
                val credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(googleAccount.getAccount());
                val googleDriveService = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                .setApplicationName("Drive API Migration")
                .build();


                Log.e("SG2", "Success to sign in.")

                mDriveServiceHelper = DriveServiceHelper(googleDriveService)
            }
            .addOnFailureListener { exception ->
                Log.e("SG2", "Unable to sign in.", exception)
            }
    }

    private fun requestLogin(login : Boolean) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(Scopes.DRIVE_APPFOLDER))
            .requestEmail()
            .build();

        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        if (login) {
            val signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, 9999);
        } else {
            mGoogleSignInClient.signOut()
                .addOnSuccessListener {
                    Toast.makeText(baseContext, "LOGOUT!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(baseContext, "LOGOUT FAILED!", Toast.LENGTH_SHORT).show()
                }
        }
    }


}