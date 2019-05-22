package com.mobitechstudio.linkup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
/*
* Google Map Activity to view user location on Map
* */
class UserMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    lateinit var tinyDB: TinyDB
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_map)
        tinyDB = TinyDB(this)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker on map using users latitude, longitude and address name
        val toUserLat = tinyDB.getDouble("toUserLat", 0.0)
        val toUserLon = tinyDB.getDouble("toUserLon", 0.0)
        val toUserAddress = tinyDB.getString("toUserAddress")
        val toUserName = tinyDB.getString("toUserName")
        val sydney = LatLng(toUserLat, toUserLon)
        mMap.addMarker(MarkerOptions()
            .position(sydney)
            .snippet(toUserName)
            .title(toUserAddress))
        mMap.moveCamera(
            CameraUpdateFactory
            .newLatLngZoom(sydney, 14f)
        )
    }
    private fun loadPopAd(){
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(premiumExpired=="Yes"){
            val intent = Intent(this, FacebookAdActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onDestroy() {
        loadPopAd()
        super.onDestroy()
    }
}
