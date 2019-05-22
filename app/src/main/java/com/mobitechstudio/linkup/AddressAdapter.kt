package com.mobitechstudio.linkup

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import kotlinx.android.synthetic.main.list_item_address.view.*

/**
 * Adapter class for Searched Address in MainActivity.kt,
 *
 * Controls Display of location search result
 */

class AddressAdapter(private val context: Context, private val dataSource: ArrayList<Address>) : BaseAdapter() {
    var tinyDB: TinyDB = TinyDB(context)
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater


    //get total found addresses
    override fun getCount(): Int {
        return dataSource.size
    }

    //get item at specific location in list of found addresses
    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    //get unique item id at specific location in list of found addresses
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    //
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // Get view for row item
        val rowView = inflater.inflate(R.layout.list_item_address, parent, false)
        val latitude = dataSource[position].latitude
        val longitude = dataSource[position].longitude
        rowView.tvAddressName.text = dataSource[position].displayName
        rowView.tvLatitude.text = latitude.toString()
        rowView.tvLongitude.text = longitude.toString()
        //hide longitude and latitude on list of searched addresses from Location IQ
        rowView.tvLatitude.visibility = View.GONE
        rowView.tvLongitude.visibility = View.GONE

        return rowView
    }



}